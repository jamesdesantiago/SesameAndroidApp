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
from app.schemas import user as user_schemas # Needed for collaborator response
from app.api import deps # For mocking dependencies if needed
from app.schemas.token import FirebaseTokenData # For mocking
from app.crud import crud_list, crud_place # To check DB state if needed
from unittest.mock import patch

# Import helpers from utils
from tests.utils import ( # <<< IMPORT HELPERS
    create_test_list_direct,
    create_test_place_direct,
    add_collaborator_direct,
    create_test_user_direct # Assuming this is also in utils
)

# Mark all tests in this file as async and needing the DB event loop
pytestmark = [pytest.mark.asyncio]

# API Prefix from settings
API_V1 = settings.API_V1_STR
API_V1_LISTS = f"{settings.API_V1_STR}/lists" # Base path for this router

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
    # Cleanup is handled by test_user1 fixture if CASCADE is set, otherwise manual needed in fixture

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
    response = await client.get(API_V1_LISTS, headers=headers) # Get base path for user lists
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["items"] == []
    assert data["total_items"] == 0

async def test_get_user_lists_pagination(client: AsyncClient, db_conn: asyncpg.Connection, mock_auth, test_user1: Dict[str, Any], test_user2: Dict[str, Any]):
    """Test GET /lists - Pagination and ownership check."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    user1_lists = []
    user2_list = None
    try:
        for i in range(3): # User1 has 3 lists
            # Use imported helper
            lst = await create_test_list_direct(db_conn, test_user1["id"], f"U1 List {i}", i % 2 == 0)
            user1_lists.append(lst)
        # User2 has 1 list (should not be returned)
        # Use imported helper
        user2_list = await create_test_list_direct(db_conn, test_user2["id"], "U2 List 0", False)

        response1 = await client.get(API_V1_LISTS, headers=headers, params={"page": 1, "page_size": 2})
        assert response1.status_code == status.HTTP_200_OK
        data1 = response1.json()
        assert data1["total_items"] == 3
        assert data1["total_pages"] == 2
        assert len(data1["items"]) == 2
        ids1 = {item["id"] for item in data1["items"]}

        response2 = await client.get(API_V1_LISTS, headers=headers, params={"page": 2, "page_size": 2})
        assert response2.status_code == status.HTTP_200_OK
        data2 = response2.json()
        assert len(data2["items"]) == 1
        ids2 = {item["id"] for item in data2["items"]}

        all_retrieved_ids = ids1.union(ids2)
        expected_ids = {lst["id"] for lst in user1_lists}
        assert all_retrieved_ids == expected_ids
        assert user2_list["id"] not in all_retrieved_ids

    finally:
        # Manual cleanup for this test's lists if fixtures don't handle it
        for lst in user1_lists:
            try: await db_conn.execute("DELETE FROM lists WHERE id = $1", lst["id"])
            except Exception as e: print(f"Cleanup error list {lst.get('id')}: {e}")
        if user2_list:
             try: await db_conn.execute("DELETE FROM lists WHERE id = $1", user2_list["id"])
             except Exception as e: print(f"Cleanup error list {user2_list.get('id')}: {e}")


async def test_get_list_detail_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test GET /lists/{list_id} - Success for owner, includes collaborators."""
    # Arrange: Add collaborator using imported helper
    await add_collaborator_direct(db_conn, test_list1["id"], test_user2["id"])
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]

    response = await client.get(f"{API_V1_LISTS}/{list_id}", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["id"] == list_id
    assert data["name"] == test_list1["name"]
    assert data["collaborators"] == [test_user2["email"]] # Check collaborator email

async def test_get_list_detail_forbidden(client: AsyncClient, mock_auth, test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test GET /lists/{list_id} - Forbidden for non-owner/collaborator of private list."""
    list_data = await create_test_list_direct(db_conn, test_user2["id"], "Other Private List", True)
    list_id = list_data["id"]
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1_LISTS}/{list_id}", headers=headers)
    assert response.status_code == status.HTTP_403_FORBIDDEN

async def test_get_list_detail_not_found(client: AsyncClient, mock_auth, test_user1: Dict[str, Any]):
    """Test GET /lists/{list_id} - List does not exist."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1_LISTS}/99999", headers=headers)
    assert response.status_code == status.HTTP_404_NOT_FOUND

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

    db_list = await db_conn.fetchrow("SELECT name, is_private FROM lists WHERE id = $1", list_id)
    assert db_list["name"] == new_name
    assert db_list["is_private"] is True

async def test_update_list_forbidden(client: AsyncClient, mock_auth, test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test PATCH /lists/{list_id} - Non-owner cannot update."""
    list_data = await create_test_list_direct(db_conn, test_user2["id"], "Another User List", False)
    list_id = list_data["id"]
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    payload = {"name": "Hacked!"}
    response = await client.patch(f"{API_V1_LISTS}/{list_id}", headers=headers, json=payload)
    assert response.status_code == status.HTTP_403_FORBIDDEN # verify_list_ownership dependency fails

async def test_delete_list_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test DELETE /lists/{list_id} - Success."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    await create_test_place_direct(db_conn, list_id, "Place in deleted list", "Addr", "ext_del_list")
    assert await db_conn.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)

    del_response = await client.delete(f"{API_V1_LISTS}/{list_id}", headers=headers)
    assert del_response.status_code == status.HTTP_204_NO_CONTENT
    assert not await db_conn.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)

async def test_delete_list_forbidden(client: AsyncClient, mock_auth, test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test DELETE /lists/{list_id} - Non-owner cannot delete."""
    list_data = await create_test_list_direct(db_conn, test_user2["id"], "Another User List To Delete", False)
    list_id = list_data["id"]
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.delete(f"{API_V1_LISTS}/{list_id}", headers=headers)
    assert response.status_code == status.HTTP_403_FORBIDDEN # verify_list_ownership dependency fails

# =====================================================
# Test Collaborator Endpoints
# =====================================================
async def test_add_collaborator_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], test_user2: Dict[str, Any]):
    """Test POST /{list_id}/collaborators - Success adding."""
    owner_id = test_list1["owner_id"]
    list_id = test_list1["id"]
    collaborator_email = test_user2["email"]
    headers = {"Authorization": f"Bearer fake-token-for-{owner_id}"}
    payload = {"email": collaborator_email}

    response = await client.post(f"{API_V1_LISTS}/{list_id}/collaborators", headers=headers, json=payload)
    assert response.status_code == status.HTTP_201_CREATED
    assert response.json()["message"] == "Collaborator added"

    # Verify by getting list details
    detail_response = await client.get(f"{API_V1_LISTS}/{list_id}", headers=headers) # Owner gets details
    assert collaborator_email in detail_response.json()["collaborators"]

async def test_add_collaborator_already_exists(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test POST /{list_id}/collaborators - Collaborator already present."""
    owner_id = test_list1["owner_id"]
    list_id = test_list1["id"]
    collaborator_email = test_user2["email"]
    await add_collaborator_direct(db_conn, list_id, test_user2["id"]) # Add first
    headers = {"Authorization": f"Bearer fake-token-for-{owner_id}"}
    payload = {"email": collaborator_email}

    response = await client.post(f"{API_V1_LISTS}/{list_id}/collaborators", headers=headers, json=payload)
    assert response.status_code == status.HTTP_409_CONFLICT

async def test_delete_collaborator_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test DELETE /{list_id}/collaborators/{user_id} - Success."""
    owner_id = test_list1["owner_id"]
    list_id = test_list1["id"]
    collaborator_id_to_remove = test_user2["id"]
    await add_collaborator_direct(db_conn, list_id, collaborator_id_to_remove) # Add first
    assert await db_conn.fetchval("SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2", list_id, collaborator_id_to_remove)

    headers = {"Authorization": f"Bearer fake-token-for-{owner_id}"}
    response = await client.delete(f"{API_V1_LISTS}/{list_id}/collaborators/{collaborator_id_to_remove}", headers=headers)
    assert response.status_code == status.HTTP_204_NO_CONTENT
    assert not await db_conn.fetchval("SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2", list_id, collaborator_id_to_remove)

async def test_delete_collaborator_forbidden(client: AsyncClient, test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test DELETE /{list_id}/collaborators/{user_id} - Non-owner cannot remove."""
    list_data = await create_test_list_direct(db_conn, test_user1["id"], "List for Collab Deletion", False)
    list_id = list_data["id"]
    await add_collaborator_direct(db_conn, list_id, test_user2["id"])

    # Mock auth for user2 (the collaborator)
    mock_token_user2 = FirebaseTokenData(uid=test_user2["firebase_uid"], email=test_user2["email"])
    async def override_auth(): return mock_token_user2
    app.dependency_overrides[deps.get_verified_token_data] = override_auth
    headers = {"Authorization": f"Bearer fake-token-for-{test_user2['id']}"} # Auth as collaborator

    response = await client.delete(f"{API_V1_LISTS}/{list_id}/collaborators/{test_user2['id']}", headers=headers)
    app.dependency_overrides.clear() # Cleanup mock
    assert response.status_code == status.HTTP_403_FORBIDDEN

async def test_delete_collaborator_not_found(client: AsyncClient, mock_auth, test_list1: Dict[str, Any]):
    """Test DELETE /{list_id}/collaborators/{user_id} - Collaborator not on list."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    response = await client.delete(f"{API_V1_LISTS}/{list_id}/collaborators/98765", headers=headers)
    assert response.status_code == status.HTTP_404_NOT_FOUND

# =====================================================
# Test Place within List Endpoints
# =====================================================
async def test_get_places_in_list_empty(client: AsyncClient, mock_auth, test_list1: Dict[str, Any]):
    """Test GET /{list_id}/places - Empty list."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    response = await client.get(f"{API_V1_LISTS}/{list_id}/places", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["items"] == []
    assert data["total_items"] == 0

async def test_add_and_get_places_in_list(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test POST + GET /{list_id}/places - Add and retrieve places with pagination."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    places_created = []
    try:
        # Arrange: Add two places
        payload1 = {"placeId": "ext1", "name": f"Place A {os.urandom(2).hex()}", "address": "1 Main St", "latitude": 10.0, "longitude": -10.0}
        payload2 = {"placeId": "ext2", "name": f"Place B {os.urandom(2).hex()}", "address": "2 Side St", "latitude": 20.0, "longitude": -20.0}
        add_resp1 = await client.post(f"{API_V1_LISTS}/{list_id}/places", headers=headers, json=payload1)
        assert add_resp1.status_code == status.HTTP_201_CREATED
        places_created.append(add_resp1.json()["id"])
        add_resp2 = await client.post(f"{API_V1_LISTS}/{list_id}/places", headers=headers, json=payload2)
        assert add_resp2.status_code == status.HTTP_201_CREATED
        places_created.append(add_resp2.json()["id"])

        # Act & Assert: Get page 1, size 1
        response_p1 = await client.get(f"{API_V1_LISTS}/{list_id}/places", headers=headers, params={"page": 1, "page_size": 1})
        assert response_p1.status_code == status.HTTP_200_OK
        data_p1 = response_p1.json()
        assert data_p1["total_items"] == 2
        assert data_p1["total_pages"] == 2
        assert len(data_p1["items"]) == 1
        assert data_p1["items"][0]["id"] == places_created[-1] # Assuming newest first

        # Act & Assert: Get page 2, size 1
        response_p2 = await client.get(f"{API_V1_LISTS}/{list_id}/places", headers=headers, params={"page": 2, "page_size": 1})
        assert response_p2.status_code == status.HTTP_200_OK
        data_p2 = response_p2.json()
        assert len(data_p2["items"]) == 1
        assert data_p2["items"][0]["id"] == places_created[0] # Assuming oldest second
    finally:
        # Cleanup places if test_list1 fixture doesn't cascade
        if places_created: await db_conn.execute("DELETE FROM places WHERE id = ANY($1::int[])", places_created)


async def test_add_place_duplicate_external_id(client: AsyncClient, mock_auth, test_list1: Dict[str, Any]):
    """Test POST /{list_id}/places - Duplicate external place ID returns 409."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    payload = {"placeId": f"ext_dup_{os.urandom(3).hex()}", "name": "Duplicate Place", "address": "1 Dup St", "latitude": 1, "longitude": 1}
    add_resp1 = await client.post(f"{API_V1_LISTS}/{list_id}/places", headers=headers, json=payload)
    assert add_resp1.status_code == status.HTTP_201_CREATED
    add_resp2 = await client.post(f"{API_V1_LISTS}/{list_id}/places", headers=headers, json=payload)
    assert add_resp2.status_code == status.HTTP_409_CONFLICT

async def test_update_place_notes_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], db_conn: asyncpg.Connection):
    """Test PATCH /{list_id}/places/{place_id} - Success updating notes."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
    list_id = test_list1["id"]
    place_data = await create_test_place_direct(db_conn, list_id, "Place To Update Notes", "Addr", f"ext_upd_notes_{os.urandom(3).hex()}")
    place_db_id = place_data["id"]
    new_notes = "These are the final updated notes."
    payload = {"notes": new_notes}

    response = await client.patch(f"{API_V1_LISTS}/{list_id}/places/{place_db_id}", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["id"] == place_db_id
    assert data["notes"] == new_notes

async def test_update_place_forbidden(client: AsyncClient, test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
     """Test PATCH /{list_id}/places/{place_id} - Forbidden for non-owner/collaborator."""
     list_data = await create_test_list_direct(db_conn, test_user2["id"], "List Other User Update Place", False)
     place_data = await create_test_place_direct(db_conn, list_data["id"], "Other Place", "Addr", f"ext_other_upd_{os.urandom(3).hex()}")
     list_id = list_data["id"]
     place_db_id = place_data["id"]

     # Mock auth for user1
     mock_token_user1 = FirebaseTokenData(uid=test_user1["firebase_uid"], email=test_user1["email"])
     async def override_auth(): return mock_token_user1
     app.dependency_overrides[deps.get_verified_token_data] = override_auth
     headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}

     payload = {"notes": "Attempted update"}
     response = await client.patch(f"{API_V1_LISTS}/{list_id}/places/{place_db_id}", headers=headers, json=payload)
     app.dependency_overrides.clear() # Cleanup mock
     assert response.status_code == status.HTTP_403_FORBIDDEN

async def test_delete_place_success(client: AsyncClient, mock_auth, test_list1: Dict[str, Any], db_conn: asyncpg.Connection):
     """Test DELETE /{list_id}/places/{place_id} - Success."""
     headers = {"Authorization": f"Bearer fake-token-for-{test_list1['owner_id']}"}
     list_id = test_list1["id"]
     place_data = await create_test_place_direct(db_conn, list_id, "Place To Delete", "Addr", f"ext_del_{os.urandom(3).hex()}")
     place_db_id = place_data["id"]
     assert await db_conn.fetchval("SELECT 1 FROM places WHERE id=$1", place_db_id)

     del_response = await client.delete(f"{API_V1_LISTS}/{list_id}/places/{place_db_id}", headers=headers)
     assert del_response.status_code == status.HTTP_204_NO_CONTENT
     assert not await db_conn.fetchval("SELECT 1 FROM places WHERE id=$1", place_db_id)

async def test_delete_place_forbidden(client: AsyncClient, test_user1: Dict[str, Any], test_user2: Dict[str, Any], db_conn: asyncpg.Connection):
     """Test DELETE /{list_id}/places/{place_id} - Forbidden for non-owner/collaborator."""
     list_data = await create_test_list_direct(db_conn, test_user2["id"], "List Other User Delete Place", False)
     place_data = await create_test_place_direct(db_conn, list_data["id"], "Other Place Del", "Addr", f"ext_other_del_{os.urandom(3).hex()}")
     list_id = list_data["id"]
     place_db_id = place_data["id"]

     # Mock auth for user1
     mock_token_user1 = FirebaseTokenData(uid=test_user1["firebase_uid"], email=test_user1["email"])
     async def override_auth(): return mock_token_user1
     app.dependency_overrides[deps.get_verified_token_data] = override_auth
     headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}

     response = await client.delete(f"{API_V1_LISTS}/{list_id}/places/{place_db_id}", headers=headers)
     app.dependency_overrides.clear()
     assert response.status_code == status.HTTP_403_FORBIDDEN