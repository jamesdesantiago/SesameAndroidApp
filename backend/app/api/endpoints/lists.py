# backend/app/api/endpoints/lists.py
import logging
import math
from typing import List # Import List

import asyncpg
from fastapi import APIRouter, Depends, HTTPException, Header, Query, Response, status, Request

# Import dependencies, schemas, crud functions
from app.api import deps
# Alias schemas for clarity
from app.schemas import list as list_schemas
from app.schemas import place as place_schemas
from app.schemas import user as user_schemas
from app.schemas import token as token_schemas
# Import specific CRUD functions needed
from app.crud import crud_list, crud_place, crud_user

# Import Rate Limiting stuff
from slowapi import Limiter
from slowapi.util import get_remote_address

logger = logging.getLogger(__name__)
router = APIRouter()
limiter = Limiter(key_func=get_remote_address) # Or get from app state

# Define tags for OpenAPI documentation
list_tags = ["Lists"]
place_tags = ["Places", "Lists"] # Places within lists
collab_tags = ["Collaborators", "Lists"]

# === List CRUD ===
@router.post("", response_model=list_schemas.ListDetailResponse, status_code=status.HTTP_201_CREATED, tags=list_tags)
@limiter.limit("5/minute")
async def create_list(
    request: Request,
    list_data: list_schemas.ListCreate,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Create a new list for the authenticated user.
    """
    try:
        created_list_record = await crud_list.create_list(db=db, list_in=list_data, owner_id=current_user_id)
        return list_schemas.ListDetailResponse(
            id=created_list_record['id'],
            name=created_list_record['name'],
            description=created_list_record['description'],
            isPrivate=created_list_record['is_private'],
            collaborators=[] # New list
        )
    except Exception as e:
        logger.error(f"Error creating list for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error creating list")


@router.get("", response_model=list_schemas.PaginatedListResponse, tags=list_tags)
@limiter.limit("15/minute")
async def get_lists(
    request: Request,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get lists owned by the authenticated user (paginated).
    """
    try:
        list_records, total_items = await crud_list.get_user_lists_paginated(
            db=db, owner_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [list_schemas.ListViewResponse(**lst) for lst in list_records]

        return list_schemas.PaginatedListResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching lists for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching lists")


@router.get("/{list_id}", response_model=list_schemas.ListDetailResponse, tags=list_tags)
@limiter.limit("15/minute")
async def get_list_detail(
    request: Request,
    list_id: int, # From path parameter
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get details (metadata and collaborators) for a specific list.
    Requires ownership or collaboration access.
    """
    try:
        await crud_list.check_list_access(db=db, list_id=list_id, user_id=current_user_id)
        list_details_dict = await crud_list.get_list_details(db=db, list_id=list_id)
        if not list_details_dict:
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
        return list_schemas.ListDetailResponse(**list_details_dict)
    except crud_list.ListNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to this list")
    except Exception as e:
        logger.error(f"Error fetching detail for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching list metadata")


@router.patch("/{list_id}", response_model=list_schemas.ListDetailResponse, tags=list_tags)
@limiter.limit("10/minute")
async def update_list(
    request: Request,
    list_id: int, # From path
    update_data: list_schemas.ListUpdate,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Update a list's name or privacy status. Requires ownership.
    """
    if not update_data.model_dump(exclude_unset=True):
         raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No update fields provided")
    try:
        await crud_list.check_list_ownership(db=db, list_id=list_id, user_id=current_user_id)
        updated_list_dict = await crud_list.update_list(db=db, list_id=list_id, list_in=update_data)
        if not updated_list_dict: # Should not happen if owner check passed
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found for update")
        return list_schemas.ListDetailResponse(**updated_list_dict)
    except crud_list.ListNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError: # check_list_ownership raises this
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized for this list")
    except Exception as e:
        logger.error(f"Error updating list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error updating list")


@router.delete("/{list_id}", status_code=status.HTTP_204_NO_CONTENT, tags=list_tags)
@limiter.limit("10/minute")
async def delete_list(
    request: Request,
    list_id: int, # From path
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Delete a list. Requires ownership.
    """
    try:
        await crud_list.check_list_ownership(db=db, list_id=list_id, user_id=current_user_id)
        deleted = await crud_list.delete_list(db=db, list_id=list_id)
        if not deleted: # Should not happen if owner check passed
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found for deletion")
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    except crud_list.ListNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError: # check_list_ownership raises this
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized for this list")
    except Exception as e:
        logger.error(f"Error deleting list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error deleting list")

# === Collaborators ===
@router.post("/{list_id}/collaborators", status_code=status.HTTP_201_CREATED, response_model=user_schemas.UsernameSetResponse, tags=collab_tags)
@limiter.limit("20/minute")
async def add_collaborator(
    request: Request,
    list_id: int, # From path
    collaborator: list_schemas.CollaboratorAdd,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Add a collaborator (by email) to a list. Requires list ownership.
    """
    try:
        await crud_list.check_list_ownership(db=db, list_id=list_id, user_id=current_user_id)
        await crud_list.add_collaborator_to_list(db=db, list_id=list_id, collaborator_email=collaborator.email)
        return user_schemas.UsernameSetResponse(message="Collaborator added")
    except crud_list.ListNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the list owner can add collaborators")
    except crud_list.CollaboratorNotFoundError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except crud_list.CollaboratorAlreadyExistsError as e:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    except Exception as e:
        logger.error(f"Error adding collaborator {collaborator.email} to list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error adding collaborator")

# TODO: Add DELETE /lists/{list_id}/collaborators/{user_id} endpoint

# === Places within this List ===
@router.get("/{list_id}/places", response_model=place_schemas.PaginatedPlaceResponse, tags=place_tags)
@limiter.limit("10/minute")
async def get_places_in_list(
    request: Request,
    list_id: int, # From path
    page: int = Query(1, ge=1),
    page_size: int = Query(30, ge=1, le=100),
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get places within a specific list (paginated).
    Requires ownership or collaboration access.
    """
    try:
        await crud_list.check_list_access(db=db, list_id=list_id, user_id=current_user_id)
        place_records, total_items = await crud_place.get_places_by_list_id_paginated(
            db=db, list_id=list_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [place_schemas.PlaceItem(**p) for p in place_records]

        return place_schemas.PaginatedPlaceResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except crud_list.ListNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to this list")
    except Exception as e:
        logger.error(f"Error fetching places for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching places")


@router.post("/{list_id}/places", response_model=place_schemas.PlaceItem, status_code=status.HTTP_201_CREATED, tags=place_tags)
@limiter.limit("40/minute")
async def add_place_to_list(
    request: Request,
    list_id: int, # From path
    place: place_schemas.PlaceCreate,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Add a new place to a specific list.
    Requires ownership or collaboration access.
    """
    try:
        await crud_list.check_list_access(db=db, list_id=list_id, user_id=current_user_id)
        created_place_record = await crud_place.add_place_to_list(db=db, list_id=list_id, place_in=place)
        return place_schemas.PlaceItem(**created_place_record)
    except crud_list.ListNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to add places to this list")
    except crud_place.PlaceAlreadyExistsError:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Place already exists in this list (based on external Place ID)")
    except crud_place.InvalidPlaceDataError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Invalid data provided for place: {e}")
    except Exception as e:
        logger.error(f"Error adding place to list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error adding place")


@router.patch("/{list_id}/places/{place_id}", response_model=place_schemas.PlaceItem, tags=place_tags)
@limiter.limit("20/minute")
async def update_place_in_list(
    request: Request,
    list_id: int, # From path
    place_id: int, # From path (DB ID)
    place_update: place_schemas.PlaceUpdate,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Update a place's details (e.g., notes) within a list.
    Requires ownership or collaboration access.
    """
    # Currently PlaceUpdate schema only allows notes update
    update_fields = place_update.model_dump(exclude_unset=True)
    if not update_fields:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No update fields provided")

    try:
        await crud_list.check_list_access(db=db, list_id=list_id, user_id=current_user_id)

        # Only call update_place_notes if notes are provided
        if "notes" in update_fields:
             updated_place_record = await crud_place.update_place_notes(db=db, place_id=place_id, list_id=list_id, notes=place_update.notes)
             if not updated_place_record: # update_place_notes raises PlaceNotFound now
                  raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Place not found in this list for update") # Should not happen if access check passed place
             return place_schemas.PlaceItem(**updated_place_record)
        else:
            # If only other fields were in PlaceUpdate, handle them here
            # For now, just raise error if only non-note fields were sent (if any)
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only 'notes' field is currently updatable via this endpoint.")

    except crud_place.PlaceNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Place not found in this list")
    except crud_list.ListNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to modify this list")
    except Exception as e:
        logger.error(f"Error updating place {place_id} in list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error updating place")


@router.delete("/{list_id}/places/{place_id}", status_code=status.HTTP_204_NO_CONTENT, tags=place_tags)
@limiter.limit("20/minute")
async def delete_place_from_list_endpoint( # Renamed function
    request: Request,
    list_id: int, # From path
    place_id: int, # From path (DB ID)
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Delete a place from a list.
    Requires ownership or collaboration access.
    """
    try:
        await crud_list.check_list_access(db=db, list_id=list_id, user_id=current_user_id)
        deleted = await crud_place.delete_place_from_list(db=db, place_id=place_id, list_id=list_id)
        if not deleted:
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Place not found in this list")
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    except crud_list.ListNotFoundError:
         raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to modify this list")
    except Exception as e:
        logger.error(f"Error deleting place {place_id} from list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error deleting place")


# === List Discovery Routes ===
# These need to be added to the main app router in app/main.py as they don't fit the /lists prefix

# @router.get("/public", ...) # Add to app/main.py or a different router
# @router.get("/search", ...) # Add to app/main.py or a different router
# @router.get("/recent", ...) # Add to app/main.py or a different router