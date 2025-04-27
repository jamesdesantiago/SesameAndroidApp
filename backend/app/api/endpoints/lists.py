# backend/app/api/endpoints/lists.py
import logging
import math
from typing import List # Import List

import asyncpg
from fastapi import (APIRouter, Depends, HTTPException, Header, Query, Request,
                     Response, status, Path)
# Using fastapi.Response and status directly
from fastapi.responses import JSONResponse

# Import dependencies, schemas, crud functions
from app.api import deps
# Alias schemas for clarity
from app.schemas import list as list_schemas
from app.schemas import place as place_schemas
from app.schemas import user as user_schemas # Needed for collaborator response
# Import specific CRUD exceptions
from app.crud.crud_list import (ListNotFoundError, ListAccessDeniedError,
                                 CollaboratorAlreadyExistsError, DatabaseInteractionError as ListDBError)
from app.crud.crud_place import (PlaceNotFoundError, PlaceAlreadyExistsError,
                                 InvalidPlaceDataError, DatabaseInteractionError as PlaceDBError)
from app.crud.crud_user import UserNotFoundError as UserCRUDNotFoundError # Import UserNotFoundError from user crud
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
        # crud_list.create_list raises DatabaseInteractionError (ListDBError)
        created_list_record = await crud_list.create_list(db=db, list_in=list_data, owner_id=current_user_id)
        # Map Record to Pydantic Schema for response
        # Use get_list_details to fetch the full details, including collaborators (if any were added during creation, though not currently supported)
        # crud_list.get_list_details raises DatabaseInteractionError (ListDBError)
        full_list_details = await crud_list.get_list_details(db=db, list_id=created_list_record['id'])
        if not full_list_details:
             # Should not happen after a successful insert and detail fetch
             logger.error(f"Failed to fetch full details for newly created list {created_list_record['id']}")
             raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Could not retrieve details for new list.")

        return list_schemas.ListDetailResponse(**full_list_details)

    except ListDBError as e: # Catch specific DB errors from CRUD
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
        # crud_list.get_user_lists_paginated raises DatabaseInteractionError (ListDBError)
        list_records, total_items = await crud_list.get_user_lists_paginated(
            db=db, owner_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        # Map Record list to Schema list (ListViewResponse expects place_count)
        items = [list_schemas.ListViewResponse(**lst) for lst in list_records] # Records should map directly if names match

        return list_schemas.PaginatedListResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except ListDBError as e: # Catch specific DB errors from CRUD
        logger.error(f"DB error fetching lists for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error fetching lists")
    except Exception as e:
        logger.error(f"Unexpected error fetching lists for user {current_user_id}: {e}", exc_info=True)
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
    # deps.get_list_and_verify_access handles ListNotFoundError and ListAccessDeniedError
    # and maps them to 404/403 HTTPExceptions. It also returns the list record.
    list_id = list_record['id'] # Extract ID from record provided by dependency
    try:
        # list_record is already fetched and access verified by the dependency
        # Use the CRUD function that returns details including collaborators
        # crud_list.get_list_details raises DatabaseInteractionError (ListDBError) or ListNotFoundError (if list disappears)
        full_list_details = await crud_list.get_list_details(db=db, list_id=list_id)

        # Although unlikely after dependency, handle case where list disappears between dep and this call
        if not full_list_details:
             logger.error(f"List {list_id} not found when fetching full details after access check passed.")
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found.")

        return list_schemas.ListDetailResponse(**full_list_details)

    except (ListNotFoundError, ListAccessDeniedError) as e: # Catch errors potentially re-raised by get_list_details or dependency
         # These should ideally be caught by the dependency, but handling here too for robustness
         status_code = status.HTTP_404_NOT_FOUND if isinstance(e, ListNotFoundError) else status.HTTP_403_FORBIDDEN
         raise HTTPException(status_code=status_code, detail=str(e))
    except ListDBError as e: # Catch specific DB errors from CRUD
        logger.error(f"DB error fetching detail/collaborators for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error fetching list details")
    except HTTPException as he:
        raise he # Propagate errors from dependency (403, 404)
    except Exception as e:
        logger.error(f"Unexpected error fetching detail/collaborators for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching list details")


@router.patch("/{list_id}", response_model=list_schemas.ListDetailResponse, tags=list_tags)
@limiter.limit("10/minute")
async def update_list(
    request: Request, # For limiter state
    update_data: list_schemas.ListUpdate,
    list_id: int = Path(..., description="The ID of the list to update"), # Get list_id from path
    # Use the new dependency that *only* verifies ownership
    _=Depends(deps.verify_list_ownership), # Assign to _ as we don't need a return value
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Update a list's name or privacy status. Requires ownership (checked by dependency).
    """
    # Check if any fields provided for update.
    # crud_list.update_list now handles this case and returns current details
    # So, we don't need the explicit check and 400 here.
    # Let's keep the CRUD behavior and rely on it.

    try:
        # Ownership already checked by dependency 'verify_list_ownership' which raises 403/404.
        # crud_list.update_list returns the updated data dict or current data dict if no changes.
        # It raises DatabaseInteractionError (ListDBError) or returns None if list not found (rare after dep).
        updated_list_details = await crud_list.update_list(db=db, list_id=list_id, list_in=update_data)

        if not updated_list_details:
            # CRUD returns None if the list wasn't found for update - unlikely after ownership check
            logger.error(f"List {list_id} not found for update after ownership check passed.")
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found for update")

        # Pass the dictionary returned by CRUD directly to the schema
        return list_schemas.ListDetailResponse(**updated_list_details)

    except (ListNotFoundError, ListAccessDeniedError) as e: # Catch errors potentially re-raised by update_list
         # These should ideally be caught by the dependency, but handling here too for robustness
         status_code = status.HTTP_404_NOT_FOUND if isinstance(e, ListNotFoundError) else status.HTTP_403_FORBIDDEN
         raise HTTPException(status_code=status_code, detail=str(e))
    except ListDBError as e: # Catch specific DB errors from CRUD
         logger.error(f"DB interaction error updating list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error updating list")
    except HTTPException as he: # Catch 403/404 from dependency
        raise he
    except Exception as e:
        logger.error(f"Unexpected error updating list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error updating list")

@router.delete("/{list_id}", status_code=status.HTTP_204_NO_CONTENT, tags=list_tags)
@limiter.limit("10/minute")
async def delete_list(
    request: Request, # For limiter state
    list_id: int = Path(..., description="The ID of the list to delete"), # Get list_id from path
    # Use the new dependency that *only* verifies ownership
    _=Depends(deps.verify_list_ownership), # Assign to _
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Delete a list. Requires ownership (checked by dependency).
    """
    try:
        # Ownership already checked by dependency 'verify_list_ownership' which raises 403/404.
        # crud_list.delete_list returns True if deleted, False if not found.
        # It raises DatabaseInteractionError (ListDBError).
        deleted = await crud_list.delete_list(db=db, list_id=list_id)
        if not deleted:
             # CRUD returns False if delete affected 0 rows - unlikely after ownership check
             logger.error(f"List {list_id} not found for delete after ownership check passed.")
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found for deletion")
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    except (ListNotFoundError, ListAccessDeniedError) as e: # Catch errors potentially re-raised by delete_list
         # These should ideally be caught by the dependency, but handling here too for robustness
         status_code = status.HTTP_404_NOT_FOUND if isinstance(e, ListNotFoundError) else status.HTTP_403_FORBIDDEN
         raise HTTPException(status_code=status_code, detail=str(e))
    except ListDBError as e: # Catch specific DB errors from CRUD
         logger.error(f"DB interaction error deleting list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error deleting list")
    except HTTPException as he: # Catch 403/404 from dependency
        raise he
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
    # deps.get_list_and_verify_ownership handles ListNotFoundError and ListAccessDeniedError (404/403)
    list_id = list_record['id'] # Extract ID from record provided by dependency
    try:
        # Ownership already checked by dependency
        # crud_list.add_collaborator_to_list raises CollaboratorAlreadyExistsError or DatabaseInteractionError (ListDBError)
        await crud_list.add_collaborator_to_list(db=db, list_id=list_id, collaborator_email=collaborator.email)
        return user_schemas.UsernameSetResponse(message="Collaborator added") # Match original response model

    # Catch specific CRUD errors and map to HTTP status codes
    except CollaboratorAlreadyExistsError as e:
         logger.warning(f"Attempted to add existing collaborator {collaborator.email} to list {list_id}: {e}", exc_info=False)
         raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    except ListDBError as e: # Catch generic DB errors from crud
         logger.error(f"DB interaction error adding collaborator {collaborator.email} to list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error adding collaborator")
    except HTTPException as he: # Propagate errors from dependency (403, 404)
        raise he
    except Exception as e:
        logger.error(f"Unexpected error adding collaborator {collaborator.email} to list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error adding collaborator")

@router.delete("/{list_id}/collaborators/{user_id}", status_code=status.HTTP_204_NO_CONTENT, tags=collab_tags)
@limiter.limit("20/minute")
async def remove_collaborator(
    request: Request,
    list_id: int = Path(..., description="The ID of the list"),
    user_id: int = Path(..., description="The ID of the user to remove as collaborator"),
    # Use the dependency to verify ownership (only the owner can remove)
    # deps.verify_list_ownership handles ListNotFoundError and ListAccessDeniedError (404/403)
    _=Depends(deps.verify_list_ownership),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Remove a collaborator (by user ID) from a list. Requires list ownership.
    """
    try:
        # Ownership verified by dependency
        # crud_list.delete_collaborator_from_list returns True if deleted, False if not found (as collaborator).
        # It raises DatabaseInteractionError (ListDBError).
        deleted = await crud_list.delete_collaborator_from_list(db=db, list_id=list_id, collaborator_user_id=user_id)

        if not deleted:
            # Deleted 0 rows from list_collaborators. This could mean:
            # 1. The user_id wasn't a collaborator on this list.
            # 2. The user_id is the OWNER of the list (and owner cannot be removed as a collaborator).
            # 3. The user_id doesn't exist at all.

            # Check if the user exists at all to distinguish between 1/2 and 3.
            # crud_user.check_user_exists raises DatabaseInteractionError (UserDBError)
            user_exists = await crud_user.check_user_exists(db, user_id)

            if not user_exists:
                 # If user doesn't exist, it's a 404 on the user ID itself
                 logger.warning(f"Attempted to remove non-existent user {user_id} as collaborator from list {list_id}.")
                 raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Collaborator user not found.")
            else:
                 # User exists, but wasn't a collaborator on this list (or is the owner).
                 # Check if they are the owner
                 list_record = await crud_list.get_list_by_id(db=db, list_id=list_id) # Get list details (already checked access, but need owner_id)
                 if list_record and list_record['owner_id'] == user_id:
                      # Cannot remove the owner via this endpoint
                      logger.warning(f"Attempted to remove owner {user_id} as collaborator from list {list_id}.")
                      raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot remove the list owner as a collaborator.")
                 else:
                     # User exists but wasn't a collaborator (and wasn't the owner if list exists)
                     # Return 404 as the *collaborator relationship* wasn't found
                      logger.warning(f"Attempted to remove user {user_id} from list {list_id}, but they are not a collaborator.")
                      raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User is not a collaborator on this list.")

        # If deleted is True, return 204 NO CONTENT
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    # Catch specific CRUD errors and map to HTTP status codes
    except (ListNotFoundError, ListAccessDeniedError) as e: # Propagate errors from verify_list_ownership dependency
         status_code = status.HTTP_404_NOT_FOUND if isinstance(e, ListNotFoundError) else status.HTTP_403_FORBIDDEN
         raise HTTPException(status_code=status_code, detail=str(e))
    except (ListDBError, UserCRUDNotFoundError) as e: # Catch DB errors from crud_list or crud_user.check_user_exists
         # Note: UserCRUDNotFoundError from check_user_exists is handled above by checking user_exists,
         # but keeping this catch block just in case for other potential DatabaseInteractionError from user_crud.
         logger.error(f"DB error removing collaborator {user_id} from list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error removing collaborator.")
    except HTTPException as he: # Propagate other HTTP exceptions (e.g., Cannot remove owner)
         raise he
    except Exception as e:
         logger.error(f"Unexpected error removing collaborator {user_id} from list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error removing collaborator.")


# === Places within this List ===
@router.get("/{list_id}/places", response_model=place_schemas.PaginatedPlaceResponse, tags=place_tags)
@limiter.limit("10/minute")
async def get_places_in_list(
    request: Request, # For limiter state
    page: int = Query(1, ge=1, description="Page number to retrieve"),
    page_size: int = Query(30, ge=1, le=100, description="Number of places per page"),
    # Use the dependency to verify access and get the record
    # deps.get_list_and_verify_access handles ListNotFoundError and ListAccessDeniedError (404/403)
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
        # crud_place.get_places_by_list_id_paginated raises DatabaseInteractionError (PlaceDBError)
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
    # Propagate errors from dependency (403, 404)
    except HTTPException as he:
        raise he
    except PlaceDBError as e: # Catch specific DB errors from CRUD
        logger.error(f"DB error fetching places for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error fetching places")
    except Exception as e:
        logger.error(f"Unexpected error fetching places for list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error fetching places")


@router.post("/{list_id}/places", response_model=place_schemas.PlaceItem, status_code=status.HTTP_201_CREATED, tags=place_tags)
@limiter.limit("40/minute")
async def add_place_to_list(
    request: Request, # For limiter state
    place: place_schemas.PlaceCreate,
    # Use the dependency to verify access and get the record
    # deps.get_list_and_verify_access handles ListNotFoundError and ListAccessDeniedError (404/403)
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
        # crud_place.add_place_to_list raises PlaceAlreadyExistsError, InvalidPlaceDataError, PlaceDBError
        created_place_record = await crud_place.add_place_to_list(db=db, list_id=list_id, place_in=place)
        return place_schemas.PlaceItem(**created_place_record) # Record maps directly

    # Catch specific CRUD errors and map to HTTP status codes
    except PlaceAlreadyExistsError as e:
        logger.warning(f"Attempted to add existing place {place.placeId} to list {list_id}: {e}", exc_info=False)
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(e))
    except InvalidPlaceDataError as e:
        logger.warning(f"Invalid data adding place to list {list_id}: {e}", exc_info=False)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Invalid data provided for place: {e}")
    except PlaceDBError as e: # Catch generic DB errors from crud
         logger.error(f"DB interaction error adding place to list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error adding place")
    # Propagate errors from dependency (403, 404)
    except HTTPException as he:
        raise he
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
    # deps.get_list_and_verify_access handles ListNotFoundError and ListAccessDeniedError (404/403)
    list_record: asyncpg.Record = Depends(deps.get_list_and_verify_access),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Update a place's details within a list.
    Requires ownership or collaboration access (checked by dependency).
    """
    list_id = list_record['id'] # Extract ID from record provided by dependency

    # Check if any update fields are provided.
    # crud_place.update_place now handles this and returns the current record if no changes.
    # So, we don't need the explicit check and 400 here. Rely on CRUD behavior.

    try:
        # Access checked by dependency
        # Call the generic update_place function in CRUD
        # crud_place.update_place raises PlaceNotFoundError, InvalidPlaceDataError, PlaceDBError
        updated_place_record = await crud_place.update_place(
            db=db,
            place_id=place_id,
            list_id=list_id,
            place_update_in=place_update # Pass the Pydantic model
        )
        # crud_place.update_place raises PlaceNotFoundError if update fails (place not in list/not found)
        return place_schemas.PlaceItem(**updated_place_record)

    # Catch specific CRUD errors and map to HTTP status codes
    except PlaceNotFoundError as e:
        logger.warning(f"Attempted to update non-existent place {place_id} in list {list_id}: {e}", exc_info=False)
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except InvalidPlaceDataError as e:
        logger.warning(f"Invalid data updating place {place_id} in list {list_id}: {e}", exc_info=False)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Invalid data provided for update: {e}")
    except PlaceDBError as e: # Catch generic DB errors from crud
         logger.error(f"DB interaction error updating place {place_id} in list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error updating place")
    # Propagate errors from dependency (403, 404)
    except HTTPException as he:
        raise he
    except Exception as e:
        logger.error(f"Unexpected error updating place {place_id} in list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error updating place")


@router.delete("/{list_id}/places/{place_id}", status_code=status.HTTP_204_NO_CONTENT, tags=place_tags)
@limiter.limit("20/minute")
async def delete_place_from_list_endpoint( # Renamed function
    request: Request, # For limiter state
    place_id: int, # From path
    # Use the dependency to verify access and get the record
    # deps.get_list_and_verify_access handles ListNotFoundError and ListAccessDeniedError (404/403)
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
        # crud_place.delete_place_from_list returns True if deleted, False if not found (in list).
        # It raises DatabaseInteractionError (PlaceDBError).
        deleted = await crud_place.delete_place_from_list(db=db, place_id=place_id, list_id=list_id)
        if not deleted:
             # This might happen if the place was already deleted concurrently or place_id wasn't in list_id
             logger.warning(f"Attempted delete for place {place_id} in list {list_id}, but not found by CRUD.")
             raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Place not found in this list")
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    # Propagate errors from dependency (403, 404)
    except HTTPException as he:
        raise he
    except PlaceDBError as e: # Catch specific DB errors from CRUD
         logger.error(f"DB interaction error deleting place {place_id} from list {list_id}: {e}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Database error deleting place")
    except Exception as e:
        logger.error(f"Unexpected error deleting place {place_id} from list {list_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error deleting place")