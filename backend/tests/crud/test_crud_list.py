# backend/tests/crud/test_crud_list.py

import pytest
import asyncpg
from unittest.mock import AsyncMock, MagicMock, patch
from typing import Dict, Any, List, Optional, Tuple

# Import module and functions/exceptions
from app.crud import crud_list
from app.crud.crud_list import (ListNotFoundError, ListAccessDeniedError,
                                CollaboratorNotFoundError, CollaboratorAlreadyExistsError,
                                DatabaseInteractionError) # Import specific errors
from app.schemas import list as list_schemas

# Import the helper from utils
from tests.utils import create_mock_record # <<< IMPORT HELPER

pytestmark = pytest.mark.asyncio

# Helper to create mock records easily
def create_mock_record(data: Dict[str, Any]) -> MagicMock:
    mock = MagicMock(spec=asyncpg.Record)
    mock.__getitem__.side_effect = lambda key: data.get(key)
    mock.get.side_effect = lambda key, default=None: data.get(key, default)
    for key, value in data.items(): setattr(mock, key, value)
    mock.items.return_value = data.items()
    mock.keys.return_value = data.keys()
    mock._asdict = lambda: data
    return mock

# --- Tests for create_list ---
async def test_create_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1
    list_in = list_schemas.ListCreate(name="Test List", description="Desc", isPrivate=False)
    expected_id = 101
    mock_conn.fetchrow.return_value = create_mock_record({
        "id": expected_id, "name": list_in.name, "description": list_in.description, "is_private": list_in.isPrivate
    })

    created_record = await crud_list.create_list(mock_conn, list_in, owner_id)

    assert created_record is not None
    assert created_record['id'] == expected_id
    assert created_record['name'] == list_in.name
    mock_conn.fetchrow.assert_awaited_once()
    # Check args passed to insert (adjust indices based on actual query)
    assert mock_conn.fetchrow.await_args.args[1] == list_in.name
    assert mock_conn.fetchrow.await_args.args[3] == owner_id
    assert mock_conn.fetchrow.await_args.args[4] == list_in.isPrivate

async def test_create_list_db_error():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1
    list_in = list_schemas.ListCreate(name="Error List", isPrivate=True)
    mock_conn.fetchrow.side_effect = asyncpg.PostgresError("DB connection failed") # Simulate DB error

    with pytest.raises(RuntimeError): # Assuming create_list wraps it in RuntimeError
        await crud_list.create_list(mock_conn, list_in, owner_id)

# --- Tests for get_user_lists_paginated ---
async def test_get_user_lists_paginated_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1; page = 1; page_size = 5; offset = 0
    total_expected = 2
    mock_conn.fetchval.return_value = total_expected
    mock_conn.fetch.return_value = [
        create_mock_record({"id": 1, "name": "L1", "place_count": 3}),
        create_mock_record({"id": 2, "name": "L2", "place_count": 0}),
    ]

    lists, total = await crud_list.get_user_lists_paginated(mock_conn, owner_id, page, page_size)

    assert total == total_expected
    assert len(lists) == 2
    assert lists[0]['id'] == 1
    assert lists[1]['place_count'] == 0
    mock_conn.fetchval.assert_awaited_once() # Count query
    mock_conn.fetch.assert_awaited_once() # Fetch query
    assert mock_conn.fetch.await_args.args[-2] == page_size # Limit
    assert mock_conn.fetch.await_args.args[-1] == offset # Offset

async def test_get_user_lists_paginated_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1; page = 1; page_size = 5;
    mock_conn.fetchval.return_value = 0
    mock_conn.fetch.return_value = []

    lists, total = await crud_list.get_user_lists_paginated(mock_conn, owner_id, page, page_size)

    assert total == 0
    assert len(lists) == 0
    mock_conn.fetchval.assert_awaited_once()
    mock_conn.fetch.assert_awaited_once()

# --- Tests for get_list_details ---
async def test_get_list_details_found_no_collab():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Details List"}) # Mock list fetch
    mock_conn.fetch.return_value = [] # Mock collaborator fetch (empty)

    details = await crud_list.get_list_details(mock_conn, list_id)

    assert details is not None
    assert details["id"] == list_id
    assert details["name"] == "Details List"
    assert details["collaborators"] == []
    assert mock_conn.fetchrow.await_count == 1
    assert mock_conn.fetch.await_count == 1

async def test_get_list_details_found_with_collab():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 2
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Details List Collab"})
    mock_conn.fetch.return_value = [ # Mock collaborator fetch (with data)
        create_mock_record({"email": "collab1@test.com"}),
        create_mock_record({"email": "collab2@test.com"}),
    ]

    details = await crud_list.get_list_details(mock_conn, list_id)

    assert details is not None
    assert details["id"] == list_id
    assert details["collaborators"] == ["collab1@test.com", "collab2@test.com"]

async def test_get_list_details_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None # Mock list fetch returns None
    details = await crud_list.get_list_details(mock_conn, 999)
    assert details is None
    mock_conn.fetchrow.assert_awaited_once()
    mock_conn.fetch.assert_not_awaited() # Collaborators shouldn't be fetched

# --- Tests for update_list ---
async def test_update_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    update_data = list_schemas.ListUpdate(name="Updated Name", isPrivate=True)
    # Mock RETURNING clause
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Updated Name", "is_private": True})
    mock_conn.fetch.return_value = [] # Mock collaborator fetch returns empty

    updated_dict = await crud_list.update_list(mock_conn, list_id, update_data)

    assert updated_dict is not None
    assert updated_dict["name"] == "Updated Name"
    assert updated_dict["isPrivate"] is True
    assert updated_dict["collaborators"] == []
    mock_conn.fetchrow.assert_awaited_once() # Update call
    mock_conn.fetch.assert_awaited_once() # Collaborator fetch call

async def test_update_list_partial():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    update_data = list_schemas.ListUpdate(isPrivate=False) # Only update privacy
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Original Name", "is_private": False})
    mock_conn.fetch.return_value = []

    updated_dict = await crud_list.update_list(mock_conn, list_id, update_data)

    assert updated_dict is not None
    assert updated_dict["isPrivate"] is False
    # Check that name wasn't part of the SET clause implicitly
    update_sql = mock_conn.fetchrow.await_args.args[0]
    assert "name =" not in update_sql.lower()
    assert "is_private =" in update_sql.lower()

async def test_update_list_no_data():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    update_data = list_schemas.ListUpdate() # No fields set
    # Mock the get_list_details call that happens when no fields are updated
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Current"})
    mock_conn.fetch.return_value = []

    result = await crud_list.update_list(mock_conn, list_id, update_data)

    assert result is not None
    assert result["name"] == "Current"
    mock_conn.fetchrow.assert_awaited_once() # get_list_details uses fetchrow
    mock_conn.fetch.assert_awaited_once() # get_list_details uses fetch

async def test_update_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 999
    update_data = list_schemas.ListUpdate(name="Update fail")
    mock_conn.fetchrow.return_value = None # Simulate update returning nothing
    mock_conn.fetchval.return_value = False # Simulate existence check returning false

    result = await crud_list.update_list(mock_conn, list_id, update_data)
    assert result is None # Should return None if list not found initially

# --- Tests for delete_list ---
async def test_delete_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 1"
    deleted = await crud_list.delete_list(mock_conn, 1)
    assert deleted is True
    mock_conn.execute.assert_awaited_once()

async def test_delete_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 0"
    deleted = await crud_list.delete_list(mock_conn, 999)
    assert deleted is False
    mock_conn.execute.assert_awaited_once()

# --- Tests for add_collaborator_to_list ---
async def test_add_collaborator_success_user_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; collaborator_email = "collab@exists.com"; collaborator_id = 5
    mock_conn.fetchval.return_value = collaborator_id # User found by email
    mock_conn.execute.return_value = "INSERT 0 1" # Simulate successful insert

    await crud_list.add_collaborator_to_list(mock_conn, list_id, collaborator_email)

    assert mock_conn.fetchval.await_count == 1 # Only email lookup needed
    mock_conn.execute.assert_awaited_once_with(
        "INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2)",
        list_id, collaborator_id
    )

async def test_add_collaborator_success_user_created():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; collaborator_email = "new_collab@test.com"; new_user_id = 6
    # Simulate user not found by email initially, then created successfully
    mock_conn.fetchval.side_effect = [None, new_user_id]
    mock_conn.execute.return_value = "INSERT 0 1"

    await crud_list.add_collaborator_to_list(mock_conn, list_id, collaborator_email)

    assert mock_conn.fetchval.await_count == 2 # Email lookup + Insert user
    mock_conn.execute.assert_awaited_once_with(
        "INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2)",
        list_id, new_user_id
    )

async def test_add_collaborator_already_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; collaborator_email = "already@test.com"; collaborator_id = 7
    mock_conn.fetchval.return_value = collaborator_id # User found
    # Simulate INSERT failing due to unique constraint
    mock_conn.execute.side_effect = asyncpg.exceptions.UniqueViolationError("duplicate key")

    with pytest.raises(CollaboratorAlreadyExistsError):
        await crud_list.add_collaborator_to_list(mock_conn, list_id, collaborator_email)

# --- Tests for delete_collaborator_from_list ---
async def test_delete_collaborator_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 1"
    deleted = await crud_list.delete_collaborator_from_list(mock_conn, 1, 5)
    assert deleted is True
    mock_conn.execute.assert_awaited_once_with(
        "DELETE FROM list_collaborators WHERE list_id = $1 AND user_id = $2", 1, 5
    )

async def test_delete_collaborator_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 0"
    deleted = await crud_list.delete_collaborator_from_list(mock_conn, 1, 99)
    assert deleted is False
    mock_conn.execute.assert_awaited_once()

# --- Tests for check_list_ownership ---
async def test_check_list_ownership_is_owner():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = True # Simulate EXISTS returns true
    await crud_list.check_list_ownership(mock_conn, 1, 10) # Should not raise
    mock_conn.fetchval.assert_awaited_once()

async def test_check_list_ownership_not_owner():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.side_effect = [False, True] # EXISTS is false, check list exists is true
    with pytest.raises(ListAccessDeniedError):
        await crud_list.check_list_ownership(mock_conn, 1, 20)
    assert mock_conn.fetchval.await_count == 2

async def test_check_list_ownership_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.side_effect = [False, False] # EXISTS is false, check list exists is false
    with pytest.raises(ListNotFoundError):
        await crud_list.check_list_ownership(mock_conn, 999, 10)
    assert mock_conn.fetchval.await_count == 2

# --- Tests for check_list_access ---
async def test_check_list_access_is_owner():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = True # Simulate combined EXISTS returns true
    await crud_list.check_list_access(mock_conn, 1, 10) # Should not raise
    mock_conn.fetchval.assert_awaited_once()

async def test_check_list_access_is_collaborator():
     mock_conn = AsyncMock(spec=asyncpg.Connection)
     # Simulate owner check fails, but collaborator check passes in UNION
     mock_conn.fetchval.return_value = True
     await crud_list.check_list_access(mock_conn, 1, 15) # User 15 is collaborator
     mock_conn.fetchval.assert_awaited_once()

async def test_check_list_access_no_access():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.side_effect = [False, True] # Combined EXISTS is false, list exists check is true
    with pytest.raises(ListAccessDeniedError):
        await crud_list.check_list_access(mock_conn, 1, 30)
    assert mock_conn.fetchval.await_count == 2

async def test_check_list_access_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.side_effect = [False, False] # Combined EXISTS is false, list exists check is false
    with pytest.raises(ListNotFoundError):
        await crud_list.check_list_access(mock_conn, 999, 30)
    assert mock_conn.fetchval.await_count == 2

# --- Tests for Discovery Functions ---
async def test_get_public_lists_paginated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = 1 # Total count
    mock_conn.fetch.return_value = [create_mock_record({"id": 10, "name": "Public List"})]
    results, total = await crud_list.get_public_lists_paginated(mock_conn, 1, 10)
    assert total == 1 and len(results) == 1
    assert "WHERE is_private = FALSE" in mock_conn.fetchval.await_args.args[0]
    assert "WHERE is_private = FALSE" in mock_conn.fetch.await_args.args[0]

async def test_search_lists_paginated_unauthenticated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = 1
    mock_conn.fetch.return_value = [create_mock_record({"id": 11})]
    results, total = await crud_list.search_lists_paginated(mock_conn, "query", None, 1, 10)
    assert total == 1 and len(results) == 1
    assert "l.is_private = FALSE" in mock_conn.fetchval.await_args.args[0] # Should only search public
    assert "l.is_private = FALSE" in mock_conn.fetch.await_args.args[0]

async def test_search_lists_paginated_authenticated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 5
    mock_conn.fetchval.return_value = 2
    mock_conn.fetch.return_value = [create_mock_record({"id": 11}), create_mock_record({"id": 12})]
    results, total = await crud_list.search_lists_paginated(mock_conn, "query", user_id, 1, 10)
    assert total == 2 and len(results) == 2
    # Check for the OR condition allowing user's private lists
    assert f"l.is_private = FALSE OR l.owner_id = ${len(mock_conn.fetchval.await_args.args)}" in mock_conn.fetchval.await_args.args[0]
    assert user_id in mock_conn.fetchval.await_args.args # Check user_id was passed

async def test_get_recent_lists_paginated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    user_id = 5; total = 3
    mock_conn.fetchval.return_value = total
    mock_conn.fetch.return_value = [create_mock_record({"id": i}) for i in range(3)]
    results, total_items = await crud_list.get_recent_lists_paginated(mock_conn, user_id, 1, 10)
    assert total_items == total and len(results) == 3
    assert "l.is_private = FALSE OR l.owner_id = $1" in mock_conn.fetchval.await_args.args[0]
    assert user_id in mock_conn.fetchval.await_args.args

# --- Tests for get_list_by_id ---
async def test_get_list_by_id_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "owner_id": 5})
    record = await crud_list.get_list_by_id(mock_conn, list_id)
    assert record is not None and record['id'] == list_id

async def test_get_list_by_id_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None
    record = await crud_list.get_list_by_id(mock_conn, 999)
    assert record is None

async def test_create_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1
    list_in = list_schemas.ListCreate(name="Test List", description="Desc", isPrivate=False)
    expected_id = 101
    mock_conn.fetchrow.return_value = create_mock_record({ # Use helper
        "id": expected_id, "name": list_in.name, "description": list_in.description, "is_private": list_in.isPrivate
    })

    created_record = await crud_list.create_list(mock_conn, list_in, owner_id)

    assert created_record is not None
    assert created_record['id'] == expected_id
    # ... other assertions ...
    mock_conn.fetchrow.assert_awaited_once()