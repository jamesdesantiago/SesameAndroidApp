# backend/tests/api/test_lists_api.py

import pytest
from httpx import AsyncClient
from fastapi import status
from typing import Dict, Any, Optional, List
import asyncpg
import os
import math # For pagination checks

# Import app components AFTER conftest sets environment
from app.core.config import settings
from app.schemas import list as list_schemas
from app.schemas import place as place_schemas
from app.api import deps # For mocking dependencies if needed
from app.schemas.token import FirebaseTokenData # For mocking
from app.crud import crud_list, crud_place # To check DB state if needed
from unittest.mock import patch

# Mark all tests in this file as async and needing the DB event loop
pytestmark = [pytest.mark.asyncio]

# API Prefix from settings
API_V1 = settings.API_V1_STR
API_V1_LISTS = f"{settings.API_V1_STR}/lists" # Base path for this router

# --- Test Helper Functions (Assume these exist in conftest.py or tests/utils.py) ---
# Example definitions (copy/adapt if not already in conftest)
async def create_test_list_direct(
    db_conn: asyncpg.Connection, owner_id: int, name: str, is_private: bool, description: Optional[str] = None
) -> Dict[str, Any]:
    list_id = await db_conn.fetchval(
        "INSERT INTO lists (owner_id, name, description, is_private) VALUES ($1, $2, $3, $4) RETURNING id",
        owner_id, name, description, is_private
    )
    if not list_id: pytest.fail(f"Failed to create list '{name}' for owner {owner_id}")
    print(f"   [Helper] Created List ID: {list_id}")
    # Fetch place count (optional, could be asserted separately)
    place_count = await db_conn.fetchval("SELECT COUNT(*) FROM places WHERE list_id = $1", list_id) or 0
    return {"id": list_id, "owner_id": owner_id, "name": name, "isPrivate": is_private, "description": description, "place_count": place_count}

async def create_test_place_direct(
    db_conn: asyncpg.Connection, list_id: int, name: str, address: str, place_id_ext: str
) -> Dict[str, Any]:
    place_id_db = await db_conn.fetchval(
        "INSERT INTO places (list_id, place_id, name, address, latitude, longitude) VALUES ($1, $2, $3, $4, 0.0, 0.0) RETURNING id",
        list_id, place_id_ext, name, address
    )
    if not place_id_db: pytest.fail(f"Failed to create place '{name}' for list {list_id}")
    print(f"   [Helper] Created Place DB ID: {place_id_db} in List ID: {list_id}")
    return {"id": place_id_db, "list_id": list_id, "name": name, "place_id_ext": place_id_ext}

async def add_collaborator_direct(db_conn: asyncpg.Connection, list_id: int, user_id: int):
     await db_conn.execute("INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", list_id, user_id)
     print(f"   [Helper] Ensured collaborator User ID: {user_id} on List ID: {list_id}")


# =====================================================
# Test List CRUD Endpoints (POST /, GET /, GET /{id}, PATCH /{id}, DELETE /{id})
# =====================================================

@pytest.mark.parametrize("is_private", [True, False], ids=["Private List", "Public List"])
async def test_create_list_success(client: AsyncClient, mock_auth, test_user1: Dict[str, Any], is_private: bool, db_conn: asyncpg.Connection):
    """Test POST /lists - Creating a list successfully."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    list_name = f"My API New {'Private' if is_private else 'Public'} List {os.urandom(2).hex()}"
    list_desc = "Created via API test"
    payload = {"name": list_name, "description": list_desc, "isPrivate": is_private}

    response = await client.post(API_V1_LISTS, headers=headers, json=payload)

    assert response.status_code == status.HTTP_201_CREATED
    data = response.json()
    assert data["name"] == list_name
    assert data["description"] == list_desc
    assert data["isPrivate"] == is_private
    assert "id" in data
    list_id = data["id"]
    assert data["collaborators"] == []

    # Verify in DB (optional but good)
    db_list = await db_conn.fetchrow("SELECT owner_id, name, description, is_private FROM lists WHERE id = $1", list_id)
    assert db_list is not None
    assert db_list["owner_id"] == test_user1["id"]
    assert db_list["name"] == list_name
    assert db_list["is_private"] == is_private

async def test_create_list_missing_name(client: AsyncClient, mock_auth, test_user1: Dict[str, Any]):
    """Test POST /lists - Fails validation if required 'name' is missing."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    payload = {"description": "List without a name", "isPrivate": False}
    response = await client.post(API_V1_LISTS, headers=headers, json=payload)
    assert response.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY

async def test_create_list_unauthenticated(client: AsyncClient):
    """Test POST /lists - Fails without authentication."""
    payload = {"name": "Unauthorized List", "isPrivate": False}
    response = await client.post(API_V1_LISTS, json=payload)
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

async def test_get_user_lists_empty(client: AsyncClient, mock_auth, test_user1: Dict[str, Any]):
    """Test GET /lists - User has no lists."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(API_V1_LISTS, headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["items"] == []
    assert data["total_items"] == 0

async def test_get_user_lists_pagination(client: AsyncClient, db_conn: asyncpg.Connection, mock_auth, test_user1: Dict[str, Any], test_user2: Dict[str, Any]):
    """Test GET /lists - Pagination and ownership check."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    # Arrange: Create lists (ensure cleanup via fixtures or here)
    user1_lists = []
    try:
        for i in range(3): # User1 has 3 lists
            lst = await create_test_list_direct(db_conn, test_user1["id"], f"U1 List {i}", i % 2 == 0)
            user1_lists.append(lst)
        # User2 has 1 list (should not be returned)
        await create_test_list_direct(db_conn, test_user2["id"], "U2 List 0", False)

        # Act: Get page 1, size 2 for user1
        response = await client.get(API_V1_LISTS, headers=headers, params={"page": 1, "page_size": 2})
        assert response.status_code == status.HTTP_200_OK
        data1 = response.json()
        assert data1["total_items"] == 3
        assert data1["total_pages"] == 2
        assert len(data1["items"]) == 2
        ids1 = {item["id"] for item in data1["items"]}

        # Act: Get page 2, size 2
        response2 = await client.get(API_V1_LISTS, headers=headers, params={"page": 2, "page_size": 2})
        assert response2.status_code == status.HTTP_200_OK
        data2 = response2.json()
        assert len(data2["items"]) == 1
        ids2 = {item["id"] for item in data2["items"]}

        # Assert all user1's lists were found
        all_retrieved_ids = ids1.union(ids2)
        expected_ids = {lst["id"] for lst in user1_lists}
        assert all_retrieved_ids == expected_ids

    finally:
        # Manual cleanup for this test's lists if fixtures don't handle it
        for lst in user1_lists: await db_conn.execute("DELETE FROM lists WHERE id = $1", lst["id"])
        # Need to find user2's list id to delete it or rely on user2 fixture cleanup

async def test_get_list_detail_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test GET /lists/{list_id} - Success for owner."""
    # Arrange: Add a collaborator to check if they are returned
    await add_collaborator_direct(db_conn, test_list1["id"], test_user2["id"])
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]

    response = await client.get(f"{API_V1_LISTS}/{list_id}", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["id"] == list_id
    assert data["name"] == test_list1["name"]
    assert data["collaborators"] == [test_user2["email"]] # Check collaborator email

# test_get_list_detail_success_collaborator already covered in lists.py tests
# test_get_list_detail_forbidden already covered in lists.py tests
# test_get_list_detail_not_found already covered in lists.py tests

async def test_update_list_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test PATCH /lists/{list_id} - Success."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    new_name = f"Updated List Name {os.urandom(2).hex()}"
    payload = {"name": new_name, "isPrivate": True}

    response = await client.patch(f"{API_V1_LISTS}/{list_id}", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["name"] == new_name
    assert data["isPrivate"] is True

    # Verify DB
    db_list = await db_conn.fetchrow("SELECT name, is_private FROM lists WHERE id = $1", list_id)
    assert db_list["name"] == new_name
    assert db_list["is_private"] is True

async def test_update_list_partial(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test PATCH /lists/{list_id} - Updating only one field."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    payload = {"isPrivate": True} # Only change privacy

    response = await client.patch(f"{API_V1_LISTS}/{list_id}", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["name"] == test_list1["name"] # Name should be unchanged
    assert data["isPrivate"] is True

async def test_update_list_no_data(client: AsyncClient, mock_auth, test_list1: Dict[str, Any]):
    """Test PATCH /lists/{list_id} - Empty payload returns 400."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    payload = {}
    response = await client.patch(f"{API_V1_LISTS}/{list_id}", headers=headers, json=payload)
    assert response.status_code == status.HTTP_400_BAD_REQUEST

# test_update_list_forbidden already exists

async def test_delete_list_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test DELETE /lists/{list_id} - Success."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    # Add a place to test cascade if configured
    await create_test_place_direct(db_conn, list_id, "Place in deleted list", "Addr", "ext_del_list")

    # Pre-check
    assert await db_conn.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)

    del_response = await client.delete(f"{API_V1_LISTS}/{list_id}", headers=headers)
    assert del_response.status_code == status.HTTP_204_NO_CONTENT

    # Verify list is gone
    assert not await db_conn.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
    # Verify place is gone (if cascade delete is set)
    # assert not await db_conn.fetchval("SELECT EXISTS (SELECT 1 FROM places WHERE list_id = $1)", list_id)

# test_delete_list_forbidden already exists

# =====================================================
# Test Collaborator Endpoints
# =====================================================
# test_add_collaborator_success already exists
# test_add_collaborator_already_exists already exists

async def test_add_collaborator_non_existent_user(client: AsyncClient, mock_auth, test_list1: Dict[str, Any]):
     """Test POST /{list_id}/collaborators - Adding non-existent user email (CRUD creates user)."""
     headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
     list_id = test_list1["id"]
     non_existent_email = f"new_collab_{os.urandom(4).hex()}@example.com"
     payload = {"email": non_existent_email}

     response = await client.post(f"{API_V1_LISTS}/{list_id}/collaborators", headers=headers, json=payload)

     assert response.status_code == status.HTTP_201_CREATED
     assert response.json()["message"] == "Collaborator added"

async def test_add_collaborator_forbidden(client: AsyncClient, mock_auth, test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
     """Test POST /{list_id}/collaborators - Non-owner cannot add."""
     # Arrange: user2 owns the list
     list_data = await create_test_list_direct(db_conn, test_user2["id"], "List owned by User2", False)
     list_id = list_data["id"]
     # Act: user1 tries to add collaborator
     headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
     payload = {"email": "someone@example.com"}
     response = await client.post(f"{API_V1_LISTS}/{list_id}/collaborators", headers=headers, json=payload)
     assert response.status_code == status.HTTP_403_FORBIDDEN # Expect 403 because deps.get_list_and_verify_ownership fails

async def test_delete_collaborator_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test DELETE /{list_id}/collaborators/{user_id} - Success."""
    owner_id = test_list1["owner_id"]
    list_id = test_list1["id"]
    collaborator_id_to_remove = test_user2["id"]
    # Arrange: Add collaborator first
    await add_collaborator_direct(db_conn, list_id, collaborator_id_to_remove)
    assert await db_conn.fetchval("SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2", list_id, collaborator_id_to_remove)

    # Act: Owner removes collaborator
    headers = {"Authorization": f"Bearer fake-token-for-{owner_id}"}
    response = await client.delete(f"{API_V1_LISTS}/{list_id}/collaborators/{collaborator_id_to_remove}", headers=headers)

    # Assert: Success (No Content)
    assert response.status_code == status.HTTP_204_NO_CONTENT
    # Verify collaborator removed
    assert not await db_conn.fetchval("SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2", list_id, collaborator_id_to_remove)

async def test_delete_collaborator_forbidden(client: AsyncClient, mock_auth, test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test DELETE /{list_id}/collaborators/{user_id} - Non-owner cannot remove."""
    # Arrange: user1 owns list, user2 is collaborator
    list_data = await create_test_list_direct(db_conn, test_user1["id"], "List for Collab Deletion", False)
    list_id = list_data["id"]
    collaborator_id = test_user2["id"]
    await add_collaborator_direct(db_conn, list_id, collaborator_id)

    # Act: user2 (collaborator) tries to remove themselves (or another user)
    headers = {"Authorization": f"Bearer fake-token-for-{collaborator_id}"} # Auth as collaborator
    response = await client.delete(f"{API_V1_LISTS}/{list_id}/collaborators/{collaborator_id}", headers=headers)

    # Assert: Forbidden (because dependency checks for *owner*)
    assert response.status_code == status.HTTP_403_FORBIDDEN

async def test_delete_collaborator_not_found(client: AsyncClient, mock_auth, test_list1: Dict[str, Any]):
    """Test DELETE /{list_id}/collaborators/{user_id} - Collaborator not on list."""
    owner_id = test_list1["owner_id"]
    list_id = test_list1["id"]
    non_collaborator_id = 98765 # Assume this user ID doesn't exist or isn't a collaborator
    headers = {"Authorization": f"Bearer fake-token-for-{owner_id}"}

    response = await client.delete(f"{API_V1_LISTS}/{list_id}/collaborators/{non_collaborator_id}", headers=headers)
    assert response.status_code == status.HTTP_404_NOT_FOUND # CRUD delete returns False -> endpoint raises 404

# =====================================================
# Test Place within List Endpoints
# =====================================================
# test_get_places_in_list_empty already covered
# test_add_and_get_places_in_list already covered
# test_add_place_duplicate_external_id already covered
# test_update_place_notes_success already covered
# test_update_place_forbidden already covered
# test_delete_place_success already covered
# test_delete_place_forbidden already covered

async def test_add_place_list_not_found(client: AsyncClient, mock_auth, test_user1: Dict[str, Any]):
    """Test POST /{list_id}/places - List does not exist."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    non_existent_list_id = 99988
    payload = {"placeId": "ext_nonexist", "name": "Place NonExist", "address": "Addr", "latitude": 0, "longitude": 0}
    response = await client.post(f"{API_V1_LISTS}/{non_existent_list_id}/places", headers=headers, json=payload)
    assert response.status_code == status.HTTP_404_NOT_FOUND # Because get_list_and_verify_access fails

async def test_update_place_place_not_found(client: AsyncClient, mock_auth, test_list1: Dict[str, Any]):
     """Test PATCH /{list_id}/places/{place_id} - Place does not exist."""
     headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
     list_id = test_list1["id"]
     non_existent_place_id = 88877
     payload = {"notes": "Update non-existent"}
     response = await client.patch(f"{API_V1_LISTS}/{list_id}/places/{non_existent_place_id}", headers=headers, json=payload)
     assert response.status_code == status.HTTP_404_NOT_FOUND # Because crud update fails

async def test_delete_place_place_not_found(client: AsyncClient, mock_auth, test_list1: Dict[str, Any]):
    """Test DELETE /{list_id}/places/{place_id} - Place does not exist."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    non_existent_place_id = 77766
    response = await client.delete(f"{API_V1_LISTS}/{list_id}/places/{non_existent_place_id}", headers=headers)
    assert response.status_code == status.HTTP_404_NOT_FOUND # Because crud delete returns False