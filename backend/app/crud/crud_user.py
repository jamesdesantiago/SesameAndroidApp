# backend/app/crud/crud_user.py
import asyncpg
import logging
from typing import Tuple, List, Optional

# Import schemas - adjust paths if necessary
from app.schemas import user as user_schemas
from app.schemas import token as token_schemas

logger = logging.getLogger(__name__)

# --- Custom Exceptions for User CRUD ---
class UserNotFoundError(Exception):
    """Raised when a user is expected but not found."""
    pass

class UsernameAlreadyExistsError(Exception):
    """Raised when attempting to set an already taken username."""
    pass

class DatabaseInteractionError(Exception):
    """Generic error for unexpected DB issues during CRUD operations."""
    pass


# --- CRUD Functions ---

async def get_user_by_id(db: asyncpg.Connection, user_id: int) -> Optional[asyncpg.Record]:
    """Fetches a complete user record by their database ID."""
    logger.debug(f"Fetching user by ID: {user_id}")
    query = "SELECT id, email, username, display_name, profile_picture FROM users WHERE id = $1"
    user = await db.fetchrow(query, user_id)
    if not user:
        logger.warning(f"User with ID {user_id} not found.")
    return user

async def get_user_by_firebase_uid(db: asyncpg.Connection, firebase_uid: str) -> Optional[asyncpg.Record]:
    """Fetches a user record by their Firebase UID."""
    logger.debug(f"Fetching user by Firebase UID: {firebase_uid}")
    query = "SELECT id, email, username FROM users WHERE firebase_uid = $1"
    return await db.fetchrow(query, firebase_uid)

async def get_user_by_email(db: asyncpg.Connection, email: str) -> Optional[asyncpg.Record]:
     """Fetches a user record by email."""
     logger.debug(f"Fetching user by email: {email}")
     query = "SELECT id, email, username, firebase_uid FROM users WHERE email = $1"
     return await db.fetchrow(query, email)

async def check_user_exists(db: asyncpg.Connection, user_id: int) -> bool:
     """Checks if a user exists by their database ID."""
     logger.debug(f"Checking existence of user ID: {user_id}")
     query = "SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)"
     exists = await db.fetchval(query, user_id)
     return exists or False # Ensure boolean return

async def create_user(db: asyncpg.Connection, email: str, firebase_uid: str, display_name: Optional[str] = None, profile_picture: Optional[str] = None) -> int:
    """Creates a new user entry and returns the new user ID."""
    logger.info(f"Creating new user entry for email: {email}, firebase_uid: {firebase_uid}")
    query = """
        INSERT INTO users (email, firebase_uid, display_name, profile_picture, created_at, updated_at)
        VALUES ($1, $2, $3, $4, NOW(), NOW())
        RETURNING id
    """
    try:
        user_id = await db.fetchval(query, email, firebase_uid, display_name, profile_picture)
        if not user_id:
            logger.error(f"Failed to insert new user for email {email} - no ID returned.")
            raise DatabaseInteractionError("Database insert failed to return new user ID")
        logger.info(f"New user created with ID: {user_id}")
        return user_id
    except asyncpg.exceptions.UniqueViolationError as e:
        # This might happen in race conditions if email/firebase_uid should be unique
        logger.error(f"Unique constraint violation during user creation for email {email}: {e}")
        raise DatabaseInteractionError(f"User with email {email} or Firebase UID already exists.") from e
    except Exception as e:
        logger.error(f"Unexpected error creating user {email}: {e}", exc_info=True)
        raise DatabaseInteractionError("Failed to create user record.") from e


async def update_user_firebase_uid(db: asyncpg.Connection, user_id: int, firebase_uid: str):
    """Updates the Firebase UID for an existing user."""
    logger.warning(f"Updating firebase_uid for user {user_id} to {firebase_uid}")
    query = "UPDATE users SET firebase_uid = $1, updated_at = NOW() WHERE id = $2"
    status = await db.execute(query, firebase_uid, user_id)
    if status == 'UPDATE 0':
        logger.error(f"Failed to update firebase_uid for user {user_id} - user not found?")
        # Optionally check if user exists and raise UserNotFoundError if appropriate


async def get_or_create_user_by_firebase(db: asyncpg.Connection, token_data: token_schemas.FirebaseTokenData) -> Tuple[int, bool]:
    """
    Gets user ID from DB based on firebase token data, creating if necessary.
    Updates Firebase UID if user found by email but UID differs.
    Returns (user_id: int, needs_username: bool).
    """
    firebase_uid = token_data.uid
    email = token_data.email

    # Validate input from token data
    if not email:
        logger.error(f"Firebase token for uid {firebase_uid} missing email.")
        raise ValueError("Email missing from Firebase token data")

    async with db.transaction(): # Use transaction for get/update/create logic
        # 1. Check by firebase_uid
        user_record_by_uid = await get_user_by_firebase_uid(db, firebase_uid)
        if user_record_by_uid:
            user_id = user_record_by_uid['id']
            needs_username = user_record_by_uid['username'] is None
            logger.debug(f"User found by firebase_uid: {user_id}, NeedsUsername: {needs_username}")
            return user_id, needs_username

        # 2. Check by email
        user_record_by_email = await get_user_by_email(db, email)
        if user_record_by_email:
            user_id = user_record_by_email['id']
            existing_firebase_uid = user_record_by_email['firebase_uid']
            needs_username = user_record_by_email['username'] is None
            logger.debug(f"User found by email: {user_id}. Existing UID: {existing_firebase_uid}, Token UID: {firebase_uid}")

            # Update Firebase UID if it's different or null
            if existing_firebase_uid != firebase_uid:
                await update_user_firebase_uid(db, user_id, firebase_uid)
            return user_id, needs_username

        # 3. Create new user
        # Extract optional profile info from token if available
        display_name = getattr(token_data, 'name', None) # Use getattr for safety
        profile_picture = getattr(token_data, 'picture', None)
        user_id = await create_user(db, email, firebase_uid, display_name, profile_picture)
        return user_id, True # New user always needs username


async def set_user_username(db: asyncpg.Connection, user_id: int, username: str):
    """Sets the username for a given user ID, checking for uniqueness."""
    logger.info(f"Attempting to set username for user_id {user_id} to '{username}'")
    # Check if username exists (case-insensitive) excluding the current user
    check_query = "SELECT id FROM users WHERE LOWER(username) = LOWER($1) AND id != $2"
    existing_user = await db.fetchrow(check_query, username, user_id)
    if existing_user:
        logger.warning(f"Username '{username}' already taken by user {existing_user['id']}.")
        raise UsernameAlreadyExistsError(f"Username '{username}' is already taken.")

    # Attempt to update
    update_query = "UPDATE users SET username = $1, updated_at = NOW() WHERE id = $2"
    try:
        status = await db.execute(update_query, username, user_id)
        if status == 'UPDATE 0':
            # Check if the user actually exists before raising UserNotFoundError
            user_exists = await check_user_exists(db, user_id)
            if not user_exists:
                logger.error(f"Failed to set username: User with ID {user_id} not found.")
                raise UserNotFoundError(f"User with ID {user_id} not found.")
            else:
                # This implies an unexpected issue if user exists but update failed
                logger.error(f"Failed to update username for existing user {user_id} - rowcount 0.")
                raise DatabaseInteractionError("Failed to update username for existing user.")
        logger.info(f"Username successfully set for user_id {user_id}")
    except asyncpg.exceptions.UniqueViolationError as e:
         # This could happen in a race condition if the check passed but another request set the username
         logger.warning(f"UniqueViolation setting username for user {user_id} (race condition?): {e}")
         raise UsernameAlreadyExistsError(f"Username '{username}' became taken during update.") from e
    except Exception as e:
        logger.error(f"Unexpected DB error setting username for user {user_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error setting username.") from e


async def get_following(db: asyncpg.Connection, user_id: int, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Gets users the given user_id is following (paginated)."""
    offset = (page - 1) * page_size
    logger.debug(f"Fetching following for user {user_id}, page {page}, size {page_size}")

    # Get total count first
    count_query = "SELECT COUNT(*) FROM user_follows WHERE follower_id = $1"
    total_items = await db.fetchval(count_query, user_id) or 0

    # Get paginated items
    fetch_query = """
        SELECT u.id, u.email, u.username, u.display_name, u.profile_picture
        FROM user_follows uf
        JOIN users u ON uf.followed_id = u.id
        WHERE uf.follower_id = $1
        ORDER BY u.username ASC -- Or uf.created_at DESC
        LIMIT $2 OFFSET $3
    """
    following_records = await db.fetch(fetch_query, user_id, page_size, offset)
    logger.debug(f"Found {len(following_records)} following users (total: {total_items})")
    return following_records, total_items

async def get_followers(db: asyncpg.Connection, user_id: int, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """
    Gets users following the given user_id (paginated).
    Includes 'is_following' field indicating if user_id follows the follower back.
    """
    offset = (page - 1) * page_size
    logger.debug(f"Fetching followers for user {user_id}, page {page}, size {page_size}")

    # Get total count
    count_query = "SELECT COUNT(*) FROM user_follows WHERE followed_id = $1"
    total_items = await db.fetchval(count_query, user_id) or 0

    # Fetch query including is_following status relative to user_id
    fetch_query = """
        SELECT
            u.id, u.email, u.username, u.display_name, u.profile_picture,
            EXISTS (
                SELECT 1 FROM user_follows f_back
                WHERE f_back.follower_id = $1 -- The user whose followers list is being viewed
                  AND f_back.followed_id = u.id -- Check if they follow this specific follower
            ) AS is_following
        FROM user_follows uf -- The relationship indicating u follows user_id
        JOIN users u ON uf.follower_id = u.id -- Get the follower's details (u)
        WHERE uf.followed_id = $1 -- Filter for followers of user_id
        ORDER BY u.username ASC -- Or uf.created_at DESC
        LIMIT $2 OFFSET $3
    """
    follower_records = await db.fetch(fetch_query, user_id, page_size, offset)
    logger.debug(f"Found {len(follower_records)} followers (total: {total_items})")
    return follower_records, total_items

async def search_users(db: asyncpg.Connection, current_user_id: int, query: str, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
     """Searches users by email/username, excluding self, including follow status relative to current_user_id."""
     offset = (page - 1) * page_size
     search_term = f"%{query}%"
     logger.debug(f"Searching users for '{query}' by user {current_user_id}, page {page}, size {page_size}")

     # Count query
     count_query = """
         SELECT COUNT(*)
         FROM users
         WHERE (LOWER(email) LIKE LOWER($1) OR LOWER(username) LIKE LOWER($1))
           AND id != $2
     """
     total_items = await db.fetchval(count_query, search_term, current_user_id) or 0

     # Fetch query including is_following status
     fetch_query = """
         SELECT
             u.id, u.email, u.username, u.display_name, u.profile_picture,
             EXISTS (
                 SELECT 1 FROM user_follows uf_check
                 WHERE uf_check.follower_id = $1 -- The searching user's ID
                   AND uf_check.followed_id = u.id
             ) AS is_following
         FROM users u
         WHERE (LOWER(u.email) LIKE LOWER($2) OR LOWER(u.username) LIKE LOWER($2)) -- Search term
           AND u.id != $1 -- Exclude self
         ORDER BY u.username ASC, u.email ASC
         LIMIT $3 OFFSET $4
     """
     users_found = await db.fetch(fetch_query, current_user_id, search_term, page_size, offset)
     logger.debug(f"Found {len(users_found)} users matching search (total: {total_items})")
     return users_found, total_items

async def follow_user(db: asyncpg.Connection, follower_id: int, followed_id: int) -> bool:
    """Creates a follow relationship. Returns True if already following, False otherwise."""
    logger.info(f"User {follower_id} attempting to follow user {followed_id}")
    # Check if target user exists first
    if not await check_user_exists(db, followed_id):
        logger.warning(f"Attempt to follow non-existent user {followed_id}")
        raise UserNotFoundError("User to follow not found")

    insert_query = """
        INSERT INTO user_follows (follower_id, followed_id, created_at)
        VALUES ($1, $2, NOW())
        ON CONFLICT (follower_id, followed_id) DO NOTHING
    """
    try:
        status = await db.execute(insert_query, follower_id, followed_id)
        if status == 'INSERT 0 1': # Indicates a new row was inserted
            logger.info(f"User {follower_id} successfully followed user {followed_id}")
            return False # Not already following
        else: # ON CONFLICT DO NOTHING was triggered or other issue (status == 'INSERT 0 0'?)
            # Check if they were already following to be sure
            already_following = await db.fetchval(
                "SELECT EXISTS (SELECT 1 FROM user_follows WHERE follower_id = $1 AND followed_id = $2)",
                follower_id, followed_id
            )
            if already_following:
                logger.warning(f"User {follower_id} already following user {followed_id}")
                return True # Already following
            else:
                # This case is unexpected if ON CONFLICT was the only possibility
                logger.error(f"INSERT status was not 'INSERT 0 1' for follow {follower_id}->{followed_id}, but not already following. Status: {status}")
                raise DatabaseInteractionError("Unexpected result during follow operation.")
    except Exception as e:
        logger.error(f"DB error during follow {follower_id}->{followed_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error during follow operation.") from e


async def unfollow_user(db: asyncpg.Connection, follower_id: int, followed_id: int) -> bool:
    """Removes a follow relationship. Returns True if unfollowed, False if not following."""
    logger.info(f"User {follower_id} attempting to unfollow user {followed_id}")
    delete_query = "DELETE FROM user_follows WHERE follower_id = $1 AND followed_id = $2"
    try:
        status = await db.execute(delete_query, follower_id, followed_id)
        deleted_count = int(status.split(" ")[1]) # Extracts count from 'DELETE 1' or 'DELETE 0'
        if deleted_count > 0:
            logger.info(f"User {follower_id} unfollowed user {followed_id}")
            return True
        else:
            logger.warning(f"User {follower_id} tried to unfollow {followed_id}, but was not following or target user DNE.")
            return False
    except Exception as e:
        logger.error(f"DB error during unfollow {follower_id}->{followed_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error during unfollow operation.") from e


async def get_user_notifications(db: asyncpg.Connection, user_id: int, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
     """Fetches notifications for a user, ordered by timestamp descending."""
     offset = (page - 1) * page_size
     logger.debug(f"Fetching notifications for user {user_id}, page {page}, size {page_size}")

     count_query = "SELECT COUNT(*) FROM notifications WHERE user_id = $1"
     total_items = await db.fetchval(count_query, user_id) or 0

     fetch_query = """
         SELECT id, title, message, is_read, timestamp
         FROM notifications
         WHERE user_id = $1
         ORDER BY timestamp DESC
         LIMIT $2 OFFSET $3
     """
     notifications = await db.fetch(fetch_query, user_id, page_size, offset)
     logger.debug(f"Found {len(notifications)} notifications (total: {total_items})")
     return notifications, total_items

# TODO: Add CRUD functions for:
# - Updating user profile (display name, picture URL) -> update_user_profile
# - Fetching privacy settings -> get_privacy_settings
# - Updating privacy settings -> update_privacy_settings
# - Deleting user account -> delete_user_account