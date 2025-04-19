# backend/tests/utils.py

import os
import asyncpg
import pytest # For pytest.fail
from typing import Dict, Any, Optional
from unittest.mock import MagicMock # For mocking records
import asyncio # For sleep

# --- Mocking Helpers ---

def create_mock_record(data: Dict[str, Any]) -> MagicMock:
    """ Creates a mock asyncpg.Record for unit testing CRUD functions. """
    mock = MagicMock(spec=asyncpg.Record)
    # Configure __getitem__ to return values from the dictionary
    mock.__getitem__.side_effect = lambda key: data.get(key)
    # Allow get method access
    mock.get.side_effect = lambda key, default=None: data.get(key, default)
    # Allow direct attribute access if needed (less common for Record)
    for key, value in data.items():
        setattr(mock, key, value)
    # Make it behave like a dictionary for ** expansion if needed
    mock.items.return_value = data.items()
    mock.keys.return_value = data.keys()
    # Add _asdict method if your endpoint mapping relies on it
    mock._asdict = lambda: data
    return mock

# --- Direct DB Data Creation Helpers (for Integration Tests) ---
# These interact directly with the database provided by the db_conn fixture

async def create_test_user_direct(db_conn: asyncpg.Connection, suffix: str, make_unique: bool = True) -> Dict[str, Any]:
    """Creates a user directly in the DB for test setup. Handles potential conflicts."""
    unique_part = f"_{os.urandom(3).hex()}" if make_unique else ""
    email = f"test_{suffix}{unique_part}@example.com"
    fb_uid = f"test_fb_uid_{suffix}{unique_part}"
    username = f"testuser_{suffix}{unique_part}"
    display_name = f"Test User {suffix}"
    user_id = None
    try:
        user_id = await db_conn.fetchval(
            """
            INSERT INTO users (email, firebase_uid, username, display_name, created_at, updated_at)
            VALUES ($1, $2, $3, $4, NOW(), NOW())
            ON CONFLICT (email) DO NOTHING -- Basic conflict handling for email
            RETURNING id
            """,
            email, fb_uid, username, display_name
        )
        # Refetch if conflict occurred on email
        if not user_id:
            user_id = await db_conn.fetchval("SELECT id FROM users WHERE email = $1", email)
        # Try insert/fetch based on firebase_uid if still not found (less likely)
        if not user_id:
             user_id = await db_conn.fetchval(
                 """
                 INSERT INTO users (email, firebase_uid, username, display_name, created_at, updated_at)
                 VALUES ($1, $2, $3, $4, NOW(), NOW())
                 ON CONFLICT (firebase_uid) DO NOTHING
                 RETURNING id
                 """,
                 email, fb_uid, username, display_name
             )
             if not user_id: user_id = await db_conn.fetchval("SELECT id FROM users WHERE firebase_uid = $1", fb_uid)

        if not user_id:
             pytest.fail(f"Failed to create or find test user {email}/{fb_uid} in helper.")

        print(f"   [Helper] Created/Found User ID: {user_id} ({username})")
        # Return essential info, fetch more if needed by tests
        return {"id": user_id, "email": email, "firebase_uid": fb_uid, "username": username, "display_name": display_name}
    except Exception as e:
         pytest.fail(f"Error in create_test_user_direct helper for {suffix}: {e}")

async def create_test_list_direct(
    db_conn: asyncpg.Connection, owner_id: int, name: str, is_private: bool, description: Optional[str] = None
) -> Dict[str, Any]:
    """ Directly creates a list in the DB for test setup. """
    try:
        list_id = await db_conn.fetchval(
            "INSERT INTO lists (owner_id, name, description, is_private, created_at, updated_at) VALUES ($1, $2, $3, $4, NOW(), NOW()) RETURNING id",
            owner_id, name, description, is_private
        )
        if not list_id: pytest.fail(f"Failed to create list '{name}' for owner {owner_id}")
        print(f"   [Helper] Created List ID: {list_id}")
        # Fetch place count (optional, could be asserted separately)
        place_count = await db_conn.fetchval("SELECT COUNT(*) FROM places WHERE list_id = $1", list_id) or 0
        return {"id": list_id, "owner_id": owner_id, "name": name, "isPrivate": is_private, "description": description, "place_count": place_count}
    except Exception as e:
         pytest.fail(f"Error in create_test_list_direct helper for {name}: {e}")

async def create_test_place_direct(
    db_conn: asyncpg.Connection, list_id: int, name: str, address: str, place_id_ext: str, # External place ID
    notes: Optional[str] = None, rating: Optional[str] = None, visit_status: Optional[str] = None
) -> Dict[str, Any]:
    """ Directly creates a place in the DB for test setup. """
    try:
        place_id_db = await db_conn.fetchval(
            """
            INSERT INTO places (list_id, place_id, name, address, latitude, longitude, rating, notes, visit_status, created_at, updated_at)
            VALUES ($1, $2, $3, $4, 0.0, 0.0, $5, $6, $7, NOW(), NOW())
            ON CONFLICT (list_id, place_id) DO NOTHING -- Handle potential conflict
            RETURNING id
            """,
            list_id, place_id_ext, name, address, rating, notes, visit_status
        )
        # Refetch if conflict
        if not place_id_db:
             place_id_db = await db_conn.fetchval("SELECT id FROM places WHERE list_id = $1 AND place_id = $2", list_id, place_id_ext)

        if not place_id_db: pytest.fail(f"Failed to create/find place '{name}' (ext: {place_id_ext}) for list {list_id}")
        print(f"   [Helper] Created/Found Place DB ID: {place_id_db} in List ID: {list_id}")
        return {"id": place_id_db, "list_id": list_id, "name": name, "place_id_ext": place_id_ext}
    except Exception as e:
         pytest.fail(f"Error in create_test_place_direct helper for {name}: {e}")


async def add_collaborator_direct(db_conn: asyncpg.Connection, list_id: int, user_id: int):
     """ Directly adds a collaborator relationship, ignoring conflicts. """
     try:
         await db_conn.execute(
             "INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING",
             list_id, user_id
         )
         print(f"   [Helper] Ensured collaborator User ID: {user_id} on List ID: {list_id}")
     except Exception as e:
         pytest.fail(f"Error in add_collaborator_direct helper for list {list_id}, user {user_id}: {e}")

async def create_notification_direct(db_conn: asyncpg.Connection, user_id: int, title: str, message: str) -> int:
    """Creates a notification directly in DB."""
    try:
        notif_id = await db_conn.fetchval(
            "INSERT INTO notifications (user_id, title, message, timestamp) VALUES ($1, $2, $3, NOW()) RETURNING id",
            user_id, title, message
        )
        if not notif_id: pytest.fail(f"Failed to create notification for user {user_id}")
        print(f"   [Helper] Created Notification ID: {notif_id} for User ID: {user_id}")
        return notif_id
    except Exception as e:
         pytest.fail(f"Error in create_notification_direct helper for user {user_id}: {e}")

async def create_follow_direct(db_conn: asyncpg.Connection, follower_id: int, followed_id: int):
     """Creates a follow relationship directly in DB."""
     try:
         await db_conn.execute(
             "INSERT INTO user_follows (follower_id, followed_id, created_at) VALUES ($1, $2, NOW()) ON CONFLICT DO NOTHING",
             follower_id, followed_id
         )
         print(f"   [Helper] Ensured Follow Exists: {follower_id} -> {followed_id}")
     except Exception as e:
          pytest.fail(f"Error in create_follow_direct helper {follower_id}->{followed_id}: {e}")