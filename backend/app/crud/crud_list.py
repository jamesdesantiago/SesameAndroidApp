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
        SELECT
            l.id, l.name, l.description, l.is_private,
            (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
        FROM lists l
        WHERE l.owner_id = $1
        ORDER BY l.created_at DESC
        LIMIT $2 OFFSET $3
    """
    lists = await db.fetch(fetch_query, owner_id, page_size, offset)
    return lists, total_items

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


# Add functions for get_public_lists, search_lists, get_recent_lists if needed