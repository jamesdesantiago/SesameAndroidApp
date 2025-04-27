# backend/tests/crud/test_crud_place.py

import pytest
import asyncpg
from unittest.mock import AsyncMock, MagicMock, call
import unittest.mock # <--- IMPORT unittest.mock for ANY
from typing import Dict, Any, Optional, Tuple

# Import logging setup module early
import backend.app.core.logging

# Import module and functions/exceptions
from backend.app.crud import crud_place
from backend.app.crud.crud_place import PlaceNotFoundError, PlaceAlreadyExistsError, InvalidPlaceDataError, DatabaseInteractionError
from backend.app.schemas import place as place_schemas

# Import the helper from utils
from backend.tests.utils import create_mock_record

pytestmark = pytest.mark.asyncio

# Logger for this test file
logger = backend.app.core.logging.get_logger(__name__)

# Helper to create a mock asyncpg.exceptions.UniqueViolationError (from user crud tests)
def create_mock_unique_violation_error(message, constraint_name=None):
    mock_exc = MagicMock(spec=asyncpg.exceptions.UniqueViolationError)
    mock_exc.args = (message,)
    mock_exc.constraint_name = constraint_name
    mock_exc.sqlstate = '23505'
    return mock_exc

# Helper to create a mock asyncpg.exceptions.CheckViolationError (from user crud tests)
def create_mock_check_violation_error(message, constraint_name=None):
    mock_exc = MagicMock(spec=asyncpg.exceptions.CheckViolationError)
    mock_exc.args = (message,)
    mock_exc.constraint_name = constraint_name
    mock_exc.sqlstate = '23514'
    return mock_exc


# --- Tests for get_places_by_list_id_paginated ---
async def test_get_places_by_list_id_paginated_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; page = 1; page_size = 5; offset = (page - 1) * page_size; total_expected = 2
    # Mock fetchval for the count query
    mock_conn.fetchval.return_value = total_expected
    # Mock fetch for the main query (include all PlaceItem fields)
    mock_conn.fetch.return_value = [
        create_mock_record({"id": 10, "name": "Place A", "address": "addr A", "latitude": 10.0, "longitude": 20.0, "rating": None, "notes": None, "visit_status": None, "place_id": "extA"}),
        create_mock_record({"id": 11, "name": "Place B", "address": "addr B", "latitude": 11.0, "longitude": 21.0, "rating": None, "notes": None, "visit_status": None, "place_id": "extB"})
    ]

    places, total = await crud_place.get_places_by_list_id_paginated(mock_conn, list_id, page, page_size)

    assert total == total_expected
    assert len(places) == 2
    assert places[0]['id'] == 10
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM places WHERE list_id = $1", list_id) # Check count query
    mock_conn.fetch.assert_awaited_once() # Check fetch query
    # Check arguments for fetch query
    fetch_args = mock_conn.fetch.await_args.args
    assert fetch_args[0] == unittest.mock.ANY # SQL string
    assert fetch_args[1] == list_id # Check list_id in fetch
    assert fetch_args[2] == page_size # Check limit
    assert fetch_args[3] == offset # Check offset

async def test_get_places_by_list_id_paginated_empty():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1; page = 1; page_size = 5;
    # Mock fetchval for the count query (returns 0)
    mock_conn.fetchval.return_value = 0
    # Mock fetch (returns empty list)
    mock_conn.fetch.return_value = []

    places, total = await crud_place.get_places_by_list_id_paginated(mock_conn, list_id, page, page_size)

    assert total == 0
    assert len(places) == 0
    mock_conn.fetchval.assert_awaited_once_with("SELECT COUNT(*) FROM places WHERE list_id = $1", list_id) # Count query is called
    mock_conn.fetch.assert_not_awaited() # <--- CORRECTED: Fetch should *not* be called if count is 0


# --- Tests for add_place_to_list ---
async def test_add_place_to_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    # Pass the Pydantic model instance
    place_in = place_schemas.PlaceCreate(
        placeId="google123", name="Test Cafe", address="123 Main", latitude=10.0, longitude=20.0,
        rating="MUST_VISIT", notes="Some notes", visitStatus="VISITED"
    )
    place_in_dict = place_in.model_dump(by_alias=True) # Get dict for DB call comparison

    expected_db_id = 50
    # Mock fetchrow to return the record as it would on successful INSERT ... RETURNING
    mock_return_record_data = {
        "id": expected_db_id, "name": place_in_dict["name"], "address": place_in_dict["address"],
        "latitude": place_in_dict["latitude"], "longitude": place_in_dict["longitude"],
        "rating": place_in_dict["rating"], "notes": place_in_dict["notes"],
        "visit_status": place_in_dict["visit_status"]
    }
    mock_conn.fetchrow.return_value = create_mock_record(mock_return_record_data)

    result = await crud_place.add_place_to_list(mock_conn, list_id, place_in) # Pass Pydantic model

    assert result is not None
    assert dict(result) == mock_return_record_data
    place_schemas.PlaceItem.model_validate(result)

    mock_conn.fetchrow.assert_awaited_once()
    # Check args passed to insert
    insert_args = mock_conn.fetchrow.await_args.args
    assert insert_args[1:] == (list_id, place_in_dict["place_id"], place_in_dict["name"],
                               place_in_dict["address"], place_in_dict["latitude"],
                               place_in_dict["longitude"], place_in_dict["rating"],
                               place_in_dict["notes"], place_in_dict["visit_status"])


# FIX: Corrected exception mocking and input type
async def test_add_place_to_list_already_exists():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    # Pass the Pydantic model instance
    place_in = place_schemas.PlaceCreate(placeId="google123", name="Test Cafe", address="123 Main", latitude=10, longitude=10)
    # Simulate UniqueViolation on the fetchrow (INSERT) call
    mock_exception = create_mock_unique_violation_error('duplicate key value violates unique constraint "places_list_id_place_id_key"', 'places_list_id_place_id_key')
    mock_conn.fetchrow.side_effect = mock_exception

    with pytest.raises(PlaceAlreadyExistsError, match="Place already exists in this list"):
        await crud_place.add_place_to_list(mock_conn, list_id, place_in) # Pass Pydantic model
    mock_conn.fetchrow.assert_awaited_once()


# FIX: Corrected exception mocking and input type
async def test_add_place_to_list_invalid_data():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    # Pass the Pydantic model instance
    place_in = place_schemas.PlaceCreate(placeId="google123", name="Test Cafe", address="123 Main", latitude=10, longitude=10)
    # Simulate CheckViolation
    mock_exception = create_mock_check_violation_error('new row for relation "places" violates check constraint "places_latitude_check"', 'places_latitude_check')
    mock_conn.fetchrow.side_effect = mock_exception

    with pytest.raises(InvalidPlaceDataError, match="Invalid data for place"): # Check exception type and message
        await crud_place.add_place_to_list(mock_conn, list_id, place_in) # Pass Pydantic model
    mock_conn.fetchrow.assert_awaited_once()


# FIX: Corrected mock return and exception wrapping, input type
async def test_add_place_to_list_db_error():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    list_id = 1
    # Pass the Pydantic model instance
    place_in = place_schemas.PlaceCreate(placeId="google123", name="Test Cafe", address="123 Main", latitude=10, longitude=10)
    # Simulate generic DB error on the fetchrow (INSERT) call
    mock_conn.fetchrow.side_effect = asyncpg.PostgresError("Connection timeout")

    with pytest.raises(DatabaseInteractionError, match="Database error adding place."): # Check wrapped error message
        await crud_place.add_place_to_list(mock_conn, list_id, place_in) # Pass Pydantic model
    mock_conn.fetchrow.assert_awaited_once()


# --- Tests for update_place ---

# RENAMED and FIXED test_update_place_notes_success
async def test_update_place_success_notes_only():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 50; list_id = 1; new_notes = "Updated notes here"
    # Pass the Pydantic model instance
    place_update_in = place_schemas.PlaceUpdate(notes=new_notes)
    update_data_dict = place_update_in.model_dump(exclude_unset=True, by_alias=True) # Get dict for check

    # Simulate RETURNING clause returns updated record. Include all fields from PlaceItem.
    mock_return_record_data = {
        "id": place_id, "name": "Place Name", "address": "Addr", "latitude": 0.0, "longitude": 0.0,
        "rating": "OLD_RATING", "notes": new_notes, "visit_status": "OLD_STATUS"
    }
    mock_conn.fetchrow.return_value = create_mock_record(mock_return_record_data)

    result = await crud_place.update_place(mock_conn, place_id, list_id, place_update_in) # Call update_place, pass Pydantic model

    assert result is not None
    assert dict(result) == mock_return_record_data

    mock_conn.fetchrow.assert_awaited_once() # The UPDATE ... RETURNING call
    # Check parameters passed to UPDATE
    update_sql = mock_conn.fetchrow.await_args.args[0]
    update_params = mock_conn.fetchrow.await_args.args[1:]

    # Check that the update statement constructed in CRUD includes only 'notes'
    assert "SET notes = $1" in update_sql
    assert f"WHERE id = ${1+1}" in update_sql # 1 param for notes + 1 for id
    assert f"AND list_id = ${1+2}" in update_sql # 1 param for notes + 1 for id + 1 for list_id

    assert update_params[0] == new_notes # $1 in the query for notes
    assert update_params[1] == place_id # $2 in the query for id
    assert update_params[2] == list_id # $3 in the query for list_id


# ADDED test for partial update with multiple fields
async def test_update_place_success_multiple_fields():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 51; list_id = 1
    # Pass the Pydantic model instance
    place_update_in = place_schemas.PlaceUpdate(notes="New Notes", rating="MUST_VISIT")
    update_data_dict = place_update_in.model_dump(exclude_unset=True, by_alias=True) # Get dict for check

    mock_return_record_data = {
        "id": place_id, "name": "Place Name", "address": "Addr", "latitude": 0.0, "longitude": 0.0,
        "rating": "MUST_VISIT", "notes": "New Notes", "visit_status": "OLD_STATUS"
    }
    mock_conn.fetchrow.return_value = create_mock_record(mock_return_record_data)

    result = await crud_place.update_place(mock_conn, place_id, list_id, place_update_in) # Pass Pydantic model

    assert result is not None
    assert dict(result) == mock_return_record_data

    mock_conn.fetchrow.assert_awaited_once()
    # Check that the update statement constructed in CRUD includes both fields
    update_sql = mock_conn.fetchrow.await_args.args[0]
    update_params = mock_conn.fetchrow.await_args.args[1:]
    # Check existence of set parts and parameters (order might vary)
    assert ("notes = $1" in update_sql or "notes = $2" in update_sql)
    assert ("rating = $1" in update_sql or "rating = $2" in update_sql)
    assert f"WHERE id = ${2+1}" in update_sql # 2 params for fields + 1 for id
    assert f"AND list_id = ${2+2}" in update_sql # 2 params for fields + 1 for id + 1 for list_id

    # Check that the parameters match the expected values (use set comparison as order might vary)
    expected_param_values = [update_data_dict['notes'], update_data_dict['rating'], place_id, list_id]
    assert set(update_params) == set(expected_param_values)


async def test_update_place_no_fields():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 52; list_id = 1
    # Create empty update model
    place_update_in = place_schemas.PlaceUpdate()

    # Mock fetchrow for the initial lookup (called when no fields are updated)
    mock_current_place_record_data = {
        "id": place_id, "name": "Current Place", "address": "Addr", "latitude": 0.0, "longitude": 0.0,
        "rating": "CURRENT_RATING", "notes": "Current Notes", "visit_status": "CURRENT_STATUS"
    }
    mock_conn.fetchrow.return_value = create_mock_record(mock_current_place_record_data)

    result = await crud_place.update_place(mock_conn, place_id, list_id, place_update_in) # Pass empty Pydantic model

    assert result is not None
    assert dict(result) == mock_current_place_record_data

    # Check that only the lookup query was called
    mock_conn.fetchrow.assert_awaited_once_with(
        """
             SELECT id, name, address, latitude, longitude, rating, notes, visit_status
             FROM places
             WHERE id = $1 AND list_id = $2
             """,
        place_id, list_id
    )
    mock_conn.execute.assert_not_awaited() # UPDATE should not be called


# RENAMED and FIXED test_update_place_notes_not_found
async def test_update_place_not_found_in_list():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 999; list_id = 1; new_notes = "Notes"
    # Pass the Pydantic model instance
    place_update_in = place_schemas.PlaceUpdate(notes=new_notes)
    # Simulate update returns no rows (place not found in this list)
    mock_conn.fetchrow.return_value = None

    with pytest.raises(PlaceNotFoundError, match="Place not found in this list for update."):
        await crud_place.update_place(mock_conn, place_id, list_id, place_update_in) # Call update_place

    # Check the update attempt happened and returned None
    mock_conn.fetchrow.assert_awaited_once()


# RENAMED and FIXED test_update_place_notes_db_error
async def test_update_place_db_error():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 50; list_id = 1; new_notes = "Notes"
    # Pass the Pydantic model instance
    place_update_in = place_schemas.PlaceUpdate(notes=new_notes)
    # Simulate DB error on the fetchrow (UPDATE RETURNING) call
    mock_conn.fetchrow.side_effect = asyncpg.PostgresError("Update failed")

    with pytest.raises(DatabaseInteractionError, match="Database error updating place."): # Check wrapped error message
        await crud_place.update_place(mock_conn, place_id, list_id, place_update_in) # Call update_place

    mock_conn.fetchrow.assert_awaited_once()


# --- Tests for delete_place_from_list ---
# (These tests look correct)
async def test_delete_place_from_list_success():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 50; list_id = 1
    mock_conn.execute.return_value = "DELETE 1" # Simulate 1 row deleted
    deleted = await crud_place.delete_place_from_list(mock_conn, place_id, list_id)
    assert deleted is True
    mock_conn.execute.assert_awaited_once_with(
        "DELETE FROM places WHERE id = $1 AND list_id = $2", place_id, list_id
    )

async def test_delete_place_from_list_not_found():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 999; list_id = 1
    mock_conn.execute.return_value = "DELETE 0" # Simulate 0 rows deleted
    deleted = await crud_place.delete_place_from_list(mock_conn, place_id, list_id)
    assert deleted is False
    mock_conn.execute.assert_awaited_once_with(
         "DELETE FROM places WHERE id = $1 AND list_id = $2", place_id, list_id
    )


async def test_delete_place_from_list_db_error():
    mock_conn = AsyncMock(spec=asyncpg.Connection)
    place_id = 50; list_id = 1
    # Simulate DB error on the execute (DELETE) call
    mock_conn.execute.side_effect = asyncpg.PostgresError("Delete failed")

    with pytest.raises(DatabaseInteractionError, match="Database error deleting place."): # Check wrapped error message
        await crud_place.delete_place_from_list(mock_conn, place_id, list_id)

    mock_conn.execute.assert_awaited_once_with(
         "DELETE FROM places WHERE id = $1 AND list_id = $2", place_id, list_id
    )