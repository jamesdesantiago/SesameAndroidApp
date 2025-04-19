# backend/app/api/endpoints/users.py
import logging
import math
from typing import List

import asyncpg
from fastapi import (APIRouter, Depends, HTTPException, Header, Query, Request,
                     Response, status)
# Using fastapi.Response and status directly
from fastapi.responses import JSONResponse

# Import dependencies, schemas, crud functions
from app.api import deps
from app.crud import crud_user
from app.schemas import token as token_schemas
from app.schemas import user as user_schemas

# Import Rate Limiting stuff
from slowapi import Limiter
from slowapi.util import get_remote_address

logger = logging.getLogger(__name__)
router = APIRouter()
# Limiter uses IP address by default. Add user-specific logic if needed via deps.get_current_user_id
limiter = Limiter(key_func=get_remote_address)

# Define tags for OpenAPI documentation grouping
tags = ["User", "Friends", "Notifications"]

# === User Specific Endpoints ===

@router.get("/users/check-username", response_model=user_schemas.UsernameCheckResponse, tags=["User"])
@limiter.limit("7/minute")
async def check_username(
    request: Request, # For limiter state
    token_data: token_schemas.FirebaseTokenData = Depends(deps.get_verified_token_data),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Checks if the authenticated user needs to set a username.
    Returns `{"needsUsername": true}` or `{"needsUsername": false}`.
    """
    try:
        _, needs_username = await crud_user.get_or_create_user_by_firebase(db=db, token_data=token_data)
        return user_schemas.UsernameCheckResponse(needsUsername=needs_username)
    except ValueError as ve: # Catch specific errors from CRUD if needed
        logger.error(f"Value error checking username for uid {token_data.uid}: {ve}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(ve))
    except HTTPException as he:
        raise he # Re-raise exceptions from dependencies
    except Exception as e:
        logger.error(f"Unexpected error checking username for uid {token_data.uid}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error checking username status")

@router.post("/users/set-username", response_model=user_schemas.UsernameSetResponse, status_code=status.HTTP_200_OK, tags=["User"])
@limiter.limit("2/minute")
async def set_username(
    request: Request, # For limiter state
    data: user_schemas.UsernameSet,
    current_user_id: int = Depends(deps.get_current_user_id), # Gets user ID from token
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Sets the username for the authenticated user. Username must be unique (case-insensitive).
    """
    try:
        await crud_user.set_user_username(db=db, user_id=current_user_id, username=data.username)
        return user_schemas.UsernameSetResponse(message="Username set successfully")
    except crud_user.UsernameAlreadyExistsError as e:
         raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    except crud_user.UserNotFoundError as e:
         raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except crud_user.DatabaseInteractionError as e:
         logger.error(f"DB interaction error setting username for {current_user_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error setting username")
    except HTTPException as he:
        raise he
    except Exception as e:
        logger.error(f"Unexpected error setting username for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error setting username")

# === Friends/Followers Endpoints ===

@router.get("/users/following", response_model=user_schemas.PaginatedUserResponse, tags=["Friends"])
@limiter.limit("10/minute")
async def get_following(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(20, ge=1, le=100, description="Number of users per page"),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get the list of users the authenticated user is following (paginated).
    """
    try:
        following_records, total_items = await crud_user.get_following(
            db=db, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        # Map DB records to response schema. Since this is the "following" list, is_following is always True.
        items = [user_schemas.UserFollowInfo(**record, is_following=True) for record in following_records]

        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching following list for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching following list")


@router.get("/users/followers", response_model=user_schemas.PaginatedUserResponse, tags=["Friends"])
@limiter.limit("5/minute")
async def get_followers(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(20, ge=1, le=100, description="Number of users per page"),
    current_user_id: int = Depends(deps.get_current_user_id), # Needed to check follow-back status
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get the list of users following the authenticated user (paginated).
    Includes whether the authenticated user is following each follower back (`is_following` field).
    """
    try:
        follower_records, total_items = await crud_user.get_followers(
            db=db, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        # Map DB records to response schema - crud_user.get_followers returns 'is_following'
        items = [user_schemas.UserFollowInfo(**record) for record in follower_records]

        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching followers list for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching followers list")


@router.get("/users/search", response_model=user_schemas.PaginatedUserResponse, tags=["Friends"])
@limiter.limit("30/minute")
async def search_users(
    request: Request, # For limiter state
    email: str = Query(..., min_length=1, description="Email or username fragment to search for."), # Parameter name kept as 'email'
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(10, ge=1, le=50, description="Number of users per page"),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Search for users by email or username fragment (paginated).
    Excludes the authenticated user and indicates follow status relative to the searcher.
    """
    try:
        users_found_records, total_items = await crud_user.search_users(
            db=db, current_user_id=current_user_id, query=email, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        # Map DB records - crud_user.search_users returns 'is_following'
        items = [user_schemas.UserFollowInfo(**user) for user in users_found_records]

        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error searching users with term '{email}' by user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error searching users")

@router.post("/users/{user_id}/follow", status_code=status.HTTP_201_CREATED, tags=["Friends"], responses={
    status.HTTP_200_OK: {"description": "Already following the user", "model": user_schemas.UsernameSetResponse}, # Use appropriate schema
    status.HTTP_201_CREATED: {"description": "Successfully followed the user", "model": user_schemas.UsernameSetResponse}, # Use appropriate schema
    status.HTTP_400_BAD_REQUEST: {"description": "Cannot follow yourself"},
    status.HTTP_404_NOT_FOUND: {"description": "User to follow not found"},
})
@limiter.limit("10/minute")
async def follow_user(
    request: Request, # For limiter state
    user_id: int, # Target user ID from path
    current_user_id: int = Depends(deps.get_current_user_id), # Follower ID
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Follow a target user specified by `user_id`.
    Returns 201 if newly followed, 200 if already following.
    """
    if current_user_id == user_id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot follow yourself")
    try:
        already_following = await crud_user.follow_user(db=db, follower_id=current_user_id, followed_id=user_id)
        if already_following:
             # Use JSONResponse for non-default status codes with bodies
             return JSONResponse(status_code=status.HTTP_200_OK, content={"message": "Already following this user"})
        # Default return uses status_code from decorator
        return user_schemas.UsernameSetResponse(message="User followed")
    except crud_user.UserNotFoundError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except crud_user.DatabaseInteractionError as e:
         logger.error(f"DB interaction error during follow {current_user_id}->{user_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error processing follow request")
    except Exception as e:
        logger.error(f"Unexpected error processing follow {current_user_id}->{user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error processing follow request")


@router.delete("/users/{user_id}/follow", status_code=status.HTTP_200_OK, tags=["Friends"], response_model=user_schemas.UsernameSetResponse, responses={
    status.HTTP_200_OK: {"description": "User successfully unfollowed or was not being followed"},
    status.HTTP_204_NO_CONTENT: {"description": "User successfully unfollowed"},
    status.HTTP_404_NOT_FOUND: {"description": "User to unfollow not found"},
})
@limiter.limit("10/minute")
async def unfollow_user(
    request: Request, # For limiter state
    user_id: int, # Target user ID from path
    current_user_id: int = Depends(deps.get_current_user_id), # Follower ID
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Unfollow a target user specified by `user_id`.
    Returns 200 OK whether unfollowed or not currently following.
    (Optionally could return 204 on successful unfollow).
    """
    try:
        deleted = await crud_user.unfollow_user(db=db, follower_id=current_user_id, followed_id=user_id)
        if not deleted:
            # Check if target user exists to differentiate 404 user vs 404 relationship
            target_exists = await crud_user.check_user_exists(db=db, user_id=user_id)
            if not target_exists:
                 raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User to unfollow not found")
            else:
                 # Still return 200 OK with appropriate message if not following
                 return user_schemas.UsernameSetResponse(message="Not following this user")
        # Return 200 OK on successful delete
        return user_schemas.UsernameSetResponse(message="User unfollowed")
        # Or optionally return 204 No Content:
        # return Response(status_code=status.HTTP_204_NO_CONTENT)
    except crud_user.DatabaseInteractionError as e:
         logger.error(f"DB interaction error during unfollow {current_user_id}->{user_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error during unfollow operation")
    except Exception as e:
        logger.error(f"Unexpected error unfollowing user {user_id} by {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error unfollowing user")


# === Notification Endpoints ===
@router.get("/notifications", response_model=user_schemas.PaginatedNotificationResponse, tags=["Notifications"])
@limiter.limit("5/minute") # Adjusted limit example
async def get_notifications(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(25, ge=1, le=100, description="Number of notifications per page"),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get the authenticated user's notifications (paginated).
    """
    try:
        notification_records, total_items = await crud_user.get_user_notifications(
            db=db, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        # Map DB records to response schema
        items = [user_schemas.NotificationItem(**n) for n in notification_records]

        return user_schemas.PaginatedNotificationResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching notifications for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching notifications")

# TODO: Add other user-related endpoints if needed:
# - GET /users/me (Get current user profile)
# - PATCH /users/me (Update current user profile)
# - DELETE /users/me (Delete current user account)
# - GET /users/{user_id} (Get another user's profile - check privacy)
# - GET /users/me/settings (Get privacy settings)
# - PATCH /users/me/settings (Update privacy settings)