# backend/app/crud/crud_place.py
import asyncpg
import logging
from typing import List, Optional, Tuple

from app.schemas import place as place_schemas

logger = logging.getLogger(__name__)

# --- Custom Exceptions ---
class PlaceNotFoundError(Exception): pass
class PlaceAlreadyExistsError(Exception): pass
class InvalidPlaceDataError(Exception): pass # For check constraint violations

# --- CRUD Operations ---

async def get_places_by_list_id_paginated(db: asyncpg.Connection, list_id: int, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Fetches paginated places belonging to a specific list."""
    offset = (page - 1) * page_size
    # Count query
    count_query = "SELECT COUNT(*) FROM places WHERE list_id = $1"
    total_items = await db.fetchval(count_query, list_id) or 0

    # Fetch query
    fetch_query = """
        SELECT id, name, address, latitude, longitude, rating, notes, visit_status
        FROM places
        WHERE list_id = $1
        ORDER BY created_at DESC -- Or by name, etc.
        LIMIT $2 OFFSET $3
    """
    places = await db.fetch(fetch_query, list_id, page_size, offset)
    return places, total_items

async def add_place_to_list(db: asyncpg.Connection, list_id: int, place_in: place_schemas.PlaceCreate) -> asyncpg.Record:
    """Adds a place to a list."""
    try:
        # Note: 'place_id' here refers to the external ID (e.g., Google Place ID)
        # The database 'id' column is the primary key auto-generated.
        created_place_record = await db.fetchrow(
            """
            INSERT INTO places (list_id, place_id, name, address, latitude, longitude, rating, notes, visit_status, created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
            RETURNING id, name, address, latitude, longitude, rating, notes, visit_status
            """,
            list_id, place_in.placeId, place_in.name, place_in.address, place_in.latitude, place_in.longitude,
            place_in.rating, place_in.notes, place_in.visitStatus
        )
        if not created_place_record:
            raise RuntimeError("Failed to add place (no record returned from DB)")
        logger.info(f"Place '{place_in.name}' (DB ID: {created_place_record['id']}) added to list {list_id}")
        return created_place_record
    except asyncpg.exceptions.UniqueViolationError as e:
        # Check if the violation is on the (list_id, place_id) constraint
        if 'places_list_id_place_id_key' in str(e): # Adjust constraint name if different
            logger.warning(f"Place with external ID '{place_in.placeId}' already exists in list {list_id}")
            raise PlaceAlreadyExistsError("Place already exists in this list") from e
        else: # Handle other potential unique violations if any
            logger.error(f"Unexpected UniqueViolationError adding place: {e}")
            raise RuntimeError("Database constraint violation adding place") from e
    except asyncpg.exceptions.CheckViolationError as e:
        logger.warning(f"Check constraint violation adding place to list {list_id}: {e}")
        raise InvalidPlaceDataError(f"Invalid data for place: {e}") from e

async def update_place_notes(db: asyncpg.Connection, place_id: int, list_id: int, notes: Optional[str]) -> Optional[asyncpg.Record]:
    """Updates the notes for a specific place within a list."""
    # Ensure the place belongs to the list (optional, depends on API design)
    # exists = await db.fetchval("SELECT EXISTS (SELECT 1 FROM places WHERE id = $1 AND list_id = $2)", place_id, list_id)
    # if not exists:
    #     raise PlaceNotFoundError("Place not found in this list")

    updated_place_record = await db.fetchrow(
        """
        UPDATE places SET notes = $1, updated_at = NOW()
        WHERE id = $2 AND list_id = $3 -- Update only if it belongs to the list
        RETURNING id, name, address, latitude, longitude, rating, notes, visit_status
        """,
        notes, place_id, list_id
    )
    if updated_place_record:
        logger.info(f"Updated notes for place {place_id} in list {list_id}")
    else:
         logger.warning(f"Failed to update place {place_id} notes (not found in list {list_id}?)")
         # Raise error or return None depending on desired behavior
         raise PlaceNotFoundError("Place not found in this list for update")
    return updated_place_record

# Add other update functions if needed (e.g., update_place_rating, update_visit_status)

async def delete_place_from_list(db: asyncpg.Connection, place_id: int, list_id: int) -> bool:
    """Deletes a place by its DB ID, ensuring it belongs to the specified list."""
    status = await db.execute(
        "DELETE FROM places WHERE id = $1 AND list_id = $2",
        place_id, list_id
    )
    deleted_count = int(status.split(" ")[1])
    if deleted_count > 0:
        logger.info(f"Place {place_id} deleted from list {list_id}")
        return True
    else:
        logger.warning(f"Attempted to delete place {place_id} from list {list_id}, but it was not found.")
        return False