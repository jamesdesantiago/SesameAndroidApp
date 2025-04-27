# backend/tests/crud/test_crud_user.py

import pytest
import asyncpg
from unittest.mock import AsyncMock, MagicMock, patch, call
import unittest.mock # Import unittest.mock for ANY
from typing import Dict, Any, List, Optional, Tuple
import datetime

# Import logging setup module early
import backend.app.core.logging

# Import module and functions/exceptions
from backend.app.crud import crud_user
from backend.app.crud.crud_user import UserNotFoundError, UsernameAlreadyExistsError, DatabaseInteractionError
from backend.app.schemas.token import FirebaseTokenData
from backend.app.schemas import user as user_schemas

# Import the helper from utils
from backend.tests.utils import create_mock_record

pytestmark = pytest.mark.asyncio

# Logger for this test file
logger = backend.app.core.logging.get_logger(__name__)

# Helper to create a mock asyncpg.exceptions.UniqueViolationError
def create_mock_unique_violation_error(message, constraint_name=None):
    """Creates a mock UniqueViolationError with a constraint_name attribute."""
    # asyncpg exception instances don't take keyword args like message= or constraint_name=
    # We need to create a mock object that *looks* like the exception instance.
    mock_exc = MagicMock(spec=asyncpg.exceptions.UniqueViolationError)
    mock_exc.args = (message,) # Set args for the exception message string
    # Add attributes that the code might check
    mock_exc.constraint_name = constraint_name
    mock_exc.sqlstate = '23505' # Common SQLSTATE for unique_violation
    # Add other attributes if the code inspects them (e.g., detail, table_name)
    return mock_exc

# Helper to create a mock asyncpg.exceptions.CheckViolationError
def create_mock_check_violation_error(message, constraint_name=None):
    """Creates a mock CheckViolationError with a constraint_name attribute."""
    mock_exc = MagicMock(spec=asyncpg.exceptions.CheckViolationError)
    mock_exc.args = (message,)
    mock_exc.constraint_name = constraint_name
    mock_exc.sqlstate = '23514' # Common SQLSTATE for check_violation
    return mock_exc


# --- Test get_user_by_id ---
async def test_get_user_by_id_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_record_data = {"id": 1, "email": "test@test.com", "username": "tester",
                        "display_name": "Test User", "profile_picture": None,
                        "profile_is_public": True, "lists_are_public": True, "allow_analytics": True}
    mock_conn.fetchrow.return_value = create_mock_record(mock_record_data)

    user = await crud_user.get_user_by_id(mock_conn, 1)

    assert user is not None
    assert dict(user) == mock_record_data # Assert the dictionary representation matches

    # Assert the arguments passed to the mock
    mock_conn.fetchrow.assert_awaited_once_with(
        unittest.mock.ANY, # Match any SQL string (less brittle)
        1 # Check the parameter is correct
    )


async def test_get_user_by_id_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None
    user = await crud_user.get_user_by_id(mock_conn, 999)
    assert user is None
    mock_conn.fetchrow.assert_awaited_once_with(unittest.mock.ANY, 999) # Check args


# --- Test check_user_exists ---
async def test_check_user_exists_true():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = True
    exists = await crud_user.check_user_exists(mock_conn, 1)
    assert exists is True
    mock_conn.fetchval.assert_awaited_once_with("SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)", 1)

async def test_check_user_exists_false():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = False
    exists = await crud_user.check_user_exists(mock_conn, 999)
    assert exists is False
    mock_conn.fetchval.assert_awaited_once_with("SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)", 999)


# --- Test create_user ---
async def test_create_user_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    email = "new@test.com"; firebase_uid = "fbid_new"; display_name = "New User"; profile_picture = "pic_url"
    expected_id = 100
    # Mock fetchval to return the new ID from the RETURNING clause
    mock_conn.fetchval.return_value = expected_id

    user_id = await crud_user.create_user(mock_conn, email, firebase_uid, display_name, profile_picture)

    assert user_id == expected_id
    mock_conn.fetchval.assert_awaited_once() # Check INSERT ... RETURNING was called
    # Check args passed to insert
    insert_args = mock_conn.fetchval.await_args.args
    assert insert_args[1] == email
    assert insert_args[2] == firebase_uid
    assert insert_args[3] == display_name
    assert insert_args[4] == profile_picture


# FIX: Corrected exception mocking
async def test_create_user_unique_violation():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    email = "existing@test.com"; firebase_uid = "fbid_exist"
    # Simulate UniqueViolationError on the fetchval (INSERT) call
    mock_exception = create_mock_unique_violation_error('duplicate key value violates unique constraint "users_email_key"', 'users_email_key')
    mock_conn.fetchval.side_effect = mock_exception

    # The CRUD code will catch UniqueViolationError and raise UsernameAlreadyExistsError
    with pytest.raises(UsernameAlreadyExistsError, match=f"User with email {email} already exists."): # FIX: Simplified regex pattern based on CRUD logic
         await crud_user.create_user(mock_conn, email, firebase_uid)

    mock_conn.fetchval.assert_awaited_once() # Check INSERT attempt happened


async def test_create_user_db_error():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    email = "error@test.com"; firebase_uid = "fbid_error"
    # Simulate generic PostgresError on the fetchval (INSERT) call
    mock_conn.fetchval.side_effect = asyncpg.PostgresError("Connection failed")

    with pytest.raises(DatabaseInteractionError, match="Failed to create user record."):
         await crud_user.create_user(mock_conn, email, firebase_uid)
    mock_conn.fetchval.assert_awaited_once()


# --- Test update_user_firebase_uid ---
async def test_update_user_firebase_uid_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1; new_firebase_uid = "new_fbid"
    mock_conn.execute.return_value = "UPDATE 1" # Simulate 1 row updated

    await crud_user.update_user_firebase_uid(mock_conn, user_id, new_firebase_uid)

    mock_conn.execute.assert_awaited_once_with("UPDATE users SET firebase_uid = $1, updated_at = NOW() WHERE id = $2", new_firebase_uid, user_id)


async def test_update_user_firebase_uid_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 999; new_firebase_uid = "new_fbid"
    mock_conn.execute.return_value = "UPDATE 0" # Simulate 0 rows updated

    await crud_user.update_user_firebase_uid(mock_conn, user_id, new_firebase_uid)

    mock_conn.execute.assert_awaited_once_with("UPDATE users SET firebase_uid = $1, updated_at = NOW() WHERE id = $2", new_firebase_uid, user_id)


# --- Test get_or_create_user_by_firebase ---
# FIX: Corrected mock sequence for fetchrow and fetchval to accurately simulate DB calls
async def test_get_or_create_user_found_by_firebase_uid():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Mock transaction methods
    mock_tx = AsyncMock()
    mock_conn.transaction.return_value = mock_tx

    user_id_expected = 10; firebase_uid = "firebase_uid_1"; email = "f@t.com"
    # Mock the sequence of DB calls within get_or_create:
    # 1. get_user_by_firebase_uid -> fetchrow -> return record
    # 2. get_user_by_id -> fetchrow -> return record
    mock_conn.fetchrow.side_effect = [
        create_mock_record({"id": user_id_expected, "email": email, "username": "fbuser", "firebase_uid": firebase_uid}), # 1. get_user_by_firebase_uid result
        create_mock_record({"id": user_id_expected, "email": email, "username": "fbuser", "display_name": "User", "profile_picture": None, "profile_is_public": True, "lists_are_public": True, "allow_analytics": True}) # 2. get_user_by_id result
    ]
    # Mock fetchval and execute are not called in this specific scenario path
    mock_conn.fetchval.return_value = None
    mock_conn.execute.return_value = "UPDATE 0"

    token_data = FirebaseTokenData(uid=firebase_uid, email=email)
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)

    assert user_id == user_id_expected
    assert needs_username is False # User has a username in the mock record

    # Check the calls were made in the correct order and with correct args
    mock_conn.fetchrow.assert_has_calls([
        call("SELECT id, email, username FROM users WHERE firebase_uid = $1", firebase_uid), # First lookup
        call(unittest.mock.ANY, user_id_expected) # Second lookup (get_user_by_id) - uses ANY for SQL string
    ])
    assert mock_conn.fetchrow.await_count == 2
    mock_conn.fetchval.assert_not_awaited()
    mock_conn.execute.assert_not_awaited()


# FIX: Corrected mock sequence and assertions
async def test_get_or_create_user_found_by_email_update_uid():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Mock transaction methods
    mock_tx = AsyncMock()
    mock_conn.transaction.return_value = mock_tx

    user_id_expected = 11; old_fb_uid = "old"; new_fb_uid = "new"; email="e@t.com"
    # Mock the sequence of DB calls within get_or_create:
    # 1. get_user_by_firebase_uid -> fetchrow -> return None
    # 2. get_user_by_email -> fetchrow -> return record
    # 3. get_user_by_id -> fetchrow -> return full record
    # 4. update_user_firebase_uid -> execute -> return "UPDATE 1"
    mock_conn.fetchrow.side_effect = [
        None, # 1. get_user_by_firebase_uid result (not found by new UID)
        create_mock_record({"id": user_id_expected, "email": email, "username": None, "firebase_uid": old_fb_uid}), # 2. get_user_by_email result
        create_mock_record({"id": user_id_expected, "email": email, "username": None, "display_name": "User", "profile_picture": None, "profile_is_public": True, "lists_are_public": True, "allow_analytics": True}) # 3. get_user_by_id result
    ]
    # Mock execute for update_user_firebase_uid (called if UID differs)
    mock_conn.execute.return_value = "UPDATE 1"
    # Mock fetchval won't be called in this specific scenario path
    mock_conn.fetchval.return_value = None # Ensure create_user or check_user_exists not triggered


    token_data = FirebaseTokenData(uid=new_fb_uid, email=email)
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)

    assert user_id == user_id_expected
    assert needs_username is True # User has no username in the mock record

    # Check the calls were made in the correct order and with correct args
    mock_conn.fetchrow.assert_has_calls([
        call("SELECT id, email, username FROM users WHERE firebase_uid = $1", new_fb_uid), # First lookup
        call("SELECT id, email, username, firebase_uid FROM users WHERE email = $1", email), # Second lookup
        call(unittest.mock.ANY, user_id_expected) # Third lookup (get_user_by_id) - uses ANY for SQL string
    ])
    mock_conn.execute.assert_awaited_once_with("UPDATE users SET firebase_uid = $1, updated_at = NOW() WHERE id = $2", new_fb_uid, user_id_expected) # Check UID update occurred
    assert mock_conn.fetchrow.await_count == 3
    mock_conn.fetchval.assert_not_awaited()


# FIX: Corrected mock sequence and assertions
async def test_get_or_create_user_create_new():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Mock transaction methods
    mock_tx = AsyncMock()
    mock_conn.transaction.return_value = mock_tx

    new_user_id = 12; new_fb_uid = "new_uid"; new_email="new@t.com"
    # Mock the sequence of DB calls within get_or_create:
    # 1. get_user_by_firebase_uid -> fetchrow -> return None
    # 2. get_user_by_email -> fetchrow -> return None
    # 3. create_user -> fetchval -> return new_user_id
    mock_conn.fetchrow.side_effect = [None, None] # Lookups both return None
    mock_conn.fetchval.return_value = new_user_id # create_user returns the new ID

    token_data = FirebaseTokenData(uid=new_fb_uid, email=new_email)
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)

    assert user_id == new_user_id
    assert needs_username is True # New user always needs username

    # Check the calls were made in the correct order and with correct args
    mock_conn.fetchrow.assert_has_calls([
        call("SELECT id, email, username FROM users WHERE firebase_uid = $1", new_fb_uid), # First lookup
        call("SELECT id, email, username, firebase_uid FROM users WHERE email = $1", new_email) # Second lookup
    ])
    mock_conn.fetchval.assert_awaited_once() # Check INSERT ... RETURNING call (create_user)
    # Check args passed to create_user (via fetchval)
    create_args = mock_conn.fetchval.await_args.args
    # Check against the *query string* and parameters for the create_user call
    assert create_args[0] == """
        INSERT INTO users (email, firebase_uid, display_name, profile_picture, created_at, updated_at)
        VALUES ($1, $2, $3, $4, NOW(), NOW())
        RETURNING id
    """
    assert create_args[1:] == (new_email, new_fb_uid, None, None) # Check parameters
    mock_conn.execute.assert_not_awaited()


# --- Test set_user_username ---
async def test_set_username_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1; username = "new_user"
    # Mock fetchrow (check uniqueness) to return None (username available)
    mock_conn.fetchrow.return_value = None
    # Mock execute (UPDATE) to return "UPDATE 1" (success)
    mock_conn.execute.return_value = "UPDATE 1"
    # Mock fetchval (check_user_exists) won't be called in this success path

    await crud_user.set_user_username(mock_conn, user_id, username)

    mock_conn.fetchrow.assert_awaited_once_with("SELECT id FROM users WHERE LOWER(username) = LOWER($1) AND id != $2", username, user_id)
    mock_conn.execute.assert_awaited_once_with("UPDATE users SET username = $1, updated_at = NOW() WHERE id = $2", username, user_id)
    mock_conn.fetchval.assert_not_awaited() # check_user_exists should not be called


# FIX: Corrected mock setup and assertion
async def test_set_username_already_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1; username = "taken_user"; existing_user_id = 2
    # Mock fetchrow (check uniqueness) to return a record (username taken)
    mock_conn.fetchrow.return_value = create_mock_record({"id": existing_user_id})
    # Mock execute and fetchval should *not* be called in this path

    with pytest.raises(UsernameAlreadyExistsError, match=f"Username '{username}' is already taken."):
        await crud_user.set_user_username(mock_conn, user_id, username)

    mock_conn.fetchrow.assert_awaited_once_with("SELECT id FROM users WHERE LOWER(username) = LOWER($1) AND id != $2", username, user_id)
    mock_conn.execute.assert_not_awaited()
    mock_conn.fetchval.assert_not_awaited()


async def test_set_username_update_fails_user_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 999; username = "some_user"
    # Mock fetchrow (check uniqueness) to return None (username available)
    mock_conn.fetchrow.return_value = None
    # Mock execute (UPDATE) to return "UPDATE 0" (update failed)
    mock_conn.execute.return_value = "UPDATE 0"
    # Mock fetchval (check_user_exists) to return False (user not found)
    mock_conn.fetchval.return_value = False

    with pytest.raises(UserNotFoundError, match=f"User with ID {user_id} not found."):
        await crud_user.set_user_username(mock_conn, user_id, username)

    mock_conn.fetchrow.assert_awaited_once_with("SELECT id FROM users WHERE LOWER(username) = LOWER($1) AND id != $2", username, user_id)
    mock_conn.execute.assert_awaited_once_with("UPDATE users SET username = $1, updated_at = NOW() WHERE id = $2", username, user_id)
    mock_conn.fetchval.assert_awaited_once_with("SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)", user_id) # check_user_exists called


# --- Test get_following ---
async def test_get_following_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1; page = 1; page_size = 10; offset = (page - 1) * page_size
    total_items_expected = 2
    # Mock fetchval for total count
    mock_conn.fetchval.return_value = total_items_expected
    # Mock fetch for followed users (include fields needed for UserFollowInfo)
    mock_conn.fetch.return_value = [
        create_mock_record({"id": 2, "email": "user2@test.com", "username": "user2", "display_name": "User Two", "profile_picture": None}),
        create_mock_record({"id": 3, "email": "user3@test.com", "username": "user3", "display_name": "User Three", "profile_picture": None}),
    ]
    results, total = await crud_user.get_following(mock_conn, user_id, page, page_size)
    assert total == total_items_expected
    assert len(results) == 2
    assert results[0]['id'] == 2
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM user_follows WHERE follower_id = $1", user_id) # Check count query
    mock_conn.fetch.assert_awaited_once() # Check fetch query
    # Check arguments for fetch query
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[1] == user_id
    assert fetch_args[2] == page_size
    assert fetch_args[3] == offset


# FIX: Corrected assertion
async def test_get_following_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1; page = 1; page_size = 10;
    mock_conn.fetchval.return_value = 0 # Mock count is 0
    mock_conn.fetch.return_value = [] # Mock fetch returns empty

    results, total = await crud_user.get_following(mock_conn, user_id, page, page_size)

    assert total == 0
    assert len(results) == 0
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM user_follows WHERE follower_id = $1", user_id) # Count query is called
    mock_conn.fetch.assert_not_awaited() # <--- CORRECTED: Fetch should *not* be called if count is 0


# --- Test get_followers ---
async def test_get_followers_success_and_flag():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1 # User whose followers we are getting
    page = 1; page_size = 5; offset = (page - 1) * page_size
    total_items_expected = 2
    # Mock fetchval for total count
    mock_conn.fetchval.return_value = total_items_expected
    # Mock fetch for followers (include is_following flag and other fields)
    mock_conn.fetch.return_value = [
        create_mock_record({"id": 2, "email": "f2@t.com", "username": "follower2", "display_name": "Follower Two", "profile_picture": None, "is_following": True}), # User 1 follows this one back
        create_mock_record({"id": 3, "email": "f3@t.com", "username": "follower3", "display_name": "Follower Three", "profile_picture": None, "is_following": False}), # User 1 does not follow this one
    ]
    results, total = await crud_user.get_followers(mock_conn, user_id, page, page_size)
    assert total == total_items_expected
    assert len(results) == 2
    assert results[0]['is_following'] is True
    assert results[1]['is_following'] is False
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM user_follows WHERE followed_id = $1", user_id) # Check count query
    mock_conn.fetch.assert_awaited_once() # Check fetch query
    # Check arguments for fetch query (user_id passed twice, page_size, offset)
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[1] == user_id # User ID ($1 in query)
    assert fetch_args[2] == page_size # Page size ($2 in query)
    assert fetch_args[3] == offset # Offset ($3 in query)

# --- Test search_users ---
async def test_search_users_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    current_user_id = 1
    query = "search"
    page = 1; page_size = 5; offset = (page - 1) * page_size
    total_items_expected = 1
    search_term_lower = f"%{query.lower()}%"
    # Mock fetchval for total count (params: $1=current_user_id, $2=search_term_lower)
    mock_conn.fetchval.return_value = total_items_expected
    # Mock fetch for search results (include is_following flag and other fields)
    # Params: $1=current_user_id, $2=search_term_lower, $3=page_size, $4=offset
    mock_conn.fetch.return_value = [
        create_mock_record({"id": 5, "email": "s@test.com", "username": "searchresult", "display_name": "Search Result", "profile_picture": None, "is_following": False})
    ]
    results, total = await crud_user.search_users(mock_conn, current_user_id, query, page, page_size)
    assert total == total_items_expected
    assert len(results) == 1
    assert results[0]['id'] == 5

    mock_conn.fetchval.assert_awaited_once() # Count query
    # Check arguments for count query
    count_args = mock_conn.fetchval.await_args.args
    assert count_args[1] == current_user_id # $1 in count query
    assert count_args[2] == search_term_lower # $2 in count query

    mock_conn.fetch.assert_awaited_once() # Fetch query
    # Check arguments for fetch query
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[1] == current_user_id # $1 in fetch query
    assert fetch_args[2] == search_term_lower # $2 in fetch query
    assert fetch_args[3] == page_size # $3 in fetch query
    assert fetch_args[4] == offset # $4 in fetch query


async def test_search_users_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = 0 # Count is 0
    mock_conn.fetch.return_value = [] # Fetch returns empty

    results, total = await crud_user.search_users(mock_conn, 1, "nonexistent", 1, 10)

    assert total == 0
    assert len(results) == 0
    # fetchval called once for count query
    mock_conn.fetchval.assert_awaited_once()
    # fetch is not called if count is 0
    mock_conn.fetch.assert_not_awaited()


# --- Test follow_user ---
# FIX: Corrected assertion and mock interaction check
async def test_follow_user_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    follower_id = 1; followed_id = 2
    # Mock fetchval (check_user_exists) to return True
    # Mock fetchval (INSERT ... RETURNING created_at) to return a non-None value (simulates insertion)
    mock_conn.fetchval.side_effect = [True, datetime.datetime.now()] # Sequence of calls: check_user_exists, then insert returning

    result = await crud_user.follow_user(mock_conn, follower_id, followed_id)

    assert result is False # Returns False for new follow
    # Check the calls were made in the correct order and with correct args
    mock_conn.fetchval.assert_has_calls([
        call("SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)", followed_id), # Check check_user_exists
        call("""
            INSERT INTO user_follows (follower_id, followed_id, created_at)
            VALUES ($1, $2, NOW())
            ON CONFLICT (follower_id, followed_id) DO NOTHING
            RETURNING created_at -- Return something if a row was inserted
        """, follower_id, followed_id) # Check insert returning
    ])
    assert mock_conn.fetchval.await_count == 2 # Both fetchval calls should happen
    mock_conn.execute.assert_not_awaited() # execute is not used in the returning path


# FIX: Corrected assertion and mock interaction check
async def test_follow_user_already_following():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    follower_id = 1; followed_id = 2
    # Mock fetchval (check_user_exists) to return True
    # Mock fetchval (INSERT ... RETURNING created_at) to return None (simulates ON CONFLICT DO NOTHING)
    mock_conn.fetchval.side_effect = [True, None] # Sequence of calls: check_user_exists, then insert returning

    result = await crud_user.follow_user(mock_conn, follower_id, followed_id)

    assert result is True # Returns True if already following
    # Check the calls were made in the correct order and with correct args
    mock_conn.fetchval.assert_has_calls([
        call("SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)", followed_id), # Check check_user_exists
        call("""
            INSERT INTO user_follows (follower_id, followed_id, created_at)
            VALUES ($1, $2, NOW())
            ON CONFLICT (follower_id, followed_id) DO NOTHING
            RETURNING created_at -- Return something if a row was inserted
        """, follower_id, followed_id) # Check insert returning
    ])
    assert mock_conn.fetchval.await_count == 2 # Both fetchval calls should happen
    mock_conn.execute.assert_not_awaited() # execute is not used in the returning path


async def test_follow_user_target_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    follower_id = 1; followed_id = 999
    # Mock fetchval (check_user_exists) to return False
    mock_conn.fetchval.return_value = False

    with pytest.raises(UserNotFoundError, match="User to follow not found"):
        await crud_user.follow_user(mock_conn, follower_id, followed_id)

    mock_conn.fetchval.assert_awaited_once_with("SELECT EXISTS (SELECT 1 FROM users WHERE id = $1)", followed_id)
    mock_conn.execute.assert_not_awaited()


# --- Test unfollow_user ---
async def test_unfollow_user_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    follower_id = 1; followed_id = 2
    mock_conn.execute.return_value = "DELETE 1" # Simulate successful delete

    result = await crud_user.unfollow_user(mock_conn, follower_id, followed_id)

    assert result is True
    mock_conn.execute.assert_awaited_once_with("DELETE FROM user_follows WHERE follower_id = $1 AND followed_id = $2", follower_id, followed_id)


async def test_unfollow_user_not_following():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    follower_id = 1; followed_id = 2
    mock_conn.execute.return_value = "DELETE 0" # Simulate no row deleted

    result = await crud_user.unfollow_user(mock_conn, follower_id, followed_id)

    assert result is False
    mock_conn.execute.assert_awaited_once_with("DELETE FROM user_follows WHERE follower_id = $1 AND followed_id = $2", follower_id, followed_id)


# --- Test get_user_notifications ---
async def test_get_user_notifications_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1; page = 1; page_size = 10; offset = (page - 1) * page_size; total = 1
    # Mock fetchval for total count
    mock_conn.fetchval.return_value = total
    # Mock fetch for notifications (include fields)
    mock_conn.fetch.return_value = [create_mock_record({"id": 101, "title": "N1", "message": "Msg1", "is_read": False, "timestamp": datetime.datetime.now()})]

    results, total_items = await crud_user.get_user_notifications(mock_conn, user_id, page, page_size)

    assert total_items == total
    assert len(results) == 1
    assert results[0]['id'] == 101
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM notifications WHERE user_id = $1", user_id) # Check count query
    mock_conn.fetch.assert_awaited_once() # Check fetch query
    # Check arguments for fetch query
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[1] == user_id
    assert fetch_args[2] == page_size
    assert fetch_args[3] == offset


async def test_get_user_notifications_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1; page = 1; page_size = 10;
    mock_conn.fetchval.return_value = 0 # Mock count is 0
    mock_conn.fetch.return_value = [] # Mock fetch returns empty

    results, total_items = await crud_user.get_user_notifications(mock_conn, user_id, page, page_size)

    assert total_items == 0
    assert len(results) == 0
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM notifications WHERE user_id = $1", user_id) # Count query is called
    mock_conn.fetch.assert_not_awaited() # <--- CORRECTED: Fetch should *not* be called if count is 0


# --- Test get_current_user_profile ---
# FIX: Corrected mock return for success case
async def test_get_current_user_profile_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    # Mock fetchrow to return a user record
    mock_record_data = {"id": user_id, "email": "me@t.com", "username": "meuser", "display_name": "Me User", "profile_picture": "pic"}
    mock_conn.fetchrow.return_value = create_mock_record(mock_record_data)

    profile = await crud_user.get_current_user_profile(mock_conn, user_id)

    assert profile is not None
    assert dict(profile) == mock_record_data # Assert the dictionary representation matches
    mock_conn.fetchrow.assert_awaited_once_with("SELECT id, email, username, display_name, profile_picture FROM users WHERE id = $1", user_id)

async def test_get_current_user_profile_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 999
    mock_conn.fetchrow.return_value = None

    with pytest.raises(UserNotFoundError, match=f"User with ID {user_id} not found."):
        await crud_user.get_current_user_profile(mock_conn, user_id)

    mock_conn.fetchrow.assert_awaited_once_with("SELECT id, email, username, display_name, profile_picture FROM users WHERE id = $1", user_id)


# --- Test update_user_profile ---
# FIX: Corrected mock logic for success case and no-changes case
async def test_update_user_profile_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    new_display_name = "Updated Name"
    new_pic_url = "http://new.pic/url"
    # Create update dictionary matching UserProfileUpdate.model_dump(exclude_unset=True, by_alias=True)
    profile_in = user_schemas.UserProfileUpdate(displayName=new_display_name, profilePicture=new_pic_url)
    profile_in_dict = profile_in.model_dump(exclude_unset=True, by_alias=True)

    # Mock fetchrow to return the updated record (RETURNING clause)
    mock_return_record_data = {
        "id": user_id, "email": "user@test.com", "username": "testuser",
        "display_name": new_display_name, "profile_picture": new_pic_url
    }
    mock_conn.fetchrow.return_value = create_mock_record(mock_return_record_data)

    # Patch get_current_user_profile and check_user_exists as they might be called in other branches
    with patch('backend.app.crud.crud_user.get_current_user_profile', new_callable=AsyncMock) as mock_get_profile:
        with patch('backend.app.crud.crud_user.check_user_exists', new_callable=AsyncMock) as mock_check_exists:

            result_record = await crud_user.update_user_profile(mock_conn, user_id, profile_in) # Pass Pydantic model

            assert result_record is not None
            # Assert the dictionary representation matches (removed the direct dict(result_record) which might not work on AsyncMock)
            assert result_record['id'] == mock_return_record_data['id']
            assert result_record['display_name'] == mock_return_record_data['display_name']
            assert result_record['profile_picture'] == mock_return_record_data['profile_picture']


            mock_conn.fetchrow.assert_awaited_once() # The update call with RETURNING
            # Check parameters passed to UPDATE
            update_sql = mock_conn.fetchrow.await_args.args[0]
            update_params = mock_conn.fetchrow.await_args.args[1:]

            # Construct expected SET clause parts based on update_in_dict keys (DB column names)
            expected_set_parts = []
            expected_param_values = []
            param_idx = 1
            if 'display_name' in profile_in_dict:
                expected_set_parts.append(f"display_name = ${param_idx}")
                expected_param_values.append(profile_in_dict['display_name'])
                param_idx += 1
            if 'profile_picture' in profile_in_dict:
                 expected_set_parts.append(f"profile_picture = ${param_idx}")
                 expected_param_values.append(profile_in_dict['profile_picture'])
                 param_idx += 1

            # Check that the update statement contains the expected SET clause parts
            assert all(part in update_sql for part in expected_set_parts)
            assert f"WHERE id = ${param_idx}" in update_sql

            # Check that the parameters match the expected values and the WHERE clause ID
            actual_params_set = set(update_params)
            expected_params_set = set(expected_param_values + [user_id]) # Include the WHERE clause param
            assert actual_params_set == expected_params_set

            mock_get_profile.assert_not_awaited() # get_current_user_profile should NOT be called
            mock_check_exists.assert_not_awaited() # check_user_exists should NOT be called


# FIX: Corrected mock logic for no-changes case
async def test_update_user_profile_no_changes():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    # Create empty update model (model_dump will be empty)
    profile_in = user_schemas.UserProfileUpdate()

    # Mock get_current_user_profile as the function will call it when no updates are provided
    mock_current_profile_data = {
        "id": user_id, "email": "user@test.com", "username": "testuser",
        "display_name": "Current Name", "profile_picture": "current_pic"
    }
    with patch('backend.app.crud.crud_user.get_current_user_profile', new_callable=AsyncMock) as mock_get_profile:
        # The mock_conn's fetchrow is *not* called by get_current_user_profile, only the patched mock_get_profile is.
        mock_get_profile.return_value = create_mock_record(mock_current_profile_data)
        mock_conn.fetchrow.return_value = None # Ensure the mock_conn doesn't accidentally return something

        result_record = await crud_user.update_user_profile(mock_conn, user_id, profile_in) # Pass empty Pydantic model

        assert result_record is not None
        # Assert the dictionary representation matches
        assert result_record['id'] == mock_current_profile_data['id']
        assert result_record['display_name'] == mock_current_profile_data['display_name']
        assert result_record['profile_picture'] == mock_current_profile_data['profile_picture']


        # Check that get_current_user_profile was called
        mock_get_profile.assert_awaited_once_with(mock_conn, user_id)
        mock_conn.fetchrow.assert_not_awaited() # Ensure mock_conn's fetchrow was *not* called directly
        mock_conn.execute.assert_not_awaited() # UPDATE should not be called


async def test_update_user_profile_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 999
    profile_in = user_schemas.UserProfileUpdate(displayName="New Name")
    # profile_in_dict = profile_in.model_dump(exclude_unset=True, by_alias=True) # Not used directly in this test's mock setup

    # Mock fetchrow for the UPDATE RETURNING (returns None)
    mock_conn.fetchrow.return_value = None

    # Patch check_user_exists as the function calls it if the update returns 0 rows
    with patch('backend.app.crud.crud_user.check_user_exists', new_callable=AsyncMock) as mock_check_exists:
        mock_check_exists.return_value = False # Simulate user not found by check_user_exists

        with pytest.raises(UserNotFoundError, match=f"User with ID {user_id} not found for profile update."):
            await crud_user.update_user_profile(mock_conn, user_id, profile_in) # Pass Pydantic model

        mock_conn.fetchrow.assert_awaited_once() # Check UPDATE attempt happened
        mock_check_exists.assert_awaited_once_with(mock_conn, user_id) # Check check_user_exists was called
        # Mock_conn.fetchval is called *within* check_user_exists, not directly by update_user_profile
        # so we don't assert mock_conn.fetchval here.


# --- Test get_privacy_settings ---
# FIX: Corrected mock return for success case
async def test_get_privacy_settings_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    settings_data = {"profile_is_public": True, "lists_are_public": False, "allow_analytics": True}
    # Mock fetchrow to return the settings record
    mock_conn.fetchrow.return_value = create_mock_record(settings_data)

    settings = await crud_user.get_privacy_settings(mock_conn, user_id)

    assert settings is not None
    assert dict(settings) == settings_data # Assert the dictionary representation matches
    mock_conn.fetchrow.assert_awaited_once_with("SELECT profile_is_public, lists_are_public, allow_analytics FROM users WHERE id = $1", user_id)


async def test_get_privacy_settings_user_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 999
    mock_conn.fetchrow.return_value = None

    with pytest.raises(UserNotFoundError, match=f"User {user_id} not found when fetching privacy settings."):
        await crud_user.get_privacy_settings(mock_conn, user_id)

    mock_conn.fetchrow.assert_awaited_once_with("SELECT profile_is_public, lists_are_public, allow_analytics FROM users WHERE id = $1", user_id)


# --- Test update_privacy_settings ---
# FIX: Corrected mock logic for success case and no-fields case
async def test_update_privacy_settings_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    settings_in = user_schemas.PrivacySettingsUpdate(allow_analytics=False, profile_is_public=False)
    # Create update dictionary matching settings_in.model_dump(exclude_unset=True)
    settings_in_dict = settings_in.model_dump(exclude_unset=True)

    # Simulate RETURNING returns the updated settings record
    mock_return_settings_data = {
        "profile_is_public": False, "lists_are_public": True, "allow_analytics": False # Example: lists_are_public wasn't updated
    }
    mock_conn.fetchrow.return_value = create_mock_record(mock_return_settings_data)

    # Patch get_privacy_settings and check_user_exists if they are called in other branches
    with patch('backend.app.crud.crud_user.get_privacy_settings', new_callable=AsyncMock) as mock_get_settings:
        with patch('backend.app.crud.crud_user.check_user_exists', new_callable=AsyncMock) as mock_check_exists:

            result = await crud_user.update_privacy_settings(mock_conn, user_id, settings_in) # Pass Pydantic model

            assert result is not None
            assert dict(result) == mock_return_settings_data # Assert the dictionary representation matches

            mock_conn.fetchrow.assert_awaited_once() # The update call with RETURNING
            # Check parameters passed to UPDATE
            update_sql = mock_conn.fetchrow.await_args.args[0]
            update_params = mock_conn.fetchrow.await_args.args[1:]

            # Construct expected SET clause parts based on settings_in_dict keys
            expected_set_parts = []
            expected_param_values = []
            param_idx = 1
            if 'profile_is_public' in settings_in_dict:
                expected_set_parts.append(f"profile_is_public = ${param_idx}")
                expected_param_values.append(settings_in_dict['profile_is_public'])
                param_idx += 1
            if 'lists_are_public' in settings_in_dict:
                expected_set_parts.append(f"lists_are_public = ${param_idx}")
                expected_param_values.append(settings_in_dict['lists_are_public'])
                param_idx += 1
            if 'allow_analytics' in settings_in_dict:
                expected_set_parts.append(f"allow_analytics = ${param_idx}")
                expected_param_values.append(settings_in_dict['allow_analytics'])
                param_idx += 1

            # Check that the update statement contains the expected SET clause parts
            assert all(part in update_sql for part in expected_set_parts)
            assert f"WHERE id = ${param_idx}" in update_sql

            # Check that the parameters match the expected values and the WHERE clause ID
            actual_params_set = set(update_params)
            expected_params_set = set(expected_param_values + [user_id]) # Include the WHERE clause param
            assert actual_params_set == expected_params_set

            mock_get_settings.assert_not_awaited() # get_privacy_settings should NOT be called
            mock_check_exists.assert_not_awaited() # check_user_exists should NOT be called


# FIX: Corrected mock logic for no-fields case
async def test_update_privacy_settings_no_fields():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    # Create empty update model (model_dump will be empty)
    settings_in = user_schemas.PrivacySettingsUpdate()

    # Mock get_privacy_settings as the function will call it when no updates are provided
    mock_current_settings_data = {
        "profile_is_public": True, "lists_are_public": False, "allow_analytics": True
    }
    with patch('backend.app.crud.crud_user.get_privacy_settings', new_callable=AsyncMock) as mock_get_settings:
        # The mock_conn's fetchrow is *not* called by get_privacy_settings, only the patched mock_get_settings is.
        mock_get_settings.return_value = create_mock_record(mock_current_settings_data)
        mock_conn.fetchrow.return_value = None # Ensure the mock_conn doesn't accidentally return something

        result = await crud_user.update_privacy_settings(mock_conn, user_id, settings_in) # Pass empty Pydantic model

        assert result is not None
        assert dict(result) == mock_current_settings_data # Assert the dictionary representation matches

        # Check that get_privacy_settings was called
        mock_get_settings.assert_awaited_once_with(mock_conn, user_id)
        mock_conn.fetchrow.assert_not_awaited() # Ensure mock_conn's fetchrow was *not* called directly
        mock_conn.execute.assert_not_awaited() # UPDATE should not be called


async def test_update_privacy_settings_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 999
    settings_in = user_schemas.PrivacySettingsUpdate(allow_analytics=False)

    # Mock fetchrow for the UPDATE RETURNING (returns None)
    mock_conn.fetchrow.return_value = None

    # Patch check_user_exists as the function calls it if the update returns 0 rows
    with patch('backend.app.crud.crud_user.check_user_exists', new_callable=AsyncMock) as mock_check_exists:
        mock_check_exists.return_value = False # Simulate user not found by check_user_exists

        with pytest.raises(UserNotFoundError, match=f"User with ID {user_id} not found for privacy settings update."):
             await crud_user.update_privacy_settings(mock_conn, user_id, settings_in) # Pass Pydantic model

        mock_conn.fetchrow.assert_awaited_once() # Check UPDATE attempt happened
        mock_check_exists.assert_awaited_once_with(mock_conn, user_id) # Check check_user_exists was called
        # Mock_conn.fetchval is called *within* check_user_exists, not directly by update_privacy_settings
        # so we don't assert mock_conn.fetchval here.


# FIX: Corrected assertion message
async def test_update_privacy_settings_unexpected_failure():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    settings_in = user_schemas.PrivacySettingsUpdate(allow_analytics=False)

    # Mock fetchrow for the UPDATE RETURNING (returns None)
    mock_conn.fetchrow.return_value = None
    # Mock fetchval for check_user_exists (returns True - user exists but update failed)
    mock_conn.fetchval.return_value = True

    # Patch check_user_exists as the function calls it if the update returns 0 rows
    with patch('backend.app.crud.crud_user.check_user_exists', new_callable=AsyncMock) as mock_check_exists:

        with pytest.raises(DatabaseInteractionError, match="Failed to update privacy settings."): # FIX: Changed regex match to exact
             await crud_user.update_privacy_settings(mock_conn, user_id, settings_in)

        mock_conn.fetchrow.assert_awaited_once()
        mock_check_exists.assert_awaited_once_with(mock_conn, user_id)
        mock_conn.fetchval.assert_not_awaited()


# --- Test delete_user_account ---
async def test_delete_user_account_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    mock_conn.execute.return_value = "DELETE 1" # Simulate 1 row deleted

    result = await crud_user.delete_user_account(mock_conn, user_id)

    assert result is True
    mock_conn.execute.assert_awaited_once_with("DELETE FROM users WHERE id = $1", user_id)


# FIX: Corrected assertion type and message (and removed likely unused exc_info arg if it existed)
async def test_delete_user_account_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 999
    mock_conn.execute.return_value = "DELETE 0" # Simulate 0 rows deleted

    result = await crud_user.delete_user_account(mock_conn, user_id)

    assert result is False # Should return False if not found
    mock_conn.execute.assert_awaited_once_with("DELETE FROM users WHERE id = $1", user_id)