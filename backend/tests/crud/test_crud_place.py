# backend/tests/crud/test_crud_place.py

import pytest
import asyncpg
from unittest.mock import AsyncMock, MagicMock
from typing import Dict, Any, Optional, Tuple

# Import module and functions/exceptions
from app.crud import crud_place
from app.crud.crud_place import PlaceNotFoundError, PlaceAlreadyExistsError, InvalidPlaceDataError, DatabaseInteractionError
from app.schemas import place as place_schemas

# Import the helper from utils
from tests.utils import create_mock_record # <<< IMPORT HELPER

pytestmark = pytest.mark.asyncio

# Helper function (copy from test_crud_list or move to conftest/utils)
def create_mock_record(data: Dict[str, Any]) -> MagicMock:
    mock = MagicMock(spec=asyncpg.Record)
    mock.__getitem__.side_effect = lambda key: data.get(key)
    mock.get.side_effect = lambda key, default=None: data.get(key, default)
    for key, value in data.items(): setattr(mock, key, value)
    mock.items.return_value = data.items(); mock.keys.return_value = data.keys()
    mock._asdict = lambda: data
    return mock

# --- Tests for get_places_by_list_id_paginated ---
async def test_get_places_by_list_id_paginated_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; page = 1; page_size = 5; offset = 0; total_expected = 2
    mock_conn.fetchval.return_value = total_expected
    mock_conn.fetch.return_value = [
        create_mock_record({"id": 10, "name": "Place A"}),
        create_mock_record({"id": 11, "name": "Place B"})
    ]

    places, total = await crud_place.get_places_by_list_id_paginated(mock_conn, list_id, page, page_size)

    assert total == total_expected
    assert len(places) == 2
    assert places[0]['id'] == 10
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM places WHERE list_id = $1", list_id)
    mock_conn.fetch.assert_awaited_once()
    assert mock_conn.fetch.await_args.args[-3] == list_id # Check list_id in fetch
    assert mock_conn.fetch.await_args.args[-2] == page_size # Check limit
    assert mock_conn.fetch.await_args.args[-1] == offset # Check offset

async def test_get_places_by_list_id_paginated_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; page = 1; page_size = 5;
    mock_conn.fetchval.return_value = 0
    mock_conn.fetch.return_value = []

    places, total = await crud_place.get_places_by_list_id_paginated(mock_conn, list_id, page, page_size)

    assert total == 0
    assert len(places) == 0
    mock_conn.fetchval.assert_awaited_once()
    mock_conn.fetch.assert_awaited_once()

# --- Tests for add_place_to_list ---
async def test_add_place_to_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    place_in = place_schemas.PlaceCreate(
        placeId="google123", name="Test Cafe", address="123 Main", latitude=10.0, longitude=20.0
    )
    expected_db_id = 50
    mock_conn.fetchrow.return_value = create_mock_record({
        "id": expected_db_id, "name": place_in.name, "address": place_in.address # etc
    })

    result = await crud_place.add_place_to_list(mock_conn, list_id, place_in)

    assert result is not None
    assert result['id'] == expected_db_id
    assert result['name'] == place_in.name
    mock_conn.fetchrow.assert_awaited_once()
    # Check args passed to insert
    insert_args = mock_conn.fetchrow.await_args.args
    assert insert_args[1] == list_id
    assert insert_args[2] == place_in.placeId
    assert insert_args[3] == place_in.name
    # ... check other args

async def test_add_place_to_list_already_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    place_in = place_schemas.PlaceCreate(placeId="google123", name="Test Cafe", address="123 Main", latitude=10, longitude=10)
    # Simulate UniqueViolation on the specific constraint
    mock_conn.fetchrow.side_effect = asyncpg.exceptions.UniqueViolationError("violates unique constraint places_list_id_place_id_key")

    with pytest.raises(PlaceAlreadyExistsError):
        await crud_place.add_place_to_list(mock_conn, list_id, place_in)

async def test_add_place_to_list_invalid_data():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    place_in = place_schemas.PlaceCreate(placeId="google123", name="Test Cafe", address="123 Main", latitude=10, longitude=10)
    # Simulate CheckViolation
    mock_conn.fetchrow.side_effect = asyncpg.exceptions.CheckViolationError("violates check constraint some_check")

    with pytest.raises(InvalidPlaceDataError):
        await crud_place.add_place_to_list(mock_conn, list_id, place_in)

# --- Tests for update_place_notes ---
async def test_update_place_notes_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 50; list_id = 1; new_notes = "Updated notes here"
    # Simulate RETURNING clause returns updated record
    mock_conn.fetchrow.return_value = create_mock_record({"id": place_id, "notes": new_notes})

    result = await crud_place.update_place_notes(mock_conn, place_id, list_id, new_notes)

    assert result is not None
    assert result['id'] == place_id
    assert result['notes'] == new_notes
    mock_conn.fetchrow.assert_awaited_once()
    # Check args passed to update
    update_args = mock_conn.fetchrow.await_args.args
    assert update_args[1] == new_notes
    assert update_args[2] == place_id
    assert update_args[3] == list_id

async def test_update_place_notes_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 999; list_id = 1; new_notes = "Notes"
    # Simulate update returns no rows (because place_id/list_id combo doesn't exist)
    mock_conn.fetchrow.return_value = None

    with pytest.raises(PlaceNotFoundError):
        await crud_place.update_place_notes(mock_conn, place_id, list_id, new_notes)

    mock_conn.fetchrow.assert_awaited_once()

# --- Tests for delete_place_from_list ---
async def test_delete_place_from_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 1" # Simulate 1 row deleted
    deleted = await crud_place.delete_place_from_list(mock_conn, 50, 1)
    assert deleted is True
    mock_conn.execute.assert_awaited_once_with(
        "DELETE FROM places WHERE id = $1 AND list_id = $2", 50, 1
    )

async def test_delete_place_from_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    mock_conn.execute.return_value = "DELETE 0" # Simulate 0 rows deleted
    deleted = await crud_place.delete_place_from_list(mock_conn, 999, 1)
    assert deleted is False
    mock_conn.execute.assert_awaited_once()

async def test_add_place_to_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    place_in = place_schemas.PlaceCreate(
        placeId="google123", name="Test Cafe", address="123 Main", latitude=10.0, longitude=20.0
    )
    expected_db_id = 50
    mock_conn.fetchrow.return_value = create_mock_record({ # Use helper
        "id": expected_db_id, "name": place_in.name, "address": place_in.address
    })

    result = await crud_place.add_place_to_list(mock_conn, list_id, place_in)

    assert result is not None
    assert result['id'] == expected_db_id
    # ... other assertions ...
    mock_conn.fetchrow.assert_awaited_once()