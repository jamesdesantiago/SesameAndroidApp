# backend/tests/crud/test_crud_list.py

import pytest
import asyncpg
from unittest.mock import AsyncMock, MagicMock
from typing import Dict, Any, List, Optional, Tuple

# Import module and functions/exceptions
from app.crud import crud_list
from app.crud.crud_list import (ListNotFoundError, ListAccessDeniedError,
                                CollaboratorNotFoundError, CollaboratorAlreadyExistsError,
                                DatabaseInteractionError) # Make sure DBInteractionError is defined/imported if used
from app.schemas import list as list_schemas

# Import the helper from utils
from tests.utils import create_mock_record

pytestmark = pytest.mark.asyncio

# --- Tests for create_list ---
async def test_create_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1
    list_in = list_schemas.ListCreate(name="My Awesome List", description="Cool stuff", isPrivate=False)
    expected_id = 101
    mock_conn.fetchrow.return_value = create_mock_record({
        "id": expected_id, "name": list_in.name, "description": list_in.description, "is_private": list_in.isPrivate
    })

    created_record = await crud_list.create_list(mock_conn, list_in, owner_id)

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
    list_in = list_schemas.ListCreate(name="Error List", isPrivate=True)
    mock_conn.fetchrow.side_effect = asyncpg.PostgresError("DB connection failed")

    with pytest.raises(RuntimeError, match="Failed to create list"): # Check specific error message if defined
        await crud_list.create_list(mock_conn, list_in, owner_id)

# --- Tests for get_user_lists_paginated ---
async def test_get_user_lists_paginated_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1; page = 1; page_size = 5; offset = 0
    total_expected = 2
    # Mock count query result
    mock_conn.fetchval.return_value = total_expected
    # Mock fetch lists result (without place_count initially)
    mock_conn.fetch.side_effect = [
        [ # First fetch (lists)
            create_mock_record({"id": 1, "name": "L1", "description": "d1", "is_private": False}),
            create_mock_record({"id": 2, "name": "L2", "description": "d2", "is_private": True}),
        ],
        [ # Second fetch (place counts)
            create_mock_record({"list_id": 1, "count": 5}),
            create_mock_record({"list_id": 2, "count": 0}),
        ]
    ]

    lists_with_counts, total = await crud_list.get_user_lists_paginated(mock_conn, owner_id, page, page_size)

    assert total == total_expected
    assert len(lists_with_counts) == 2
    assert lists_with_counts[0]['id'] == 1
    assert lists_with_counts[0]['place_count'] == 5 # Check added place_count
    assert lists_with_counts[1]['id'] == 2
    assert lists_with_counts[1]['place_count'] == 0
    assert mock_conn.fetchval.await_count == 1 # Count query
    assert mock_conn.fetch.await_count == 2 # Fetch lists + Fetch counts

async def test_get_user_lists_paginated_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    owner_id = 1; page = 1; page_size = 5;
    mock_conn.fetchval.return_value = 0 # Count is 0
    mock_conn.fetch.return_value = [] # Fetch returns empty

    lists, total = await crud_list.get_user_lists_paginated(mock_conn, owner_id, page, page_size)

    assert total == 0
    assert len(lists) == 0
    mock_conn.fetchval.assert_awaited_once()
    mock_conn.fetch.assert_awaited_once() # List fetch is still called

# --- Tests for get_list_details ---
async def test_get_list_details_found_no_collab():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Details List", "description": "d", "is_private": False})
    mock_conn.fetch.return_value = [] # No collaborators

    details = await crud_list.get_list_details(mock_conn, list_id)

    assert details is not None
    assert details["id"] == list_id
    assert details["collaborators"] == []
    assert mock_conn.fetchrow.await_count == 1 # Get list details
    assert mock_conn.fetch.await_count == 1 # Get collaborators

async def test_get_list_details_found_with_collab():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 2
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Details List Collab"})
    mock_conn.fetch.return_value = [
        create_mock_record({"email": "collab1@test.com"}),
        create_mock_record({"email": "collab2@test.com"}),
    ]

    details = await crud_list.get_list_details(mock_conn, list_id)

    assert details is not None
    assert details["id"] == list_id
    assert details["collaborators"] == ["collab1@test.com", "collab2@test.com"]

async def test_get_list_details_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = None # Simulate list not found
    details = await crud_list.get_list_details(mock_conn, 999)
    assert details is None
    mock_conn.fetchrow.assert_awaited_once()
    mock_conn.fetch.assert_not_awaited() # Collaborators not fetched

# --- Tests for update_list ---
async def test_update_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    update_data = list_schemas.ListUpdate(name="Updated Name", isPrivate=True)
    mock_conn.fetchrow.return_value = create_mock_record({"id": list_id, "name": "Updated Name", "description": None, "is_private": True}) # RETURNING
    mock_conn.fetch.return_value = [] # _get_collaborator_emails

    updated_dict = await crud_list.update_list(mock_conn, list_id, update_data)

    assert updated_dict is not None
    assert updated_dict["name"] == "Updated Name"
    assert updated_dict["isPrivate"] is True
    mock_conn.fetchrow.assert_awaited_once() # UPDATE call
    mock_conn.fetch.assert_awaited_once() # Collaborator fetch call

# --- Tests for delete_list ---
async def test_delete_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 1"
    deleted = await crud_list.delete_list(mock_conn, 1)
    assert deleted is True
    mock_conn.execute.assert_awaited_once_with("DELETE FROM lists WHERE id = $1", 1)

# --- Tests for add_collaborator_to_list ---
async def test_add_collaborator_user_created():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; email = "new@test.com"; new_user_id = 10
    mock_conn.fetchval.side_effect = [None, new_user_id] # Not found by email, then insert returns ID
    mock_conn.execute.return_value = "INSERT 0 1" # Insert collaborator succeeds
    await crud_list.add_collaborator_to_list(mock_conn, list_id, email)
    assert mock_conn.fetchval.await_count == 2
    mock_conn.execute.assert_awaited_once()

async def test_add_collaborator_already_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; email = "exists@test.com"; user_id = 11
    mock_conn.fetchval.return_value = user_id # User exists
    mock_conn.execute.side_effect = asyncpg.exceptions.UniqueViolationError("PK") # Collab insert fails
    with pytest.raises(CollaboratorAlreadyExistsError):
        await crud_list.add_collaborator_to_list(mock_conn, list_id, email)

# --- Tests for delete_collaborator_from_list ---
async def test_delete_collaborator_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 1"
    deleted = await crud_list.delete_collaborator_from_list(mock_conn, 1, 5)
    assert deleted is True

async def test_delete_collaborator_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 0"
    deleted = await crud_list.delete_collaborator_from_list(mock_conn, 1, 99)
    assert deleted is False

# --- Tests for check_list_ownership ---
async def test_check_list_ownership_is_owner():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = True
    await crud_list.check_list_ownership(mock_conn, 1, 10) # Should not raise

async def test_check_list_ownership_not_owner():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.side_effect = [False, True] # Not owner, list exists
    with pytest.raises(ListAccessDeniedError):
        await crud_list.check_list_ownership(mock_conn, 1, 20)

# --- Tests for check_list_access ---
async def test_check_list_access_is_collaborator():
     mock_conn = AsyncMock(spec=asyncpg.Connection)
     mock_conn.fetchval.return_value = True # Combined query returns true
     await crud_list.check_list_access(mock_conn, 1, 15) # Should not raise

async def test_check_list_access_no_access():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.side_effect = [False, True] # No access, list exists
    with pytest.raises(ListAccessDeniedError):
        await crud_list.check_list_access(mock_conn, 1, 30)

# --- Tests for Discovery Functions ---
# (Add tests similar to get_user_lists_paginated, verifying WHERE clauses)
async def test_get_public_lists_paginated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = 1 # Count
    mock_conn.fetch.return_value = [create_mock_record({"id": 1})] # Lists
    mock_conn.fetch.side_effect = [[create_mock_record({"id": 1})], []] # Mock both fetches needed by optimized version
    await crud_list.get_public_lists_paginated(mock_conn, 1, 10)
    # Add assertions checking SQL contains 'is_private = FALSE'

async def test_search_lists_paginated_authenticated():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchval.return_value = 1
    mock_conn.fetch.side_effect = [[create_mock_record({"id": 1})], []] # Mock both fetches needed by optimized version
    await crud_list.search_lists_paginated(mock_conn, "q", 5, 1, 10)
    # Add assertions checking SQL contains 'is_private = FALSE OR owner_id =' and correct params

# --- Tests for get_list_by_id ---
async def test_get_list_by_id_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.fetchrow.return_value = create_mock_record({"id": 1, "owner_id": 5})
    record = await crud_list.get_list_by_id(mock_conn, 1)
    assert record is not None