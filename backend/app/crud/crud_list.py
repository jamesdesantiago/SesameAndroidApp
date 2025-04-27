# backend/app/crud/crud_list.py
import asyncpg
import logging
from typing import List, Optional, Tuple, Dict, Any

from app.schemas import list as list_schemas
# from app.schemas import place as place_schemas # Not strictly needed in this file

logger = logging.getLogger(__name__)

# --- Custom Exceptions ---
class ListNotFoundError(Exception): pass
class ListAccessDeniedError(Exception): pass
class CollaboratorNotFoundError(Exception): # Not currently raised, maybe remove?
    pass
class CollaboratorAlreadyExistsError(Exception): pass
class DatabaseInteractionError(Exception):
    """Generic database interaction error."""
    pass


# --- Helper Functions (Internal to CRUD) ---

async def _get_collaborator_emails(db: asyncpg.Connection, list_id: int) -> List[str]:
    """Fetches collaborator emails for a given list ID."""
    try:
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
    except Exception as e:
        logger.error(f"Error fetching collaborators for list {list_id}: {e}", exc_info=True)
        # This is an internal helper, raising a specific error for caller to handle is fine
        # Or wrap in DatabaseInteractionError if preferred. Let's wrap for consistency.
        raise DatabaseInteractionError("Database error fetching collaborators.") from e


# --- CRUD Operations ---

async def create_list(db: asyncpg.Connection, list_in: list_schemas.ListCreate, owner_id: int) -> asyncpg.Record:
    """Creates a new list in the database."""
    logger.info(f"Creating list '{list_in.name}' for owner {owner_id}")
    try:
        created_list_record = await db.fetchrow(
            """
            INSERT INTO lists (name, description, owner_id, created_at, is_private)
            VALUES ($1, $2, $3, NOW(), $4)
            RETURNING id, name, description, is_private -- Add other fields if needed by response schema
            """,
            list_in.name, list_in.description, owner_id, list_in.isPrivate
        )
        if not created_list_record:
            # This indicates a fundamental DB issue where INSERT didn't return the record
            raise DatabaseInteractionError("Failed to create list (no record returned from DB)")
        logger.info(f"List created with ID {created_list_record['id']}")
        # TODO: Handle adding initial collaborators if provided in list_in
        return created_list_record # Return basic record, caller can fetch full details if needed

    except asyncpg.PostgresError as e:
         # Catch specific asyncpg errors if you want finer-grained CRUD exceptions
         # E.g., asyncpg.exceptions.UniqueViolationError if list names were unique per user
         logger.error(f"PostgresError creating list for user {owner_id}: {e}", exc_info=True)
         raise DatabaseInteractionError("Database error creating list.") from e
    except Exception as e:
        logger.error(f"Unexpected error creating list for user {owner_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Unexpected error creating list.") from e


async def get_list_by_id(db: asyncpg.Connection, list_id: int) -> Optional[asyncpg.Record]:
    """Fetches a single list by its ID."""
    # Fetches columns needed by permission checks and potentially by endpoints using it
    query = "SELECT id, owner_id, name, description, is_private FROM lists WHERE id = $1"
    try:
        list_record = await db.fetchrow(query, list_id)
        return list_record # Return None if not found, API/deps handles 404
    except Exception as e:
         logger.error(f"Error fetching list by ID {list_id}: {e}", exc_info=True)
         raise DatabaseInteractionError("Database error fetching list by ID.") from e


async def get_list_details(db: asyncpg.Connection, list_id: int) -> Optional[Dict[str, Any]]:
    """Fetches list metadata and collaborator emails."""
    try:
        list_record = await db.fetchrow(
            "SELECT l.id, l.name, l.description, l.is_private FROM lists l WHERE l.id = $1",
            list_id
        )
        if not list_record:
            return None # Return None if list not found

        # _get_collaborator_emails might raise DatabaseInteractionError
        collaborators = await _get_collaborator_emails(db, list_id)
        list_data = dict(list_record) # Convert Record to dict
        list_data['collaborators'] = collaborators # Add collaborator list
        return list_data
    except DatabaseInteractionError:
        raise # Re-raise specific DB errors from helpers
    except Exception as e:
         logger.error(f"Error fetching list details for {list_id}: {e}", exc_info=True)
         raise DatabaseInteractionError("Database error fetching list details.") from e


async def get_user_lists_paginated(db: asyncpg.Connection, owner_id: int, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Fetches paginated lists owned by a user."""
    offset = (page - 1) * page_size
    logger.debug(f"Fetching lists for user {owner_id}, page {page}, size {page_size}")

    try:
        # Count query
        count_query = "SELECT COUNT(*) FROM lists WHERE owner_id = $1"
        total_items = await db.fetchval(count_query, owner_id) or 0

        if total_items == 0:
            return [], 0

        # Fetch query including place count (using subquery)
        fetch_query = """
            SELECT
                l.id, l.name, l.description, l.is_private,
                (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
            FROM lists l
            WHERE l.owner_id = $1
            ORDER BY l.created_at DESC LIMIT $2 OFFSET $3
        """
        list_records = await db.fetch(fetch_query, owner_id, page_size, offset)

        # The fetch query already includes place_count as 'place_count'
        # So we can just return the records directly, they should map to ListViewResponse

        logger.debug(f"Found {len(list_records)} lists for user {owner_id} (total: {total_items})")
        return list_records, total_items
    except Exception as e:
        logger.error(f"Error fetching paginated lists for user {owner_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error fetching user lists.") from e


async def update_list(db: asyncpg.Connection, list_id: int, list_in: list_schemas.ListUpdate) -> Optional[Dict[str, Any]]:
    """Updates a list's name or privacy status."""
    update_fields = list_in.model_dump(exclude_unset=True)
    if not update_fields:
        logger.warning(f"Update list called for {list_id} with no fields to update.")
        # Fetch and return current details if no updates requested
        return await get_list_details(db, list_id) # Use get_list_details to include collaborators

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

    try:
        updated_list_record = await db.fetchrow(sql, *params)
        if not updated_list_record:
            # Check if the list existed at all before the update attempt
            exists = await db.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
            if not exists:
                 return None # Return None if list didn't exist
            else:
                 # This case implies an issue, maybe concurrent deletion or permission problem
                 # (although permission is handled by API). Raising an error is best.
                 logger.error(f"Update failed for list {list_id} despite existence check")
                 raise DatabaseInteractionError("List update failed unexpectedly.")

        logger.info(f"List {list_id} updated successfully.")
        # Fetch collaborators again to return the full detail response
        collaborators = await _get_collaborator_emails(db, list_id)
        updated_data = dict(updated_list_record)
        updated_data['collaborators'] = collaborators
        return updated_data
    except DatabaseInteractionError: # Catch and re-raise specific DB errors from helpers
        raise
    except Exception as e:
        logger.error(f"Error updating list {list_id}: {e}", exc_info=True)
        # Catch any other general exception
        raise DatabaseInteractionError("Database error updating list.") from e


async def delete_list(db: asyncpg.Connection, list_id: int) -> bool:
    """Deletes a list by ID. Returns True if deleted, False otherwise."""
    # Assuming ON DELETE CASCADE handles related places, collaborators in DB schema
    try:
        status = await db.execute("DELETE FROM lists WHERE id = $1", list_id)
        deleted_count = int(status.split(" ")[1])
        if deleted_count > 0:
            logger.info(f"List {list_id} deleted successfully.")
            return True
        else:
            logger.warning(f"Attempted to delete list {list_id}, but it was not found.")
            return False # Indicate not deleted
    except Exception as e:
        logger.error(f"Error deleting list {list_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error deleting list.") from e


async def add_collaborator_to_list(db: asyncpg.Connection, list_id: int, collaborator_email: str):
    """Adds a user (by email) as a collaborator to a list."""
    # Find collaborator user ID by email. Using transaction for atomicity.
    async with db.transaction():
        try:
            collaborator_user_id = await db.fetchval("SELECT id FROM users WHERE email = $1", collaborator_email)
            if not collaborator_user_id:
                # If user does not exist, create a placeholder user with just the email
                logger.info(f"Collaborator email {collaborator_email} not found, creating placeholder user.")
                # Use ON CONFLICT DO UPDATE to handle race conditions gracefully
                collaborator_user_id = await db.fetchval(
                    "INSERT INTO users (email, created_at, updated_at) VALUES ($1, NOW(), NOW()) ON CONFLICT (email) DO UPDATE SET updated_at = NOW() RETURNING id",
                    collaborator_email
                )
                if not collaborator_user_id:
                     # This case should ideally not happen if ON CONFLICT works as expected
                     logger.error(f"Failed to create placeholder user for {collaborator_email}.")
                     raise DatabaseInteractionError(f"Failed to create user record for {collaborator_email}")
                logger.info(f"Created placeholder user with ID {collaborator_user_id} for email {collaborator_email}")


            # Insert into list_collaborators
            try:
                # Check if already collaborator to avoid UniqueViolation exception flow for common case
                is_collaborator = await db.fetchval("SELECT EXISTS(SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2)", list_id, collaborator_user_id)
                if is_collaborator:
                     logger.warning(f"User {collaborator_user_id} ({collaborator_email}) is already a collaborator on list {list_id}")
                     raise CollaboratorAlreadyExistsError(f"User {collaborator_email} is already a collaborator.")

                await db.execute(
                    "INSERT INTO list_collaborators (list_id, user_id) VALUES ($1, $2)",
                    list_id, collaborator_user_id
                )
                logger.info(f"User {collaborator_user_id} ({collaborator_email}) added as collaborator to list {list_id}")

            except asyncpg.exceptions.UniqueViolationError:
                # This specific exception should be caught by the `is_collaborator` check above,
                # but keeping this as a fallback for unexpected unique constraint issues.
                logger.error(f"UniqueViolationError adding collaborator {collaborator_user_id} to list {list_id}. Email: {collaborator_email}", exc_info=True)
                # Re-checking existence is safer than assuming it's the collaborator unique constraint
                is_collaborator = await db.fetchval("SELECT EXISTS(SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2)", list_id, collaborator_user_id)
                if is_collaborator:
                    raise CollaboratorAlreadyExistsError(f"User {collaborator_email} is already a collaborator.")
                else:
                     # Some other unexpected unique violation
                     raise DatabaseInteractionError("Database constraint violation adding collaborator.") from e

            except Exception as e:
                logger.error(f"Error adding collaborator {collaborator_email} to list {list_id}: {e}", exc_info=True)
                # Catch any non-UniqueViolation errors during the insert
                raise DatabaseInteractionError("Database error adding collaborator.") from e

        # Catch exceptions from finding/creating the user or the main insert block
        except (CollaboratorAlreadyExistsError, DatabaseInteractionError):
            raise # Re-raise our specific exceptions

        except Exception as e:
            logger.error(f"Unexpected error in add_collaborator_to_list flow for email {collaborator_email}, list {list_id}: {e}", exc_info=True)
            raise DatabaseInteractionError("Internal error adding collaborator.") from e


async def delete_collaborator_from_list(db: asyncpg.Connection, list_id: int, collaborator_user_id: int) -> bool:
    """Removes a collaborator from a list. Returns True if removed, False if not found."""
    logger.info(f"Attempting to remove collaborator user ID {collaborator_user_id} from list {list_id}")
    try:
        # Check if the user is the owner first. Owner cannot be removed via collaborator relationship.
        owner_id = await db.fetchval("SELECT owner_id FROM lists WHERE id = $1", list_id)
        if owner_id == collaborator_user_id:
           logger.warning(f"Attempted to remove owner {collaborator_user_id} as collaborator from list {list_id}")
           # Indicate they weren't removed *as a collaborator*. Caller can handle this case.
           return False

        query = "DELETE FROM list_collaborators WHERE list_id = $1 AND user_id = $2"
        status = await db.execute(query, list_id, collaborator_user_id)
        deleted_count = int(status.split(" ")[1])
        if deleted_count > 0:
            logger.info(f"Collaborator user ID {collaborator_user_id} removed from list {list_id}")
            return True
        else:
            logger.warning(f"Collaborator user ID {collaborator_user_id} not found on list {list_id} for deletion.")
            return False # Collaborator was not on the list

    except Exception as e:
        logger.error(f"Error removing collaborator user ID {collaborator_user_id} from list {list_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error removing collaborator.") from e


# --- Permission Checks ---

async def check_list_ownership(db: asyncpg.Connection, list_id: int, user_id: int):
    """Checks if the user owns the list. Raises error if not owner or list not found."""
    try:
        is_owner = await db.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2)", list_id, user_id)
        if not is_owner:
            # To provide a specific 404 vs 403, we check if the list exists at all
            list_exists = await db.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
            if list_exists:
                # List exists, but user is not the owner
                raise ListAccessDeniedError("Not authorized for this list")
            else:
                # List does not exist
                raise ListNotFoundError("List not found")
        logger.debug(f"List ownership check passed for user {user_id} on list {list_id}")
    except (ListAccessDeniedError, ListNotFoundError):
         raise # Re-raise specific exceptions
    except Exception as e:
         logger.error(f"Error during ownership check for list {list_id} by user {user_id}: {e}", exc_info=True)
         raise DatabaseInteractionError("Database error during ownership check.") from e


async def check_list_access(db: asyncpg.Connection, list_id: int, user_id: int):
    """Checks if user is owner or collaborator. Raises error if no access or list not found."""
    try:
        # Use UNION ALL to check ownership or collaboration
        has_access_query = """
            SELECT EXISTS (
                SELECT 1 FROM lists WHERE id = $1 AND owner_id = $2
                UNION ALL
                SELECT 1 FROM list_collaborators WHERE list_id = $1 AND user_id = $2
            )
        """
        has_access = await db.fetchval(has_access_query, list_id, user_id)

        if not has_access:
            # Check if the list exists to differentiate 404 vs 403
            list_exists = await db.fetchval("SELECT EXISTS (SELECT 1 FROM lists WHERE id = $1)", list_id)
            if list_exists:
                # List exists, but user is neither owner nor collaborator
                raise ListAccessDeniedError("Access denied to this list")
            else:
                # List does not exist
                raise ListNotFoundError("List not found")
        logger.debug(f"List access check passed for user {user_id} on list {list_id}")
    except (ListAccessDeniedError, ListNotFoundError):
         raise # Re-raise specific exceptions
    except Exception as e:
         logger.error(f"Error during access check for list {list_id} by user {user_id}: {e}", exc_info=True)
         raise DatabaseInteractionError("Database error during access check.") from e


# --- List Discovery CRUD Functions ---

async def get_public_lists_paginated(db: asyncpg.Connection, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Fetches paginated public lists."""
    offset = (page - 1) * page_size
    logger.debug(f"Fetching public lists, page {page}, size {page_size}")

    try:
        count_query = "SELECT COUNT(*) FROM lists WHERE is_private = FALSE"
        total_items = await db.fetchval(count_query) or 0

        if total_items == 0:
            return [], 0

        # Select fields needed by ListViewResponse schema, including place_count
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
    except Exception as e:
        logger.error(f"Error fetching public lists paginated: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error fetching public lists.") from e


async def search_lists_paginated(db: asyncpg.Connection, query: str, user_id: Optional[int], page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Searches lists by name/description. Includes user's private lists if authenticated."""
    offset = (page - 1) * page_size
    search_term_lower = f"%{query.lower()}%" # Use lower() for case-insensitive search
    logger.debug(f"Searching lists for '{query}', user_id {user_id}, page {page}, size {page_size}")

    params = [search_term_lower]
    param_idx = 2 # Next param starts at $2

    # Use LOWER() on DB columns for case-insensitive search
    where_clauses = ["(LOWER(l.name) LIKE $1 OR LOWER(l.description) LIKE $1)"]

    if user_id is not None: # Check if user_id is provided (authenticated)
        # If user is authenticated, include their private lists AND public lists
        # The condition becomes: (matches query AND (is public OR is owner))
        where_clauses.append(f"(l.is_private = FALSE OR l.owner_id = ${param_idx})")
        params.append(user_id)
        param_idx += 1
    else:
        # If not authenticated, only search public lists
        # The condition becomes: (matches query AND is public)
        where_clauses.append("l.is_private = FALSE")

    # Combine where clauses. The query match and the access check are separate conditions.
    # Correct logic: Find lists that match the query AND (are public OR are owned by the user)
    # The structure (A AND B) is correct if A is query match and B is access check.
    # Let's build the WHERE clause more explicitly based on conditions:
    # If authenticated: (LOWER(name) LIKE $1 OR LOWER(desc) LIKE $1) AND (is_private = FALSE OR owner_id = $2)
    # If not authenticated: (LOWER(name) LIKE $1 OR LOWER(desc) LIKE $1) AND is_private = FALSE
    # This is correctly handled by adding `(is_private = FALSE OR owner_id = $X)` or `is_private = FALSE`
    # as *another* clause ANDed with the initial search term clause.

    full_where_sql = " AND ".join(where_clauses)

    base_query_from = f"FROM lists l WHERE {full_where_sql}"

    try:
        # Count query
        count_sql = f"SELECT COUNT(*) {base_query_from}"
        total_items = await db.fetchval(count_sql, *params) or 0

        if total_items == 0:
            return [], 0

        # Fetch query - select fields needed by ListViewResponse schema, including place_count
        fetch_sql = f"""
            SELECT
                l.id, l.name, l.description, l.is_private,
                (SELECT COUNT(*) FROM places p WHERE p.list_id = l.id) as place_count
            {base_query_from}
            ORDER BY l.created_at DESC -- Consider relevance score if using full-text search
            LIMIT ${param_idx} OFFSET ${param_idx + 1}
        """
        params.extend([page_size, offset])
        lists = await db.fetch(fetch_sql, *params)
        logger.debug(f"Found {len(lists)} lists matching search (total: {total_items})")
        return lists, total_items
    except Exception as e:
        logger.error(f"Error searching lists paginated for '{query}': {e}", exc_info=True)
        raise DatabaseInteractionError("Database error searching lists.") from e


async def get_recent_lists_paginated(db: asyncpg.Connection, user_id: int, page: int, page_size: int) -> Tuple[List[asyncpg.Record], int]:
    """Fetches recently created lists (public or owned by the user), paginated."""
    # Note: This fetches lists created recently overall, filtered by user access.
    # If "recent" means recently *interacted with* by the user, the logic is more complex.
    offset = (page - 1) * page_size
    logger.debug(f"Fetching recent lists for user {user_id}, page {page}, size {page_size}")

    try:
        # Base query for filtering: lists that are public OR owned by the user
        where_clause = "WHERE l.is_private = FALSE OR l.owner_id = $1"

        # Count query (Public or owned by user)
        count_query = f"SELECT COUNT(*) FROM lists l {where_clause}"
        total_items = await db.fetchval(count_query, user_id) or 0

        if total_items == 0:
             return [], 0

        # Fetch query - select fields needed by ListViewResponse schema, including place_count
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
    except Exception as e:
        logger.error(f"Error fetching recent lists paginated for user {user_id}: {e}", exc_info=True)
        raise DatabaseInteractionError("Database error fetching recent lists.") from e