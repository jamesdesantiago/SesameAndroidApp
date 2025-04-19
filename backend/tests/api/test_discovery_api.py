# backend/tests/api/test_discovery_api.py

import pytest
from httpx import AsyncClient
from fastapi import status
from typing import Dict, Any, Optional
import asyncpg

# Import app components AFTER conftest sets environment
from app.core.config import settings # To construct URLs
from app.schemas import list as list_schemas # For asserting response structure
from app.api import deps # For mocking dependencies if needed
from app.schemas.token import FirebaseTokenData # For mocking
from unittest.mock import patch

# Mark all tests in this file as async and needing the DB event loop
pytestmark = [pytest.mark.asyncio]

# API Prefix
API_V1 = settings.API_V1_STR

# Helper function to create lists directly for testing setup
async def create_test_list(
    db_conn: asyncpg.Connection,
    owner_id: int,
    name: str,
    is_private: bool,
    description: Optional[str] = None
) -> Dict[str, Any]:
    list_id = await db_conn.fetchval(
        """
        INSERT INTO lists (owner_id, name, description, is_private, created_at, updated_at)
        VALUES ($1, $2, $3, $4, NOW(), NOW()) RETURNING id
        """,
        owner_id, name, description, is_private
    )
    if not list_id:
        pytest.fail(f"Failed to create list '{name}' for owner {owner_id} during test setup.")
    # Fetch place count (optional, could be asserted separately)
    place_count = await db_conn.fetchval("SELECT COUNT(*) FROM places WHERE list_id = $1", list_id) or 0
    return {"id": list_id, "owner_id": owner_id, "name": name, "isPrivate": is_private, "place_count": place_count, "description": description}


# --- Tests for GET /public-lists ---

async def test_get_public_lists_empty(client: AsyncClient):
    """Test fetching public lists when none exist."""
    response = await client.get(f"{API_V1}/public-lists")
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["items"] == []
    assert data["total_items"] == 0
    assert data["page"] == 1

async def test_get_public_lists_success(client: AsyncClient, db_conn: asyncpg.Connection, test_user1, test_user2):
    """Test fetching public lists successfully with pagination."""
    # Arrange: Create some public and private lists
    public_list1 = await create_test_list(db_conn, test_user1["id"], "Public List 1", False)
    private_list1 = await create_test_list(db_conn, test_user1["id"], "User1 Private List", True)
    public_list2 = await create_test_list(db_conn, test_user2["id"], "Public List 2", False)

    # Act: Fetch first page
    response_page1 = await client.get(f"{API_V1}/public-lists?page=1&page_size=1")
    assert response_page1.status_code == status.HTTP_200_OK
    data1 = response_page1.json()

    # Assert: Page 1
    assert data1["total_items"] == 2 # Only public lists counted
    assert data1["total_pages"] == 2
    assert data1["page"] == 1
    assert data1["page_size"] == 1
    assert len(data1["items"]) == 1
    # Assuming default order is created_at DESC, public_list2 should be first
    assert data1["items"][0]["id"] == public_list2["id"]
    assert data1["items"][0]["name"] == public_list2["name"]
    assert data1["items"][0]["isPrivate"] is False

    # Act: Fetch second page
    response_page2 = await client.get(f"{API_V1}/public-lists?page=2&page_size=1")
    assert response_page2.status_code == status.HTTP_200_OK
    data2 = response_page2.json()

    # Assert: Page 2
    assert data2["total_items"] == 2
    assert data2["total_pages"] == 2
    assert data2["page"] == 2
    assert data2["page_size"] == 1
    assert len(data2["items"]) == 1
    assert data2["items"][0]["id"] == public_list1["id"]
    assert data2["items"][0]["isPrivate"] is False

# --- Tests for GET /search-lists ---

async def test_search_lists_unauthenticated(client: AsyncClient, db_conn: asyncpg.Connection, test_user1, test_user2):
    """Test searching lists without authentication (should only find public)."""
    # Arrange: Create lists
    pub1 = await create_test_list(db_conn, test_user1["id"], "Search Public Alpha", False)
    priv1 = await create_test_list(db_conn, test_user1["id"], "Search Private Alpha", True)
    pub2 = await create_test_list(db_conn, test_user2["id"], "Another Public Search", False)

    # Act: Search for "Search"
    response = await client.get(f"{API_V1}/search-lists?q=Search")
    assert response.status_code == status.HTTP_200_OK
    data = response.json()

    # Assert: Only public lists are found
    assert data["total_items"] == 2
    assert len(data["items"]) == 2
    found_ids = {item["id"] for item in data["items"]}
    assert found_ids == {pub1["id"], pub2["id"]} # Only public ones
    assert priv1["id"] not in found_ids

async def test_search_lists_authenticated(client: AsyncClient, db_conn: asyncpg.Connection, test_user1, test_user2, mock_auth):
    """Test searching lists while authenticated (should find public + user's private)."""
    # mock_auth mocks auth for test_user1
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}

    # Arrange: Create lists
    pub1 = await create_test_list(db_conn, test_user1["id"], "My Search Public Alpha", False, description="Contains target")
    priv1 = await create_test_list(db_conn, test_user1["id"], "My Search Private Alpha", True)
    pub2 = await create_test_list(db_conn, test_user2["id"], "Other Public Search", False)
    priv2 = await create_test_list(db_conn, test_user2["id"], "Other Private Alpha", True) # Should not be found

    # Act: Search for "Alpha"
    response = await client.get(f"{API_V1}/search-lists?q=Alpha", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()

    # Assert: Finds user1's public, user1's private, but not user2's private
    assert data["total_items"] == 2 # pub1 and priv1 match "Alpha" in name
    assert len(data["items"]) == 2
    found_ids = {item["id"] for item in data["items"]}
    assert found_ids == {pub1["id"], priv1["id"]}
    assert pub2["id"] not in found_ids
    assert priv2["id"] not in found_ids

    # Act: Search for "target" (in description)
    response_desc = await client.get(f"{API_V1}/search-lists?q=target", headers=headers)
    assert response_desc.status_code == status.HTTP_200_OK
    data_desc = response_desc.json()

    # Assert: Finds pub1 based on description search
    assert data_desc["total_items"] == 1
    assert len(data_desc["items"]) == 1
    assert data_desc["items"][0]["id"] == pub1["id"]

async def test_search_lists_no_results(client: AsyncClient, mock_auth):
    """Test searching when no lists match."""
    headers = {"Authorization": "Bearer fake-token"} # Auth needed if logic requires user_id

    response = await client.get(f"{API_V1}/search-lists?q=nonexistentquery", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["items"] == []
    assert data["total_items"] == 0

# --- Tests for GET /recent-lists ---

async def test_get_recent_lists_unauthenticated(client: AsyncClient):
    """Test getting recent lists requires authentication."""
    response = await client.get(f"{API_V1}/recent-lists")
    # This endpoint requires authentication based on its definition in discovery.py
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

async def test_get_recent_lists_success(client: AsyncClient, db_conn: asyncpg.Connection, test_user1, test_user2, mock_auth):
    """Test fetching recent lists (user's + public)."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}

    # Arrange: Create lists - order matters for recency (created_at defaults to NOW())
    # Order: priv2 (oldest), pub2, priv1, pub1 (newest)
    priv2 = await create_test_list(db_conn, test_user2["id"], "Other Private Recent", True)
    await asyncio.sleep(0.01) # Ensure distinct timestamps
    pub2 = await create_test_list(db_conn, test_user2["id"], "Other Public Recent", False)
    await asyncio.sleep(0.01)
    priv1 = await create_test_list(db_conn, test_user1["id"], "My Private Recent", True)
    await asyncio.sleep(0.01)
    pub1 = await create_test_list(db_conn, test_user1["id"], "My Public Recent", False)

    # Act: Fetch first page (size 2)
    response = await client.get(f"{API_V1}/recent-lists?page=1&page_size=2", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()

    # Assert: Should include user1's lists (pub1, priv1) and public list (pub2), ordered by creation desc
    assert data["total_items"] == 3 # pub1, priv1, pub2 are visible to user1
    assert data["total_pages"] == 2
    assert len(data["items"]) == 2
    assert data["items"][0]["id"] == pub1["id"] # Newest
    assert data["items"][1]["id"] == priv1["id"]

    # Act: Fetch second page
    response_p2 = await client.get(f"{API_V1}/recent-lists?page=2&page_size=2", headers=headers)
    assert response_p2.status_code == status.HTTP_200_OK
    data_p2 = response_p2.json()

    assert data_p2["total_items"] == 3
    assert data_p2["total_pages"] == 2
    assert len(data_p2["items"]) == 1
    assert data_p2["items"][0]["id"] == pub2["id"] # The public list from user2

async def test_get_recent_lists_empty(client: AsyncClient, test_user1, mock_auth):
    """Test fetching recent lists when user has none and none are public."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1}/recent-lists", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["items"] == []
    assert data["total_items"] == 0