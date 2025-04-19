# backend/app/api/endpoints/users.py
import logging
import math
from typing import List # Import List

import asyncpg
from fastapi import APIRouter, Depends, HTTPException, Header, Query, Response, status, Request

# Import dependencies, schemas, crud functions
from app.api import deps
from app.schemas import user as user_schemas
from app.schemas import token as token_schemas
from app.crud import crud_user

# Import Rate Limiting stuff
from slowapi import Limiter
from slowapi.util import get_remote_address

logger = logging.getLogger(__name__)
router = APIRouter()
limiter = Limiter(key_func=get_remote_address) # Uses IP address

# Define tags for OpenAPI documentation
tags = ["User", "Friends", "Notifications"]

# === User Specific Endpoints ===

@router.get("/users/check-username", response_model=user_schemas.UsernameCheckResponse, tags=["User"])
@limiter.limit("7/minute")
async def check_username(
    request: Request, # Inject request for limiter
    token_data: token_schemas.FirebaseTokenData = Depends(deps.get_verified_token_data),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Checks if the authenticated user needs to set a username.
    """
    try:
        _, needs_username = await crud_user.get_or_create_user_by_firebase(db=db, token_data=token_data)
        return user_schemas.UsernameCheckResponse(needsUsername=needs_username)
    except HTTPException as he:
        raise he # Re-raise exceptions from dependencies
    except Exception as e:
        logger.error(f"Unexpected error checking username for uid {token_data.uid}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error checking username")

@router.post("/users/set-username", response_model=user_schemas.UsernameSetResponse, status_code=status.HTTP_200_OK, tags=["User"])
@limiter.limit("2/minute")
async def set_username(
    request: Request, # Inject request for limiter
    data: user_schemas.UsernameSet,
    current_user_id: int = Depends(deps.get_current_user_id), # Gets user ID from token
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Sets the username for the authenticated user.
    """
    try:
        await crud_user.set_user_username(db=db, user_id=current_user_id, username=data.username)
        return user_schemas.UsernameSetResponse(message="Username set successfully")
    except crud_user.UsernameAlreadyExistsError:
         raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username already taken")
    except crud_user.UserNotFoundError:
         # Should not happen if get_current_user_id worked, but handle defensively
         raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found for update")
    except HTTPException as he:
        raise he
    except Exception as e:
        logger.error(f"Unexpected error setting username for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error setting username")


# === Friends/Followers Endpoints ===

@router.get("/users/following", response_model=user_schemas.PaginatedUserResponse, tags=["Friends"])
@limiter.limit("10/minute")
async def get_following(
    request: Request,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
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
        # Map DB records to response schema - Assuming crud returns necessary fields including 'id', 'email', 'username'
        items = [user_schemas.UserFollowInfo(**record, is_following=True) for record in following_records]

        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching following for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching following list")


@router.get("/users/followers", response_model=user_schemas.PaginatedUserResponse, tags=["Friends"])
@limiter.limit("5/minute")
async def get_followers(
    request: Request,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    current_user_id: int = Depends(deps.get_current_user_id), # Needed to check follow-back status
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get the list of users following the authenticated user (paginated).
    Includes whether the authenticated user is following each follower back.
    """
    try:
        follower_records, total_items = await crud_user.get_followers(
            db=db, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        # Map DB records to response schema - crud_user.get_followers should return 'is_following' field now
        items = [user_schemas.UserFollowInfo(**record) for record in follower_records]

        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching followers for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error fetching followers list")


@router.get("/users/search", response_model=user_schemas.PaginatedUserResponse, tags=["Friends"])
@limiter.limit("30/minute")
async def search_users(
    request: Request,
    email: str = Query(..., min_length=1, description="Email or username fragment to search for."), # Parameter name kept as 'email'
    page: int = Query(1, ge=1),
    page_size: int = Query(10, ge=1, le=50),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Search for users by email or username fragment (paginated).
    Excludes the authenticated user and indicates follow status.
    """
    try:
        users_found_records, total_items = await crud_user.search_users(
            db=db, current_user_id=current_user_id, query=email, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        # Map DB records - crud_user.search_users should return 'is_following' field
        items = [user_schemas.UserFollowInfo(**user) for user in users_found_records]

        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error searching users with term '{email}': {e}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error searching users")

@router.post("/users/{user_id}/follow", status_code=status.HTTP_201_CREATED, tags=["Friends"], responses={
    200: {"description": "Already following the user"},
    201: {"description": "Successfully followed the user"}
})
@limiter.limit("10/minute")
async def follow_user(
    request: Request,
    user_id: int, # Target user ID
    current_user_id: int = Depends(deps.get_current_user_id), # Follower ID
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Follow a target user. Returns 201 if newly followed, 200 if already following.
    """
    if current_user_id == user_id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot follow yourself")
    try:
        already_following = await crud_user.follow_user(db=db, follower_id=current_user_id, followed_id=user_id)
        if already_following:
             # Use JSONResponse to set status code and body
             return JSONResponse(status_code=status.HTTP_200_OK, content={"message": "Already following this user"})
        # Default return (FastAPI handles 201 based on decorator)
        return {"message": "User followed"}
    except crud_user.UserNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User to follow not found")
    except Exception as e:
        logger.error(f"Error processing follow request from {current_user_id} to {user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error processing follow request")


@router.delete("/users/{user_id}/follow", status_code=status.HTTP_200_OK, tags=["Friends"], responses={
    200: {"description": "User successfully unfollowed or was not being followed"},
    204: {"description": "User successfully unfollowed (alternative success code)"}
})
@limiter.limit("10/minute")
async def unfollow_user(
    request: Request,
    user_id: int, # Target user ID
    current_user_id: int = Depends(deps.get_current_user_id), # Follower ID
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Unfollow a target user. Returns 200 OK whether unfollowed or not currently following.
    """
    try:
        deleted = await crud_user.unfollow_user(db=db, follower_id=current_user_id, followed_id=user_id)
        if not deleted:
            target_exists = await crud_user.check_user_exists(db=db, user_id=user_id)
            if not target_exists:
                 raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User to unfollow not found")
            else:
                 # Return 200 OK with message even if not following
                 return {"message": "Not following this user"}
        # Return 200 OK on successful delete
        return {"message": "User unfollowed"}
        # Or optionally return 204 No Content:
        # return Response(status_code=status.HTTP_204_NO_CONTENT)
    except Exception as e:
        logger.error(f"Error unfollowing user {user_id} by {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error unfollowing user")


# === Notification Endpoints ===
@router.get("/notifications", response_model=user_schemas.PaginatedNotificationResponse, tags=["Notifications"])
@limiter.limit("2/minute")
async def get_notifications(
    request: Request,
    page: int = Query(1, ge=1),
    page_size: int = Query(25, ge=1, le=100),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get the authenticated user's notifications (paginated).
    """
    try:
        # Assuming get_user_notifications exists in crud_user or a crud_notification module
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

# Add endpoints for marking notifications read/deleting if needed