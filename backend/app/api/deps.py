# backend/app/api/deps.py
import logging
from typing import Optional, AsyncGenerator # Use AsyncGenerator for async yield

import asyncpg
from fastapi import Depends, HTTPException, Header, status, Request, Path

# Import schemas, crud, db base, config, firebase_admin
from app.schemas import user as user_schemas
from app.schemas import token as token_schemas
from app.crud import crud_user, crud_list # Import crud modules
from app.db.base import db_pool # Import the pool instance
from app.core.config import settings # Import settings if needed

# Firebase Admin SDK (initialized in main.py)
from firebase_admin import auth as firebase_auth

logger = logging.getLogger(__name__)

# --- Database Dependency ---
async def get_db() -> AsyncGenerator[asyncpg.Connection, None]:
    """
    FastAPI dependency that provides an asyncpg connection from the pool.
    Handles acquiring and releasing the connection.
    """
    if not db_pool:
        # This should ideally not happen if lifespan startup succeeded
        logger.error("Database pool is not available when trying to get connection.")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Database service is not available.",
        )

    connection = None
    try:
        # Acquire connection using async with, handles release automatically
        async with db_pool.acquire() as conn:
            # Yield the connection to the endpoint function
            yield conn
    except asyncpg.PostgresError as db_err:
        logger.error(f"Database connection error during request processing: SQLSTATE={db_err.sqlstate} - {db_err}", exc_info=True)
        # Re-raise as HTTPException for FastAPI to handle
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="A database error occurred.")
    except Exception as e:
        logger.error(f"Unexpected error acquiring/using DB connection: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="An internal server error occurred.")
    # Connection is automatically released when exiting the 'async with db_pool.acquire()' block

# --- Authentication/Authorization Dependencies ---

async def get_verified_token_data(
    authorization: Optional[str] = Header(None, alias="Authorization") # Match original header name
) -> token_schemas.FirebaseTokenData:
    """
    Dependency to verify the Firebase ID token from the Authorization header.
    Returns a Pydantic model containing the verified token data.
    Raises HTTPException 401/403 on failure.
    """
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    invalid_token_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired token",
        headers={"WWW-Authenticate": "Bearer error=\"invalid_token\""},
    )

    if not authorization or not authorization.startswith("Bearer "):
        logger.warning("Missing or invalid Authorization Bearer header.")
        raise credentials_exception

    token = authorization.split("Bearer ")[1] # Safer split

    try:
        decoded_token = firebase_auth.verify_id_token(token)
        logger.debug(f"Token verified for uid: {decoded_token.get('uid')}")

        # Validate essential fields and map to Pydantic model
        try:
            token_data = token_schemas.FirebaseTokenData(**decoded_token)
            # Ensure critical fields are present after mapping
            if not token_data.uid:
                 raise ValueError("Token 'uid' missing after validation")
            # Add check for email if it's strictly required by your app logic
            # if not token_data.email:
            #      raise ValueError("Token 'email' missing after validation")

            return token_data
        except Exception as pydantic_error: # Catch Pydantic validation errors or missing fields
            logger.error(f"Decoded token failed Pydantic validation: {pydantic_error}", exc_info=True)
            raise invalid_token_exception # Treat validation errors as invalid token

    except firebase_auth.ExpiredIdTokenError:
         logger.warning("Firebase token has expired.")
         raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired",
            headers={"WWW-Authenticate": "Bearer error=\"invalid_token\", error_description=\"Token expired\""},
        )
    except firebase_auth.InvalidIdTokenError as e:
        logger.error(f"Firebase token is invalid: {e}", exc_info=False) # Avoid logging the token itself
        raise invalid_token_exception
    except Exception as e: # Catch other Firebase verification errors or unexpected issues
        logger.error(f"Firebase token verification failed unexpectedly: {e}", exc_info=True)
        raise credentials_exception


async def get_current_user_record(
    db: asyncpg.Connection = Depends(get_db),
    token_data: token_schemas.FirebaseTokenData = Depends(get_verified_token_data)
) -> asyncpg.Record:
    """
    Dependency to get the full user record from the database
    based on the verified Firebase token. Creates the user if they don't exist.
    Raises HTTPException 404 if user cannot be found/created.
    """
    try:
        user_id, _ = await crud_user.get_or_create_user_by_firebase(db=db, token_data=token_data)
        # Fetch the full record after ensuring the user exists
        user_record = await crud_user.get_user_by_id(db=db, user_id=user_id)
        if not user_record:
            # This indicates a potential inconsistency if get_or_create succeeded but get failed
            logger.error(f"User record not found for ID {user_id} after get_or_create succeeded.")
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")
        return user_record
    except HTTPException as he:
        raise he # Propagate HTTP exceptions from underlying calls
    except Exception as e:
        logger.error(f"Error getting/creating user record for firebase uid {token_data.uid}: {e}", exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Error retrieving user information.")


async def get_current_user_id(
    user_record: asyncpg.Record = Depends(get_current_user_record)
) -> int:
    """
    Dependency to simply extract the user ID (database primary key)
    from the current user's database record.
    """
    # The user_record dependency already ensures the user exists
    return user_record['id']


# --- Optional: Permission Dependencies ---

async def get_list_and_verify_ownership(
    list_id: int = Path(...), # Extract list_id from path parameter
    db: asyncpg.Connection = Depends(get_db),
    current_user_id: int = Depends(get_current_user_id)
) -> asyncpg.Record: # Or return the specific Pydantic schema if preferred
    """
    Dependency to fetch a list by ID and verify the current user owns it.
    Raises 404 if list not found, 403 if not owner.
    Returns the list record on success.
    """
    try:
        # Use a CRUD function that specifically checks ownership
        list_record = await crud_list.get_list_by_id(db=db, list_id=list_id) # Fetch first
        if not list_record:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
        if list_record['owner_id'] != current_user_id:
            logger.warning(f"Ownership check failed: User {current_user_id} does not own list {list_id}")
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized for this list")
        return list_record # Return the fetched record
    except HTTPException as he:
        raise he
    except Exception as e:
         logger.error(f"Error verifying ownership for list {list_id} user {current_user_id}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error checking list ownership")

async def get_list_and_verify_access(
    list_id: int = Path(...), # Extract list_id from path parameter
    db: asyncpg.Connection = Depends(get_db),
    current_user_id: int = Depends(get_current_user_id)
) -> asyncpg.Record: # Or return the specific Pydantic schema if preferred
    """
    Dependency to fetch a list by ID and verify the current user has access
    (is owner or collaborator).
    Raises 404 if list not found, 403 if no access.
    Returns the list record on success.
    """
    try:
        # Use the CRUD permission check function
        await crud_list.check_list_access(db=db, list_id=list_id, user_id=current_user_id)
        # If check passes, fetch the list record (optional, if needed by endpoint)
        list_record = await crud_list.get_list_by_id(db=db, list_id=list_id) # Assumes get_list_by_id exists
        if not list_record:
            # Should have been caught by check_list_access, but double-check
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
        return list_record
    except crud_list.ListNotFoundError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found")
    except crud_list.ListAccessDeniedError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to this list")
    except HTTPException as he:
        raise he
    except Exception as e:
         logger.error(f"Error verifying access for list {list_id} user {current_user_id}", exc_info=True)
         raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Internal server error checking list access")