# backend/app/main.py
import logging
import os
from contextlib import asynccontextmanager

import asyncpg
import firebase_admin
import sentry_sdk
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from firebase_admin import credentials
from sentry_sdk.integrations.asyncpg import AsyncpgIntegration
from sentry_sdk.integrations.fastapi import FastApiIntegration
from sentry_sdk.integrations.starlette import StarletteIntegration
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

# Import config, db base, routers, and specific endpoint modules
from app.core.config import settings
from app.db.base import close_db_pool, init_db_pool
from app.api.endpoints import lists as lists_router # Renamed import
from app.api.endpoints import users as users_router # Renamed import
# Note: places_router is likely not needed if routes are nested in lists_router

# Configure logging (can be moved to core/logging.py)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# --- Sentry Initialization ---
if settings.SENTRY_DSN:
    try:
        sentry_sdk.init(
            dsn=settings.SENTRY_DSN,
            traces_sample_rate=1.0, # Adjust in production
            profiles_sample_rate=1.0, # Adjust in production
            environment=settings.ENVIRONMENT,
            integrations=[
                StarletteIntegration(),
                FastApiIntegration(),
                AsyncpgIntegration(),
            ],
            send_default_pii=False
        )
        logger.info(f"Sentry initialized for environment: {settings.ENVIRONMENT}")
    except Exception as e:
        logger.error(f"Failed to initialize Sentry: {e}", exc_info=True)
else:
    logger.warning("SENTRY_DSN not found, Sentry integration disabled.")

# --- Firebase Admin SDK Initialization ---
try:
    # Construct absolute path from BASE_DIR if needed
    base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    cred_path = os.path.join(base_dir, settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH)

    if not os.path.exists(cred_path):
         logger.critical(f"Firebase service account key not found at: {cred_path}")
         raise FileNotFoundError(f"Service account key not found: {cred_path}")
    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred)
    logger.info("Firebase Admin SDK initialized successfully.")
except Exception as e:
    logger.critical(f"CRITICAL: Failed to initialize Firebase Admin SDK: {e}", exc_info=True)
    # Decide if the app should exit or continue without Firebase Auth
    exit(1) # Example: Exit if Firebase is critical

# --- Lifespan Manager ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Application startup...")
    await init_db_pool()
    logger.info("Application startup complete.")
    yield
    # Shutdown
    logger.info("Application shutdown...")
    await close_db_pool()
    logger.info("Application shutdown complete.")

# --- FastAPI App Initialization ---
app = FastAPI(
    title="Sesame App API",
    version="1.0.0",
    lifespan=lifespan,
    # openapi_url=f"{settings.API_V1_STR}/openapi.json" # Optional: Adjust OpenAPI URL
)

# --- Rate Limiting ---
limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# --- Middleware ---
# Optional: CORS Middleware if your frontend is on a different domain
# from fastapi.middleware.cors import CORSMiddleware
# if settings.BACKEND_CORS_ORIGINS:
#     app.add_middleware(
#         CORSMiddleware,
#         allow_origins=[str(origin) for origin in settings.BACKEND_CORS_ORIGINS],
#         allow_credentials=True,
#         allow_methods=["*"],
#         allow_headers=["*"],
#     )

@app.middleware("http")
async def log_request_middleware(request: Request, call_next):
    # Simple logging
    logger.info(f"RID: {request.headers.get('x-request-id')} - Request: {request.method} {request.url.path}")
    try:
        response = await call_next(request)
        logger.info(f"RID: {request.headers.get('x-request-id')} - Response: {request.method} {request.url.path} Status: {response.status_code}")
        return response
    except Exception as e:
        logger.error(f"RID: {request.headers.get('x-request-id')} - Error during request {request.url.path}: {e}", exc_info=True)
        # Re-raise to be caught by exception handlers
        raise e

@app.middleware("http")
async def add_security_headers(request: Request, call_next):
    response: Response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    # response.headers["Content-Security-Policy"] = "default-src 'self'" # Be cautious with CSP
    # HSTS is typically handled by a reverse proxy (like Nginx)
    # response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
    return response

# --- Exception Handlers ---
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.error(f"Validation error for request {request.method} {request.url}: {exc.errors()}")
    return JSONResponse(status_code=422, content={"detail": "Validation Error", "errors": exc.errors()})

@app.exception_handler(asyncpg.PostgresError)
async def db_exception_handler(request: Request, exc: asyncpg.PostgresError):
    logger.error(f"Database error during request {request.method} {request.url}: SQLSTATE={exc.sqlstate} - {exc}", exc_info=True)
    if isinstance(exc, asyncpg.exceptions.UniqueViolationError):
         return JSONResponse(status_code=409, content={"detail": "Resource already exists or conflicts."})
    # Add more specific handlers if needed (e.g., CannotConnectNowError handled in lifespan/deps)
    return JSONResponse(status_code=500, content={"detail": "A database error occurred."})

# Keep the generic handler as a fallback
@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    # Don't catch and report HTTPExceptions raised intentionally
    if isinstance(exc, HTTPException):
        # Use the status code and detail from the HTTPException
        return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})
    # Log and report unexpected errors
    logger.error(f"Unhandled exception during request {request.method} {request.url}: {exc}", exc_info=True)
    sentry_sdk.capture_exception(exc) # Manually capture if not automatically done
    return JSONResponse(status_code=500, content={"detail": "An internal server error occurred."})


# --- Include Routers ---
# Add prefixes as needed
app.include_router(users_router.router, prefix=settings.API_V1_STR, tags=["User", "Friends", "Notifications"])
app.include_router(lists_router.router, prefix=f"{settings.API_V1_STR}/lists", tags=["Lists", "Places", "Collaborators"])

# --- Root Endpoint (Optional) ---
@app.get("/")
async def read_root():
    return {"message": "Welcome to the Sesame App API!"}

# --- Sentry Debug Endpoint (Keep if useful during development) ---
if settings.ENVIRONMENT == "development":
    @app.get("/sentry-debug", tags=["Debug"])
    async def trigger_sentry_error():
        division_by_zero = 1 / 0
        return {"message": "This should not be reached."}