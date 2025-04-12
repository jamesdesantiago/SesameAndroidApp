# main.py (Fully Updated with Pagination for Lists, Following, Followers, Search)

from fastapi import FastAPI, HTTPException, Header, Request, Query, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field # Added Field for validation description
import psycopg2
from psycopg2.extras import RealDictCursor
from firebase_admin import auth, credentials, initialize_app
from dotenv import load_dotenv
import os
import logging
from typing import Optional, List
import re
import math
from datetime import datetime # For Notification timestamp

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

# Create the FastAPI app first
# Added title and version for documentation purposes
app = FastAPI(
    title="Sesame App API",
    version="1.0.0",
    redirect_slashes=False # Keep trailing slashes distinct if needed
)

# Global exception handler for validation errors
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    logger.error(f"Validation error for request {request.url}: {exc.errors()}")
    # Provide a more user-friendly error structure if desired
    return JSONResponse(
        status_code=422,
        content={"detail": "Validation Error", "errors": exc.errors()}
    )

# Global exception handler for database connection errors during request handling
@app.exception_handler(psycopg2.OperationalError)
async def db_connection_exception_handler(request: Request, exc: psycopg2.OperationalError):
    logger.error(f"Database connection error during request {request.url}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=503, # Service Unavailable
        content={"detail": "Database connection failed. Please try again later."}
    )

# Global handler for unhandled exceptions
@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception during request {request.url}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": "An internal server error occurred."}
    )


# Initialize Firebase Admin SDK
try:
    # Consider using environment variables for the path too
    cred_path = os.getenv("FIREBASE_SERVICE_ACCOUNT_KEY", "service-account.json")
    if not os.path.exists(cred_path):
         logger.critical(f"Firebase service account key not found at: {cred_path}")
         # Exit or raise a more specific startup error
         raise FileNotFoundError(f"Service account key not found: {cred_path}")
    cred = credentials.Certificate(cred_path)
    initialize_app(cred)
    logger.info("Firebase Admin SDK initialized successfully.")
except Exception as e:
    logger.critical(f"CRITICAL: Failed to initialize Firebase Admin SDK: {e}", exc_info=True)
    # Exit application on critical initialization failure
    exit(1)


# Middleware to log request details
@app.middleware("http")
async def log_request_middleware(request: Request, call_next):
    # Avoid logging Authorization header directly
    headers_to_log = {k: v for k, v in request.headers.items() if k.lower() != 'authorization'}
    auth_present = 'authorization' in request.headers
    logger.info(f"Incoming Request: {request.method} {request.url.path} | Auth Present: {auth_present} | Headers (Sanitized): {headers_to_log}")
    try:
        response = await call_next(request)
        logger.info(f"Outgoing Response: {request.method} {request.url.path} | Status: {response.status_code}")
        return response
    except Exception as e:
         # Log errors that might occur *before* reaching endpoint handlers
        logger.error(f"Error during middleware processing for {request.url.path}: {e}", exc_info=True)
         # Re-raise the exception to be caught by global handlers
        raise e


# --- Database Connection Pool (Recommended for Production) ---
# Using a simple function for now, but a pool is better.
# Example using context manager for safety
from contextlib import contextmanager

@contextmanager
def get_db_connection():
    conn = None
    try:
        conn = psycopg2.connect(
            host=os.getenv("DB_HOST", "sesame-app-db-do-user-16453789-0.e.db.ondigitalocean.com"),
            port=os.getenv("DB_PORT", "25060"),
            database=os.getenv("DB_NAME", "defaultdb"),
            user=os.getenv("DB_USER", "doadmin"),
            password=os.getenv("DB_PASSWORD"),
            sslmode="require",
            connect_timeout=5 # Add a connection timeout
        )
        yield conn
    except psycopg2.OperationalError as e:
         # Logged by global handler, re-raise
        raise e
    except Exception as e:
        logger.error(f"Unexpected error getting DB connection: {str(e)}", exc_info=True)
        # Convert to OperationalError to be caught by handler
        raise psycopg2.OperationalError(f"Unexpected error getting DB connection: {str(e)}")
    finally:
        if conn:
            conn.close()
            # logger.debug("Database connection closed.") # Can be noisy


# === Pydantic Models (Input/Output Schemas) ===

# Base model for user info included in various responses
class UserBase(BaseModel):
    id: int
    email: str
    username: Optional[str] = None
    # Add other fields if needed, like displayName, profilePictureUrl

# User model specifically for search/follow lists
class UserFollowInfo(UserBase):
    pass # Inherits fields from UserBase

class PlaceItem(BaseModel):
    id: int
    name: str
    address: str
    latitude: float
    longitude: float
    rating: Optional[str] = None
    visitStatus: Optional[str] = None # Matches column name if different from API
    notes: Optional[str] = None
    # Add timestamps if needed

class ListCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=100) # Added validation
    description: Optional[str] = Field(None, max_length=500)
    isPrivate: bool = False

class ListViewResponse(BaseModel): # For paginated list endpoints
    id: int
    name: str
    description: Optional[str] = None
    isPrivate: bool
    place_count: int = 0 # Number of places in the list

class ListDetailResponse(BaseModel): # For single list detail endpoints
    id: int
    name: str
    description: Optional[str] = None
    isPrivate: bool
    collaborators: List[str] = []
    # places: List[PlaceItem] = []
    # Add owner info if needed

    class Config:
        orm_mode = True

class PaginatedListResponse(BaseModel): # Wrapper for paginated lists
    items: List[ListViewResponse]
    page: int
    page_size: int
    total_items: int
    total_pages: int

class PaginatedUserResponse(BaseModel): # Wrapper for paginated users
    items: List[UserFollowInfo] # Use specific user model for this context
    page: int
    page_size: int
    total_items: int
    total_pages: int

class PaginatedPlaceResponse(BaseModel):
    items: List[PlaceItem] # List of PlaceItem models
    page: int
    page_size: int
    total_items: int
    total_pages: int

class ListUpdate(BaseModel):
    name: Optional[str] = Field(None, min_length=1, max_length=100)
    isPrivate: Optional[bool] = None

class CollaboratorAdd(BaseModel):
    email: str = Field(..., pattern=r"[^@]+@[^@]+\.[^@]+") # Basic email format validation

class PlaceCreate(BaseModel):
    placeId: str # Google Place ID or similar external ID
    name: str = Field(..., max_length=200)
    address: str = Field(..., max_length=300)
    latitude: float
    longitude: float
    rating: Optional[str] = None # Consider Enum if values are fixed
    notes: Optional[str] = Field(None, max_length=1000)
    visitStatus: Optional[str] = None # Consider Enum

class PlaceUpdate(BaseModel):
    notes: Optional[str] = Field(None, max_length=1000)
    # Add other updatable fields like rating, visitStatus?

class UsernameSet(BaseModel):
    username: str = Field(..., min_length=1, max_length=30, pattern=r'^[a-zA-Z0-9._]+$') # Combined validation

class UsernameCheckResponse(BaseModel):
    needsUsername: bool

class UsernameSetResponse(BaseModel):
    message: str

class NotificationItem(BaseModel):
    id: int # Or UUID/String depending on DB schema
    title: str
    message: str
    is_read: bool = Field(..., alias="isRead") # Match JSON key if different
    timestamp: datetime # Or str/int depending on DB/API format

    class Config:
        orm_mode = True
        allow_population_by_field_name = True

# NEW: Paginated Notification Response
class PaginatedNotificationResponse(BaseModel):
    items: List[NotificationItem]
    page: int
    page_size: int
    total_items: int
    total_pages: int


# === Helper Functions ===

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
        logger.error(f"Firebase token verification failed: {str(e)}", exc_info=Tru)
        # Provide more specific error messages if possible (e.g., token expired)
        raise HTTPException(status_code=401, detail=f"Invalid or expired token: {str(e)}")


def get_owner_id(firebase_token: dict, conn) -> tuple[int, bool]:
    """Gets user ID from DB based on firebase token, creating if necessary."""
    # ... (Implementation from previous step - keep as is) ...
    firebase_uid = firebase_token.get("uid")
    email = firebase_token.get("email")

    if not firebase_uid:
        logger.error("Firebase token missing 'uid'.")
        raise HTTPException(status_code=400, detail="Invalid token data (missing uid)")
    if not email:
        logger.error("Firebase token missing 'email'.")
        raise HTTPException(status_code=400, detail="Invalid token data (missing email)")

    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute("SELECT id, username FROM users WHERE firebase_uid = %s", (firebase_uid,))
        result = cur.fetchone()
        if result:
            user_id = result['id']
            username = result['username']
            logger.debug(f"User found by firebase_uid: {user_id}")
            return user_id, username is None

        cur.execute("SELECT id, firebase_uid, username FROM users WHERE email = %s", (email,))
        result = cur.fetchone()
        if result:
            user_id = result['id']
            existing_firebase_uid = result['firebase_uid']
            username = result['username']
            logger.debug(f"User found by email: {user_id}. Existing UID: {existing_firebase_uid}, Token UID: {firebase_uid}")
            if existing_firebase_uid != firebase_uid:
                logger.warning(f"Updating firebase_uid for user {user_id} from {existing_firebase_uid} to {firebase_uid}")
                cur.execute("UPDATE users SET firebase_uid = %s WHERE id = %s", (firebase_uid, user_id))
                conn.commit()
            return user_id, username is None

        logger.info(f"Creating new user entry for email: {email}, firebase_uid: {firebase_uid}")
        cur.execute(
            "INSERT INTO users (email, firebase_uid) VALUES (%s, %s) RETURNING id",
            (email, firebase_uid)
        )
        user_id_tuple = cur.fetchone()
        if not user_id_tuple:
             logger.error(f"Failed to insert new user for email {email}")
             raise HTTPException(status_code=500, detail="Failed to create user record")
        user_id = user_id_tuple['id']
        conn.commit()
        logger.info(f"New user created with ID: {user_id}")
        return user_id, True


def get_collaborators(list_id: int, conn) -> List[str]:
    """Fetches collaborator emails for a given list ID."""
    # ... (Implementation from previous step - keep as is) ...
    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(
            """
            SELECT u.email
            FROM list_collaborators lc
            JOIN users u ON lc.user_id = u.id
            WHERE lc.list_id = %s
            """,
            (list_id,)
        )
        return [row["email"] for row in cur.fetchall()]

# Helper to check list ownership (can be expanded for collaboration checks)
def check_list_ownership(conn, list_id: int, user_id: int):
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM lists WHERE id = %s AND owner_id = %s", (list_id, user_id))
        if not cur.fetchone():
            logger.warning(f"Ownership check failed: User {user_id} does not own list {list_id}")
             # Check if list exists at all for better error message
            cur.execute("SELECT id FROM lists WHERE id = %s", (list_id,))
            if cur.fetchone():
                 raise HTTPException(status_code=403, detail="Not authorized for this list") # Forbidden
            else:
                 raise HTTPException(status_code=404, detail="List not found") # Not Found


# === API Endpoints ===

# --- User Specific Endpoints ---
@app.get("/users/check-username", response_model=UsernameCheckResponse, tags=["User"])
async def check_username(authorization: str = Header(..., alias="Authorization")):
    """Checks if the authenticated user needs to set a username."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, needs_username = get_owner_id(firebase_token, conn)
            return {"needsUsername": needs_username}
    except HTTPException as he:
        raise he
    except Exception as e: # Catch unexpected errors during DB interaction
        logger.error("Error during username check DB interaction", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error during username check")


@app.post("/users/set-username", response_model=UsernameSetResponse, status_code=200, tags=["User"])
async def set_username(data: UsernameSet, authorization: str = Header(..., alias="Authorization")):
    """Sets the username for the authenticated user if not already set or taken."""
    firebase_token = verify_firebase_token(authorization)

    # Pydantic handles format validation based on model definition

    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            with conn.cursor() as cur:
                # Check if username exists (case-insensitive) excluding the current user
                cur.execute(
                    "SELECT id FROM users WHERE LOWER(username) = LOWER(%s) AND id != %s",
                    (data.username, user_id)
                )
                if cur.fetchone():
                    raise HTTPException(status_code=409, detail="Username already taken") # Conflict

                # Attempt to update
                cur.execute(
                    "UPDATE users SET username = %s WHERE id = %s",
                    (data.username, user_id)
                )
                if cur.rowcount == 0:
                    logger.error(f"Failed to update username for user_id {user_id} - rowcount 0")
                    raise HTTPException(status_code=404, detail="User not found for update")
                conn.commit()
                logger.info(f"Username successfully set for user_id {user_id}")

    except HTTPException as he:
        raise he
    except psycopg2.Error as db_err:
        conn.rollback()
        logger.error(f"Database error setting username for user {user_id}: {db_err}", exc_info=True)
        if isinstance(db_err, psycopg2.errors.UniqueViolation):
             raise HTTPException(status_code=409, detail="Username already taken (concurrent update?)") # Conflict
        raise HTTPException(status_code=500, detail="Database error setting username")
    except Exception as e:
        logger.error(f"Unexpected error setting username for user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error setting username")

    return {"message": "Username set successfully"}


# --- Friends/Followers Endpoints (Paginated) ---

@app.get("/users/following", response_model=PaginatedUserResponse, tags=["Friends"])
async def get_following(
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1, description="Page number, starting from 1."),
    page_size: int = Query(20, ge=1, le=100, description="Items per page (1-100).")
):
    """Gets the list of users the authenticated user is following (paginated)."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Get total count
                cur.execute("SELECT COUNT(*) FROM user_follows WHERE follower_id = %s", (user_id,))
                total_items = cur.fetchone()['count']
                total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

                # Get paginated items
                items = []
                if total_items > 0 and page <= total_pages:
                    offset = (page - 1) * page_size
                    cur.execute(
                        """
                        SELECT u.id, u.email, u.username
                        FROM user_follows uf
                        JOIN users u ON uf.followed_id = u.id
                        WHERE uf.follower_id = %s
                        ORDER BY uf.created_at DESC
                        LIMIT %s OFFSET %s
                        """,
                        (user_id, page_size, offset)
                    )
                    items = [UserFollowInfo(**row) for row in cur.fetchall()]

                return PaginatedUserResponse(
                    items=items, page=page, page_size=page_size,
                    total_items=total_items, total_pages=total_pages
                )
    except HTTPException as he: raise he
    except psycopg2.Error as db_err: # Catch DB errors specifically
        logger.error(f"DB error fetching following for user {user_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching following list")
    except Exception as e: # Catch any other unexpected errors
        logger.error(f"Unexpected error fetching following for user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching following list")


@app.get("/users/followers", response_model=PaginatedUserResponse, tags=["Friends"])
async def get_followers(
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1, description="Page number, starting from 1."),
    page_size: int = Query(20, ge=1, le=100, description="Items per page (1-100).")
):
    """Gets the list of users following the authenticated user (paginated)."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Get total count
                cur.execute("SELECT COUNT(*) FROM user_follows WHERE followed_id = %s", (user_id,))
                total_items = cur.fetchone()['count']
                total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

                # Get paginated items
                items = []
                if total_items > 0 and page <= total_pages:
                    offset = (page - 1) * page_size
                    cur.execute(
                        """
                        SELECT u.id, u.email, u.username
                        FROM user_follows uf
                        JOIN users u ON uf.follower_id = u.id
                        WHERE uf.followed_id = %s
                        ORDER BY uf.created_at DESC
                        LIMIT %s OFFSET %s
                        """,
                        (user_id, page_size, offset)
                    )
                    items = [UserFollowInfo(**row) for row in cur.fetchall()]
                    # Add default username logic if needed (might be better in UserFollowInfo model)
                    # for user in items:
                    #     if user.username is None:
                    #         user.username = user.email.split("@")[0]

                return PaginatedUserResponse(
                    items=items, page=page, page_size=page_size,
                    total_items=total_items, total_pages=total_pages
                )
    except HTTPException as he: raise he
    except psycopg2.Error as db_err:
        logger.error(f"DB error fetching followers for user {user_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching followers list")
    except Exception as e:
        logger.error(f"Unexpected error fetching followers for user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching followers list")


@app.get("/users/search", response_model=PaginatedUserResponse, tags=["Friends"])
async def search_users(
    email: str = Query(..., min_length=1, description="Email or username fragment to search for."), # Changed param name for clarity
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1, description="Page number, starting from 1."),
    page_size: int = Query(10, ge=1, le=50, description="Items per page (1-50).") # Smaller page size for search?
):
    """Searches for users by email or username fragment (paginated)."""
    firebase_token = verify_firebase_token(authorization)
    search_term = f"%{email}%" # Prepare search term for ILIKE

    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Get total count
                cur.execute(
                    """
                    SELECT COUNT(*)
                    FROM users
                    WHERE (email ILIKE %s OR username ILIKE %s) AND id != %s
                    """,
                    (search_term, search_term, user_id)
                )
                total_items = cur.fetchone()['count']
                total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

                # Get paginated items
                items = []
                if total_items > 0 and page <= total_pages:
                    offset = (page - 1) * page_size
                    cur.execute(
                        """
                        SELECT id, email, username
                        FROM users
                        WHERE (email ILIKE %s OR username ILIKE %s) AND id != %s
                        ORDER BY username ASC, email ASC -- Define an order
                        LIMIT %s OFFSET %s
                        """,
                        (search_term, search_term, user_id, page_size, offset)
                    )
                    items = [UserFollowInfo(**row) for row in cur.fetchall()]

                return PaginatedUserResponse(
                    items=items, page=page, page_size=page_size,
                    total_items=total_items, total_pages=total_pages
                )
    except HTTPException as he: raise he
    except psycopg2.Error as db_err:
        logger.error(f"DB error searching users with term '{email}': {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error searching users")
    except Exception as e:
        logger.error(f"Unexpected error searching users with term '{email}': {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error searching users")


@app.post("/users/{user_id}/follow", status_code=201, tags=["Friends"])
async def follow_user(user_id: int, authorization: str = Header(..., alias="Authorization")):
    """Follows the specified user."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            follower_id, _ = get_owner_id(firebase_token, conn)
            if follower_id == user_id:
                raise HTTPException(status_code=400, detail="Cannot follow yourself")

            with conn.cursor() as cur:
                # Check if target user exists
                cur.execute("SELECT id FROM users WHERE id = %s", (user_id,))
                if not cur.fetchone():
                    raise HTTPException(status_code=404, detail="User to follow not found")

                # Attempt insert
                try:
                    cur.execute(
                        "INSERT INTO user_follows (follower_id, followed_id, created_at) VALUES (%s, %s, NOW())",
                        (follower_id, user_id)
                    )
                    conn.commit()
                    logger.info(f"User {follower_id} followed user {user_id}")
                except psycopg2.errors.UniqueViolation:
                    conn.rollback() # Rollback explicit transaction
                    logger.warning(f"User {follower_id} already following user {user_id}")
                    # Decide: return 200 OK or 409 Conflict? 200 OK is often friendlier.
                    return {"message": "Already following this user"} # Return 200 OK
                    # raise HTTPException(status_code=409, detail="Already following this user") # Or 409
                except psycopg2.Error as db_inner_err: # Catch other DB errors during insert
                    conn.rollback()
                    logger.error(f"DB error during follow insert: {db_inner_err}", exc_info=True)
                    raise HTTPException(status_code=500, detail="Database error following user")

    except HTTPException as he: raise he
    except psycopg2.Error as db_err: # Catch potential DB errors in get_owner_id or user check
        logger.error(f"DB error processing follow request for user {user_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error processing follow request")
    except Exception as e:
        logger.error(f"Unexpected error processing follow request for user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error processing follow request")

    return {"message": "User followed"}


@app.delete("/users/{user_id}/follow", status_code=200, tags=["Friends"]) # Use 200 OK as success
async def unfollow_user(user_id: int, authorization: str = Header(..., alias="Authorization")):
    """Unfollows the specified user."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            follower_id, _ = get_owner_id(firebase_token, conn)
            with conn.cursor() as cur:
                cur.execute(
                    "DELETE FROM user_follows WHERE follower_id = %s AND followed_id = %s",
                    (follower_id, user_id)
                )
                if cur.rowcount == 0:
                     # Check if target user exists to differentiate 404 user vs 404 relationship
                    cur.execute("SELECT id FROM users WHERE id = %s", (user_id,))
                    if not cur.fetchone():
                         raise HTTPException(status_code=404, detail="User to unfollow not found")
                    else:
                         # User exists, but wasn't being followed
                         logger.warning(f"User {follower_id} tried to unfollow user {user_id}, but was not following.")
                         # Return 200 OK even if not following, or 404? 200 OK is often preferred.
                         return {"message": "Not following this user"}
                         # raise HTTPException(status_code=404, detail="Not following this user") # Or 404
                conn.commit()
                logger.info(f"User {follower_id} unfollowed user {user_id}")

    except HTTPException as he: raise he
    except psycopg2.Error as db_err:
        conn.rollback()
        logger.error(f"DB error unfollowing user {user_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error unfollowing user")
    except Exception as e:
        logger.error(f"Unexpected error unfollowing user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error unfollowing user")

    return {"message": "User unfollowed"}


# ... (Keep /users/{user_id}/friend-request if used) ...


# --- List Endpoints (Paginated GET /lists) ---

@app.get("/lists", response_model=PaginatedListResponse, tags=["Lists"])
async def get_lists(
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1, description="Page number, starting from 1."),
    page_size: int = Query(20, ge=1, le=100, description="Items per page (1-100).")
    # Add other filters like publicOnly if needed:
    # publicOnly: Optional[bool] = Query(None, alias="public")
):
    """Gets the authenticated user's lists (paginated)."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Base query and params
                base_sql = "FROM lists l WHERE l.owner_id = %s"
                params = [user_id]

                # --- Example Filter ---
                # if publicOnly is not None:
                #     base_sql += " AND l.is_private = %s"
                #     params.append(not publicOnly) # Assuming is_private maps to !publicOnly

                # Get total count with filters
                count_sql = f"SELECT COUNT(*) {base_sql}"
                cur.execute(count_sql, tuple(params))
                total_items = cur.fetchone()['count']
                total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

                # Get paginated items with filters and ordering
                items = []
                if total_items > 0 and page <= total_pages:
                    offset = (page - 1) * page_size
                    items_sql = f"""
                        SELECT
                            l.id, l.name, l.description, l.is_private,
                            (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
                        {base_sql}
                        ORDER BY l.created_at DESC -- Or updated_at DESC, name ASC etc.
                        LIMIT %s OFFSET %s
                    """
                    params.extend([page_size, offset]) # Add limit and offset to params
                    cur.execute(items_sql, tuple(params))
                    items = [ListViewResponse(
                        id=row['id'],
                        name=row['name'],
                        description=row['description'],
                        isPrivate=row['is_private'] if row['is_private'] is not None else False,
                        place_count=row.get('place_count', 0)
                    ) for row in cur.fetchall()]

                return PaginatedListResponse(
                    items=items, page=page, page_size=page_size,
                    total_items=total_items, total_pages=total_pages
                )

    except HTTPException as he: raise he
    except psycopg2.Error as db_err:
        logger.error(f"DB error fetching lists for user {user_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching lists")
    except Exception as e:
        logger.error(f"Unexpected error fetching lists for user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching lists")


@app.post("/lists", response_model=ListDetailResponse, status_code=201, tags=["Lists"])
async def create_list(list_data: ListCreate, authorization: str = Header(..., alias="Authorization")):
    """Creates a new list for the authenticated user."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                cur.execute(
                    """
                    INSERT INTO lists (name, description, owner_id, created_at, is_private)
                    VALUES (%s, %s, %s, NOW(), %s)
                    RETURNING id, name, description, is_private
                    """,
                    (list_data.name, list_data.description, user_id, list_data.isPrivate)
                )
                result = cur.fetchone()
                if not result:
                    raise HTTPException(status_code=500, detail="Failed to create list")
                conn.commit()
                logger.info(f"List created with ID {result['id']} by user {user_id}")
                # Map to ListDetailResponse structure
                return ListDetailResponse(
                     id=result['id'], name=result['name'], description=result['description'],
                     isPrivate=result['is_private'] if result['is_private'] is not None else False,
                     collaborators=[], places=[]
                )
    # ... (Keep existing detailed error handling from previous step) ...
    except HTTPException as he: raise he
    except psycopg2.Error as db_err:
        conn.rollback()
        logger.error(f"DB error creating list for user {user_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error creating list")
    except Exception as e:
        logger.error(f"Unexpected error creating list for user {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error creating list")


@app.delete("/lists/{list_id}", status_code=204, tags=["Lists"]) # 204 No Content is appropriate
async def delete_list(list_id: int, authorization: str = Header(..., alias="Authorization")):
    """Deletes a specific list owned by the authenticated user."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            # Use helper to check ownership, raises 403/404 if needed
            check_list_ownership(conn, list_id, user_id)

            with conn.cursor() as cur:
                # Assuming ON DELETE CASCADE is set for places, collaborators, etc.
                # If not, delete related items first.
                # cur.execute("DELETE FROM places WHERE list_id = %s", (list_id,))
                # cur.execute("DELETE FROM list_collaborators WHERE list_id = %s", (list_id,))
                cur.execute("DELETE FROM lists WHERE id = %s", (list_id,)) # Owner already checked
                if cur.rowcount == 0:
                     # Should not happen if check_list_ownership passed, but log defensively
                    logger.error(f"List {list_id} found by owner check but failed to delete.")
                    raise HTTPException(status_code=500, detail="Failed to delete list after ownership check")
                conn.commit()
                logger.info(f"List {list_id} deleted successfully by user {user_id}")
            # Return Response with 204 status code explicitly
            return Response(status_code=204)
    except HTTPException as he: raise he
    except psycopg2.Error as db_err:
        conn.rollback()
        logger.error(f"DB error deleting list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error deleting list")
    except Exception as e:
        logger.error(f"Unexpected error deleting list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error deleting list")


@app.patch("/lists/{list_id}", response_model=ListDetailResponse, tags=["Lists"])
async def update_list(list_id: int, update_data: ListUpdate, authorization: str = Header(..., alias="Authorization")):
    """Updates the name and/or privacy of a specific list."""
    firebase_token = verify_firebase_token(authorization)
    # Check if any actual update data is provided
    if update_data.model_dump(exclude_unset=True) == {}:
         raise HTTPException(status_code=400, detail="No update fields provided")

    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            # Use helper to check ownership
            check_list_ownership(conn, list_id, user_id)

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Build SET clause dynamically
                # ... (Dynamic SET clause logic from previous step - keep as is) ...
                set_clauses = []
                params = []
                if update_data.name is not None:
                    set_clauses.append("name = %s")
                    params.append(update_data.name)
                if update_data.isPrivate is not None:
                    set_clauses.append("is_private = %s")
                    params.append(update_data.isPrivate)

                # Update timestamps if relevant to your schema
                # set_clauses.append("updated_at = NOW()")

                sql = f"UPDATE lists SET {', '.join(set_clauses)} WHERE id = %s RETURNING id, name, description, is_private"
                params.append(list_id)
                cur.execute(sql, tuple(params))

                result = cur.fetchone()
                if not result: # Should not happen if check_list_ownership passed
                    logger.error(f"List {list_id} update failed despite ownership check.")
                    raise HTTPException(status_code=500, detail="List update failed after ownership check")
                conn.commit()
                logger.info(f"List {list_id} updated successfully by user {user_id}")

                # Fetch collaborators and places for the response
                collaborators = get_collaborators(list_id, conn)
                cur.execute( # Fetch places again after update
                    """
                    SELECT id, name, address, latitude, longitude, rating, notes, visit_status as "visitStatus"
                    FROM places WHERE list_id = %s ORDER BY created_at DESC
                    """, (list_id,)
                )
                places = [PlaceItem(**p) for p in cur.fetchall()]

                return ListDetailResponse(
                     id=result['id'], name=result['name'], description=result['description'],
                     isPrivate=result['is_private'] if result['is_private'] is not None else False,
                     collaborators=collaborators, places=places
                )
    # ... (Keep existing detailed error handling from previous step) ...
    except HTTPException as he: raise he
    except psycopg2.Error as db_err:
        conn.rollback()
        logger.error(f"DB error updating list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error updating list")
    except Exception as e:
        logger.error(f"Unexpected error updating list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error updating list")


@app.get("/lists/{list_id}", response_model=ListDetailResponse, tags=["Lists"])
async def get_list_detail(list_id: int, authorization: str = Header(..., alias="Authorization")):
    """Gets the details (metadata, collaborators) of a specific list, EXCLUDING places."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            check_list_access(conn, list_id, user_id) # Use access check

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Fetch list metadata ONLY
                cur.execute(
                    "SELECT l.id, l.name, l.description, l.is_private FROM lists l WHERE l.id = %s",
                    (list_id,)
                )
                result = cur.fetchone()
                if not result:
                    # Should be caught by check_list_access, but defensive check
                    logger.error(f"List {list_id} not found after access check passed.")
                    raise HTTPException(status_code=404, detail="List not found")

                # Fetch collaborators ONLY
                collaborators = get_collaborators(list_id, conn)
                logger.debug(f"Fetched {len(collaborators)} collaborators for list {list_id}")

                # Return response WITHOUT places list
                return ListDetailResponse(
                    id=result['id'],
                    name=result['name'],
                    description=result['description'],
                    isPrivate=result['is_private'] if result['is_private'] is not None else False,
                    collaborators=collaborators,
                    # places field is omitted as per the updated ListDetailResponse model
                )
    except HTTPException as he:
        raise he # Re-raise specific HTTP errors
    except psycopg2.Error as db_err:
        logger.error(f"DB error fetching detail for list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching list metadata")
    except Exception as e:
        logger.error(f"Unexpected error fetching detail for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching list metadata")


# --- Places and Collaborators within Lists ---
# These endpoints likely don't need pagination themselves unless a list has thousands of collaborators

@app.post("/lists/{list_id}/collaborators", status_code=201, tags=["Lists", "Collaborators"], response_model=UsernameSetResponse)
def add_collaborator(list_id: int, collaborator: CollaboratorAdd, authorization: str = Header(..., alias="Authorization")):
    """Adds a collaborator to a list. Only the list owner can perform this action."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            # --- Use ownership check here: Only owner should add collaborators ---
            check_list_ownership(conn, list_id, user_id)

            collaborator_user_id = None
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                cur.execute("SELECT id FROM users WHERE email = %s", (collaborator.email,))
                user_row = cur.fetchone()
                collaborator_user_id = user_row["id"] if user_row else None
                if not collaborator_user_id:
                    logger.info(f"Collaborator email {collaborator.email} not found, creating user.")
                    cur.execute("INSERT INTO users (email) VALUES (%s) RETURNING id", (collaborator.email,))
                    collab_id_tup = cur.fetchone()
                    if not collab_id_tup: raise HTTPException(status_code=500, detail="Failed to create collaborator user")
                    collaborator_user_id = collab_id_tup["id"]
                    conn.commit() # Commit user creation separate from collaborator add

            with conn.cursor() as cur:
                try:
                    cur.execute("INSERT INTO list_collaborators (list_id, user_id) VALUES (%s, %s)", (list_id, collaborator_user_id))
                    conn.commit()
                    logger.info(f"User {collaborator_user_id} added as collaborator to list {list_id}")
                except psycopg2.errors.UniqueViolation:
                    conn.rollback()
                    raise HTTPException(status_code=409, detail="Collaborator already added")
                except psycopg2.Error as db_i_err:
                    conn.rollback()
                    logger.error(f"DB error adding collab: {db_i_err}", exc_info=True)
                    raise HTTPException(status_code=500, detail="DB error adding collaborator")

    except HTTPException as he: raise he
    except psycopg2.Error as db_err: logger.error(f"DB error processing add collab: {db_err}", exc_info=True); raise HTTPException(status_code=500, detail="DB error")
    except Exception as e: logger.error(f"Unexpected error adding collab: {e}", exc_info=True); raise HTTPException(status_code=500, detail="Server error")

    return UsernameSetResponse(message="Collaborator added")

# ... (Keep add_collaborators_batch - similar logic, consider transaction) ...

@app.post("/lists/{list_id}/places", response_model=PlaceItem, status_code=201, tags=["Lists", "Places"])
async def add_place(list_id: int, place: PlaceCreate, authorization: str = Header(..., alias="Authorization")):
    """Adds a place to a specific list. User must be owner or collaborator."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            # --- Use access check: Owner OR Collaborator can add ---
            check_list_access(conn, list_id, user_id)

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                try:
                    cur.execute(
                        """
                        INSERT INTO places (list_id, place_id, name, address, latitude, longitude, rating, notes, visit_status, created_at)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
                        RETURNING id, name, address, latitude, longitude, rating, notes, visit_status as "visitStatus"
                        """,
                        (list_id, place.placeId, place.name, place.address, place.latitude, place.longitude, place.rating, place.notes, place.visitStatus)
                    )
                    created_place_data = cur.fetchone()
                    if not created_place_data: raise HTTPException(status_code=500, detail="Failed to add place")
                    conn.commit()
                    logger.info(f"Place '{place.name}' (DB ID: {created_place_data['id']}) added to list {list_id} by user {user_id}")
                    return PlaceItem(**created_place_data)
                except psycopg2.errors.UniqueViolation:
                    conn.rollback()
                    logger.warning(f"Place with external ID '{place.placeId}' already exists in list {list_id}")
                    raise HTTPException(status_code=409, detail="Place already exists in this list")
                except psycopg2.errors.CheckViolation as e:
                    conn.rollback()
                    logger.warning(f"Check constraint violation adding place to list {list_id}: {e}")
                    raise HTTPException(status_code=400, detail=f"Invalid data provided for place: {e}")
                except psycopg2.Error as db_i_err:
                    conn.rollback()
                    logger.error(f"DB error adding place: {db_i_err}", exc_info=True)
                    raise HTTPException(status_code=500, detail="Database error adding place")

    except HTTPException as he: raise he
    except psycopg2.Error as db_err: logger.error(f"DB error processing add place request: {db_err}", exc_info=True); raise HTTPException(status_code=500, detail="DB error processing request")
    except Exception as e: logger.error(f"Unexpected error adding place: {e}", exc_info=True); raise HTTPException(status_code=500, detail="Internal server error adding place")


@app.patch("/lists/{list_id}/places/{place_id}", response_model=PlaceItem, tags=["Lists", "Places"])
async def update_place( list_id: int, place_id: int, place_update: PlaceUpdate, authorization: str = Header(..., alias="Authorization") ):
    """Updates the notes (or other fields) of a specific place within a list. User must be owner or collaborator."""
    firebase_token = verify_firebase_token(authorization)
    if place_update.model_dump(exclude_unset=True) == {}:
        # Return current data if nothing to update? Or raise 400? Raising 400 is clearer.
        raise HTTPException(status_code=400, detail="No update fields provided")

    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            # --- Use access check: Owner OR Collaborator can update ---
            check_list_access(conn, list_id, user_id)

            # Check if place exists *in this list* first
            with conn.cursor() as cur_check:
                cur_check.execute("SELECT 1 FROM places WHERE id = %s AND list_id = %s", (place_id, list_id))
                if not cur_check.fetchone():
                    raise HTTPException(status_code=404, detail="Place not found in this list")

            # Perform update (only notes currently)
            if place_update.notes is not None:
                 with conn.cursor(cursor_factory=RealDictCursor) as cur_update:
                    cur_update.execute(
                        """ UPDATE places SET notes = %s WHERE id = %s RETURNING id, name, address, latitude, longitude, rating, notes, visit_status as "visitStatus" """,
                        (place_update.notes, place_id)
                    )
                    updated_place_data = cur_update.fetchone()
                    if not updated_place_data: # Should not happen if existence check passed
                        logger.error(f"Failed to update place {place_id} notes after existence check.")
                        raise HTTPException(status_code=500, detail="Failed to update place notes")
                    conn.commit()
                    logger.info(f"Updated notes for place {place_id} in list {list_id} by user {user_id}")
                    return PlaceItem(**updated_place_data)
            else:
                 # If no updatable fields specific to this PATCH model were provided,
                 # just fetch and return the current state.
                 with conn.cursor(cursor_factory=RealDictCursor) as cur_fetch:
                     cur_fetch.execute(""" SELECT id, name, address, latitude, longitude, rating, notes, visit_status as "visitStatus" FROM places WHERE id = %s """, (place_id,))
                     current_data = cur_fetch.fetchone()
                     if not current_data: raise HTTPException(status_code=404, detail="Place not found after update attempt")
                     logger.info(f"No specific fields to update for place {place_id}, returning current data.")
                     return PlaceItem(**current_data)

    except HTTPException as he: raise he
    except psycopg2.Error as db_err:
        conn.rollback() # Rollback on any DB error during the process
        logger.error(f"DB error updating place {place_id} in list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error updating place")
    except Exception as e:
        logger.error(f"Unexpected error updating place {place_id} in list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error updating place")

def check_list_access(conn, list_id: int, user_id: int):
    """Raises HTTPException if user is not owner or collaborator."""
    with conn.cursor() as cur:
        cur.execute("""
            SELECT EXISTS (
                SELECT 1 FROM lists WHERE id = %s AND owner_id = %s
                UNION
                SELECT 1 FROM list_collaborators WHERE list_id = %s AND user_id = %s
            )
        """, (list_id, user_id, list_id, user_id))
        has_access = cur.fetchone()[0]
        if not has_access:
            cur.execute("SELECT 1 FROM lists WHERE id = %s", (list_id,)) # Check existence
            if cur.fetchone():
                 logger.warning(f"List access check failed: User {user_id} cannot access list {list_id}.")
                 raise HTTPException(status_code=403, detail="Access denied to this list")
            else:
                 logger.warning(f"List access check failed: List {list_id} not found.")
                 raise HTTPException(status_code=404, detail="List not found")
        logger.debug(f"List access check passed for user {user_id} on list {list_id}")

@app.get("/lists/{list_id}/places", response_model=PaginatedPlaceResponse, tags=["Lists", "Places"])
async def get_places_in_list(
    list_id: int,
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1, description="Page number, starting from 1."),
    page_size: int = Query(30, ge=1, le=100, description="Items per page (1-100).")
):
    """Gets places within a specific list (paginated). User must be owner or collaborator."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            # Verify user has access to the list itself first
            check_list_access(conn, list_id, user_id)

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Get total count of places for this list
                cur.execute("SELECT COUNT(*) FROM places WHERE list_id = %s", (list_id,))
                count_result = cur.fetchone()
                total_items = count_result['count'] if count_result else 0
                total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
                logger.debug(f"Total places for list {list_id}: {total_items}")

                # Get paginated place items
                items = []
                if total_items > 0 and page <= total_pages:
                    offset = (page - 1) * page_size
                    # Ensure aliases in SELECT match PlaceItem model fields or use alias= in Field()
                    cur.execute(
                        """
                        SELECT id, name, address, latitude, longitude, rating, notes, visit_status as "visitStatus"
                        FROM places
                        WHERE list_id = %s
                        ORDER BY created_at DESC -- Define consistent order (e.g., creation time, name)
                        LIMIT %s OFFSET %s
                        """,
                        (list_id, page_size, offset)
                    )
                    # Map rows directly to PlaceItem Pydantic model
                    items = [PlaceItem(**row) for row in cur.fetchall()]
                    logger.debug(f"Fetched {len(items)} places for list {list_id}, page {page}")
                else:
                     logger.debug(f"No places to fetch for list {list_id}, page {page} (total_items={total_items}, total_pages={total_pages})")


                return PaginatedPlaceResponse(
                    items=items, page=page, page_size=page_size,
                    total_items=total_items, total_pages=total_pages
                )
    except HTTPException as he:
        raise he # Re-raise specific HTTP errors
    except psycopg2.Error as db_err:
        logger.error(f"DB error fetching places for list {list_id}, page {page}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error fetching places")
    except Exception as e:
        logger.error(f"Unexpected error fetching places for list {list_id}, page {page}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching places")

@app.delete("/lists/{list_id}/places/{place_id}", status_code=204, tags=["Lists", "Places"])
async def delete_place_from_list(
    list_id: int,
    place_id: int, # The DB ID of the place record
    authorization: str = Header(..., alias="Authorization")
):
    """Deletes a specific place from a list. User must be owner or collaborator."""
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            # --- Use access check: Owner OR Collaborator can delete places ---
            check_list_access(conn, list_id, user_id)

            with conn.cursor() as cur:
                # Delete the place specifically from this list
                cur.execute(
                    "DELETE FROM places WHERE id = %s AND list_id = %s",
                    (place_id, list_id)
                )
                # Check if any row was actually deleted
                if cur.rowcount == 0:
                    # If rowcount is 0, the place didn't exist *in this list*
                    # (or potentially was already deleted concurrently)
                    logger.warning(f"Attempt to delete place {place_id} from list {list_id} by user {user_id}, but place was not found in list.")
                    # Return 404 Not Found, as the specific resource (place within this list) doesn't exist
                    raise HTTPException(status_code=404, detail="Place not found in this list")

                conn.commit()
                logger.info(f"Place {place_id} deleted from list {list_id} by user {user_id}")

            # Return 204 No Content on successful deletion
            return Response(status_code=204)

    except HTTPException as he:
        raise he # Re-raise specific HTTP errors
    except psycopg2.Error as db_err:
        conn.rollback() # Rollback on database errors
        logger.error(f"DB error deleting place {place_id} from list {list_id}: {db_err}", exc_info=True)
        raise HTTPException(status_code=500, detail="Database error deleting place")
    except Exception as e:
        logger.error(f"Unexpected error deleting place {place_id} from list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error deleting place")
    

@app.get("/lists/public", response_model=PaginatedListResponse, tags=["Lists", "Discovery"])
async def get_public_lists(
    # No authorization needed? Or optional? Assuming public means public.
    # authorization: Optional[str] = Header(None, alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100)
):
    """Gets publicly available lists (paginated)."""
    logger.info(f"Received GET /lists/public?page={page}&page_size={page_size}")
    try:
        with get_db_connection() as conn:
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Base query for public lists
                base_sql = "FROM lists l WHERE l.is_private = FALSE" # Ensure correct column name
                params = []

                # Get total count
                count_sql = f"SELECT COUNT(*) {base_sql}"
                cur.execute(count_sql, tuple(params)) # Pass empty params if none
                total_items = cur.fetchone()['count']
                total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
                logger.debug(f"Total public lists: {total_items}")

                # Get paginated items
                items = []
                if total_items > 0 and page <= total_pages:
                    offset = (page - 1) * page_size
                    items_sql = f"""
                        SELECT
                            l.id, l.name, l.description, l.is_private,
                            (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
                        {base_sql}
                        ORDER BY l.created_at DESC -- Or maybe popularity/update time?
                        LIMIT %s OFFSET %s
                    """
                    params.extend([page_size, offset])
                    cur.execute(items_sql, tuple(params))
                    items = [ListViewResponse(id=r['id'], name=r['name'], description=r['description'], isPrivate=r['is_private'], place_count=r.get('place_count', 0)) for r in cur.fetchall()]

                return PaginatedListResponse(
                    items=items, page=page, page_size=page_size,
                    total_items=total_items, total_pages=total_pages
                )
    except psycopg2.Error as db_err: logger.error(f"DB error fetching public lists: {db_err}", exc_info=True); raise HTTPException(status_code=500, detail="DB error")
    except Exception as e: logger.error(f"Unexpected error fetching public lists: {e}", exc_info=True); raise HTTPException(status_code=500, detail="Server error")
# --- ^^^ MODIFIED Public Lists Endpoint ^^^ ---


# --- vvv MODIFIED Search Lists Endpoint vvv ---
@app.get("/lists/search", response_model=PaginatedListResponse, tags=["Lists", "Discovery"])
async def search_lists(
    q: str = Query(..., min_length=1, description="Search query string."),
    authorization: Optional[str] = Header(None, alias="Authorization"), # Auth might be needed to search private lists? Optional for now.
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100)
):
    """Searches public lists (and optionally user's private lists) by name or description (paginated)."""
    logger.info(f"Received GET /lists/search?q={q}&page={page}&page_size={page_size}")
    search_term = f"%{q}%"
    user_id = None
    if authorization: # If user is authenticated, potentially search their private lists too
         try:
             firebase_token = verify_firebase_token(authorization)
             # Need get_owner_id within a DB connection context
         except HTTPException:
             # Treat as unauthenticated search if token is invalid/expired
             logger.warning("Invalid token provided for list search, searching public only.")
             pass # Proceed with public-only search

    try:
        with get_db_connection() as conn:
             if authorization: # Get user_id if authenticated
                 try:
                     user_id, _ = get_owner_id(firebase_token, conn)
                 except Exception: # Handle error during get_owner_id if needed
                      logger.error("Error getting user_id during authenticated search", exc_info=True)
                      user_id = None # Fallback to public search

             with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Build query dynamically based on auth status
                base_sql = "FROM lists l WHERE (l.name ILIKE %s OR l.description ILIKE %s)"
                params = [search_term, search_term]

                if user_id:
                     # Authenticated: Search public lists OR user's own lists
                    base_sql += " AND (l.is_private = FALSE OR l.owner_id = %s)"
                    params.append(user_id)
                else:
                     # Unauthenticated: Search public lists ONLY
                    base_sql += " AND l.is_private = FALSE"

                # Get total count
                count_sql = f"SELECT COUNT(*) {base_sql}"
                cur.execute(count_sql, tuple(params))
                total_items = cur.fetchone()['count']
                total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
                logger.debug(f"Found {total_items} lists matching search '{q}'")

                # Get paginated items
                items = []
                if total_items > 0 and page <= total_pages:
                    offset = (page - 1) * page_size
                    # Add place count subquery, define order (e.g., relevance, date)
                    # Relevance search (e.g., using pg_trgm or full-text search) is more complex
                    items_sql = f"""
                        SELECT
                            l.id, l.name, l.description, l.is_private,
                            (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
                        {base_sql}
                        ORDER BY l.created_at DESC -- Order by creation date for now
                        LIMIT %s OFFSET %s
                    """
                    params.extend([page_size, offset])
                    cur.execute(items_sql, tuple(params))
                    items = [ListViewResponse(id=r['id'], name=r['name'], description=r['description'], isPrivate=r['is_private'], place_count=r.get('place_count', 0)) for r in cur.fetchall()]

                return PaginatedListResponse(
                    items=items, page=page, page_size=page_size,
                    total_items=total_items, total_pages=total_pages
                )
    except psycopg2.Error as db_err: logger.error(f"DB error searching lists for '{q}': {db_err}", exc_info=True); raise HTTPException(status_code=500, detail="DB error")
    except Exception as e: logger.error(f"Unexpected error searching lists for '{q}': {e}", exc_info=True); raise HTTPException(status_code=500, detail="Server error")
# --- ^^^ MODIFIED Search Lists Endpoint ^^^ ---


# --- Keep /lists/recent endpoint (likely doesn't need pagination) ---
@app.get("/lists/recent", response_model=List[ListViewResponse], tags=["Lists", "Discovery"])
async def get_recent_lists(
    limit: int = Query(10, ge=1, le=50), # Keep limit based
    authorization: str = Header(..., alias="Authorization") # Assuming requires auth
):
    """Gets recently created lists (limit based, not paginated)."""
    # ... (Keep existing implementation, ensure it returns ListViewResponse) ...
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn) # Get user ID if needed for filtering?
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                 # Example: Fetch recent public lists OR lists owned by user
                 cur.execute(
                     """
                     SELECT l.id, l.name, l.description, l.is_private,
                            (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
                     FROM lists l
                     WHERE l.is_private = FALSE OR l.owner_id = %s
                     ORDER BY l.created_at DESC
                     LIMIT %s
                     """,
                     (user_id, limit)
                 )
                 items = [ListViewResponse(**row) for row in cur.fetchall()]
                 return items
    except HTTPException as he: raise he
    except psycopg2.Error as db_err: logger.error(f"DB error fetching recent lists: {db_err}", exc_info=True); raise HTTPException(status_code=500, detail="DB error")
    except Exception as e: logger.error(f"Unexpected error fetching recent lists: {e}", exc_info=True); raise HTTPException(status_code=500, detail="Server error")


# --- vvv NEW/MODIFIED Notification Endpoints vvv ---
@app.get("/notifications", response_model=PaginatedNotificationResponse, tags=["Notifications"])
async def get_notifications(
    authorization: str = Header(..., alias="Authorization"),
    page: int = Query(1, ge=1),
    page_size: int = Query(25, ge=1, le=100)
):
    """Gets notifications for the authenticated user (paginated)."""
    logger.info(f"Received GET /notifications?page={page}&page_size={page_size}")
    firebase_token = verify_firebase_token(authorization)
    try:
        with get_db_connection() as conn:
            user_id, _ = get_owner_id(firebase_token, conn)
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Assuming a 'notifications' table like:
                # CREATE TABLE notifications (
                #   id SERIAL PRIMARY KEY,
                #   user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                #   title VARCHAR(255) NOT NULL,
                #   message TEXT,
                #   is_read BOOLEAN DEFAULT FALSE,
                #   timestamp TIMESTAMPTZ DEFAULT NOW(),
                #   type VARCHAR(50), -- Optional: e.g., 'follow', 'list_update'
                #   related_id INTEGER -- Optional: ID of related user/list etc.
                # );
                # CREATE INDEX idx_notifications_user_id_timestamp ON notifications (user_id, timestamp DESC);

                # Get total count
                cur.execute("SELECT COUNT(*) FROM notifications WHERE user_id = %s", (user_id,))
                total_items = cur.fetchone()['count']
                total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0

                # Get paginated items
                items = []
                if total_items > 0 and page <= total_pages:
                    offset = (page - 1) * page_size
                    cur.execute(
                        """
                        SELECT id, title, message, is_read, timestamp
                        FROM notifications
                        WHERE user_id = %s
                        ORDER BY timestamp DESC -- Most recent first
                        LIMIT %s OFFSET %s
                        """,
                        (user_id, page_size, offset)
                    )
                    items = [NotificationItem(**row) for row in cur.fetchall()]

                return PaginatedNotificationResponse(
                    items=items, page=page, page_size=page_size,
                    total_items=total_items, total_pages=total_pages
                )
    except HTTPException as he: raise he
    except psycopg2.Error as db_err: logger.error(f"DB error fetching notifications for user {user_id}: {db_err}", exc_info=True); raise HTTPException(status_code=500, detail="DB error")
    except Exception as e: logger.error(f"Unexpected error fetching notifications for user {user_id}: {e}", exc_info=True); raise HTTPException(status_code=500, detail="Server error")

# Add DELETE endpoint for places if needed
# @app.delete("/lists/{list_id}/places/{place_id}", status_code=204, tags=["Lists", "Places"]) ...


# === Main Guard (Optional for running directly) ===
# if __name__ == "__main__":
#     import uvicorn
#     # Use host="127.0.0.1" for local development security
#     # Set reload=False for production deployments
#     uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)