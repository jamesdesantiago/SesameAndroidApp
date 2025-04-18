# main.py (Fully Refactored for asyncpg)

from fastapi import FastAPI, HTTPException, Header, Request, Query, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
# --- asyncpg Imports ---
import asyncpg # <<< Import asyncpg
from contextlib import asynccontextmanager # For lifespan

from firebase_admin import auth, credentials, initialize_app
from dotenv import load_dotenv
import os
import logging
from typing import Optional, List
import re
import math
from datetime import datetime # For Notification timestamp

import sentry_sdk
from sentry_sdk.integrations.fastapi import FastApiIntegration
from sentry_sdk.integrations.starlette import StarletteIntegration
from sentry_sdk.integrations.asyncpg import AsyncpgIntegration # <<< Add asyncpg integration

from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

import sentry_sdk 

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

SENTRY_DSN = os.getenv("SENTRY_DSN")
ENVIRONMENT = os.getenv("ENVIRONMENT", "development") # e.g., development, staging, productio

if SENTRY_DSN:
    try:
        sentry_sdk.init(
            dsn=SENTRY_DSN,
            # Enable performance monitoring by setting traces_sample_rate
            # Adjust sample rate in production (e.g., 0.1 for 10%)
            traces_sample_rate=1.0, # Sample all transactions in dev
            # Set profiles_sample_rate to profile code performance
            profiles_sample_rate=1.0, # Sample all profiles in dev
            environment=ENVIRONMENT,
            integrations=[
                StarletteIntegration(),
                FastApiIntegration(),
                AsyncpgIntegration(), # <<< Enable asyncpg integration
                # Add other integrations if needed (e.g., logging, sqlalchemy)
            ],
            # Optionally send PII, be careful with privacy regulations
            send_default_pii=False # Default is False, set True cautiously
        )
        logger.info(f"Sentry initialized for environment: {ENVIRONMENT}")
    except Exception as e:
        logger.error(f"Failed to initialize Sentry: {e}", exc_info=True)
else:
    logger.warning("SENTRY_DSN not found, Sentry integration disabled.")

# --- Global Database Pool Variable ---
db_pool: asyncpg.Pool = None # <<< Type hint for asyncpg Pool

# --- Lifespan Context Manager ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    # --- Startup: Initialize Pool ---
    global db_pool
    logger.info("App startup: Initializing asyncpg database pool...")
    retries = 5
    while retries > 0:
        try:
            # Connection string is an alternative, or pass parameters
            db_pool = await asyncpg.create_pool(
                host=os.getenv("DB_HOST", "sesame-app-db-do-user-16453789-0.e.db.ondigitalocean.com"),
                port=int(os.getenv("DB_PORT", "25060")), # <<< Port as int
                database=os.getenv("DB_NAME", "defaultdb"),
                user=os.getenv("DB_USER", "doadmin"),
                password=os.getenv("DB_PASSWORD"),
                ssl="require", # <<< asyncpg uses string 'require'
                min_size=2,   # Recommended min_size
                max_size=20,  # Adjust based on load/resources
                command_timeout=60 # Example timeout
                # Add connection retry options if needed via setup parameter
            )
            # Test connection during startup
            async with db_pool.acquire() as conn:
                 await conn.execute("SELECT 1")
            logger.info(f"Asyncpg database pool initialized and connection tested (min: 2, max: 20).")
            yield # <<< App runs after this point
            # If successful, break the loop
            break
        except (OSError, asyncpg.exceptions.PostgresConnectionError, ConnectionRefusedError) as e:
            retries -= 1
            logger.warning(f"Database pool initialization failed ({e}), retrying ({retries} left)...")
            if retries == 0:
                logger.critical("Database pool initialization failed after multiple retries.", exc_info=True)
                db_pool = None
                yield # Yield even on failure to allow FastAPI to start and potentially report errors
                break # Exit loop
            await asyncio.sleep(5) # Wait 5 seconds before retrying
        except Exception as e:
             logger.critical(f"CRITICAL: Unexpected error during database pool initialization: {e}", exc_info=True)
             db_pool = None
             yield # Yield even on failure
             break # Exit loop
    # finally block removed as pool closing should happen only if initialized successfully
    # Shutdown logic will be handled by FastAPI termination if pool exists

    # --- Cleanup logic executed after yield ---
    if db_pool:
        logger.info("App shutdown: Closing asyncpg database pool...")
        await db_pool.close() # <<< Use await pool.close()
        logger.info("Asyncpg database pool closed.")

# --- Initialize Limiter ---
# Defines how to identify the client (IP address is common)
limiter = Limiter(key_func=get_remote_address)

# --- Create FastAPI app WITH Lifespan ---
# Added title and version for documentation purposes
app = FastAPI(
    title="Sesame App API",
    version="1.0.0",
    redirect_slashes=False, # Keep trailing slashes distinct if needed
    lifespan=lifespan # <<< Assign lifespan manager
)

# --- Apply Limiter State and Exception Handler ---
# state is needed by limiter extensions
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# --- Pydantic Models (Input/Output Schemas) ---
# Keep all your Pydantic models exactly as they were. Example:
class UserBase(BaseModel):
    id: int
    email: str
    username: Optional[str] = None

class UserFollowInfo(UserBase):
    is_following: Optional[bool] = None

class PlaceItem(BaseModel):
    id: int
    name: str
    address: str
    latitude: float
    longitude: float
    rating: Optional[str] = None
    visitStatus: Optional[str] = None
    notes: Optional[str] = None

class ListCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=100)
    description: Optional[str] = Field(None, max_length=500)
    isPrivate: bool = False

class ListViewResponse(BaseModel):
    id: int
    name: str
    description: Optional[str] = None
    isPrivate: bool
    place_count: int = 0

class ListDetailResponse(BaseModel):
    id: int
    name: str
    description: Optional[str] = None
    isPrivate: bool
    collaborators: List[str] = []
    # places: List[PlaceItem] = [] # Removed based on previous update

    class Config:
        orm_mode = True # Keep if needed for ORM-like behavior

class PaginatedListResponse(BaseModel):
    items: List[ListViewResponse]
    page: int
    page_size: int
    total_items: int
    total_pages: int

class PaginatedUserResponse(BaseModel):
    items: List[UserFollowInfo]
    page: int
    page_size: int
    total_items: int
    total_pages: int

class PaginatedPlaceResponse(BaseModel):
    items: List[PlaceItem]
    page: int
    page_size: int
    total_items: int
    total_pages: int

class ListUpdate(BaseModel):
    name: Optional[str] = Field(None, min_length=1, max_length=100)
    isPrivate: Optional[bool] = None

class CollaboratorAdd(BaseModel):
    email: str = Field(..., pattern=r"[^@]+@[^@]+\.[^@]+")

class PlaceCreate(BaseModel):
    placeId: str # Google Place ID or similar external ID
    name: str = Field(..., max_length=200)
    address: str = Field(..., max_length=300)
    latitude: float
    longitude: float
    rating: Optional[str] = None
    notes: Optional[str] = Field(None, max_length=1000)
    visitStatus: Optional[str] = None

class PlaceUpdate(BaseModel):
    notes: Optional[str] = Field(None, max_length=1000)

class UsernameSet(BaseModel):
    username: str = Field(..., min_length=1, max_length=30, pattern=r'^[a-zA-Z0-9._]+$')

class UsernameCheckResponse(BaseModel):
    needsUsername: bool

class UsernameSetResponse(BaseModel):
    message: str

class NotificationItem(BaseModel):
    id: int
    title: str
    message: str
    is_read: bool = Field(..., alias="isRead")
    timestamp: datetime

    class Config:
        orm_mode = True
        allow_population_by_field_name = True

class PaginatedNotificationResponse(BaseModel):
    items: List[NotificationItem]
    page: int
    page_size: int
    total_items: int
    total_pages: int

# --- End Pydantic Models ---



# --- Rate Limiting Decorators ---

# Helper function to get user identifier after auth (if needed for user-specific limits)
# This assumes verify_firebase_token returns a dict with 'uid'
def get_user_identifier(request: Request) -> str:
    try:
        auth_header = request.headers.get("authorization")
        if auth_header:
            token = verify_firebase_token(auth_header) # Reuse your existing function
            user_id = token.get("uid")
            if user_id:
                return f"user:{user_id}" # Prefix to distinguish from IP
    except HTTPException: # Handle cases where token is invalid/missing
        pass
    # Fallback to IP if no valid user ID found
    return get_remote_address(request)


# --- Exception Handlers ---
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.error(f"Validation error for request {request.url}: {exc.errors()}")
    return JSONResponse(status_code=422, content={"detail": "Validation Error", "errors": exc.errors()})

@app.exception_handler(asyncpg.PostgresError) # Catch specific asyncpg errors
async def db_exception_handler(request: Request, exc: asyncpg.PostgresError):
    # Log specific asyncpg error details if helpful (e.g., exc.sqlstate)
    logger.error(f"Database error during request {request.url}: SQLSTATE={exc.sqlstate} - {exc}", exc_info=True)
    # Check for specific SQLSTATE codes if needed
    if isinstance(exc, asyncpg.exceptions.CannotConnectNowError):
        return JSONResponse(status_code=503, content={"detail": "Database service temporarily unavailable. Please try again later."})
    if isinstance(exc, asyncpg.exceptions.UniqueViolationError):
         return JSONResponse(status_code=409, content={"detail": "Resource already exists or conflicts."})
    # Generic DB error for others
    return JSONResponse(status_code=500, content={"detail": "A database error occurred."})

@app.exception_handler(Exception) # Generic fallback
async def generic_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception during request {request.url}: {exc}", exc_info=True)
    return JSONResponse(status_code=500, content={"detail": "An internal server error occurred."})
# --- End Exception Handlers ---


# --- Firebase Admin SDK Initialization ---
try:
    cred_path = os.getenv("FIREBASE_SERVICE_ACCOUNT_KEY", "service-account.json")
    if not os.path.exists(cred_path):
         logger.critical(f"Firebase service account key not found at: {cred_path}")
         raise FileNotFoundError(f"Service account key not found: {cred_path}")
    cred = credentials.Certificate(cred_path)
    initialize_app(cred)
    logger.info("Firebase Admin SDK initialized successfully.")
except Exception as e:
    logger.critical(f"CRITICAL: Failed to initialize Firebase Admin SDK: {e}", exc_info=True)
    exit(1)
# --- End Firebase Init ---


# --- Middleware ---
@app.middleware("http")
async def log_request_middleware(request: Request, call_next):
    headers_to_log = {k: v for k, v in request.headers.items() if k.lower() != 'authorization'}
    auth_present = 'authorization' in request.headers
    logger.info(f"Incoming Request: {request.method} {request.url.path} | Auth Present: {auth_present} | Headers (Sanitized): {headers_to_log}")
    try:
        response = await call_next(request)
        logger.info(f"Outgoing Response: {request.method} {request.url.path} | Status: {response.status_code}")
        return response
    except Exception as e:
        logger.error(f"Error during middleware processing for {request.url.path}: {e}", exc_info=True)
        raise e # Re-raise to be caught by global handlers

@app.middleware("http")
async def add_security_headers(request: Request, call_next):
    response: Response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    # Add others cautiously, especially CSP
    # response.headers["Content-Security-Policy"] = "default-src 'self'" # Example
    # HSTS is better handled by proxy if possible
    # response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
    return response
# --- End Middleware ---


# --- Async Context Manager for DB Connection ---
@asynccontextmanager
async def get_db_conn():
    global db_pool
    if not db_pool:
        logger.error("Database pool is not available.")
        raise HTTPException(status_code=503, detail="Database service temporarily unavailable.")

    # Acquire connection using async with, handles release automatically
    async with db_pool.acquire() as conn:
        # Start a transaction using async with, handles commit/rollback
        async with conn.transaction():
            yield conn
# --- End DB Connection Manager ---


# --- Helper Functions (asyncpg versions) ---

def verify_firebase_token(authorization: str) -> dict:
    """Verifies the Firebase ID token from the Authorization header."""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization header format")
    token = authorization.replace("Bearer ", "")
    try:
        decoded_token = auth.verify_id_token(token)
        logger.debug(f"Token verified for uid: {decoded_token.get('uid')}")
        return decoded_token
    except Exception as e:
        logger.error(f"Firebase token verification failed: {str(e)}", exc_info=True)
        raise HTTPException(status_code=401, detail=f"Invalid or expired token: {str(e)}")


async def get_owner_id(firebase_token: dict, conn: asyncpg.Connection) -> tuple[int, bool]:
    """Gets user ID from DB based on firebase token, creating if necessary."""
    firebase_uid = firebase_token.get("uid")
    email = firebase_token.get("email")

    if not firebase_uid:
        logger.error("Firebase token missing 'uid'.")
        raise HTTPException(status_code=400, detail="Invalid token data (missing uid)")
    if not email:
        logger.error("Firebase token missing 'email'.")
        raise HTTPException(status_code=400, detail="Invalid token data (missing email)")

    # Check by firebase_uid using asyncpg methods
    user_record = await conn.fetchrow("SELECT id, username FROM users WHERE firebase_uid = $1", firebase_uid)
    if user_record:
        user_id = user_record['id']
        username = user_record['username']
        logger.debug(f"User found by firebase_uid: {user_id}")
        return user_id, username is None

    # Check by email
    user_record = await conn.fetchrow("SELECT id, firebase_uid, username FROM users WHERE email = $1", email)
    if user_record:
        user_id = user_record['id']
        existing_firebase_uid = user_record['firebase_uid']
        username = user_record['username']
        logger.debug(f"User found by email: {user_id}. Existing UID: {existing_firebase_uid}, Token UID: {firebase_uid}")
        if existing_firebase_uid != firebase_uid:
            logger.warning(f"Updating firebase_uid for user {user_id} from {existing_firebase_uid} to {firebase_uid}")
            # Execute UPDATE; transaction handles commit
            await conn.execute("UPDATE users SET firebase_uid = $1 WHERE id = $2", firebase_uid, user_id)
        return user_id, username is None

    # Create new user
    logger.info(f"Creating new user entry for email: {email}, firebase_uid: {firebase_uid}")
    # fetchval gets the first column of the first row
    user_id = await conn.fetchval(
        "INSERT INTO users (email, firebase_uid) VALUES ($1, $2) RETURNING id",
        email, firebase_uid
    )
    if not user_id:
         logger.error(f"Failed to insert new user for email {email}")
         raise HTTPException(status_code=500, detail="Failed to create user record")
    # Transaction handles commit
    logger.info(f"New user created with ID: {user_id}")
    return user_id, True


async def get_collaborators(list_id: int, conn: asyncpg.Connection) -> List[str]:
    """Fetches collaborator emails for a given list ID."""
    rows = await conn.fetch(
        """
        SELECT u.email
        FROM list_collaborators lc
        JOIN users u ON lc.user_id = u.id
        WHERE lc.list_id = $1
        """,
        list_id
    )
    return [row["email"] for row in rows]


async def check_list_ownership(conn: asyncpg.Connection, list_id: int, user_id: int):
    """Checks if the user owns the list. Raises HTTPException if not owner or list not found."""
    is_owner = await conn.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2)", list_id, user_id)
    if not is_owner:
        logger.warning(f"Ownership check failed: User {user_id} does not own list {list_id}")
        list_exists = await conn.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
        if list_exists:
            raise HTTPException(status_code=403, detail="Not authorized for this list")
        else:
            raise HTTPException(status_code=404, detail="List not found")


async def check_list_access(conn: asyncpg.Connection, list_id: int, user_id: int):
    """Raises HTTPException if user is not owner or collaborator."""
    has_access = await conn.fetchval("""
        SELECT EXISTS (
            SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2
            UNION ALL
            SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2
            LIMIT 1
        )
    """, list_id, user_id, list_id, user_id)

    if not has_access:
        list_exists = await conn.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
        if list_exists:
            logger.warning(f"List access check failed: User {user_id} cannot access list {list_id}.")
            raise HTTPException(status_code=403, detail="Access denied to this list")
        else:
            logger.warning(f"List access check failed: List {list_id} not found.")
            raise HTTPException(status_code=404, detail="List not found")
    logger.debug(f"List access check passed for user {user_id} on list {list_id}")

# --- End Helper Functions ---


# === API Endpoints (Refactored for asyncpg) ===

# --- User Specific Endpoints ---
@limiter.limit("7/minute") # Default limit: 7 per minute per IP/user
@app.get("/users/check-username", response_model=UsernameCheckResponse, tags=["User"])
async def check_username(authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, needs_username = await get_owner_id(firebase_token, conn)
            return {"needsUsername": needs_username}
    except HTTPException as he:
        raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB Error during username check: {db_err}", exc_info=True)
        raise HTTPException(status_code=503, detail="Database error during username check.")
    except Exception as e:
        logger.error("Unexpected error during username check", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error during username check")

@app.post("/users/set-username", response_model=UsernameSetResponse, status_code=200, tags=["User"])
@limiter.limit("2/minute") # Stricter limit: 2 per minute per IP/user
async def set_username(data: UsernameSet, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)

            # Check if username exists (case-insensitive) excluding the current user
            existing_user = await conn.fetchrow(
                "SELECT id FROM users WHERE LOWER(username) = LOWER($1) AND id != $2",
                data.username, user_id
            )
            if existing_user:
                raise HTTPException(status_code=409, detail="Username already taken")

            # Attempt to update
            status = await conn.execute(
                "UPDATE users SET username = $1 WHERE id = $2",
                data.username, user_id
            )
            # status is like 'UPDATE 1' on success
            if status == 'UPDATE 0':
                logger.error(f"Failed to update username for user_id {user_id} - rowcount 0")
                raise HTTPException(status_code=404, detail="User not found for update")

            logger.info(f"Username successfully set for user_id {user_id}")
            # Transaction committed automatically by context manager
    except HTTPException as he:
        raise he
    except asyncpg.exceptions.UniqueViolationError as uv_err:
        logger.warning(f"UniqueViolation setting username for {data.username}: {uv_err}")
        raise HTTPException(status_code=409, detail="Username already taken (concurrent update?)")
    except asyncpg.PostgresError as db_err:
        logger.error(f"Database error setting username for user (inferred id): {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error setting username")
    except Exception as e:
        logger.error(f"Unexpected error setting username for user (inferred id): {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error setting username")

    return {"message": "Username set successfully"}


# --- Friends/Followers Endpoints ---

@app.get("/users/following", response_model=PaginatedUserResponse, tags=["Friends"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def get_following(
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100)
):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)

            # Get total count
            total_items_record = await conn.fetchrow("SELECT COUNT(*) as count FROM user_follows WHERE follower_id = $1", user_id)
            total_items = total_items_record['count'] if total_items_record else 0
            total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

            items = []
            if total_items > 0 and page <= total_pages:
                offset = (page - 1) * page_size
                following_records = await conn.fetch(
                    """
                    SELECT u.id, u.email, u.username
                    FROM user_follows uf
                    JOIN users u ON uf.followed_id = u.id
                    WHERE uf.follower_id = $1
                    ORDER BY uf.created_at DESC
                    LIMIT $2 OFFSET $3
                    """,
                    user_id, page_size, offset
                )
                items = [UserFollowInfo(**row) for row in following_records]

            return PaginatedUserResponse(
                items=items, page=page, page_size=page_size,
                total_items=total_items, total_pages=total_pages
            )
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error fetching following for user (inferred id): {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching following list")
    except Exception as e:
        logger.error(f"Unexpected error fetching following for user (inferred id): {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching following list")


@app.get("/users/followers", response_model=PaginatedUserResponse, tags=["Friends"])
@limiter.limit("5/minute") # Default limit: 5 per minute per IP/user
async def get_followers(
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100)
):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)

            # Get total count
            total_items_record = await conn.fetchrow("SELECT COUNT(*) as count FROM user_follows WHERE followed_id = $1", user_id)
            total_items = total_items_record['count'] if total_items_record else 0
            total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

            items = []
            if total_items > 0 and page <= total_pages:
                offset = (page - 1) * page_size
                follower_records = await conn.fetch(
                    """
                    SELECT u.id, u.email, u.username
                    FROM user_follows uf
                    JOIN users u ON uf.follower_id = u.id
                    WHERE uf.followed_id = $1
                    ORDER BY uf.created_at DESC
                    LIMIT $2 OFFSET $3
                    """,
                    user_id, page_size, offset
                )
                items = [UserFollowInfo(**row) for row in follower_records]

            return PaginatedUserResponse(
                items=items, page=page, page_size=page_size,
                total_items=total_items, total_pages=total_pages
            )
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error fetching followers for user (inferred id): {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching followers list")
    except Exception as e:
        logger.error(f"Unexpected error fetching followers for user (inferred id): {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching followers list")


@app.get("/users/search", response_model=PaginatedUserResponse, tags=["Friends"])
@limiter.limit("30/minute") # Default limit: 30 per minute per IP/user
async def search_users(
    # 'email' param name is kept for consistency with existing code, but searches both
    email: str = Query(..., min_length=1, description="Email or username fragment to search for."),
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(10, ge=1, le=50)
):
    firebase_token = verify_firebase_token(authorization)
    search_term = f"%{email}%"
    try:
        async with get_db_conn() as conn:
            # Get the ID of the user performing the search
            authenticated_user_id, _ = await get_owner_id(firebase_token, conn)

            # --- Query Modification ---
            # 1. Get total count (JOIN not needed here)
            count_record = await conn.fetchrow(
                """
                SELECT COUNT(*) as count
                FROM users
                WHERE (email ILIKE $1 OR username ILIKE $1) AND id != $2
                """,
                search_term, authenticated_user_id
            )
            total_items = count_record['count'] if count_record else 0
            total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

            items = []
            if total_items > 0 and page <= total_pages:
                offset = (page - 1) * page_size
                # 2. Get paginated items WITH is_following status
                search_records = await conn.fetch(
                    """
                    SELECT
                        u.id,
                        u.email,
                        u.username,
                        -- Calculate is_following using LEFT JOIN
                        EXISTS (
                            SELECT 1 FROM user_follows uf
                            WHERE uf.follower_id = $1 -- Authenticated user's ID
                              AND uf.followed_id = u.id
                        ) AS is_following
                    FROM users u
                    WHERE (u.email ILIKE $2 OR u.username ILIKE $2) -- Search term
                      AND u.id != $1 -- Exclude self (Authenticated user's ID)
                    ORDER BY u.username ASC, u.email ASC
                    LIMIT $3 OFFSET $4
                    """,
                    authenticated_user_id, # $1
                    search_term,           # $2
                    page_size,             # $3
                    offset                 # $4
                )
                # Map results to the Pydantic model that includes is_following
                items = [UserFollowInfo(**row) for row in search_records]

            return PaginatedUserResponse(
                items=items, page=page, page_size=page_size,
                total_items=total_items, total_pages=total_pages
            )
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error searching users with term '{email}': {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error searching users")
    except Exception as e:
        logger.error(f"Unexpected error searching users with term '{email}': {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error searching users")

@app.post("/users/{user_id}/follow", status_code=201, tags=["Friends"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def follow_user(user_id: int, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            follower_id, _ = await get_owner_id(firebase_token, conn)
            if follower_id == user_id:
                raise HTTPException(status_code=400, detail="Cannot follow yourself")

            # Check if target user exists
            target_exists = await conn.fetchval("SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)", user_id)
            if not target_exists:
                raise HTTPException(status_code=404, detail="User to follow not found")

            # Attempt insert
            try:
                await conn.execute(
                    "INSERT INTO user_follows (follower_id, followed_id, created_at) VALUES ($1, $2, NOW())",
                    follower_id, user_id
                )
                logger.info(f"User {follower_id} followed user {user_id}")
                # Transaction commits automatically
            except asyncpg.exceptions.UniqueViolationError:
                logger.warning(f"User {follower_id} already following user {user_id}")
                # Return 200 OK for idempotency
                return {"message": "Already following this user"}

    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error processing follow request for user {user_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error processing follow request")
    except Exception as e:
        logger.error(f"Unexpected error processing follow request for user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error processing follow request")

    return {"message": "User followed"}


@app.delete("/users/{user_id}/follow", status_code=200, tags=["Friends"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def unfollow_user(user_id: int, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            follower_id, _ = await get_owner_id(firebase_token, conn)

            # Execute DELETE
            status = await conn.execute(
                "DELETE FROM user_follows WHERE follower_id = $1 AND followed_id = $2",
                follower_id, user_id
            )

            # status is like 'DELETE 1' or 'DELETE 0'
            if status == 'DELETE 0':
                # Check if target user exists to differentiate 404 user vs 404 relationship
                target_exists = await conn.fetchval("SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)", user_id)
                if not target_exists:
                    raise HTTPException(status_code=404, detail="User to unfollow not found")
                else:
                    logger.warning(f"User {follower_id} tried to unfollow user {user_id}, but was not following.")
                    return {"message": "Not following this user"} # Return 200 OK

            logger.info(f"User {follower_id} unfollowed user {user_id}")
            # Transaction commits automatically
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error unfollowing user {user_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error unfollowing user")
    except Exception as e:
        logger.error(f"Unexpected error unfollowing user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error unfollowing user")

    return {"message": "User unfollowed"}


# --- List Endpoints ---

@app.get("/lists", response_model=PaginatedListResponse, tags=["Lists"])
@limiter.limit("15/minute") # Default limit: 15 per minute per IP/user
async def get_lists(
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100)
    # publicOnly: Optional[bool] = Query(None, alias="public") # Example filter
):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)

            # Base query and params (using List)
            params = [user_id]
            where_clauses = ["l.owner_id = $1"]
            param_index = 2 # Start next param index at $2

            # --- Example Filter ---
            # if publicOnly is not None:
            #     where_clauses.append(f"l.is_private = ${param_index}")
            #     params.append(not publicOnly)
            #     param_index += 1

            where_sql = " AND ".join(where_clauses)

            # Get total count with filters
            count_sql = f"SELECT COUNT(*) as count FROM lists l WHERE {where_sql}"
            total_items_record = await conn.fetchrow(count_sql, *params) # Unpack params
            total_items = total_items_record['count'] if total_items_record else 0
            total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

            items = []
            if total_items > 0 and page <= total_pages:
                offset = (page - 1) * page_size
                # Add LIMIT and OFFSET placeholders and params
                items_sql = f"""
                    SELECT
                        l.id, l.name, l.description, l.is_private,
                        (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
                    FROM lists l
                    WHERE {where_sql}
                    ORDER BY l.created_at DESC
                    LIMIT ${param_index} OFFSET ${param_index + 1}
                """
                params.extend([page_size, offset])
                list_records = await conn.fetch(items_sql, *params) # Unpack all params

                # Map records to Pydantic model
                items = [ListViewResponse(
                    id=row['id'], name=row['name'], description=row['description'],
                    isPrivate=row['is_private'] if row['is_private'] is not None else False,
                    place_count=row.get('place_count', 0)
                ) for row in list_records]

            return PaginatedListResponse(
                items=items, page=page, page_size=page_size,
                total_items=total_items, total_pages=total_pages
            )
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error fetching lists for user (inferred id): {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching lists")
    except Exception as e:
        logger.error(f"Unexpected error fetching lists for user (inferred id): {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching lists")


@app.post("/lists", response_model=ListDetailResponse, status_code=201, tags=["Lists"])
@limiter.limit("5/minute") # Default limit: 5 per minute per IP/user
async def create_list(list_data: ListCreate, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)

            # Execute INSERT and return the created row data
            created_list_record = await conn.fetchrow(
                """
                INSERT INTO lists (name, description, owner_id, created_at, is_private)
                VALUES ($1, $2, $3, NOW(), $4)
                RETURNING id, name, description, is_private
                """,
                list_data.name, list_data.description, user_id, list_data.isPrivate
            )

            if not created_list_record:
                raise HTTPException(status_code=500, detail="Failed to create list (no record returned)")

            logger.info(f"List created with ID {created_list_record['id']} by user {user_id}")

            # Map record to response model
            return ListDetailResponse(
                 id=created_list_record['id'],
                 name=created_list_record['name'],
                 description=created_list_record['description'],
                 isPrivate=created_list_record['is_private'] if created_list_record['is_private'] is not None else False,
                 collaborators=[], # New list has no collaborators yet
                 # places=[] # ListDetailResponse doesn't include places
            )
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error creating list for user (inferred id): {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error creating list")
    except Exception as e:
        logger.error(f"Unexpected error creating list for user (inferred id): {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error creating list")


@app.delete("/lists/{list_id}", status_code=204, tags=["Lists"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def delete_list(list_id: int, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            await check_list_ownership(conn, list_id, user_id) # Check ownership

            # Assuming ON DELETE CASCADE handles places, collaborators etc.
            status = await conn.execute("DELETE FROM lists WHERE id = $1", list_id)

            if status == 'DELETE 0':
                # Should not happen if check_list_ownership passed, but handle defensively
                logger.warning(f"List {list_id} not found for delete after ownership check.")
                raise HTTPException(status_code=404, detail="List not found for deletion") # Or 500?

            logger.info(f"List {list_id} deleted successfully by user {user_id}")
            # Transaction commits automatically

        # Return Response with 204 status code explicitly
        return Response(status_code=204)
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error deleting list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error deleting list")
    except Exception as e:
        logger.error(f"Unexpected error deleting list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error deleting list")


@app.patch("/lists/{list_id}", response_model=ListDetailResponse, tags=["Lists"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def update_list(list_id: int, update_data: ListUpdate, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    update_fields = update_data.model_dump(exclude_unset=True)
    if not update_fields:
         raise HTTPException(status_code=400, detail="No update fields provided")

    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            await check_list_ownership(conn, list_id, user_id) # Check ownership

            # Build SET clause dynamically
            set_clauses = []
            params = []
            param_index = 1
            if "name" in update_fields: # Check against the filtered dict
                set_clauses.append(f"name = ${param_index}")
                params.append(update_fields["name"])
                param_index += 1
            if "isPrivate" in update_fields:
                set_clauses.append(f"is_private = ${param_index}")
                params.append(update_fields["isPrivate"])
                param_index += 1

            # Add list_id as the final parameter for the WHERE clause
            params.append(list_id)
            sql = f"UPDATE lists SET {', '.join(set_clauses)} WHERE id = ${param_index} RETURNING id, name, description, is_private"

            updated_list_record = await conn.fetchrow(sql, *params) # Unpack params

            if not updated_list_record:
                logger.error(f"List {list_id} update failed despite ownership check.")
                raise HTTPException(status_code=500, detail="List update failed after ownership check")

            logger.info(f"List {list_id} updated successfully by user {user_id}")

            # Fetch collaborators for the response
            collaborators = await get_collaborators(list_id, conn)

            # Map record to response model
            return ListDetailResponse(
                 id=updated_list_record['id'],
                 name=updated_list_record['name'],
                 description=updated_list_record['description'],
                 isPrivate=updated_list_record['is_private'] if updated_list_record['is_private'] is not None else False,
                 collaborators=collaborators,
                 # places=[] # Not included in this response
            )
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error updating list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error updating list")
    except Exception as e:
        logger.error(f"Unexpected error updating list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error updating list")


@app.get("/lists/{list_id}", response_model=ListDetailResponse, tags=["Lists"])
@limiter.limit("15/minute") # Default limit: 15 per minute per IP/user
async def get_list_detail(list_id: int, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            await check_list_access(conn, list_id, user_id) # Use access check

            # Fetch list metadata ONLY
            list_record = await conn.fetchrow(
                "SELECT l.id, l.name, l.description, l.is_private FROM lists l WHERE l.id = $1",
                list_id
            )
            if not list_record:
                logger.warning(f"List {list_id} not found after access check passed.")
                raise HTTPException(status_code=404, detail="List not found")

            # Fetch collaborators ONLY
            collaborators = await get_collaborators(list_id, conn)
            logger.debug(f"Fetched {len(collaborators)} collaborators for list {list_id}")

            # Return response WITHOUT places list
            return ListDetailResponse(
                id=list_record['id'],
                name=list_record['name'],
                description=list_record['description'],
                isPrivate=list_record['is_private'] if list_record['is_private'] is not None else False,
                collaborators=collaborators,
            )
    except HTTPException as he:
        raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error fetching detail for list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching list metadata")
    except Exception as e:
        logger.error(f"Unexpected error fetching detail for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching list metadata")


# --- Places and Collaborators within Lists ---

@app.post("/lists/{list_id}/collaborators", status_code=201, tags=["Lists", "Collaborators"], response_model=UsernameSetResponse)
@limiter.limit("20/minute") # Default limit: 20 per minute per IP/user
async def add_collaborator(list_id: int, collaborator: CollaboratorAdd, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            await check_list_ownership(conn, list_id, user_id) # Only owner adds collaborators

            # Find collaborator user ID or create user if not exists
            collaborator_user_id = await conn.fetchval("SELECT id FROM users WHERE email = $1", collaborator.email)
            if not collaborator_user_id:
                logger.info(f"Collaborator email {collaborator.email} not found, creating user.")
                # Create user in a separate transaction block if needed, or rely on main transaction
                collaborator_user_id = await conn.fetchval("INSERT INTO users (email) VALUES ($1) RETURNING id", collaborator.email)
                if not collaborator_user_id:
                    raise HTTPException(status_code=500, detail="Failed to create collaborator user record")

            # Insert into list_collaborators
            try:
                await conn.execute("INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2)", list_id, collaborator_user_id)
                logger.info(f"User {collaborator_user_id} added as collaborator to list {list_id}")
            except asyncpg.exceptions.UniqueViolationError:
                raise HTTPException(status_code=409, detail="Collaborator already added")

    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error processing add collab: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error adding collaborator")
    except Exception as e:
        logger.error(f"Unexpected error adding collab: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Server error adding collaborator")

    return UsernameSetResponse(message="Collaborator added")


@app.post("/lists/{list_id}/places", response_model=PlaceItem, status_code=201, tags=["Lists", "Places"])
@limiter.limit("40/minute") # Default limit: 40 per minute per IP/user
async def add_place(list_id: int, place: PlaceCreate, authorization: str = Header(..., alias="Authorization")):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            await check_list_access(conn, list_id, user_id) # Owner OR Collaborator can add

            try:
                created_place_record = await conn.fetchrow(
                    """
                    INSERT INTO places (list_id, place_id, name, address, latitude, longitude, rating, notes, visit_status, created_at)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
                    RETURNING id, name, address, latitude, longitude, rating, notes, visit_status
                    """,
                    list_id, place.placeId, place.name, place.address, place.latitude, place.longitude,
                    place.rating, place.notes, place.visitStatus
                )
                if not created_place_record:
                    raise HTTPException(status_code=500, detail="Failed to add place (no record returned)")

                logger.info(f"Place '{place.name}' (DB ID: {created_place_record['id']}) added to list {list_id} by user {user_id}")
                # Map record to PlaceItem response model
                return PlaceItem(
                    id=created_place_record['id'], name=created_place_record['name'],
                    address=created_place_record['address'], latitude=created_place_record['latitude'],
                    longitude=created_place_record['longitude'], rating=created_place_record['rating'],
                    notes=created_place_record['notes'], visitStatus=created_place_record['visit_status']
                )
            except asyncpg.exceptions.UniqueViolationError:
                logger.warning(f"Place with external ID '{place.placeId}' already exists in list {list_id}")
                raise HTTPException(status_code=409, detail="Place already exists in this list")
            except asyncpg.exceptions.CheckViolationError as e:
                logger.warning(f"Check constraint violation adding place to list {list_id}: {e}")
                raise HTTPException(status_code=400, detail=f"Invalid data provided for place: {e}")

    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error processing add place request: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="DB error adding place")
    except Exception as e:
        logger.error(f"Unexpected error adding place: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error adding place")


@app.patch("/lists/{list_id}/places/{place_id}", response_model=PlaceItem, tags=["Lists", "Places"])
@limiter.limit("20/minute") # Default limit: 20 per minute per IP/user
async def update_place( list_id: int, place_id: int, place_update: PlaceUpdate, authorization: str = Header(..., alias="Authorization") ):
    firebase_token = verify_firebase_token(authorization)
    update_fields = place_update.model_dump(exclude_unset=True)
    if not update_fields:
        raise HTTPException(status_code=400, detail="No update fields provided")

    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            await check_list_access(conn, list_id, user_id) # Check list access first

            # Check if place exists in this list
            place_exists = await conn.fetchval("SELECT EXISTS (SELECT 1 FROM places WHERE id = $1 AND list_id = $2)", place_id, list_id)
            if not place_exists:
                raise HTTPException(status_code=404, detail="Place not found in this list")

            # Perform update (only notes currently in PlaceUpdate model)
            if "notes" in update_fields:
                updated_place_record = await conn.fetchrow(
                    """
                    UPDATE places SET notes = $1 WHERE id = $2
                    RETURNING id, name, address, latitude, longitude, rating, notes, visit_status
                    """,
                    update_fields["notes"], place_id
                )
                if not updated_place_record:
                    logger.error(f"Failed to update place {place_id} notes after existence check.")
                    raise HTTPException(status_code=500, detail="Failed to update place notes")

                logger.info(f"Updated notes for place {place_id} in list {list_id} by user {user_id}")
                # Map response
                return PlaceItem(
                     id=updated_place_record['id'], name=updated_place_record['name'],
                     address=updated_place_record['address'], latitude=updated_place_record['latitude'],
                     longitude=updated_place_record['longitude'], rating=updated_place_record['rating'],
                     notes=updated_place_record['notes'], visitStatus=updated_place_record['visit_status']
                 )
            else:
                # If no updatable fields matched, fetch and return current state
                 current_data_record = await conn.fetchrow("""
                     SELECT id, name, address, latitude, longitude, rating, notes, visit_status
                     FROM places WHERE id = $1
                     """, place_id)
                 if not current_data_record: raise HTTPException(status_code=404, detail="Place not found after update attempt")
                 logger.info(f"No specific fields to update for place {place_id}, returning current data.")
                 return PlaceItem(
                     id=current_data_record['id'], name=current_data_record['name'],
                     address=current_data_record['address'], latitude=current_data_record['latitude'],
                     longitude=current_data_record['longitude'], rating=current_data_record['rating'],
                     notes=current_data_record['notes'], visitStatus=current_data_record['visit_status']
                 )

    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error updating place {place_id} in list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error updating place")
    except Exception as e:
        logger.error(f"Unexpected error updating place {place_id} in list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error updating place")


@app.get("/lists/{list_id}/places", response_model=PaginatedPlaceResponse, tags=["Lists", "Places"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def get_places_in_list(
    list_id: int,
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(30, ge=1, le=100)
):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            await check_list_access(conn, list_id, user_id) # Verify list access

            # Get total count
            count_record = await conn.fetchrow("SELECT COUNT(*) as count FROM places WHERE list_id = $1", list_id)
            total_items = count_record['count'] if count_record else 0
            total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
            logger.debug(f"Total places for list {list_id}: {total_items}")

            items = []
            if total_items > 0 and page <= total_pages:
                offset = (page - 1) * page_size
                place_records = await conn.fetch(
                    """
                    SELECT id, name, address, latitude, longitude, rating, notes, visit_status
                    FROM places
                    WHERE list_id = $1
                    ORDER BY created_at DESC
                    LIMIT $2 OFFSET $3
                    """,
                    list_id, page_size, offset
                )
                items = [PlaceItem(
                     id=row['id'], name=row['name'], address=row['address'],
                     latitude=row['latitude'], longitude=row['longitude'],
                     rating=row['rating'], notes=row['notes'],
                     visitStatus=row['visit_status']
                 ) for row in place_records]
                logger.debug(f"Fetched {len(items)} places for list {list_id}, page {page}")
            else:
                 logger.debug(f"No places to fetch for list {list_id}, page {page} (total_items={total_items}, total_pages={total_pages})")

            return PaginatedPlaceResponse(
                items=items, page=page, page_size=page_size,
                total_items=total_items, total_pages=total_pages
            )
    except HTTPException as he:
        raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error fetching places for list {list_id}, page {page}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching places")
    except Exception as e:
        logger.error(f"Unexpected error fetching places for list {list_id}, page {page}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching places")


@app.delete("/lists/{list_id}/places/{place_id}", status_code=204, tags=["Lists", "Places"])
@limiter.limit("20/minute") # Default limit: 20 per minute per IP/user
async def delete_place_from_list(
    list_id: int,
    place_id: int,
    authorization: str = Header(..., alias="Authorization")
):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            await check_list_access(conn, list_id, user_id) # Check access first

            # Execute delete
            status = await conn.execute(
                "DELETE FROM places WHERE id = $1 AND list_id = $2",
                place_id, list_id
            )

            if status == 'DELETE 0':
                # If 0 rows deleted, the place wasn't in this list (or was already deleted)
                logger.warning(f"Attempt to delete place {place_id} from list {list_id} by user {user_id}, but place was not found in list.")
                raise HTTPException(status_code=404, detail="Place not found in this list")

            logger.info(f"Place {place_id} deleted from list {list_id} by user {user_id}")
            # Transaction commits automatically

        return Response(status_code=204)

    except HTTPException as he:
        raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error deleting place {place_id} from list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error deleting place")
    except Exception as e:
        logger.error(f"Unexpected error deleting place {place_id} from list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error deleting place")


# --- List Discovery ---

@app.get("/lists/public", response_model=PaginatedListResponse, tags=["Lists", "Discovery"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def get_public_lists(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100)
):
    logger.info(f"Received GET /lists/public?page={page}&page_size={page_size}")
    try:
        async with get_db_conn() as conn:
            # Get total count
            count_record = await conn.fetchrow("SELECT COUNT(*) as count FROM lists l WHERE l.is_private = FALSE")
            total_items = count_record['count'] if count_record else 0
            total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
            logger.debug(f"Total public lists: {total_items}")

            items = []
            if total_items > 0 and page <= total_pages:
                offset = (page - 1) * page_size
                list_records = await conn.fetch(
                    """
                    SELECT
                        l.id, l.name, l.description, l.is_private,
                        (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
                    FROM lists l WHERE l.is_private = FALSE
                    ORDER BY l.created_at DESC
                    LIMIT $1 OFFSET $2
                    """,
                    page_size, offset
                )
                items = [ListViewResponse(**row) for row in list_records]

            return PaginatedListResponse(
                items=items, page=page, page_size=page_size,
                total_items=total_items, total_pages=total_pages
            )
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error fetching public lists: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="DB error fetching public lists")
    except Exception as e:
        logger.error(f"Unexpected error fetching public lists: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Server error fetching public lists")


@app.get("/lists/search", response_model=PaginatedListResponse, tags=["Lists", "Discovery"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def search_lists(
    q: str = Query(..., min_length=1),
    authorization: Optional[str] = Header(None, alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100)
):
    logger.info(f"Received GET /lists/search?q={q}&page={page}&page_size={page_size}")
    search_term = f"%{q}%"
    user_id = None

    if authorization:
         try:
             firebase_token = verify_firebase_token(authorization)
             # Need to get user ID within DB context
         except HTTPException:
             logger.warning("Invalid token provided for list search, searching public only.")
             firebase_token = None # Indicate unauthenticated

    try:
        async with get_db_conn() as conn:
            if firebase_token:
                try:
                    user_id, _ = await get_owner_id(firebase_token, conn)
                except Exception:
                    logger.error("Error getting user_id during authenticated list search", exc_info=True)
                    user_id = None # Fallback to public search

            # Build query dynamically
            params = [search_term, search_term]
            where_clauses = ["(l.name ILIKE $1 OR l.description ILIKE $1)"]
            param_idx = 3 # Next param is $3

            if user_id:
                where_clauses.append(f"(l.is_private = FALSE OR l.owner_id = ${param_idx})")
                params.append(user_id)
                param_idx += 1
            else:
                where_clauses.append("l.is_private = FALSE")

            where_sql = " AND ".join(where_clauses)
            base_sql = f"FROM lists l WHERE {where_sql}"

            # Get total count
            count_sql = f"SELECT COUNT(*) as count {base_sql}"
            count_record = await conn.fetchrow(count_sql, *params)
            total_items = count_record['count'] if count_record else 0
            total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
            logger.debug(f"Found {total_items} lists matching search '{q}' (User ID: {user_id})")

            items = []
            if total_items > 0 and page <= total_pages:
                offset = (page - 1) * page_size
                # Add LIMIT and OFFSET placeholders and params
                items_sql = f"""
                    SELECT
                        l.id, l.name, l.description, l.is_private,
                        (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
                    {base_sql}
                    ORDER BY l.created_at DESC
                    LIMIT ${param_idx} OFFSET ${param_idx + 1}
                """
                params.extend([page_size, offset])
                list_records = await conn.fetch(items_sql, *params)
                items = [ListViewResponse(**row) for row in list_records]

            return PaginatedListResponse(
                items=items, page=page, page_size=page_size,
                total_items=total_items, total_pages=total_pages
            )
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error searching lists for '{q}': {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="DB error searching lists")
    except Exception as e:
        logger.error(f"Unexpected error searching lists for '{q}': {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Server error searching lists")


@app.get("/lists/recent", response_model=List[ListViewResponse], tags=["Lists", "Discovery"])
@limiter.limit("10/minute") # Default limit: 10 per minute per IP/user
async def get_recent_lists(
    limit: int = Query(10, ge=1, le=50),
    authorization: str = Header(..., alias="Authorization")
):
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)
            list_records = await conn.fetch(
                 """
                 SELECT l.id, l.name, l.description, l.is_private,
                        (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
                 FROM lists l
                 WHERE l.is_private = FALSE OR l.owner_id = $1
                 ORDER BY l.created_at DESC
                 LIMIT $2
                 """,
                 user_id, limit
             )
            return [ListViewResponse(**row) for row in list_records]
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error fetching recent lists: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="DB error fetching recent lists")
    except Exception as e:
        logger.error(f"Unexpected error fetching recent lists: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Server error fetching recent lists")


# --- Notification Endpoints ---
@app.get("/notifications", response_model=PaginatedNotificationResponse, tags=["Notifications"])
@limiter.limit("2/minute") # Default limit: 2 per minute per IP/user
async def get_notifications(
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(25, ge=1, le=100)
):
    logger.info(f"Received GET /notifications?page={page}&page_size={page_size}")
    firebase_token = verify_firebase_token(authorization)
    try:
        async with get_db_conn() as conn:
            user_id, _ = await get_owner_id(firebase_token, conn)

            # Get total count
            count_record = await conn.fetchrow("SELECT COUNT(*) as count FROM notifications WHERE user_id = $1", user_id)
            total_items = count_record['count'] if count_record else 0
            total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

            items = []
            if total_items > 0 and page <= total_pages:
                offset = (page - 1) * page_size
                notification_records = await conn.fetch(
                    """
                    SELECT id, title, message, is_read, timestamp
                    FROM notifications
                    WHERE user_id = $1
                    ORDER BY timestamp DESC
                    LIMIT $2 OFFSET $3
                    """,
                    user_id, page_size, offset
                )
                # Ensure your NotificationItem model can handle the data types from asyncpg (e.g., datetime)
                items = [NotificationItem(**row) for row in notification_records]

            return PaginatedNotificationResponse(
                items=items, page=page, page_size=page_size,
                total_items=total_items, total_pages=total_pages
            )
    except HTTPException as he: raise he
    except asyncpg.PostgresError as db_err:
        logger.error(f"DB error fetching notifications for user (inferred id): {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching notifications")
    except Exception as e:
        logger.error(f"Unexpected error fetching notifications for user (inferred id): {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching notifications")

@app.get("/sentry-debug")
async def trigger_error():
    division_by_zero = 1 / 0

# === Main Guard (Optional for running directly with uvicorn) ===
if __name__ == "__main__":
    import uvicorn
    import asyncio # Required for asyncpg lifespan management
    # Use host="0.0.0.0" to allow access from network during development
    # Set reload=False for production deployments
    # Ensure LOGGING_CONFIG is set appropriately if using uvicorn's logging
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True) # reload=True for development
    