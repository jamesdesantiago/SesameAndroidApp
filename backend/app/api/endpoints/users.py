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
from app.schemas import user as user_schemas # Use aliased schemas

# Import Rate Limiting stuff
from slowapi import Limiter
from slowapi.util import get_remote_address

logger = logging.getLogger(__name__)
router = APIRouter()
# Limiter uses IP address by default. Add user-specific logic if needed via deps.get_current_user_id
limiter = Limiter(key_func=get_remote_address)

# Define tags for OpenAPI documentation grouping
user_tags = ["User"]
friend_tags = ["Friends"]
notification_tags = ["Notifications"]
settings_tags = ["Settings", "User"]

# === User Account & Profile Endpoints ===

@router.get("/users/me", response_model=user_schemas.UserBase, tags=user_tags)
async def read_users_me(
    current_user_record: asyncpg.Record = Depends(deps.get_current_user_record)
):
    """
    Get profile of the currently authenticated user.
    """
    # The dependency already fetched the record, just return it
    # Pydantic will automatically map based on the response_model
    return current_user_record

@router.patch("/users/me", response_model=user_schemas.UserBase, tags=user_tags)
async def update_user_me(
    profile_update: user_schemas.UserProfileUpdate,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Update the profile (display name, profile picture) for the currently authenticated user.
    """
    try:
        updated_user_record = await crud_user.update_user_profile(
            db=db, user_id=current_user_id, profile_in=profile_update
        )
        if updated_user_record is None: # Should be caught by CRUD errors, but double-check
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found during update.")
        return updated_user_record
    except crud_user.UserNotFoundError as e:
         raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except crud_user.DatabaseInteractionError as e:
        logger.error(f"DB interaction error updating profile for {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error updating profile")
    except Exception as e:
        logger.error(f"Unexpected error updating profile for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error updating profile")

@router.delete("/users/me", status_code=status.HTTP_204_NO_CONTENT, tags=user_tags)
async def delete_user_me(
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Delete the account of the currently authenticated user.
    """
    try:
        deleted = await crud_user.delete_user_account(db=db, user_id=current_user_id)
        if not deleted:
            # This shouldn't happen if get_current_user_id succeeded
            logger.error(f"Attempted to delete user {current_user_id}, but CRUD reported not found.")
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found for deletion.")
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    except crud_user.DatabaseInteractionError as e:
        logger.error(f"DB interaction error deleting account {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error deleting account.")
    except Exception as e:
        logger.error(f"Unexpected error deleting account for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error deleting account")

@router.get("/users/{user_id}", response_model=user_schemas.UserBase, tags=user_tags)
async def read_user_by_id(
    user_id: int,
    # Require authentication to view any profile for now
    requester_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get public profile information for a specific user by their ID.
    (Currently returns basic info, privacy checks might be needed).
    """
    # TODO: Implement privacy checks:
    # 1. Fetch user profile
    # 2. Fetch user's privacy settings (e.g., profile_is_public)
    # 3. If profile is not public AND requester_id != user_id, raise 403 Forbidden.
    # 4. If profile is public OR requester is the user, return profile data.

    user_record = await crud_user.get_user_by_id(db=db, user_id=user_id)
    if not user_record:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    # --- Placeholder for Privacy Check ---
    # For now, returning the profile if found, assuming basic public visibility
    # profile_is_public = user_record.get('profile_is_public', True) # Assume public if setting not present
    # if not profile_is_public and requester_id != user_id:
    #     raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="This profile is private")
    # --- End Placeholder ---

    # Pydantic will map the record to UserBase response model
    return user_record


# === User Settings Endpoints ===

@router.get("/users/me/settings", response_model=user_schemas.PrivacySettingsResponse, tags=settings_tags)
async def read_privacy_settings_me(
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get the privacy settings for the currently authenticated user.
    """
    try:
        settings_record = await crud_user.get_privacy_settings(db=db, user_id=current_user_id)
        if not settings_record:
            # Should be caught by UserNotFoundError in CRUD, but handle here too
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User settings not found.")
        # Map record to Pydantic response model
        return settings_record
    except crud_user.UserNotFoundError as e:
         raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except Exception as e:
        logger.error(f"Error fetching settings for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching settings.")


@router.patch("/users/me/settings", response_model=user_schemas.PrivacySettingsResponse, tags=settings_tags)
async def update_privacy_settings_me(
    settings_update: user_schemas.PrivacySettingsUpdate,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Update privacy settings for the currently authenticated user.
    """
    if not settings_update.model_dump(exclude_unset=True):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No settings provided for update.")
    try:
        updated_settings_record = await crud_user.update_privacy_settings(
            db=db, user_id=current_user_id, settings_in=settings_update
        )
        if not updated_settings_record:
             # Should be caught by UserNotFoundError in CRUD, but handle here too
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found for settings update.")
        # Map record to Pydantic response model
        return updated_settings_record
    except crud_user.UserNotFoundError as e:
         raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except crud_user.DatabaseInteractionError as e:
        logger.error(f"DB interaction error updating settings for {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error updating settings")
    except Exception as e:
        logger.error(f"Unexpected error updating settings for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error updating settings")


# === Existing Endpoints (Username Check, Set Username, Friends/Followers, Notifications) ===

@router.get("/users/check-username", response_model=user_schemas.UsernameCheckResponse, tags=user_tags)
@limiter.limit("7/minute")
async def check_username(
    request: Request, # For limiter state
    token_data: token_schemas.FirebaseTokenData = Depends(deps.get_verified_token_data),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    # Implementation unchanged from previous version
    try:
        _, needs_username = await crud_user.get_or_create_user_by_firebase(db=db, token_data=token_data)
        return user_schemas.UsernameCheckResponse(needsUsername=needs_username)
    except ValueError as ve:
        logger.error(f"Value error checking username for uid {token_data.uid}: {ve}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(ve))
    except HTTPException as he:
        raise he
    except Exception as e:
        logger.error(f"Unexpected error checking username for uid {token_data.uid}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error checking username status")

@router.post("/users/set-username", response_model=user_schemas.UsernameSetResponse, status_code=status.HTTP_200_OK, tags=user_tags)
@limiter.limit("2/minute")
async def set_username(
    request: Request, # For limiter state
    data: user_schemas.UsernameSet,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    # Implementation unchanged from previous version
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

@router.get("/users/following", response_model=user_schemas.PaginatedUserResponse, tags=friend_tags)
@limiter.limit("10/minute")
async def get_following(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(20, ge=1, le=100, description="Number of users per page"),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    # Implementation unchanged from previous version
    try:
        following_records, total_items = await crud_user.get_following(
            db=db, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [user_schemas.UserFollowInfo(**record, is_following=True) for record in following_records]
        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching following list for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching following list")

@router.get("/users/followers", response_model=user_schemas.PaginatedUserResponse, tags=friend_tags)
@limiter.limit("5/minute")
async def get_followers(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(20, ge=1, le=100, description="Number of users per page"),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    # Implementation unchanged from previous version
    try:
        follower_records, total_items = await crud_user.get_followers(
            db=db, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [user_schemas.UserFollowInfo(**record) for record in follower_records]
        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching followers list for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching followers list")

@router.get("/users/search", response_model=user_schemas.PaginatedUserResponse, tags=friend_tags)
@limiter.limit("30/minute")
async def search_users(
    request: Request, # For limiter state
    email: str = Query(..., min_length=1, description="Email or username fragment to search for."),
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(10, ge=1, le=50, description="Number of users per page"),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    # Implementation unchanged from previous version
    try:
        users_found_records, total_items = await crud_user.search_users(
            db=db, current_user_id=current_user_id, query=email, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [user_schemas.UserFollowInfo(**user) for user in users_found_records]
        return user_schemas.PaginatedUserResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error searching users with term '{email}' by user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error searching users")

@router.post("/users/{user_id}/follow", status_code=status.HTTP_201_CREATED, tags=friend_tags, responses={
    status.HTTP_200_OK: {"description": "Already following the user", "model": user_schemas.UsernameSetResponse},
    status.HTTP_201_CREATED: {"description": "Successfully followed the user", "model": user_schemas.UsernameSetResponse},
    status.HTTP_400_BAD_REQUEST: {"description": "Cannot follow yourself"},
    status.HTTP_404_NOT_FOUND: {"description": "User to follow not found"},
})
@limiter.limit("10/minute")
async def follow_user(
    request: Request, # For limiter state
    user_id: int, # Target user ID from path
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    # Implementation unchanged from previous version
    if current_user_id == user_id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot follow yourself")
    try:
        already_following = await crud_user.follow_user(db=db, follower_id=current_user_id, followed_id=user_id)
        if already_following:
             return JSONResponse(status_code=status.HTTP_200_OK, content={"message": "Already following this user"})
        return user_schemas.UsernameSetResponse(message="User followed")
    except crud_user.UserNotFoundError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except crud_user.DatabaseInteractionError as e:
         logger.error(f"DB interaction error during follow {current_user_id}->{user_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error processing follow request")
    except Exception as e:
        logger.error(f"Unexpected error processing follow {current_user_id}->{user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error processing follow request")

@router.delete("/users/{user_id}/follow", status_code=status.HTTP_200_OK, tags=friend_tags, response_model=user_schemas.UsernameSetResponse, responses={
    status.HTTP_200_OK: {"description": "User successfully unfollowed or was not being followed"},
    status.HTTP_204_NO_CONTENT: {"description": "User successfully unfollowed"},
    status.HTTP_404_NOT_FOUND: {"description": "User to unfollow not found"},
})
@limiter.limit("10/minute")
async def unfollow_user(
    request: Request, # For limiter state
    user_id: int, # Target user ID from path
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    # Implementation unchanged from previous version
    try:
        deleted = await crud_user.unfollow_user(db=db, follower_id=current_user_id, followed_id=user_id)
        if not deleted:
            target_exists = await crud_user.check_user_exists(db=db, user_id=user_id)
            if not target_exists:
                 raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User to unfollow not found")
            else:
                 return user_schemas.UsernameSetResponse(message="Not following this user")
        return user_schemas.UsernameSetResponse(message="User unfollowed")
    except crud_user.DatabaseInteractionError as e:
         logger.error(f"DB interaction error during unfollow {current_user_id}->{user_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error during unfollow operation")
    except Exception as e:
        logger.error(f"Unexpected error unfollowing user {user_id} by {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error unfollowing user")

@router.get("/notifications", response_model=user_schemas.PaginatedNotificationResponse, tags=notification_tags)
@limiter.limit("5/minute")
async def get_notifications(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(25, ge=1, le=100, description="Number of notifications per page"),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    # Implementation unchanged from previous version
    try:
        notification_records, total_items = await crud_user.get_user_notifications(
            db=db, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [user_schemas.NotificationItem(**n) for n in notification_records]
        return user_schemas.PaginatedNotificationResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching notifications for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching notifications")