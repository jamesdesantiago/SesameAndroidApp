# backend/tests/crud/test_crud_user.py

import pytest
import asyncpg
from unittest.mock import AsyncMock, MagicMock, patch # For mocking

# Import the module and functions/exceptions to test
from app.crud import crud_user
from app.crud.crud_user import UserNotFoundError, UsernameAlreadyExistsError, DatabaseInteractionError
from app.schemas.token import FirebaseTokenData

# Mark all tests in this file as async
pytestmark = pytest.mark.asyncio

# --- Test get_user_by_id ---

async def test_get_user_by_id_found():
    """Test finding a user by ID successfully."""
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Simulate a record being returned
    mock_record = MagicMock(spec=asyncpg.Record)
    mock_record.__getitem__.side_effect = lambda key: {"id": 1, "email": "test@test.com", "username": "tester"}.get(key)
    mock_conn.fetchrow.return_value = mock_record

    user_id_to_find = 1
    user = await crud_user.get_user_by_id(mock_conn, user_id_to_find)

    assert user is not None
    assert user['id'] == user_id_to_find
    assert user['email'] == "test@test.com"
    mock_conn.fetchrow.assert_awaited_once()
    # Verify the query structure (optional but good)
    # assert "WHERE id = $1" in mock_conn.fetchrow.await_args.args[0]
    assert mock_conn.fetchrow.await_args.args[1] == user_id_to_find

async def test_get_user_by_id_not_found():
    """Test handling when a user ID is not found."""
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None # Simulate no record found

    user_id_to_find = 999
    user = await crud_user.get_user_by_id(mock_conn, user_id_to_find)

    assert user is None
    mock_conn.fetchrow.assert_awaited_once()
    assert mock_conn.fetchrow.await_args.args[1] == user_id_to_find

# --- Test set_user_username ---

async def test_set_username_success():
    """Test setting a username successfully."""
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None # Simulate username is available
    mock_conn.execute.return_value = "UPDATE 1" # Simulate successful update

    user_id = 1
    new_username = "new_cool_user"
    await crud_user.set_user_username(mock_conn, user_id, new_username)

    # Assert the check query was called
    mock_conn.fetchrow.assert_awaited_once()
    assert mock_conn.fetchrow.await_args.args[1] == new_username
    assert mock_conn.fetchrow.await_args.args[2] == user_id

    # Assert the update query was called
    mock_conn.execute.assert_awaited_once()
    assert mock_conn.execute.await_args.args[1] == new_username
    assert mock_conn.execute.await_args.args[2] == user_id


async def test_set_username_already_exists():
    """Test setting a username that is already taken."""
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    existing_record = MagicMock(spec=asyncpg.Record); existing_record.__getitem__.return_value = 2 # Simulate user ID 2 has it
    mock_conn.fetchrow.return_value = existing_record # Simulate username check finds existing

    user_id = 1
    existing_username = "already_taken"

    with pytest.raises(UsernameAlreadyExistsError) as excinfo:
        await crud_user.set_user_username(mock_conn, user_id, existing_username)

    assert existing_username in str(excinfo.value)
    mock_conn.fetchrow.assert_awaited_once() # Check was made
    mock_conn.execute.assert_not_awaited() # Update was NOT called

async def test_set_username_update_fails_user_not_found():
    """Test when the DB update returns 'UPDATE 0' and user doesn't exist."""
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None # Simulate username is available
    mock_conn.execute.return_value = "UPDATE 0" # Simulate update affecting 0 rows
    mock_conn.fetchval.return_value = False # Simulate check_user_exists returns False

    user_id = 999 # Non-existent user
    new_username = "some_user"

    with pytest.raises(UserNotFoundError):
        await crud_user.set_user_username(mock_conn, user_id, new_username)

    assert mock_conn.execute.await_count == 1 # execute was called
    assert mock_conn.fetchval.await_count == 1 # check_user_exists was called

# --- Test get_or_create_user_by_firebase ---

async def test_get_or_create_user_found_by_firebase_uid():
    """Test finding an existing user via Firebase UID."""
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_db_tx = MagicMock() # Mock transaction context manager if needed, though asyncpg handles it internally usually
    mock_conn.transaction.return_value = mock_db_tx # Setup mock transaction context if needed by your logic

    user_id_expected = 10
    # Simulate finding user by firebase_uid
    mock_conn.fetchrow.side_effect = [
        MagicMock(id=user_id_expected, email="found@test.com", username="existinguser"), # First fetchrow (by firebase_uid)
        MagicMock(id=user_id_expected, email="found@test.com", username="existinguser"), # Second fetchrow (get_user_by_id)
    ]

    token_data = FirebaseTokenData(uid="firebase_uid_1", email="found@test.com")
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)

    assert user_id == user_id_expected
    assert needs_username is False
    assert mock_conn.fetchrow.await_count == 2 # Called twice (by_uid, then get_by_id)

async def test_get_or_create_user_found_by_email_update_uid():
    """Test finding user by email and updating their Firebase UID."""
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_db_tx = MagicMock()
    mock_conn.transaction.return_value = mock_db_tx

    user_id_expected = 11
    old_fb_uid = "old_firebase_uid"
    new_fb_uid = "new_firebase_uid_2"

    # Simulate NOT finding by new firebase_uid, then finding by email, then fetching full record
    mock_conn.fetchrow.side_effect = [
        None, # First call (get_user_by_firebase_uid) returns None
        MagicMock(id=user_id_expected, email="found_email@test.com", username=None, firebase_uid=old_fb_uid), # Second call (get_user_by_email)
        MagicMock(id=user_id_expected, email="found_email@test.com", username=None), # Third call (get_user_by_id inside)
    ]
    mock_conn.execute.return_value = "UPDATE 1" # Simulate successful UID update

    token_data = FirebaseTokenData(uid=new_fb_uid, email="found_email@test.com")
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)

    assert user_id == user_id_expected
    assert needs_username is True # username is None in mock
    assert mock_conn.fetchrow.await_count == 3 # by_uid, by_email, get_by_id
    # Verify update_user_firebase_uid was called
    mock_conn.execute.assert_awaited_once()
    assert mock_conn.execute.await_args.args[0] == new_fb_uid # Correct new UID
    assert mock_conn.execute.await_args.args[1] == user_id_expected # Correct user ID

async def test_get_or_create_user_create_new():
    """Test creating a new user."""
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_db_tx = MagicMock()
    mock_conn.transaction.return_value = mock_db_tx

    new_user_id = 12
    new_fb_uid = "new_firebase_uid_3"
    new_email = "new@test.com"

    # Simulate NOT finding by firebase_uid or email, then successful INSERT
    mock_conn.fetchrow.side_effect = [None, None]
    mock_conn.fetchval.return_value = new_user_id # Simulate INSERT returning the new ID

    token_data = FirebaseTokenData(uid=new_fb_uid, email=new_email, name="New User", picture="http://pic.com")
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)

    assert user_id == new_user_id
    assert needs_username is True
    assert mock_conn.fetchrow.await_count == 2 # by_uid, by_email
    # Verify create_user was called (via fetchval)
    mock_conn.fetchval.assert_awaited_once()
    # Check args passed to create_user (fetchval)
    assert mock_conn.fetchval.await_args.args[1] == new_email
    assert mock_conn.fetchval.await_args.args[2] == new_fb_uid
    assert mock_conn.fetchval.await_args.args[3] == "New User"
    assert mock_conn.fetchval.await_args.args[4] == "http://pic.com"

# --- Add more tests ---
# Test get_following, get_followers, search_users (check query args, limit, offset, results)
# Test follow_user (success, already following, user not found)
# Test unfollow_user (success, not following, user not found)
# Test update_user_profile, get/update_privacy_settings, delete_user_account
# Test error conditions (e.g., mock execute/fetchrow to raise asyncpg.PostgresError)