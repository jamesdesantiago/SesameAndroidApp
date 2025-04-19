# backend/app/crud/crud_list.py
import asyncpg
import logging
from typing import List, Optional, Tuple

from app.schemas import list as list_schemas

logger = logging.getLogger(__name__)

# --- Custom Exceptions ---
class ListNotFoundError(Exception): pass
class ListAccessDeniedError(Exception): pass
class CollaboratorNotFoundError(Exception): pass
class CollaboratorAlreadyExistsError(Exception): pass

# --- Helper Functions (Internal to CRUD) ---

async def _get_collaborator_emails(db: asyncpg.Connection, list_id: int) -> List[str]:
    """Fetches collaborator emails for a given list ID."""
    rows = await db.fetch(
        """
        SELECT u.email
        FROM list_collaborators lc
        JOIN users u ON lc.user_id = u.id
        WHERE lc.list_id = $1
        """,
        list_id
    )
    return [row["email"] for row in rows]

# --- CRUD Operations ---

async def create_list(db: asyncpg.Connection, list_in: list_schemas.ListCreate, owner_id: int) -> asyncpg.Record:
    """Creates a new list in the database."""
    logger.info(f"Creating list '{list_in.name}' for owner {owner_id}")
    created_list_record = await db.fetchrow(
        """
        INSERT INTO lists (name, description, owner_id, created_at, is_private)
        VALUES ($1, $2, $3, NOW(), $4)
        RETURNING id, name, description, is_private -- Add other fields if needed by response schema
        """,
        list_in.name, list_in.description, owner_id, list_in.isPrivate
    )
    if not created_list_record:
        raise RuntimeError("Failed to create list (no record returned from DB)")
    logger.info(f"List created with ID {created_list_record['id']}")
    # TODO: Handle adding initial collaborators if provided in list_in
    return created_list_record

async def get_user_lists_paginated(db: asyncpg.Connection, owner_id: int, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Fetches paginated lists owned by a user."""
    offset = (page - 1) * page_size
    # Count query
    count_query = "SELECT COUNT(*) FROM lists WHERE owner_id = $1"
    total_items = await db.fetchval(count_query, owner_id) or 0

    # Fetch query
    fetch_query = """
            SELECT l.id, l.name, l.description, l.is_private -- No subquery here
            FROM lists l
            WHERE l.owner_id = $1
            ORDER BY l.created_at DESC LIMIT $2 OFFSET $3
        """
        list_records = await db.fetch(fetch_query, owner_id, page_size, offset)

        # Fetch counts separately for the retrieved list IDs
        list_ids = [r['id'] for r in list_records]
        place_counts = {}
        if list_ids:
            count_query = """
                SELECT list_id, COUNT(*) as count
                FROM places
                WHERE list_id = ANY($1::int[])
                GROUP BY list_id
            """
            count_records = await db.fetch(count_query, list_ids)
            place_counts = {r['list_id']: r['count'] for r in count_records}

        # Add counts to the records (or handle in endpoint mapping)
        lists_with_counts = []
        for record in list_records:
            record_dict = dict(record) # Convert to mutable dict
            record_dict['place_count'] = place_counts.get(record['id'], 0)
            # Optionally convert back to a Record-like object or just return dicts
            lists_with_counts.append(asyncpg.Record(record_dict)) # Example, might need adjustment

        return lists_with_counts, total_items

async def get_list_details(db: asyncpg.Connection, list_id: int) -> Optional[dict]:
    """Fetches list metadata and collaborator emails."""
    list_record = await db.fetchrow(
        "SELECT l.id, l.name, l.description, l.is_private FROM lists l WHERE l.id = $1",
        list_id
    )
    if not list_record:
        return None

    collaborators = await _get_collaborator_emails(db, list_id)
    list_data = dict(list_record) # Convert Record to dict
    list_data['collaborators'] = collaborators # Add collaborator list
    return list_data


async def update_list(db: asyncpg.Connection, list_id: int, list_in: list_schemas.ListUpdate) -> Optional[dict]:
    """Updates a list's name or privacy status."""
    update_fields = list_in.model_dump(exclude_unset=True)
    if not update_fields:
        logger.warning("Update list called with no fields to update.")
        # Return current details if no updates requested
        return await get_list_details(db, list_id)

    set_clauses = []
    params = []
    param_index = 1
    if "name" in update_fields:
        set_clauses.append(f"name = ${param_index}")
        params.append(update_fields["name"])
        param_index += 1
    if "isPrivate" in update_fields:
        set_clauses.append(f"is_private = ${param_index}")
        params.append(update_fields["isPrivate"])
        param_index += 1

    params.append(list_id) # For WHERE clause
    sql = f"""
        UPDATE lists SET {', '.join(set_clauses)}, updated_at = NOW()
        WHERE id = ${param_index}
        RETURNING id, name, description, is_private
        """

    updated_list_record = await db.fetchrow(sql, *params)
    if not updated_list_record:
        # Check if the list existed at all before the update attempt
        exists = await db.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
        if not exists:
             return None # Return None if list didn't exist
        else:
             # This case implies an issue, maybe concurrent deletion?
             logger.error(f"Update failed for list {list_id} despite existence check")
             raise RuntimeError("List update failed unexpectedly")

    logger.info(f"List {list_id} updated successfully.")
    # Fetch collaborators again to return the full detail response
    collaborators = await _get_collaborator_emails(db, list_id)
    updated_data = dict(updated_list_record)
    updated_data['collaborators'] = collaborators
    return updated_data

async def delete_list(db: asyncpg.Connection, list_id: int) -> bool:
    """Deletes a list by ID. Returns True if deleted, False otherwise."""
    # Assuming ON DELETE CASCADE handles related places, collaborators in DB schema
    status = await db.execute("DELETE FROM lists WHERE id = $1", list_id)
    deleted_count = int(status.split(" ")[1])
    if deleted_count > 0:
        logger.info(f"List {list_id} deleted successfully.")
        return True
    else:
        logger.warning(f"Attempted to delete list {list_id}, but it was not found.")
        return False

async def add_collaborator_to_list(db: asyncpg.Connection, list_id: int, collaborator_email: str):
    """Adds a user (by email) as a collaborator to a list."""
    # Find collaborator user ID by email
    collaborator_user_id = await db.fetchval("SELECT id FROM users WHERE email = $1", collaborator_email)
    if not collaborator_user_id:
         # Option 1: Raise error if user must exist
         # raise CollaboratorNotFoundError(f"User with email {collaborator_email} not found.")
         # Option 2: Create the user (as done in the original code)
         logger.info(f"Collaborator email {collaborator_email} not found, creating placeholder user.")
         # Make sure INSERT INTO users only requires email or handles nulls appropriately
         collaborator_user_id = await db.fetchval("INSERT INTO users (email) VALUES ($1) ON CONFLICT (email) DO NOTHING RETURNING id", collaborator_email)
         if not collaborator_user_id:
             # If user already existed due to race condition or conflict, refetch ID
             collaborator_user_id = await db.fetchval("SELECT id FROM users WHERE email = $1", collaborator_email)
             if not collaborator_user_id:
                  raise RuntimeError(f"Failed to create or find collaborator user record for {collaborator_email}")

    # Insert into list_collaborators
    try:
        await db.execute("INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2)", list_id, collaborator_user_id)
        logger.info(f"User {collaborator_user_id} added as collaborator to list {list_id}")
    except asyncpg.exceptions.UniqueViolationError:
        raise CollaboratorAlreadyExistsError(f"User {collaborator_email} is already a collaborator.")

# --- Permission Checks ---

async def check_list_ownership(db: asyncpg.Connection, list_id: int, user_id: int):
    """Checks if the user owns the list. Raises error if not owner or list not found."""
    is_owner = await db.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2)", list_id, user_id)
    if not is_owner:
        list_exists = await db.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
        if list_exists:
            raise ListAccessDeniedError("Not authorized for this list")
        else:
            raise ListNotFoundError("List not found")

async def check_list_access(db: asyncpg.Connection, list_id: int, user_id: int):
    """Checks if user is owner or collaborator. Raises error if no access or list not found."""
    # Corrected query (original had duplicate params)
    has_access_query = """
        SELECT EXISTS (
            SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2
            UNION ALL
            SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2
        )
    """
    has_access = await db.fetchval(has_access_query, list_id, user_id)

    if not has_access:
        list_exists = await db.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
        if list_exists:
            raise ListAccessDeniedError("Access denied to this list")
        else:
            raise ListNotFoundError("List not found")
    logger.debug(f"List access check passed for user {user_id} on list {list_id}")

async def delete_collaborator_from_list(db: asyncpg.Connection, list_id: int, collaborator_user_id: int) -> bool:
    """Removes a collaborator from a list. Returns True if removed, False if not found."""
    logger.info(f"Attempting to remove collaborator {collaborator_user_id} from list {list_id}")
    # Need to ensure owner cannot be removed? Depends on business logic.
    # owner_id = await db.fetchval("SELECT owner_id FROM lists WHERE id = $1", list_id)
    # if owner_id == collaborator_user_id:
    #    logger.warning(f"Attempted to remove owner {collaborator_user_id} as collaborator from list {list_id}")
    #    return False # Or raise specific error

    query = "DELETE FROM list_collaborators WHERE list_id = $1 AND user_id = $2"
    try:
        status = await db.execute(query, list_id, collaborator_user_id)
        deleted_count = int(status.split(" ")[1])
        if deleted_count > 0:
            logger.info(f"Collaborator {collaborator_user_id} removed from list {list_id}")
            return True
        else:
            logger.warning(f"Collaborator {collaborator_user_id} not found on list {list_id} for deletion.")
            return False # Collaborator was not on the list
    except Exception as e:
        logger.error(f"Error removing collaborator {collaborator_user_id} from list {list_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error removing collaborator.") from e # Use custom generic DB error


# --- List Discovery CRUD Functions ---

async def get_public_lists_paginated(db: asyncpg.Connection, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Fetches paginated public lists."""
    offset = (page - 1) * page_size
    logger.debug(f"Fetching public lists, page {page}, size {page_size}")

    count_query = "SELECT COUNT(*) FROM lists WHERE is_private = FALSE"
    total_items = await db.fetchval(count_query) or 0

    # Select fields needed by ListViewResponse schema
    fetch_query = """
        SELECT
            l.id, l.name, l.description, l.is_private,
            (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
        FROM lists l
        WHERE l.is_private = FALSE
        ORDER BY l.created_at DESC -- Or popularity, name, etc.
        LIMIT $1 OFFSET $2
    """
    lists = await db.fetch(fetch_query, page_size, offset)
    logger.debug(f"Found {len(lists)} public lists (total: {total_items})")
    return lists, total_items

async def search_lists_paginated(db: asyncpg.Connection, query: str, user_id: Optional[int], page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Searches lists by name/description. Includes user's private lists if authenticated."""
    offset = (page - 1) * page_size
    search_term = f"%{query}%"
    logger.debug(f"Searching lists for '{query}', user_id {user_id}, page {page}, size {page_size}")

    params = [search_term]
    # Use LOWER() for case-insensitive search on indexed columns if possible
    # Ensure you have trigram indexes (pg_trgm extension) or full-text search for better performance
    where_clauses = ["(LOWER(l.name) LIKE LOWER($1) OR LOWER(l.description) LIKE LOWER($1))"]
    param_idx = 2 # Next param is $2

    if user_id:
        # If user is authenticated, include their private lists
        where_clauses.append(f"(l.is_private = FALSE OR l.owner_id = ${param_idx})")
        params.append(user_id)
        param_idx += 1
    else:
        # If not authenticated, only search public lists
        where_clauses.append("l.is_private = FALSE")

    where_sql = " AND ".join(where_clauses)
    base_query = f"FROM lists l WHERE {where_sql}"

    # Count query
    count_sql = f"SELECT COUNT(*) {base_query}"
    total_items = await db.fetchval(count_sql, *params) or 0

    # Fetch query - select fields needed by ListViewResponse schema
    fetch_sql = f"""
        SELECT
            l.id, l.name, l.description, l.is_private,
            (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
        {base_query}
        ORDER BY l.created_at DESC -- Or relevance score? Or name?
        LIMIT ${param_idx} OFFSET ${param_idx + 1}
    """
    params.extend([page_size, offset])
    lists = await db.fetch(fetch_sql, *params)
    logger.debug(f"Found {len(lists)} lists matching search (total: {total_items})")
    return lists, total_items


async def get_recent_lists_paginated(db: asyncpg.Connection, user_id: int, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Fetches recently created lists (public or owned by the user), paginated."""
    # Note: This fetches lists created recently overall, filtered by user access.
    # If "recent" means recently *interacted with* by the user, the logic is more complex.
    offset = (page - 1) * page_size
    logger.debug(f"Fetching recent lists for user {user_id}, page {page}, size {page_size}")

    # Base query for filtering
    where_clause = "WHERE l.is_private = FALSE OR l.owner_id = $1"

    # Count query (Public or owned by user)
    count_query = f"SELECT COUNT(*) FROM lists l {where_clause}"
    total_items = await db.fetchval(count_query, user_id) or 0

    # Fetch query - select fields needed by ListViewResponse schema
    fetch_query = f"""
        SELECT
            l.id, l.name, l.description, l.is_private,
            (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
        FROM lists l
        {where_clause}
        ORDER BY l.created_at DESC
        LIMIT $2 OFFSET $3
    """
    lists = await db.fetch(fetch_query, user_id, page_size, offset)
    logger.debug(f"Found {len(lists)} recent lists (total: {total_items})")
    return lists, total_items

# Add get_list_by_id function if it doesn't exist (needed by permission checks)
async def get_list_by_id(db: asyncpg.Connection, list_id: int) -> Optional[asyncpg.Record]:
    """Fetches a single list by its ID."""
    # Fetches columns needed by permission checks and potentially by endpoints using it
    query = "SELECT id, owner_id, name, description, is_private FROM lists WHERE id = $1"
    list_record = await db.fetchrow(query, list_id)
    return list_record