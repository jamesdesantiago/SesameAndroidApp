# backend/tests/crud/test_crud_user.py

import pytest
import asyncpg
from unittest.mock import AsyncMock, MagicMock, patch
from typing import Dict, Any, List, Optional, Tuple # Added Tuple
import datetime

# Import module and functions/exceptions
from app.crud import crud_user
from app.crud.crud_user import UserNotFoundError, UsernameAlreadyExistsError, DatabaseInteractionError
from app.schemas.token import FirebaseTokenData
from app.schemas import user as user_schemas

# Import the helper from utils
from tests.utils import create_mock_record # <<< IMPORT HELPER

pytestmark = pytest.mark.asyncio

# Helper to create mock records easily
def create_mock_record(data: Dict[str, Any]) -> MagicMock:
    mock = MagicMock(spec=asyncpg.Record)
    # Configure __getitem__ to return values from the dictionary
    mock.__getitem__.side_effect = lambda key: data.get(key)
    # Allow direct attribute access if needed (less common for Record)
    for key, value in data.items():
        setattr(mock, key, value)
    # Make it behave like a dictionary for ** expansion if needed
    mock.items.return_value = data.items()
    mock.keys.return_value = data.keys()
    # Add _asdict if your endpoint mapping relies on it
    mock._asdict = lambda: data
    return mock

# --- Test get_user_by_id ---
# (Keep existing tests for get_user_by_id)
async def test_get_user_by_id_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_record = create_mock_record({"id": 1, "email": "test@test.com", "username": "tester"})
    mock_conn.fetchrow.return_value = mock_record
    user = await crud_user.get_user_by_id(mock_conn, 1)
    assert user is not None and user['id'] == 1
    mock_conn.fetchrow.assert_awaited_once()

async def test_get_user_by_id_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None
    user = await crud_user.get_user_by_id(mock_conn, 999)
    assert user is None
    mock_conn.fetchrow.assert_awaited_once()

# --- Test set_user_username ---
# (Keep existing tests for set_user_username)
async def test_set_username_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None # Username available
    mock_conn.execute.return_value = "UPDATE 1"
    mock_conn.fetchval.return_value = True # User exists check passes if needed
    await crud_user.set_user_username(mock_conn, 1, "new_user")
    mock_conn.fetchrow.assert_awaited_once() # Check uniqueness
    mock_conn.execute.assert_awaited_once() # Check update call

async def test_set_username_already_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = create_mock_record({"id": 2}) # Username taken
    with pytest.raises(UsernameAlreadyExistsError):
        await crud_user.set_user_username(mock_conn, 1, "taken_user")
    mock_conn.execute.assert_not_awaited()

async def test_set_username_update_fails_user_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None # Username available
    mock_conn.execute.return_value = "UPDATE 0" # Update failed
    mock_conn.fetchval.return_value = False # User check fails
    with pytest.raises(UserNotFoundError):
        await crud_user.set_user_username(mock_conn, 999, "some_user")
    assert mock_conn.fetchval.await_count == 1 # Check user exists called

# --- Test get_or_create_user_by_firebase ---
# (Keep existing tests for get_or_create_user_by_firebase)
async def test_get_or_create_user_found_by_firebase_uid():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.transaction.return_value = AsyncMock() # Mock transaction context manager entry/exit
    user_id_expected = 10
    mock_conn.fetchrow.side_effect = [
        create_mock_record({"id": user_id_expected, "email": "f@t.com", "username": "fbuser"}), # Found by UID
        create_mock_record({"id": user_id_expected, "username": "fbuser"}) # get_user_by_id result
    ]
    token_data = FirebaseTokenData(uid="firebase_uid_1", email="f@t.com")
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)
    assert user_id == user_id_expected and needs_username is False
    assert mock_conn.fetchrow.await_count == 2

async def test_get_or_create_user_found_by_email_update_uid():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.transaction.return_value = AsyncMock()
    user_id_expected = 11; old_fb_uid = "old"; new_fb_uid = "new"
    mock_conn.fetchrow.side_effect = [
        None, # Not found by new UID
        create_mock_record({"id": user_id_expected, "email": "e@t.com", "username": None, "firebase_uid": old_fb_uid}), # Found by Email
        create_mock_record({"id": user_id_expected, "username": None}) # get_user_by_id result
    ]
    mock_conn.execute.return_value = "UPDATE 1"
    token_data = FirebaseTokenData(uid=new_fb_uid, email="e@t.com")
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)
    assert user_id == user_id_expected and needs_username is True
    mock_conn.execute.assert_awaited_once() # Check UID update occurred

async def test_get_or_create_user_create_new():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.transaction.return_value = AsyncMock()
    new_user_id = 12; new_fb_uid = "new_uid"; new_email="new@t.com"
    mock_conn.fetchrow.side_effect = [None, None] # Not found by UID or Email
    mock_conn.fetchval.return_value = new_user_id # Simulate INSERT returning ID
    token_data = FirebaseTokenData(uid=new_fb_uid, email=new_email)
    user_id, needs_username = await crud_user.get_or_create_user_by_firebase(mock_conn, token_data)
    assert user_id == new_user_id and needs_username is True
    mock_conn.fetchval.assert_awaited_once() # Check INSERT call

# --- Test get_following ---
async def test_get_following_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    page = 1
    page_size = 10
    offset = 0
    total_items_expected = 2
    mock_conn.fetchval.return_value = total_items_expected # Mock count
    mock_conn.fetch.return_value = [ # Mock followed users
        create_mock_record({"id": 2, "username": "user2"}),
        create_mock_record({"id": 3, "username": "user3"}),
    ]
    results, total = await crud_user.get_following(mock_conn, user_id, page, page_size)
    assert total == total_items_expected
    assert len(results) == 2
    assert results[0]['id'] == 2
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM user_follows WHERE follower_id = $1", user_id)
    mock_conn.fetch.assert_awaited_once()
    assert mock_conn.fetch.await_args.args[-2] == page_size # Check limit
    assert mock_conn.fetch.await_args.args[-1] == offset # Check offset

async def test_get_following_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = 0 # Mock count is 0
    mock_conn.fetch.return_value = [] # Mock fetch returns empty
    results, total = await crud_user.get_following(mock_conn, 1, 1, 10)
    assert total == 0
    assert len(results) == 0
    mock_conn.fetchval.assert_awaited_once()
    mock_conn.fetch.assert_awaited_once() # Fetch is still called even if count is 0

# --- Test get_followers ---
async def test_get_followers_success_and_flag():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1 # User whose followers we are getting
    page = 1; page_size = 5; offset = 0
    total_items_expected = 2
    mock_conn.fetchval.return_value = total_items_expected # Mock count
    mock_conn.fetch.return_value = [ # Mock followers with is_following flag
        create_mock_record({"id": 2, "username": "follower2", "is_following": True}), # User 1 follows this one back
        create_mock_record({"id": 3, "username": "follower3", "is_following": False}), # User 1 does not follow this one
    ]
    results, total = await crud_user.get_followers(mock_conn, user_id, page, page_size)
    assert total == total_items_expected
    assert len(results) == 2
    assert results[0]['is_following'] is True
    assert results[1]['is_following'] is False
    mock_conn.fetchval.assert_awaited_once()
    mock_conn.fetch.assert_awaited_once()
    assert mock_conn.fetch.await_args.args[-2] == page_size
    assert mock_conn.fetch.await_args.args[-1] == offset

# --- Test search_users ---
async def test_search_users_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    current_user_id = 1
    query = "search"
    page = 1; page_size = 5; offset = 0
    total_items_expected = 1
    mock_conn.fetchval.return_value = total_items_expected
    mock_conn.fetch.return_value = [
        create_mock_record({"id": 5, "username": "searchresult", "is_following": False})
    ]
    results, total = await crud_user.search_users(mock_conn, current_user_id, query, page, page_size)
    assert total == total_items_expected
    assert len(results) == 1
    assert results[0]['id'] == 5
    mock_conn.fetchval.assert_awaited_once()
    mock_conn.fetch.assert_awaited_once()
    assert f"%{query}%" in mock_conn.fetchval.await_args.args # Check search term in count
    assert current_user_id in mock_conn.fetchval.await_args.args # Check self exclusion in count
    assert current_user_id in mock_conn.fetch.await_args.args # Check self exclusion and follow check in fetch

# --- Test follow_user ---
async def test_follow_user_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = True # Simulate target user exists
    mock_conn.execute.return_value = "INSERT 0 1" # Simulate successful insert
    result = await crud_user.follow_user(mock_conn, 1, 2)
    assert result is False # Returns False for new follow
    mock_conn.fetchval.assert_awaited_once() # Check target exists
    mock_conn.execute.assert_awaited_once() # Check insert

async def test_follow_user_already_following():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.side_effect = [True, True] # Target exists, already following exists
    mock_conn.execute.return_value = "INSERT 0 0" # Simulate ON CONFLICT trigger
    result = await crud_user.follow_user(mock_conn, 1, 2)
    assert result is True # Returns True if already following
    assert mock_conn.fetchval.await_count == 2 # check target, check if already following

async def test_follow_user_target_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = False # Simulate target user DOES NOT exist
    with pytest.raises(UserNotFoundError):
        await crud_user.follow_user(mock_conn, 1, 999)
    mock_conn.execute.assert_not_awaited()

# --- Test unfollow_user ---
async def test_unfollow_user_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 1" # Simulate successful delete
    result = await crud_user.unfollow_user(mock_conn, 1, 2)
    assert result is True
    mock_conn.execute.assert_awaited_once()

async def test_unfollow_user_not_following():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 0" # Simulate no row deleted
    result = await crud_user.unfollow_user(mock_conn, 1, 2)
    assert result is False
    mock_conn.execute.assert_awaited_once()

# --- Test get_user_notifications ---
async def test_get_user_notifications_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1; page = 1; page_size = 10; offset = 0; total = 1
    mock_conn.fetchval.return_value = total
    mock_conn.fetch.return_value = [create_mock_record({"id": 101, "title": "N1"})]
    results, total_items = await crud_user.get_user_notifications(mock_conn, user_id, page, page_size)
    assert total_items == total
    assert len(results) == 1
    assert results[0]['id'] == 101
    mock_conn.fetchval.assert_awaited_once()
    mock_conn.fetch.assert_awaited_once()

# --- Test get_current_user_profile ---
async def test_get_current_user_profile_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_record = create_mock_record({"id": 1, "email": "me@t.com"})
    mock_conn.fetchrow.return_value = mock_record
    profile = await crud_user.get_current_user_profile(mock_conn, 1)
    assert profile is not None and profile['id'] == 1

async def test_get_current_user_profile_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None
    with pytest.raises(UserNotFoundError):
        await crud_user.get_current_user_profile(mock_conn, 999)

# --- Test update_user_profile ---
async def test_update_user_profile_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    new_display_name = "Updated Name"
    profile_in = user_schemas.UserProfileUpdate(display_name=new_display_name) # Pydantic V2 uses model_validate
    # profile_in = user_schemas.UserProfileUpdate.model_validate({"displayName": new_display_name}) # Pydantic V2

    mock_return_record = create_mock_record({"id": user_id, "display_name": new_display_name})
    mock_conn.fetchrow.return_value = mock_return_record # Simulate RETURNING clause

    result_record = await crud_user.update_user_profile(mock_conn, user_id, profile_in)

    assert result_record is not None
    assert result_record['display_name'] == new_display_name
    mock_conn.fetchrow.assert_awaited_once() # The update call with RETURNING
    assert mock_conn.fetchrow.await_args.args[1] == new_display_name # Check param binding

async def test_update_user_profile_no_changes():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    profile_in = user_schemas.UserProfileUpdate() # Empty update
    mock_current_profile = create_mock_record({"id": user_id, "display_name": "Current Name"})
    mock_conn.fetchrow.return_value = mock_current_profile # Mock the get_current_user_profile call

    result_record = await crud_user.update_user_profile(mock_conn, user_id, profile_in)

    assert result_record is not None
    assert result_record['id'] == user_id
    assert result_record['display_name'] == "Current Name"
    mock_conn.fetchrow.assert_awaited_once() # Only the get profile call should happen

# --- Test get_privacy_settings ---
async def test_get_privacy_settings_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    settings_data = {"profile_is_public": True, "lists_are_public": False, "allow_analytics": True}
    mock_conn.fetchrow.return_value = create_mock_record(settings_data)
    settings = await crud_user.get_privacy_settings(mock_conn, 1)
    assert settings is not None
    assert settings['lists_are_public'] is False

async def test_get_privacy_settings_user_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None
    with pytest.raises(UserNotFoundError):
        await crud_user.get_privacy_settings(mock_conn, 999)

# --- Test update_privacy_settings ---
async def test_update_privacy_settings_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 1
    settings_in = user_schemas.PrivacySettingsUpdate(allow_analytics=False, profile_is_public=False)
    # Simulate RETURNING
    mock_conn.fetchrow.return_value = create_mock_record({
        "profile_is_public": False, "lists_are_public": True, "allow_analytics": False # lists_are_public wasn't updated
    })

    result = await crud_user.update_privacy_settings(mock_conn, user_id, settings_in)

    assert result is not None
    assert result['allow_analytics'] is False
    assert result['profile_is_public'] is False
    mock_conn.fetchrow.assert_awaited_once()
    assert mock_conn.fetchrow.await_args.args[1] is False # Check allow_analytics param
    assert mock_conn.fetchrow.await_args.args[2] is False # Check profile_is_public param

# --- Test delete_user_account ---
async def test_delete_user_account_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 1"
    result = await crud_user.delete_user_account(mock_conn, 1)
    assert result is True
    mock_conn.execute.assert_awaited_once()

async def test_delete_user_account_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 0"
    result = await crud_user.delete_user_account(mock_conn, 999)
    assert result is False
    mock_conn.execute.assert_awaited_once()

async def test_get_user_by_id_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_record = create_mock_record({"id": 1, "email": "test@test.com", "username": "tester"}) # Use imported helper
    mock_conn.fetchrow.return_value = mock_record
    user = await crud_user.get_user_by_id(mock_conn, 1)
    assert user is not None and user['id'] == 1
    mock_conn.fetchrow.assert_awaited_once()