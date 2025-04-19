# backend/app/api/endpoints/lists.py
import logging
import math
from typing import List # Import List

import asyncpg
from fastapi import (APIRouter, Depends, HTTPException, Header, Query, Request,
                     Response, status)
# Using fastapi.Response and status directly
from fastapi.responses import JSONResponse

# Import dependencies, schemas, crud functions
from app.api import deps
# Alias schemas for clarity
from app.schemas import list as list_schemas
from app.schemas import place as place_schemas
from app.schemas import user as user_schemas # Needed for collaborator response
# Import specific CRUD functions needed
from app.crud import crud_list, crud_place, crud_user # crud_user might be needed if collab returns user info

# Import Rate Limiting stuff
from slowapi import Limiter
from slowapi.util import get_remote_address

logger = logging.getLogger(__name__)
router = APIRouter()
# Create a specific limiter instance for this router if needed, or use global
limiter = Limiter(key_func=get_remote_address)

# Define tags for OpenAPI documentation grouping
list_tags = ["Lists"]
place_tags = ["Places", "Lists"] # Places within lists
collab_tags = ["Collaborators", "Lists"]

# === List CRUD ===
@router.post("", response_model=list_schemas.ListDetailResponse, status_code=status.HTTP_201_CREATED, tags=list_tags)
@limiter.limit("5/minute")
async def create_list(
    request: Request, # For limiter state
    list_data: list_schemas.ListCreate,
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Create a new list for the authenticated user.
    """
    try:
        created_list_record = await crud_list.create_list(db=db, list_in=list_data, owner_id=current_user_id)
        # Map Record to Pydantic Schema for response
        return list_schemas.ListDetailResponse(
            id=created_list_record['id'],
            name=created_list_record['name'],
            description=created_list_record['description'],
            isPrivate=created_list_record['is_private'],
            collaborators=[] # New lists have no collaborators initially
        )
    except crud_list.DatabaseInteractionError as e: # Catch specific CRUD errors
        logger.error(f"DB interaction error creating list for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error creating list")
    except Exception as e:
        logger.error(f"Unexpected error creating list for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error creating list")


@router.get("", response_model=list_schemas.PaginatedListResponse, tags=list_tags)
@limiter.limit("15/minute")
async def get_lists(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(20, ge=1, le=100, description="Number of lists per page"),
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
        # Map Record list to Schema list
        items = [list_schemas.ListViewResponse(**lst) for lst in list_records] # Records should map directly if names match

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
    request: Request, # For limiter state
    # Use the dependency to check access and get the list record
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_access), # Extracts list_id from path
    db: asyncpg.Connection = Depends(deps.get_db) # Still need db for collaborator fetch
):
    """
    Get details (metadata and collaborators) for a specific list identified by `list_id`.
    Requires ownership or collaboration access (checked by dependency).
    """
    try:
        # list_record is already fetched and access verified by the dependency
        list_id = list_record['id']
        collaborators = await crud_list._get_collaborator_emails(db, list_id) # Use internal helper or dedicated CRUD func
        # Combine and return
        return list_schemas.ListDetailResponse(
             id=list_record['id'],
             name=list_record['name'],
             description=list_record['description'],
             isPrivate=list_record['is_private'],
             collaborators=collaborators
         )
    except HTTPException as he:
        raise he # Propagate errors from dependency (403, 404)
    except Exception as e:
        # list_id might not be available here if dependency failed early
        logger.error(f"Error fetching detail/collaborators for list after access check: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching list details")


@router.patch("/{list_id}", response_model=list_schemas.ListDetailResponse, tags=list_tags)
@limiter.limit("10/minute")
async def update_list(
    request: Request, # For limiter state
    update_data: list_schemas.ListUpdate,
    # Use the dependency to verify ownership and get the record
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_ownership),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Update a list's name or privacy status. Requires ownership (checked by dependency).
    """
    list_id = list_record['id'] # Extract ID from record provided by dependency

    if not update_data.model_dump(exclude_unset=True):
         raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No update fields provided")
    try:
        # Ownership already checked by dependency
        updated_list_dict = await crud_list.update_list(db=db, list_id=list_id, list_in=update_data)
        if not updated_list_dict:
            # Should not happen if ownership dependency worked, but handle defensively
            logger.error(f"List {list_id} not found for update after ownership check.")
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found for update")
        return list_schemas.ListDetailResponse(**updated_list_dict)
    except HTTPException as he:
        raise he
    except crud_list.DatabaseInteractionError as e: # Catch specific CRUD errors
         logger.error(f"DB interaction error updating list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error updating list")
    except Exception as e:
        logger.error(f"Unexpected error updating list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error updating list")


@router.delete("/{list_id}", status_code=status.HTTP_204_NO_CONTENT, tags=list_tags)
@limiter.limit("10/minute")
async def delete_list(
    request: Request, # For limiter state
    # Use the dependency to verify ownership and get the record (to ensure it exists)
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_ownership),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Delete a list. Requires ownership (checked by dependency).
    """
    list_id = list_record['id'] # Extract ID from record provided by dependency
    try:
        # Ownership already checked by dependency
        deleted = await crud_list.delete_list(db=db, list_id=list_id)
        if not deleted:
             # Should not happen if ownership dependency worked
             logger.error(f"List {list_id} not found for delete after ownership check.")
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found for deletion")
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    except HTTPException as he:
        raise he
    except crud_list.DatabaseInteractionError as e: # Catch specific CRUD errors
         logger.error(f"DB interaction error deleting list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error deleting list")
    except Exception as e:
        logger.error(f"Unexpected error deleting list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error deleting list")

# === Collaborators ===
@router.post("/{list_id}/collaborators", status_code=status.HTTP_201_CREATED, response_model=user_schemas.UsernameSetResponse, tags=collab_tags)
@limiter.limit("20/minute")
async def add_collaborator(
    request: Request, # For limiter state
    collaborator: list_schemas.CollaboratorAdd,
    # Use the dependency to verify ownership and get the record
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_ownership),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Add a collaborator (by email) to a list specified by `list_id`. Requires list ownership.
    """
    list_id = list_record['id'] # Extract ID from record provided by dependency
    try:
        # Ownership already checked by dependency
        await crud_list.add_collaborator_to_list(db=db, list_id=list_id, collaborator_email=collaborator.email)
        return user_schemas.UsernameSetResponse(message="Collaborator added") # Match original response model
    except HTTPException as he: # Handle errors from dependency
        raise he
    except crud_list.CollaboratorNotFoundError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except crud_list.CollaboratorAlreadyExistsError as e:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    except crud_list.DatabaseInteractionError as e: # Catch specific CRUD errors
         logger.error(f"DB interaction error adding collaborator {collaborator.email} to list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error adding collaborator")
    except Exception as e:
        logger.error(f"Unexpected error adding collaborator {collaborator.email} to list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error adding collaborator")

# TODO: Add DELETE /lists/{list_id}/collaborators/{user_id} endpoint

# === Places within this List ===
@router.get("/{list_id}/places", response_model=place_schemas.PaginatedPlaceResponse, tags=place_tags)
@limiter.limit("10/minute")
async def get_places_in_list(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(30, ge=1, le=100, description="Number of places per page"),
    # Use the dependency to verify access and get the record
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_access),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Get places within a specific list (paginated).
    Requires ownership or collaboration access (checked by dependency).
    """
    list_id = list_record['id'] # Extract ID from record provided by dependency
    try:
        # Access already checked by dependency
        place_records, total_items = await crud_place.get_places_by_list_id_paginated(
            db=db, list_id=list_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        # Map Record list to Schema list
        items = [place_schemas.PlaceItem(**p) for p in place_records] # Records should map directly

        return place_schemas.PaginatedPlaceResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except HTTPException as he:
        raise he
    except Exception as e:
        logger.error(f"Error fetching places for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching places")


@router.post("/{list_id}/places", response_model=place_schemas.PlaceItem, status_code=status.HTTP_201_CREATED, tags=place_tags)
@limiter.limit("40/minute")
async def add_place_to_list(
    request: Request, # For limiter state
    place: place_schemas.PlaceCreate,
    # Use the dependency to verify access and get the record
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_access),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Add a new place to a specific list identified by `list_id`.
    Requires ownership or collaboration access.
    """
    list_id = list_record['id'] # Extract ID from record provided by dependency
    try:
        # Access checked by dependency
        created_place_record = await crud_place.add_place_to_list(db=db, list_id=list_id, place_in=place)
        return place_schemas.PlaceItem(**created_place_record) # Record maps directly
    except HTTPException as he:
        raise he
    except crud_place.PlaceAlreadyExistsError as e:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    except crud_place.InvalidPlaceDataError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Invalid data provided for place: {e}")
    except crud_place.DatabaseInteractionError as e: # Catch specific CRUD errors
         logger.error(f"DB interaction error adding place to list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error adding place")
    except Exception as e:
        logger.error(f"Unexpected error adding place to list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error adding place")


@router.patch("/{list_id}/places/{place_id}", response_model=place_schemas.PlaceItem, tags=place_tags)
@limiter.limit("20/minute")
async def update_place_in_list(
    request: Request, # For limiter state
    place_id: int, # From path
    place_update: place_schemas.PlaceUpdate,
    # Use the dependency to verify access and get the record
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_access),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Update a place's details (currently only 'notes') within a list.
    Requires ownership or collaboration access (checked by dependency).
    """
    list_id = list_record['id'] # Extract ID from record provided by dependency

    update_fields = place_update.model_dump(exclude_unset=True)
    if not update_fields:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No update fields provided")
    if "notes" not in update_fields: # Assuming only notes are updatable currently
         raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only 'notes' field is currently updatable.")

    try:
        # Access checked by dependency
        updated_place_record = await crud_place.update_place_notes(
            db=db,
            place_id=place_id,
            list_id=list_id,
            notes=place_update.notes # Pass notes value
        )
        # crud_place.update_place_notes raises PlaceNotFoundError if update fails
        return place_schemas.PlaceItem(**updated_place_record)
    except HTTPException as he:
        raise he
    except crud_place.PlaceNotFoundError as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except crud_place.DatabaseInteractionError as e: # Catch specific CRUD errors
         logger.error(f"DB interaction error updating place {place_id} in list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error updating place")
    except Exception as e:
        logger.error(f"Unexpected error updating place {place_id} in list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error updating place")


@router.delete("/{list_id}/places/{place_id}", status_code=status.HTTP_204_NO_CONTENT, tags=place_tags)
@limiter.limit("20/minute")
async def delete_place_from_list_endpoint( # Renamed function
    request: Request, # For limiter state
    place_id: int, # From path
    # Use the dependency to verify access and get the record
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_access),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Delete a place (identified by `place_id`) from a list (identified by `list_id`).
    Requires ownership or collaboration access (checked by dependency).
    """
    list_id = list_record['id'] # Extract ID from record provided by dependency
    try:
        # Access checked by dependency
        deleted = await crud_place.delete_place_from_list(db=db, place_id=place_id, list_id=list_id)
        if not deleted:
             # This might happen if the place was already deleted concurrently
             logger.warning(f"Attempted delete for place {place_id} in list {list_id}, but not found by CRUD.")
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Place not found in this list")
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    except HTTPException as he:
        raise he
    except crud_place.DatabaseInteractionError as e: # Catch specific CRUD errors
         logger.error(f"DB interaction error deleting place {place_id} from list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error deleting place")
    except Exception as e:
        logger.error(f"Unexpected error deleting place {place_id} from list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error deleting place")

# Note: List Discovery routes (/public, /search, /recent) should be added to app/main.py
# or a separate top-level router (e.g., app/api/endpoints/discovery.py) as they likely
# don't belong under the /lists/ prefix.