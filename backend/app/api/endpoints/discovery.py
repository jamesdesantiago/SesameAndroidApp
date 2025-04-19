# backend/app/api/endpoints/discovery.py
import logging
import math
from typing import List, Optional

import asyncpg
from fastapi import APIRouter, Depends, HTTPException, Header, Query, Request, status

# Import dependencies, schemas, crud functions
from app.api import deps
from app.schemas import list as list_schemas
# Import specific CRUD functions needed
from app.crud import crud_list

# Import Rate Limiting stuff
from slowapi import Limiter
from slowapi.util import get_remote_address

logger = logging.getLogger(__name__)
router = APIRouter()
limiter = Limiter(key_func=get_remote_address)

tags = ["Discovery"]

# Dependency for optional user ID
async def get_optional_current_user_id(
    db: asyncpg.Connection = Depends(deps.get_db),
    token_data: Optional[deps.token_schemas.FirebaseTokenData] = Depends(deps.get_optional_verified_token_data) # New optional dep needed
) -> Optional[int]:
    if not token_data:
        return None
    try:
        user_id, _ = await deps.crud_user.get_or_create_user_by_firebase(db=db, token_data=token_data)
        return user_id
    except Exception:
        # Log error but don't fail the request, just proceed as unauthenticated
        logger.error("Error getting user ID for optional auth", exc_info=True)
        return None

# Add get_optional_verified_token_data to deps.py (similar to get_verified_token_data but returns None on error/missing header)
# Example in deps.py:
# async def get_optional_verified_token_data(authorization: Optional[str] = Header(None, alias="Authorization")) -> Optional[token_schemas.FirebaseTokenData]:
#     if not authorization: return None
#     try: return await get_verified_token_data(authorization)
#     except HTTPException: return None


@router.get("/public-lists", response_model=list_schemas.PaginatedListResponse, tags=tags)
@limiter.limit("10/minute")
async def get_public_lists(
    request: Request, # For limiter state
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """Get publicly available lists (paginated)."""
    try:
        # Needs implementation in crud_list
        list_records, total_items = await crud_list.get_public_lists_paginated(db, page=page, page_size=page_size)
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [list_schemas.ListViewResponse(**lst) for lst in list_records]
        return list_schemas.PaginatedListResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error fetching public lists: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Server error fetching public lists")

@router.get("/search-lists", response_model=list_schemas.PaginatedListResponse, tags=tags)
@limiter.limit("15/minute")
async def search_lists(
    request: Request, # For limiter state
    q: str = Query(..., min_length=1, description="Search query for list name or description"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    # Use optional user ID dependency
    current_user_id: Optional[int] = Depends(get_optional_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """
    Search lists by query. Includes public lists and private lists owned by the user if authenticated.
    """
    try:
        # Needs implementation in crud_list
        list_records, total_items = await crud_list.search_lists_paginated(
            db, query=q, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [list_schemas.ListViewResponse(**lst) for lst in list_records]
        return list_schemas.PaginatedListResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except Exception as e:
        logger.error(f"Error searching lists for '{q}': {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Server error searching lists")

@router.get("/recent-lists", response_model=list_schemas.PaginatedListResponse, tags=tags)
@limiter.limit("10/minute")
async def get_recent_lists(
    request: Request, # For limiter state
    page: int = Query(1, ge=1),
    page_size: int = Query(10, ge=1, le=50), # Smaller page size for recent?
    # Requires authentication to see user's recent + public
    current_user_id: int = Depends(deps.get_current_user_id),
    db: asyncpg.Connection = Depends(deps.get_db)
):
    """Get recently created lists (public or owned by the user, paginated)."""
    try:
        # Needs implementation in crud_list
        list_records, total_items = await crud_list.get_recent_lists_paginated(
            db, user_id=current_user_id, page=page, page_size=page_size
        )
        total_pages = math.ceil(total_items / page_size) if page_size > 0 else 0
        items = [list_schemas.ListViewResponse(**lst) for lst in list_records]
        return list_schemas.PaginatedListResponse(
            items=items, page=page, page_size=page_size,
            total_items=total_items, total_pages=total_pages
        )
    except HTTPException as he:
         raise he
    except Exception as e:
        logger.error(f"Error fetching recent lists for user {current_user_id}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Server error fetching recent lists")