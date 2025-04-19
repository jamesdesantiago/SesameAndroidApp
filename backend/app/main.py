# backend/app/main.py
import logging
import os
from contextlib import asynccontextmanager

import asyncpg
import firebase_admin
import sentry_sdk
from fastapi import FastAPI, HTTPException, Request, Response, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from firebase_admin import credentials
from sentry_sdk.integrations.asyncpg import AsyncpgIntegration
from sentry_sdk.integrations.fastapi import FastApiIntegration
from sentry_sdk.integrations.starlette import StarletteIntegration
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

# --- Core App Imports ---
from app.core.config import settings  # Centralized settings
from app.db.base import close_db_pool, init_db_pool # DB Pool management
# --- API Router Imports ---
from app.api.endpoints import users as users_router
from app.api.endpoints import lists as lists_router
from app.api.endpoints import discovery as discovery_router # Import the new router
# Import other top-level routers if you create them (e.g., discovery)
# from app.api.endpoints import discovery as discovery_router

# Configure logging (Consider moving to a dedicated logging setup in core)
# logging.basicConfig(level=logging.INFO if settings.ENVIRONMENT == "production" else logging.DEBUG)
# For simplicity, keeping basic config here
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
logger.info(f"Starting application in {settings.ENVIRONMENT} mode...")

# --- Sentry Initialization ---
if settings.SENTRY_DSN and settings.ENVIRONMENT != "development": # Often disabled in dev
    try:
        logger.info("Initializing Sentry...")
        sentry_sdk.init(
            dsn=settings.SENTRY_DSN,
            traces_sample_rate=0.2, # Sample 20% of transactions in production
            profiles_sample_rate=0.1, # Sample 10% of profiles in production
            environment=settings.ENVIRONMENT,
            integrations=[
                StarletteIntegration(),
                FastApiIntegration(),
                AsyncpgIntegration(),
            ],
            send_default_pii=False
        )
        logger.info(f"Sentry initialized successfully for environment: {settings.ENVIRONMENT}")
    except Exception as e:
        logger.error(f"Failed to initialize Sentry: {e}", exc_info=True)
else:
    logger.warning("Sentry DSN not found or ENVIRONMENT is development, Sentry integration disabled.")

# --- Firebase Admin SDK Initialization ---
try:
    # Construct absolute path from BASE_DIR defined relative to config.py
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__))) # app/ directory
    project_root = os.path.dirname(base_dir) # backend/ directory
    cred_path = os.path.join(project_root, settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH)

    logger.info(f"Attempting to load Firebase credentials from: {cred_path}")
    if not os.path.exists(cred_path):
         logger.critical(f"Firebase service account key not found at: {cred_path}")
         # Depending on deployment, you might fetch credentials differently (e.g., env var)
         raise FileNotFoundError(f"Service account key not found: {cred_path}")

    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred)
    logger.info("Firebase Admin SDK initialized successfully.")
except Exception as e:
    logger.critical(f"CRITICAL: Failed to initialize Firebase Admin SDK: {e}", exc_info=True)
    # Decide if the app should exit or continue without Firebase Auth
    exit(1) # Exit if Firebase Auth is essential for the app to function

# --- Lifespan Manager (Handles DB Pool) ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    """Handles application startup and shutdown events."""
    logger.info("Application startup sequence initiated...")
    # Startup: Initialize Database Pool
    try:
        await init_db_pool()
    except Exception as e:
        logger.critical(f"Failed to initialize DB pool during startup: {e}", exc_info=True)
        # Optionally prevent app startup or handle gracefully
        # raise # Re-raise to potentially stop FastAPI startup?
    logger.info("Application startup complete.")
    yield # Application runs here
    # Shutdown: Close Database Pool
    logger.info("Application shutdown sequence initiated...")
    await close_db_pool()
    logger.info("Application shutdown complete.")

# --- FastAPI App Instance ---
app = FastAPI(
    title="Sesame App API",
    version="1.0.0", # Consider pulling from config or pyproject.toml
    lifespan=lifespan,
    # Configure OpenAPI documentation URL if using API prefix
    # openapi_url=f"{settings.API_V1_STR}/openapi.json",
    # docs_url=f"{settings.API_V1_STR}/docs",
    # redoc_url=f"{settings.API_V1_STR}/redoc",
)

# --- Rate Limiting ---
limiter = Limiter(key_func=get_remote_address) # Identify clients by IP address
app.state.limiter = limiter # Add limiter to app state for use in endpoints
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler) # Handle rate limit errors

# --- Middleware ---
# Optional: CORS Middleware (Uncomment and configure if needed)
# from fastapi.middleware.cors import CORSMiddleware
# if settings.BACKEND_CORS_ORIGINS:
#     app.add_middleware(
#         CORSMiddleware,
#         allow_origins=[str(origin).strip('/') for origin in settings.BACKEND_CORS_ORIGINS],
#         allow_credentials=True,
#         allow_methods=["*"],
#         allow_headers=["*"],
#     )
#     logger.info(f"CORS enabled for origins: {settings.BACKEND_CORS_ORIGINS}")

@app.middleware("http")
async def log_request_middleware(request: Request, call_next):
    """Logs basic request and response info."""
    # Generate or retrieve a request ID (useful for tracing)
    request_id = request.headers.get("x-request-id", "N/A") # Example: Use existing or generate one
    logger.info(f"RID:{request_id} START Request: {request.method} {request.url.path}")
    try:
        response = await call_next(request)
        # Add request ID to response headers for tracing
        response.headers["X-Request-ID"] = request_id
        logger.info(f"RID:{request_id} END Request: {request.method} {request.url.path} Status: {response.status_code}")
        return response
    except Exception as e:
        logger.error(f"RID:{request_id} Error during request {request.url.path}: {e}", exc_info=True)
        # Re-raise to be caught by exception handlers
        raise e

@app.middleware("http")
async def add_security_headers(request: Request, call_next):
    """Adds basic security headers to responses."""
    response: Response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    # Consider adding CSP header carefully if needed:
    # response.headers["Content-Security-Policy"] = "default-src 'self'; object-src 'none';"
    # HSTS header is best applied at the reverse proxy/load balancer level
    return response

# --- Global Exception Handlers ---
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    """Custom handler for HTTPExceptions to ensure consistent JSON format."""
    logger.warning(f"HTTPException: Status={exc.status_code}, Detail={exc.detail} for {request.method} {request.url.path}")
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail},
        headers=exc.headers,
    )

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """Handles Pydantic validation errors."""
    logger.error(f"Validation error for request {request.method} {request.url}: {exc.errors()}", exc_info=False) # Avoid logging potentially sensitive body
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={"detail": "Validation Error", "errors": exc.errors()}
    )

@app.exception_handler(asyncpg.PostgresError)
async def db_exception_handler(request: Request, exc: asyncpg.PostgresError):
    """Handles database errors, logging details and returning a generic 500."""
    logger.error(f"Database error during request {request.method} {request.url}: SQLSTATE={exc.sqlstate} - {exc}", exc_info=True)
    # Specific checks for user-facing messages (avoid exposing too much detail)
    if isinstance(exc, asyncpg.exceptions.UniqueViolationError):
        # Can customize based on constraint name if needed
        # constraint_name = exc.constraint_name
        return JSONResponse(status_code=status.HTTP_409_CONFLICT, content={"detail": "A related resource already exists or there is a conflict."})
    # Add more specific handlers if needed (e.g., foreign key violation)
    # Return generic error for other DB issues
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "A database error occurred processing your request."}
    )

@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    """Handles any other unexpected errors."""
    # HTTPExceptions are already handled above
    logger.error(f"Unhandled exception during request {request.method} {request.url}: {type(exc).__name__} - {exc}", exc_info=True)
    # Manually capture in Sentry if it wasn't automatically captured
    sentry_sdk.capture_exception(exc)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "An internal server error occurred."}
    )

# --- Include API Routers ---
# Add the main API routers with a prefix (e.g., /api/v1)
app.include_router(users_router.router, prefix=settings.API_V1_STR)
app.include_router(lists_router.router, prefix=f"{settings.API_V1_STR}/lists")
app.include_router(users_router.router, prefix=settings.API_V1_STR)
app.include_router(discovery_router.router, prefix=settings.API_V1_STR, tags=["Discovery"])
# Include discovery router if created for top-level /public, /search, /recent
# app.include_router(discovery_router.router, prefix=settings.API_V1_STR, tags=["Discovery"])

# --- Root Endpoint ---
@app.get("/", include_in_schema=False) # Exclude from OpenAPI docs if desired
async def read_root():
    """Provides a simple welcome message at the root."""
    return {"message": f"Welcome to the {app.title}!"}

# --- Sentry Debug Endpoint (Conditional) ---
if settings.ENVIRONMENT == "development":
    @app.get("/sentry-debug", tags=["Debug"], include_in_schema=True) # Include in dev docs
    async def trigger_sentry_error():
        """Endpoint to test Sentry integration by causing a division by zero error."""
        logger.info("Triggering Sentry test error...")
        try:
            division_by_zero = 1 / 0
        except ZeroDivisionError as e:
            # Capture manually to test capture_exception
            sentry_sdk.capture_exception(e)
            logger.info("ZeroDivisionError captured by Sentry.")
            # Re-raise to test automatic capture if not handled above
            raise e
        return {"message": "This should not be reached."}

# Note: The __main__ block for running with uvicorn directly is removed
# as it's better practice to run via the command line:
# uvicorn app.main:app --reload --host 0.0.0.0 --port 8000