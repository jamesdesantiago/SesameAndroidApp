# backend/tests/api/test_users_api.py

import pytest
from httpx import AsyncClient
from fastapi import status
from typing import Dict, Any, Optional, List # Added List
import asyncpg
import os
import math # For pagination checks
import asyncio # For sleep

# Import app components AFTER conftest sets environment
from app.core.config import settings
from app.schemas import user as user_schemas # For asserting response structure
from app.api import deps # For mocking dependencies if needed
from app.schemas.token import FirebaseTokenData # For mocking
from app.crud import crud_user # Import crud_user to use check_user_exists
from unittest.mock import patch, MagicMock # For mocking

from tests.utils import create_test_user, create_follow, create_notification

# Mark all tests in this file as async and needing the DB event loop
pytestmark = [pytest.mark.asyncio]

# API Prefix
API_V1 = settings.API_V1_STR

# --- Test Helper Functions ---
# (Ideally move these to a shared tests/utils.py or conftest.py,
# but included here for completeness of this file)

async def create_test_user(db_conn: asyncpg.Connection, suffix: str) -> Dict[str, Any]:
     """Creates a user for testing. Uses random suffix for uniqueness."""
     email = f"test_{suffix}_{os.urandom(3).hex()}@example.com"
     fb_uid = f"test_fb_uid_{suffix}_{os.urandom(3).hex()}"
     username = f"testuser_{suffix}_{os.urandom(3).hex()}"
     display_name = f"Test User {suffix}"
     user_id = None
     try:
         user_id = await db_conn.fetchval(
             """
             INSERT INTO users (email, firebase_uid, username, display_name, created_at, updated_at)
             VALUES ($1, $2, $3, $4, NOW(), NOW())
             ON CONFLICT (email) DO NOTHING -- Basic conflict handling for email
             RETURNING id
             """,
             email, fb_uid, username, display_name
         )
         # Refetch if conflict occurred or for verification
         if not user_id:
             user_id = await db_conn.fetchval("SELECT id FROM users WHERE email = $1", email)

         if not user_id:
             pytest.fail(f"Failed to create or find test user {email} in helper.")

         print(f"   [Helper] Created/Found User ID: {user_id} ({username})")
         return {"id": user_id, "email": email, "firebase_uid": fb_uid, "username": username, "display_name": display_name}
     except Exception as e:
          pytest.fail(f"Error in create_test_user helper for {suffix}: {e}")


async def create_follow(db_conn: asyncpg.Connection, follower_id: int, followed_id: int):
     """Creates a follow relationship."""
     try:
         await db_conn.execute(
             "INSERT INTO user_follows (follower_id, followed_id, created_at) VALUES ($1, $2, NOW()) ON CONFLICT DO NOTHING",
             follower_id, followed_id
         )
         print(f"   [Helper] Ensured Follow Exists: {follower_id} -> {followed_id}")
     except Exception as e:
          pytest.fail(f"Error in create_follow helper {follower_id}->{followed_id}: {e}")


async def create_notification(db_conn: asyncpg.Connection, user_id: int, title: str, message: str) -> int:
    """Creates a notification."""
    try:
        notif_id = await db_conn.fetchval(
            "INSERT INTO notifications (user_id, title, message, timestamp) VALUES ($1, $2, $3, NOW()) RETURNING id",
            user_id, title, message
        )
        if not notif_id: pytest.fail(f"Failed to create notification for user {user_id}")
        print(f"   [Helper] Created Notification ID: {notif_id} for User ID: {user_id}")
        return notif_id
    except Exception as e:
         pytest.fail(f"Error in create_notification helper for user {user_id}: {e}")


# =====================================================
# Test User Account & Profile Endpoints
# =====================================================
# GET /users/me
async def test_get_me_unauthenticated(client: AsyncClient):
    response = await client.get(f"{API_V1}/users/me")
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

async def test_get_me_authenticated(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1}/users/me", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["id"] == test_user1["id"]
    assert data["email"] == test_user1["email"]
    assert data["username"] == test_user1["username"]
    assert "display_name" in data
    assert "profile_picture" in data

# PATCH /users/me
async def test_update_me_success(client: AsyncClient, test_user1: Dict[str, Any], mock_auth, db_conn: asyncpg.Connection):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    new_display_name = f"Updated Name {os.urandom(2).hex()}"
    new_pic_url = f"http://example.com/pic_{os.urandom(2).hex()}.jpg"
    payload = {"displayName": new_display_name, "profilePicture": new_pic_url}

    response = await client.patch(f"{API_V1}/users/me", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["display_name"] == new_display_name
    assert data["profile_picture"] == new_pic_url

    db_user = await db_conn.fetchrow("SELECT display_name, profile_picture FROM users WHERE id = $1", test_user1["id"])
    assert db_user["display_name"] == new_display_name
    assert db_user["profile_picture"] == new_pic_url

async def test_update_me_partial(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    new_display_name = f"Partial Update Name {os.urandom(2).hex()}"
    payload = {"displayName": new_display_name}
    response = await client.patch(f"{API_V1}/users/me", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["display_name"] == new_display_name

async def test_update_me_no_data(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    payload = {}
    response = await client.patch(f"{API_V1}/users/me", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK # Assumes CRUD returns current data
    assert response.json()["id"] == test_user1["id"]

async def test_update_me_unauthenticated(client: AsyncClient):
    payload = {"displayName": "No Auth Update"}
    response = await client.patch(f"{API_V1}/users/me", json=payload)
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

# DELETE /users/me
async def test_delete_me_success(client: AsyncClient, test_user1: Dict[str, Any], mock_auth, db_conn: asyncpg.Connection):
    user_id_to_delete = test_user1["id"]
    headers = {"Authorization": f"Bearer fake-token-for-{user_id_to_delete}"}
    assert await crud_user.check_user_exists(db_conn, user_id_to_delete) is True
    response = await client.delete(f"{API_V1}/users/me", headers=headers)
    assert response.status_code == status.HTTP_204_NO_CONTENT
    assert await crud_user.check_user_exists(db_conn, user_id_to_delete) is False

async def test_delete_me_unauthenticated(client: AsyncClient):
    response = await client.delete(f"{API_V1}/users/me")
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

# GET /users/{user_id}
async def test_read_user_by_id_self(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    user_id = test_user1["id"]
    response = await client.get(f"{API_V1}/users/{user_id}", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["id"] == user_id

async def test_read_user_by_id_other_public(client: AsyncClient, test_user1: Dict[str, Any], test_user2: Dict[str, Any], mock_auth, db_conn: asyncpg.Connection):
    await db_conn.execute("UPDATE users SET profile_is_public = TRUE WHERE id = $1", test_user2["id"])
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    user_id_to_view = test_user2["id"]
    response = await client.get(f"{API_V1}/users/{user_id_to_view}", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["id"] == user_id_to_view

async def test_read_user_by_id_other_private_forbidden(client: AsyncClient, test_user1: Dict[str, Any], test_user2: Dict[str, Any], mock_auth, db_conn: asyncpg.Connection):
    await db_conn.execute("UPDATE users SET profile_is_public = FALSE WHERE id = $1", test_user2["id"])
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    user_id_to_view = test_user2["id"]
    response = await client.get(f"{API_V1}/users/{user_id_to_view}", headers=headers)
    assert response.status_code == status.HTTP_403_FORBIDDEN

async def test_read_user_by_id_not_found(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1}/users/99999", headers=headers)
    assert response.status_code == status.HTTP_404_NOT_FOUND

async def test_read_user_by_id_unauthenticated(client: AsyncClient, test_user1: Dict[str, Any]):
    response = await client.get(f"{API_V1}/users/{test_user1['id']}")
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

# GET /users/me/settings
async def test_get_me_settings_success(client: AsyncClient, test_user1: Dict[str, Any], mock_auth, db_conn: asyncpg.Connection):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    await db_conn.execute("UPDATE users SET profile_is_public=$1, lists_are_public=$2, allow_analytics=$3 WHERE id=$4", True, False, True, test_user1["id"])
    response = await client.get(f"{API_V1}/users/me/settings", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["profile_is_public"] is True
    assert data["lists_are_public"] is False
    assert data["allow_analytics"] is True

async def test_get_me_settings_unauthenticated(client: AsyncClient):
    response = await client.get(f"{API_V1}/users/me/settings")
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

# PATCH /users/me/settings
async def test_update_me_settings_success(client: AsyncClient, test_user1: Dict[str, Any], mock_auth, db_conn: asyncpg.Connection):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    payload = {"profile_is_public": False, "lists_are_public": True, "allow_analytics": False}
    response = await client.patch(f"{API_V1}/users/me/settings", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["profile_is_public"] is False
    assert data["lists_are_public"] is True
    assert data["allow_analytics"] is False
    db_settings = await db_conn.fetchrow("SELECT profile_is_public, lists_are_public, allow_analytics FROM users WHERE id = $1", test_user1["id"])
    assert db_settings["profile_is_public"] is False
    assert db_settings["lists_are_public"] is True
    assert db_settings["allow_analytics"] is False

async def test_update_me_settings_partial(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    payload = {"allow_analytics": False}
    response = await client.patch(f"{API_V1}/users/me/settings", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["allow_analytics"] is False

async def test_update_me_settings_no_data(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    payload = {}
    response = await client.patch(f"{API_V1}/users/me/settings", headers=headers, json=payload)
    assert response.status_code == status.HTTP_400_BAD_REQUEST

async def test_update_me_settings_unauthenticated(client: AsyncClient):
    payload = {"profile_is_public": False}
    response = await client.patch(f"{API_V1}/users/me/settings", json=payload)
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

# =====================================================
# Test Username Check & Set Endpoints
# =====================================================

async def test_check_username_needs_it(client: AsyncClient, test_user1: Dict[str, Any], db_conn: asyncpg.Connection, mock_auth):
    await db_conn.execute("UPDATE users SET username = NULL WHERE id = $1", test_user1["id"])
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1}/users/check-username", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    assert response.json() == {"needsUsername": True}

async def test_check_username_does_not_need_it(client: AsyncClient, test_user1: Dict[str, Any], db_conn: asyncpg.Connection, mock_auth):
    await db_conn.execute("UPDATE users SET username = $1 WHERE id = $2", test_user1["username"], test_user1["id"])
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1}/users/check-username", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    assert response.json() == {"needsUsername": False}

async def test_set_username_valid(client: AsyncClient, test_user1: Dict[str, Any], mock_auth, db_conn: asyncpg.Connection): # Renamed test_set_username_success
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    new_username = f"valid_user_{os.urandom(3).hex()}"
    payload = {"username": new_username}
    await db_conn.execute("UPDATE users SET username = NULL WHERE id = $1", test_user1["id"])
    response = await client.post(f"{API_V1}/users/set-username", headers=headers, json=payload)
    assert response.status_code == status.HTTP_200_OK
    assert response.json()["message"] == "Username set successfully"
    db_username = await db_conn.fetchval("SELECT username FROM users WHERE id = $1", test_user1["id"])
    assert db_username == new_username

async def test_set_username_too_short(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    payload = {"username": "a"}
    response = await client.post(f"{API_V1}/users/set-username", headers=headers, json=payload)
    assert response.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY

# test_set_username_invalid_chars already provided and correct

# test_set_username_conflict already provided and correct

# =====================================================
# Test Friends/Followers Endpoints
# =====================================================

async def test_get_following_pagination(client: AsyncClient, test_user1: Dict[str, Any], db_conn: asyncpg.Connection, mock_auth):
    """Test /users/following - Pagination logic."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    follower_id = test_user1["id"]
    followed_users = []
    try: # Wrap data creation in try/finally for cleanup
        for i in range(5):
            user = await create_test_user(db_conn, f"following_tgt_{i}")
            followed_users.append(user)
            await create_follow(db_conn, follower_id=follower_id, followed_id=user["id"])

        resp1 = await client.get(f"{API_V1}/users/following", headers=headers, params={"page": 1, "page_size": 2})
        assert resp1.status_code == status.HTTP_200_OK
        data1 = resp1.json()
        assert data1["total_items"] == 5
        assert data1["total_pages"] == 3
        assert len(data1["items"]) == 2
        ids1 = {item["id"] for item in data1["items"]}

        resp2 = await client.get(f"{API_V1}/users/following", headers=headers, params={"page": 2, "page_size": 2})
        assert resp2.status_code == status.HTTP_200_OK
        data2 = resp2.json()
        assert len(data2["items"]) == 2
        ids2 = {item["id"] for item in data2["items"]}

        resp3 = await client.get(f"{API_V1}/users/following", headers=headers, params={"page": 3, "page_size": 2})
        assert resp3.status_code == status.HTTP_200_OK
        data3 = resp3.json()
        assert len(data3["items"]) == 1
        ids3 = {item["id"] for item in data3["items"]}

        all_retrieved_ids = ids1.union(ids2).union(ids3)
        expected_ids = {u["id"] for u in followed_users}
        assert all_retrieved_ids == expected_ids
    finally:
        # Cleanup the created users (test_user1 cleaned by its fixture)
        for user in followed_users:
            try: await db_conn.execute("DELETE FROM users WHERE id = $1", user["id"])
            except Exception as e: print(f"Cleanup error: {e}")

async def test_get_followers_pagination_and_following_flag(client: AsyncClient, test_user1: Dict[str, Any], db_conn: asyncpg.Connection, mock_auth):
    """Test /users/followers - Pagination and check is_following flag."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    user_id = test_user1["id"]
    followers = []
    followed_back_ids = set()
    try: # Wrap data creation in try/finally
        for i in range(4):
            follower = await create_test_user(db_conn, f"follower_{i}")
            followers.append(follower)
            await create_follow(db_conn, follower_id=follower["id"], followed_id=user_id)
            if i % 2 == 0:
                 await create_follow(db_conn, follower_id=user_id, followed_id=follower["id"])
                 followed_back_ids.add(follower["id"])

        resp1 = await client.get(f"{API_V1}/users/followers", headers=headers, params={"page": 1, "page_size": 3})
        assert resp1.status_code == status.HTTP_200_OK
        data1 = resp1.json()
        assert data1["total_items"] == 4
        assert data1["total_pages"] == 2
        assert len(data1["items"]) == 3
        for item in data1["items"]: assert item["is_following"] == (item["id"] in followed_back_ids)
        ids1 = {item["id"] for item in data1["items"]}

        resp2 = await client.get(f"{API_V1}/users/followers", headers=headers, params={"page": 2, "page_size": 3})
        assert resp2.status_code == status.HTTP_200_OK
        data2 = resp2.json()
        assert len(data2["items"]) == 1
        for item in data2["items"]: assert item["is_following"] == (item["id"] in followed_back_ids)
        ids2 = {item["id"] for item in data2["items"]}

        all_retrieved_ids = ids1.union(ids2)
        expected_ids = {f["id"] for f in followers}
        assert all_retrieved_ids == expected_ids
    finally:
        for follower in followers:
             try: await db_conn.execute("DELETE FROM users WHERE id = $1", follower["id"])
             except Exception as e: print(f"Cleanup error: {e}")

async def test_search_users_pagination(client: AsyncClient, test_user1: Dict[str, Any], db_conn: asyncpg.Connection, mock_auth):
    """Test /users/search - Pagination."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    searcher_id = test_user1["id"]
    matching_users = []
    non_matching = None
    try: # Wrap data creation
        for i in range(3):
            user = await create_test_user(db_conn, f"searchme_{i}")
            matching_users.append(user)
        non_matching = await create_test_user(db_conn, "dontfindme")

        resp1 = await client.get(f"{API_V1}/users/search", headers=headers, params={"email": "searchme", "page": 1, "page_size": 2})
        assert resp1.status_code == status.HTTP_200_OK
        data1 = resp1.json()
        assert data1["total_items"] == 3
        assert data1["total_pages"] == 2
        assert len(data1["items"]) == 2
        ids1 = {item["id"] for item in data1["items"]}

        resp2 = await client.get(f"{API_V1}/users/search", headers=headers, params={"email": "searchme", "page": 2, "page_size": 2})
        assert resp2.status_code == status.HTTP_200_OK
        data2 = resp2.json()
        assert len(data2["items"]) == 1
        ids2 = {item["id"] for item in data2["items"]}

        all_retrieved_ids = ids1.union(ids2)
        expected_ids = {u["id"] for u in matching_users}
        assert all_retrieved_ids == expected_ids
        assert non_matching["id"] not in all_retrieved_ids
        assert searcher_id not in all_retrieved_ids
    finally: # Cleanup
        for user in matching_users:
             try: await db_conn.execute("DELETE FROM users WHERE id = $1", user["id"])
             except Exception as e: print(f"Cleanup error: {e}")
        if non_matching:
             try: await db_conn.execute("DELETE FROM users WHERE id = $1", non_matching["id"])
             except Exception as e: print(f"Cleanup error: {e}")


async def test_search_users_no_results(client: AsyncClient, test_user1: Dict[str, Any], mock_auth):
    """Test /users/search - No results found."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1}/users/search", headers=headers, params={"email": "willnotmatchanything"})
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["items"] == []
    assert data["total_items"] == 0

async def test_follow_user_already_following(client: AsyncClient, test_user1, test_user2, db_conn: asyncpg.Connection, mock_auth):
    """Test POST /users/{user_id}/follow - Already following returns 200 OK."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    user_to_follow_id = test_user2["id"]
    await create_follow(db_conn, follower_id=test_user1["id"], followed_id=user_to_follow_id)
    response = await client.post(f"{API_V1}/users/{user_to_follow_id}/follow", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    assert "already following" in response.json()["message"].lower()

async def test_follow_user_not_found(client: AsyncClient, test_user1, mock_auth):
    """Test POST /users/{user_id}/follow - Target user not found returns 404."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    non_existent_user_id = 99998
    response = await client.post(f"{API_V1}/users/{non_existent_user_id}/follow", headers=headers)
    assert response.status_code == status.HTTP_404_NOT_FOUND
    assert "not found" in response.json()["detail"].lower()

async def test_follow_user_self(client: AsyncClient, test_user1, mock_auth):
    """Test POST /users/{user_id}/follow - Trying to follow self returns 400."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    self_id = test_user1["id"]
    response = await client.post(f"{API_V1}/users/{self_id}/follow", headers=headers)
    assert response.status_code == status.HTTP_400_BAD_REQUEST
    assert "cannot follow yourself" in response.json()["detail"].lower()

async def test_unfollow_user_not_following(client: AsyncClient, test_user1, test_user2, mock_auth):
    """Test DELETE /users/{user_id}/follow - Not following returns 200 OK."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    user_to_unfollow_id = test_user2["id"]
    response = await client.delete(f"{API_V1}/users/{user_to_unfollow_id}/follow", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    assert "not following" in response.json()["message"].lower()

async def test_unfollow_user_not_found(client: AsyncClient, test_user1, mock_auth):
    """Test DELETE /users/{user_id}/follow - Target user not found returns 404."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    non_existent_user_id = 99997
    response = await client.delete(f"{API_V1}/users/{non_existent_user_id}/follow", headers=headers)
    assert response.status_code == status.HTTP_404_NOT_FOUND
    assert "not found" in response.json()["detail"].lower()

# =====================================================
# Test Notification Endpoints
# =====================================================

async def test_get_notifications_empty(client: AsyncClient, test_user1, mock_auth):
    """Test GET /notifications - Empty list when no notifications exist."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    response = await client.get(f"{API_V1}/notifications", headers=headers)
    assert response.status_code == status.HTTP_200_OK
    data = response.json()
    assert data["items"] == []
    assert data["total_items"] == 0

async def test_get_notifications_pagination(client: AsyncClient, test_user1, db_conn: asyncpg.Connection, mock_auth):
    """Test GET /notifications - Pagination logic."""
    headers = {"Authorization": f"Bearer fake-token-for-{test_user1['id']}"}
    user_id = test_user1["id"]
    notif_ids = []
    try: # Wrap data creation
        for i in range(5):
            nid = await create_notification(db_conn, user_id, f"Title {i}", f"Message {i}")
            notif_ids.append(nid)
            await asyncio.sleep(0.01) # Ensure distinct timestamps if ordering relies on it

        resp1 = await client.get(f"{API_V1}/notifications", headers=headers, params={"page": 1, "page_size": 3})
        assert resp1.status_code == status.HTTP_200_OK
        data1 = resp1.json()
        assert data1["total_items"] == 5
        assert data1["total_pages"] == 2
        assert len(data1["items"]) == 3
        ids1 = {item["id"] for item in data1["items"]}
        assert data1["items"][0]["id"] == notif_ids[-1] # Assuming timestamp DESC order

        resp2 = await client.get(f"{API_V1}/notifications", headers=headers, params={"page": 2, "page_size": 3})
        assert resp2.status_code == status.HTTP_200_OK
        data2 = resp2.json()
        assert len(data2["items"]) == 2
        ids2 = {item["id"] for item in data2["items"]}

        all_retrieved_ids = ids1.union(ids2)
        assert all_retrieved_ids == set(notif_ids)
    finally: # Cleanup
        # Assuming test_user1 fixture cleans up user via CASCADE or manually includes notification cleanup
        # If not, add manual delete here:
        if notif_ids:
             try: await db_conn.execute("DELETE FROM notifications WHERE id = ANY($1::int[])", notif_ids)
             except Exception as e: print(f"Cleanup error notifications: {e}")


async def test_get_notifications_unauthenticated(client: AsyncClient):
    """Test GET /notifications - Fails without authentication."""
    response = await client.get(f"{API_V1}/notifications")
    assert response.status_code == status.HTTP_401_UNAUTHORIZED

async def test_get_following_pagination(client: AsyncClient, test_user1: Dict[str, Any], db_conn: asyncpg.Connection, mock_auth):
     # ... Arrange ...
     for i in range(5):
         # Use imported helper
         user = await create_test_user(db_conn, f"following_tgt_{i}")
         followed_users.append(user)
         # Use imported helper
         await create_follow(db_conn, follower_id=follower_id, followed_id=user["id"])