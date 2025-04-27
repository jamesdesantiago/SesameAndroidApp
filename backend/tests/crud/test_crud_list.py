# backend/tests/crud/test_crud_list.py

import pytest
import asyncpg
from unittest.mock import AsyncMock, MagicMock, call
import unittest.mock # <--- IMPORT unittest.mock for ANY
from typing import Dict, Any, List, Optional, Tuple

# Import logging setup module early
import backend.app.core.logging

# Import module and functions/exceptions
from backend.app.crud import crud_list
from backend.app.crud.crud_list import (ListNotFoundError, ListAccessDeniedError,
                                CollaboratorNotFoundError, CollaboratorAlreadyExistsError,
                                DatabaseInteractionError)
from backend.app.schemas import list as list_schemas

# Import the helper from utils
from backend.tests.utils import create_mock_record # Assuming create_mock_record is still needed and in tests.utils

pytestmark = pytest.mark.asyncio

# Logger for this test file
logger = backend.app.core.logging.get_logger(__name__)

# Helper to create a mock asyncpg.exceptions.UniqueViolationError (from user crud tests)
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

# Helper to create a mock asyncpg.exceptions.CheckViolationError (from user crud tests)
def create_mock_check_violation_error(message, constraint_name=None):
    """Creates a mock CheckViolationError with a constraint_name attribute."""
    mock_exc = MagicMock(spec=asyncpg.exceptions.CheckViolationError)
    mock_exc.args = (message,)
    mock_exc.constraint_name = constraint_name
    mock_exc.sqlstate = '23514' # Common SQLSTATE for check_violation
    return mock_exc


# --- Tests for create_list ---
async def test_create_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1
    # Pass the Pydantic model instance
    list_in = list_schemas.ListCreate(name="My Awesome List", description="Cool stuff", isPrivate=False)
    expected_id = 101
    # Mock fetchrow to return the record as it would on successful INSERT ... RETURNING
    mock_conn.fetchrow.return_value = create_mock_record({
        "id": expected_id, "name": list_in.name, "description": list_in.description, "is_private": list_in.isPrivate
    })

    created_record = await crud_list.create_list(mock_conn, list_in, owner_id) # Pass Pydantic model

    assert created_record is not None
    assert created_record['id'] == expected_id
    assert created_record['name'] == list_in.name
    mock_conn.fetchrow.assert_awaited_once()
    call_args = mock_conn.fetchrow.await_args.args
    assert call_args[1] == list_in.name
    assert call_args[2] == list_in.description
    assert call_args[3] == owner_id
    assert call_args[4] == list_in.isPrivate

async def test_create_list_db_error():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1
    # Pass the Pydantic model instance
    list_in = list_schemas.ListCreate(name="Error List", isPrivate=True)
    # Simulate a DB error during the fetchrow (INSERT) call
    mock_conn.fetchrow.side_effect = asyncpg.PostgresError("DB connection failed")

    with pytest.raises(DatabaseInteractionError, match="Database error creating list."):
        await crud_list.create_list(mock_conn, list_in, owner_id) # Pass Pydantic model
    mock_conn.fetchrow.assert_awaited_once()


# --- Tests for get_user_lists_paginated ---
async def test_get_user_lists_paginated_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1; page = 1; page_size = 5; offset = (page - 1) * page_size
    total_expected = 2
    mock_conn.fetchval.return_value = total_expected
    mock_conn.fetch.return_value = [
        create_mock_record({"id": 1, "name": "L1", "description": "d1", "is_private": False, "place_count": 5}),
        create_mock_record({"id": 2, "name": "L2", "description": "d2", "is_private": True, "place_count": 0}),
    ]

    lists_with_counts, total = await crud_list.get_user_lists_paginated(mock_conn, owner_id, page, page_size)

    assert total == total_expected
    assert len(lists_with_counts) == 2
    assert lists_with_counts[0]['id'] == 1
    assert lists_with_counts[0]['place_count'] == 5 # Check added place_count
    assert lists_with_counts[1]['id'] == 2
    assert lists_with_counts[1]['place_count'] == 0
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM lists WHERE owner_id = $1", owner_id) # Check count query is still called
    mock_conn.fetch.assert_awaited_once() # Check fetch query
    # Check arguments for fetch query
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[1] == owner_id
    assert fetch_args[2] == page_size
    assert fetch_args[3] == offset

async def test_get_user_lists_paginated_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1; page = 1; page_size = 5;
    mock_conn.fetchval.return_value = 0 # Count is 0
    mock_conn.fetch.return_value = [] # Fetch returns empty

    lists, total = await crud_list.get_user_lists_paginated(mock_conn, owner_id, page, page_size)

    assert total == 0
    assert len(lists) == 0
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM lists WHERE owner_id = $1", owner_id) # Count query is called
    mock_conn.fetch.assert_not_awaited() # <--- CORRECTED: Fetch should *not* be called if count is 0


# --- Tests for get_list_details ---
async def test_get_list_details_found_no_collab():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    # Mock fetchrow for list details
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Details List", "description": "d", "is_private": False})
    # Mock fetch for collaborators (empty list)
    mock_conn.fetch.return_value = []

    details = await crud_list.get_list_details(mock_conn, list_id)

    assert details is not None
    assert details["id"] == list_id
    assert details["collaborators"] == []
    mock_conn.fetchrow.assert_awaited_once_with("SELECT l.id, l.name, l.description, l.is_private FROM lists l WHERE l.id = $1", list_id)
    mock_conn.fetch.assert_awaited_once_with(unittest.mock.ANY, list_id) # Check collaborator fetch args

async def test_get_list_details_found_with_collab():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 2
    # Mock fetchrow for list details
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Details List Collab", "description": "d", "is_private": True})
    # Mock fetch for collaborators (with results)
    mock_conn.fetch.return_value = [
        create_mock_record({"email": "collab1@test.com"}),
        create_mock_record({"email": "collab2@test.com"}),
    ]

    details = await crud_list.get_list_details(mock_conn, list_id)

    assert details is not None
    assert details["id"] == list_id
    assert sorted(details["collaborators"]) == sorted(["collab1@test.com", "collab2@test.com"])
    mock_conn.fetchrow.assert_awaited_once()
    mock_conn.fetch.assert_awaited_once_with(unittest.mock.ANY, list_id) # Check collaborator fetch args


async def test_get_list_details_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Simulate list not found
    mock_conn.fetchrow.return_value = None
    details = await crud_list.get_list_details(mock_conn, 999)
    assert details is None
    mock_conn.fetchrow.assert_awaited_once_with("SELECT l.id, l.name, l.description, l.is_private FROM lists l WHERE l.id = $1", 999)
    mock_conn.fetch.assert_not_awaited() # Collaborators not fetched if list not found

# --- Tests for update_list ---
async def test_update_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    # Pass the Pydantic model instance
    list_in = list_schemas.ListUpdate(name="Updated Name", isPrivate=True)
    # Mock fetchrow to return the updated record (RETURNING clause)
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Updated Name", "description": "d", "is_private": True})
    # Mock fetch for collaborators
    mock_conn.fetch.return_value = [] # _get_collaborator_emails

    updated_dict = await crud_list.update_list(mock_conn, list_id, list_in) # Pass Pydantic model

    assert updated_dict is not None
    assert updated_dict["name"] == "Updated Name"
    assert updated_dict["isPrivate"] is True
    assert "collaborators" in updated_dict

    mock_conn.fetchrow.assert_awaited_once() # UPDATE call
    mock_conn.fetch.assert_awaited_once() # Collaborator fetch call


async def test_update_list_no_fields():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    # Pass the Pydantic model instance
    list_in = list_schemas.ListUpdate()
    # Mock fetchrow for get_list_details (called when no fields provided)
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Current Name", "description": "d", "is_private": False})
    # Mock fetch for collaborators (for get_list_details)
    mock_conn.fetch.return_value = ["collab@test.com"]

    updated_dict = await crud_list.update_list(mock_conn, list_id, list_in) # Pass Pydantic model

    assert updated_dict is not None
    assert updated_dict["name"] == "Current Name"
    assert updated_dict["collaborators"] == ["collab@test.com"]
    # Check that get_list_details was called (fetchrow for list, fetch for collabs)
    assert mock_conn.fetchrow.await_count == 1
    assert mock_conn.fetch.await_count == 1
    mock_conn.execute.assert_not_awaited() # UPDATE should not be called


async def test_update_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 999
    # Pass the Pydantic model instance
    list_in = list_schemas.ListUpdate(name="New Name")
    # Mock fetchrow for the UPDATE RETURNING (returns None)
    mock_conn.fetchrow.return_value = None
    # Mock fetchval for the EXISTS check (returns False - list doesn't exist)
    mock_conn.fetchval.return_value = False

    updated_dict = await crud_list.update_list(mock_conn, list_id, list_in) # Pass Pydantic model
    assert updated_dict is None
    mock_conn.fetchrow.assert_awaited_once() # UPDATE call
    mock_conn.fetchval.assert_awaited_once_with("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id) # EXISTS call
    mock_conn.fetch.assert_not_awaited() # Collaborator fetch should not happen


async def test_update_list_unexpected_failure():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    # Pass the Pydantic model instance
    list_in = list_schemas.ListUpdate(name="New Name")
    # Mock fetchrow for the UPDATE RETURNING (returns None)
    mock_conn.fetchrow.return_value = None
    # Mock fetchval for the EXISTS check (returns True - list exists but update failed)
    mock_conn.fetchval.return_value = True

    with pytest.raises(DatabaseInteractionError, match="List update failed unexpectedly."):
        await crud_list.update_list(mock_conn, list_id, list_in) # Pass Pydantic model

    mock_conn.fetchrow.assert_awaited_once()
    mock_conn.fetchval.assert_awaited_once_with("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)


# --- Tests for delete_list ---
async def test_delete_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 1" # Simulate 1 row deleted
    deleted = await crud_list.delete_list(mock_conn, 1)
    assert deleted is True
    mock_conn.execute.assert_awaited_once_with("DELETE FROM lists WHERE id = $1", 1)

async def test_delete_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 0" # Simulate 0 rows deleted
    deleted = await crud_list.delete_list(mock_conn, 999)
    assert deleted is False
    mock_conn.execute.assert_awaited_once_with("DELETE FROM lists WHERE id = $1", 999)


# --- Tests for add_collaborator_to_list ---
# FIX: Corrected mock sequence and assertions
async def test_add_collaborator_user_created():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; email = "new@test.com"
    new_user_id = 10
    # Mock transaction methods
    mock_tx = AsyncMock()
    mock_conn.transaction.return_value = mock_tx
    # Mock fetchrow for user lookup by email (returns None - user doesn't exist)
    # Mock fetchval for INSERT user ON CONFLICT (returns new_user_id)
    # Mock fetchval for check is_collaborator (returns False)
    # Mock fetchval for owner_id check (returns 99 - not owner)
    # Mock execute for INSERT list_collaborators (returns "INSERT 0 1")
    mock_conn.fetchrow.return_value = None # Only one fetchrow call (user lookup)
    mock_conn.fetchval.side_effect = [new_user_id, False, 99] # Sequence: INSERT user RETURNING, is_collab check, owner_id check
    mock_conn.execute.return_value = "INSERT 0 1" # Simulate successful collab insert

    await crud_list.add_collaborator_to_list(mock_conn, list_id, email)

    # Assertions to check mocks were called in order with expected args
    mock_conn.fetchrow.assert_awaited_once_with("SELECT id, username FROM users WHERE email = $1", email) # User lookup
    mock_conn.fetchval.assert_any_await(
        """INSERT INTO users (email, created_at, updated_at) VALUES ($1, NOW(), NOW()) ON CONFLICT (email) DO UPDATE SET updated_at = NOW() RETURNING id""",
        email
    ) # Create user if not found
    mock_conn.fetchval.assert_any_await("SELECT EXISTS(SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2)", list_id, new_user_id) # Check if already collaborator
    mock_conn.fetchval.assert_any_await("SELECT owner_id FROM lists WHERE id = $1", list_id) # Check if owner
    mock_conn.execute.assert_awaited_once_with("INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2)", list_id, new_user_id) # Insert collaborator
    assert mock_conn.fetchrow.await_count == 1
    assert mock_conn.fetchval.await_count == 3


# FIX: Corrected mock sequence and assertions
async def test_add_collaborator_user_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; email = "exists@test.com"; user_id = 11
    # Mock transaction methods
    mock_tx = AsyncMock()
    mock_conn.transaction.return_value = mock_tx
    # Mock fetchrow for user lookup (returns user record)
    # Mock fetchval for check is_collaborator (returns False)
    # Mock fetchval for owner_id check (returns 99 - not owner)
    # Mock execute for INSERT list_collaborators (returns "INSERT 0 1")
    mock_conn.fetchrow.return_value = create_mock_record({"id": user_id, "username": "user11"}) # User exists
    mock_conn.fetchval.side_effect = [False, 99] # Sequence: is_collab check, owner_id check
    mock_conn.execute.return_value = "INSERT 0 1" # Simulate successful collab insert

    await crud_list.add_collaborator_to_list(mock_conn, list_id, email)

    mock_conn.fetchrow.assert_awaited_once_with("SELECT id, username FROM users WHERE email = $1", email)
    mock_conn.fetchval.assert_any_await("SELECT EXISTS(SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2)", list_id, user_id)
    mock_conn.fetchval.assert_any_await("SELECT owner_id FROM lists WHERE id = $1", list_id)
    mock_conn.execute.assert_awaited_once_with("INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2)", list_id, user_id)
    assert mock_conn.fetchrow.await_count == 1
    assert mock_conn.fetchval.await_count == 2


# FIX: Corrected mock sequence and assertion
async def test_add_collaborator_already_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; email = "exists@test.com"; user_id = 11
    # Mock transaction methods
    mock_tx = AsyncMock()
    mock_conn.transaction.return_value = mock_tx
    # Mock fetchrow for user lookup (returns user record)
    # Mock fetchval for check is_collaborator (returns True - already collaborator)
    mock_conn.fetchrow.return_value = create_mock_record({"id": user_id, "username": "user11"})
    mock_conn.fetchval.return_value = True # is_collab=True
    # Mock execute and other fetchval calls should *not* happen in this path

    with pytest.raises(CollaboratorAlreadyExistsError, match=f"User {email} is already a collaborator."):
        await crud_list.add_collaborator_to_list(mock_conn, list_id, email)

    mock_conn.fetchrow.assert_awaited_once_with("SELECT id, username FROM users WHERE email = $1", email)
    mock_conn.fetchval.assert_awaited_once_with("SELECT EXISTS(SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2)", list_id, user_id) # Only this fetchval called
    mock_conn.execute.assert_not_awaited() # Insert should not happen


# FIX: Corrected mock sequence and assertion
async def test_add_collaborator_owner_is_collaborator():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; owner_email = "owner@test.com"; owner_id = 11
    # Mock transaction methods
    mock_tx = AsyncMock()
    mock_conn.transaction.return_value = mock_tx
    # Mock fetchrow for user lookup (returns owner record)
    # Mock fetchval for check is_collaborator (returns False)
    # Mock fetchval for owner_id check (returns owner_id)
    mock_conn.fetchrow.return_value = create_mock_record({"id": owner_id, "username": "listowner"}) # User exists
    mock_conn.fetchval.side_effect = [False, owner_id] # Sequence: is_collab check, owner_id check
    # Mock execute and other calls should *not* happen

    with pytest.raises(CollaboratorAlreadyExistsError, match="is the list owner and does not need to be added as a collaborator."): # Check exception message
         await crud_list.add_collaborator_to_list(mock_conn, list_id, owner_email)

    mock_conn.fetchrow.assert_awaited_once_with("SELECT id, username FROM users WHERE email = $1", owner_email)
    mock_conn.fetchval.assert_any_await("SELECT EXISTS(SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2)", list_id, owner_id)
    mock_conn.fetchval.assert_any_await("SELECT owner_id FROM lists WHERE id = $1", list_id)
    assert mock_conn.fetchval.await_count == 2
    mock_conn.execute.assert_not_awaited()


# --- Tests for delete_collaborator_from_list ---
# (These tests look correct)
async def test_delete_collaborator_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; user_id = 5; owner_id = 99
    # Mock fetchval for owner_id check (returns different ID)
    # Needs to mock get_list_by_id which is called internally
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "owner_id": owner_id})
    # Mock execute for DELETE (returns "DELETE 1")
    mock_conn.execute.return_value = "DELETE 1"

    deleted = await crud_list.delete_collaborator_from_list(mock_conn, list_id, user_id)

    assert deleted is True
    mock_conn.fetchrow.assert_awaited_once_with(unittest.mock.ANY, list_id) # Check get_list_by_id call
    mock_conn.execute.assert_awaited_once_with("DELETE FROM list_collaborators WHERE list_id = $1 AND user_id = $2", list_id, user_id)

async def test_delete_collaborator_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; user_id = 99; owner_id = 111
    # Mock fetchval for owner_id check (returns different ID)
    # Needs to mock get_list_by_id which is called internally
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "owner_id": owner_id})
    # Mock execute for DELETE (returns "DELETE 0")
    mock_conn.execute.return_value = "DELETE 0"

    deleted = await crud_list.delete_collaborator_from_list(mock_conn, list_id, user_id)

    assert deleted is False
    mock_conn.fetchrow.assert_awaited_once_with(unittest.mock.ANY, list_id) # Check get_list_by_id call
    mock_conn.execute.assert_awaited_once_with("DELETE FROM list_collaborators WHERE list_id = $1 AND user_id = $2", list_id, user_id)

async def test_delete_collaborator_is_owner():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; owner_id = 5
    # Mock fetchval for owner_id check (returns the same ID)
    # Needs to mock get_list_by_id which is called internally
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "owner_id": owner_id})

    deleted = await crud_list.delete_collaborator_from_list(mock_conn, list_id, owner_id)

    assert deleted is False # Should return False as owner cannot be removed via this function
    mock_conn.fetchrow.assert_awaited_once_with(unittest.mock.ANY, list_id) # Check get_list_by_id call
    mock_conn.execute.assert_not_awaited() # Delete query should not run


# --- Tests for check_list_ownership ---
# (These tests look mostly correct, minor assertion refinement)
async def test_check_list_ownership_is_owner():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Mock fetchval for EXISTS check (returns True)
    mock_conn.fetchval.return_value = True
    await crud_list.check_list_ownership(mock_conn, 1, 10) # Should not raise
    mock_conn.fetchval.assert_awaited_once_with("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2)", 1, 10) # Only one fetchval

async def test_check_list_ownership_not_owner():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Mock fetchval for EXISTS check (returns False for ownership)
    # Mock fetchval for list exists check (returns True for existence)
    mock_conn.fetchval.side_effect = [False, True]
    with pytest.raises(ListAccessDeniedError):
        await crud_list.check_list_ownership(mock_conn, 1, 20)
    mock_conn.fetchval.assert_has_calls([
        call("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2)", 1, 20),
        call("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", 1)
    ])
    assert mock_conn.fetchval.await_count == 2

async def test_check_list_ownership_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Mock fetchval for EXISTS check (returns False for ownership)
    # Mock fetchval for list exists check (returns False for existence)
    mock_conn.fetchval.side_effect = [False, False]
    with pytest.raises(ListNotFoundError):
        await crud_list.check_list_ownership(mock_conn, 999, 10)
    mock_conn.fetchval.assert_has_calls([
        call("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2)", 999, 10),
        call("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", 999)
    ])
    assert mock_conn.fetchval.await_count == 2


# --- Tests for check_list_access ---
# (These tests look mostly correct, minor assertion refinement)
async def test_check_list_access_is_owner():
     mock_conn = AsyncMock(spec=asyncpg.Connection)
     # Mock fetchval for the combined access query (returns True)
     mock_conn.fetchval.return_value = True
     await crud_list.check_list_access(mock_conn, 1, 10) # Should not raise
     # Check the arguments for the combined query
     mock_conn.fetchval.assert_awaited_once_with(unittest.mock.ANY, 1, 10) # Only one fetchval, check args

async def test_check_list_access_is_collaborator():
     mock_conn = AsyncMock(spec=asyncpg.Connection)
     # Mock fetchval for the combined access query (returns True)
     mock_conn.fetchval.return_value = True
     await crud_list.check_list_access(mock_conn, 1, 15) # Should not raise
     # Check the arguments for the combined query
     mock_conn.fetchval.assert_awaited_once_with(unittest.mock.ANY, 1, 15) # Only one fetchval, check args

async def test_check_list_access_no_access():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Mock fetchval for the combined access query (returns False)
    # Mock fetchval for list exists check (returns True)
    mock_conn.fetchval.side_effect = [False, True]
    with pytest.raises(ListAccessDeniedError):
        await crud_list.check_list_access(mock_conn, 1, 30)
    mock_conn.fetchval.assert_any_await(unittest.mock.ANY, 1, 30) # Check args for combined query
    mock_conn.fetchval.assert_any_await("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", 1) # Check args for exists check
    assert mock_conn.fetchval.await_count == 2


async def test_check_list_access_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    # Mock fetchval for the combined access query (returns False)
    # Mock fetchval for list exists check (returns False)
    mock_conn.fetchval.side_effect = [False, False]
    with pytest.raises(ListNotFoundError):
        await crud_list.check_list_access(mock_conn, 999, 10)
    mock_conn.fetchval.assert_any_await(unittest.mock.ANY, 999, 10) # Check args
    mock_conn.fetchval.assert_any_await("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", 999) # Check args
    assert mock_conn.fetchval.await_count == 2


# --- Tests for Discovery Functions ---
# (These tests look mostly correct, minor assertion refinement)
async def test_get_public_lists_paginated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    page = 1; page_size = 10; offset = (page - 1) * page_size
    total_expected = 1
    # Mock fetchval for count
    mock_conn.fetchval.return_value = total_expected
    # Mock fetch (with place_count)
    mock_conn.fetch.return_value = [create_mock_record({"id": 1, "name": "L1", "description": "d", "is_private": False, "place_count": 5})]

    lists, total = await crud_list.get_public_lists_paginated(mock_conn, page, page_size)

    assert total == total_expected
    assert len(lists) == 1
    assert lists[0]['id'] == 1
    assert lists[0]['place_count'] == 5
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM lists WHERE is_private = FALSE") # Check count query
    mock_conn.fetch.assert_awaited_once() # Check fetch query
    # Check arguments for fetch query
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[0] == unittest.mock.ANY # SQL string
    assert fetch_args[1] == page_size
    assert fetch_args[2] == offset


async def test_get_public_lists_paginated_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    page = 1; page_size = 10;
    mock_conn.fetchval.return_value = 0 # Count is 0
    mock_conn.fetch.return_value = [] # Fetch returns empty

    lists, total = await crud_list.get_public_lists_paginated(mock_conn, page, page_size)

    assert total == 0
    assert len(lists) == 0
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM lists WHERE is_private = FALSE") # Count query is called
    mock_conn.fetch.assert_not_awaited() # <--- CORRECTED: Fetch should *not* be called if count is 0


async def test_search_lists_paginated_authenticated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    query = "q"; user_id = 5; page = 1; page_size = 10; offset = (page - 1) * page_size
    total_expected = 1
    search_term_lower = f"%{query.lower()}%"
    # Mock fetchval for count (params: $1=search_term_lower, $2=user_id)
    mock_conn.fetchval.return_value = total_expected
    # Mock fetch (params: $1=search_term_lower, $2=user_id, $3=page_size, $4=offset)
    mock_conn.fetch.return_value = [create_mock_record({"id": 1, "name": "L1", "description": "d", "is_private": False, "place_count": 5})]

    lists, total = await crud_list.search_lists_paginated(mock_conn, query, user_id, page, page_size)

    assert total == total_expected
    assert len(lists) == 1
    assert lists[0]['id'] == 1
    assert lists[0]['place_count'] == 5

    mock_conn.fetchval.assert_awaited_once() # Count query
    # Check arguments for count query
    count_args = mock_conn.fetchval.await_args.args
    assert count_args[1] == search_term_lower
    assert count_args[2] == user_id # User ID should be the second param for auth'd search

    mock_conn.fetch.assert_awaited_once() # Fetch query
    # Check arguments for fetch query
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[1] == search_term_lower
    assert fetch_args[2] == user_id # User ID should be the second param
    assert fetch_args[3] == page_size
    assert fetch_args[4] == offset


async def test_search_lists_paginated_unauthenticated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    query = "q"; user_id = None; page = 1; page_size = 10; offset = (page - 1) * page_size
    total_expected = 1
    search_term_lower = f"%{query.lower()}%"
    # Mock count (params: $1=search_term_lower)
    mock_conn.fetchval.return_value = total_expected
    # Mock fetch (params: $1=search_term_lower, $2=page_size, $3=offset)
    mock_conn.fetch.return_value = [create_mock_record({"id": 1, "name": "L1", "description": "d", "is_private": False, "place_count": 5})]

    lists, total = await crud_list.search_lists_paginated(mock_conn, query, user_id, page, page_size)

    assert total == total_expected
    assert len(lists) == 1
    assert lists[0]['id'] == 1
    assert lists[0]['place_count'] == 5
    search_term_lower = f"%{query.lower()}%"
    mock_conn.fetchval.assert_awaited_once() # Count query
    # Check arguments for count query
    count_args = mock_conn.fetchval.await_args.args
    assert count_args[1] == search_term_lower
    # User ID should NOT be in params for unauthenticated search count query (only 1 param + conn)
    assert len(count_args) == 2

    mock_conn.fetch.assert_awaited_once() # Fetch query
    # Check arguments for fetch query
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[1] == search_term_lower
    assert fetch_args[2] == page_size # page_size is $2 for unauthenticated
    assert fetch_args[3] == offset # offset is $3 for unauthenticated


async def test_search_lists_paginated_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = 0 # Count is 0
    mock_conn.fetch.return_value = [] # Fetch returns empty

    # Test both authenticated and unauthenticated paths
    lists, total = await crud_list.search_lists_paginated(mock_conn, "query", 1, 1, 10) # Authenticated
    assert total == 0
    assert len(lists) == 0
    mock_conn.fetchval.assert_awaited_once() # Count query is called (for the auth'd call)
    mock_conn.fetch.assert_not_awaited() # Fetch should *not* be called if count is 0

    # Simulate a second call for unauthenticated search
    mock_conn.fetchval.reset_mock() # Reset mock state before second call
    mock_conn.fetch.reset_mock()
    mock_conn.fetchval.return_value = 0 # Set return value again
    lists, total = await crud_list.search_lists_paginated(mock_conn, "query", None, 1, 10) # Unauthenticated
    assert total == 0
    assert len(lists) == 0
    # fetchval called once more, fetch not called at all
    assert mock_conn.fetchval.await_count == 1
    mock_conn.fetch.assert_not_awaited()


# --- Tests for get_list_by_id ---
# (These tests look mostly correct, added unittest.mock.ANY)
async def test_get_list_by_id_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_record_data = {"id": 1, "owner_id": 5, "name": "Test List", "description": None, "is_private": False}
    mock_conn.fetchrow.return_value = create_mock_record(mock_record_data)
    record = await crud_list.get_list_by_id(mock_conn, 1)
    assert record is not None
    assert dict(record) == mock_record_data
    mock_conn.fetchrow.assert_awaited_once_with(unittest.mock.ANY, 1) # Check args

async def test_get_list_by_id_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None
    record = await crud_list.get_list_by_id(mock_conn, 999)
    assert record is None
    mock_conn.fetchrow.assert_awaited_once_with(unittest.mock.ANY, 999) # Check args