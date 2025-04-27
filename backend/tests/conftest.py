# backend/tests/conftest.py
import os
import sys
import functools
import contextvars
import unittest # <--- KEEP unittest import

# --- NO sys.path modification block ---

# --- Standard Library and Third-Party Imports ---
import pytest
import pytest_asyncio
import asyncio
import asyncpg
from httpx import AsyncClient
# Import types needed for type hints
from typing import AsyncGenerator, Dict, Any, Optional

from dotenv import load_dotenv
from functools import lru_cache
# Import patch, AsyncMock, call, MagicMock from unittest.mock
from unittest.mock import patch, AsyncMock, call, MagicMock

# --- Application Code Imports (Using 'backend.' prefix) ---
# Import logging setup module early
import backend.app.core.logging # <--- Use backend. prefix

# Import necessary FastAPI components
from fastapi import Depends, HTTPException, status, Header
# Import FixtureRequest for type hint
from pytest import FixtureRequest

# Import helpers from tests.utils
from backend.tests.utils import ( # <--- Use backend.tests. prefix
    create_mock_record,
    create_test_user_direct,
    create_test_list_direct,
    create_test_place_direct,
    add_collaborator_direct,
    create_notification_direct,
    create_follow_direct
)

# Now import app components using the backend. prefix consistently
try:
    from backend.main import app
    from backend.app.core.config import settings
    from backend.app.db.base import db_pool, init_db_pool, close_db_pool
    from backend.app.api import deps
    from backend.app.schemas.token import FirebaseTokenData
    from backend.app.crud import crud_user
    from backend.app.crud import crud_list
    from backend.app.crud import crud_place
except ImportError as e:
    print(f"!!! Error importing application components in conftest: {e} !!!")
    print(f"Import error name: {e.name}")
    print(f"Import error path: {getattr(e, 'path', 'N/A')}")
    raise e
except Exception as e:
     print(f"!!! Unexpected error during app component import in conftest: {e} !!!")
     raise e


# Get the logger instance for this module
logger = backend.app.core.logging.get_logger(__name__) # <--- Use backend. prefix


# --- Test Client and DB Fixtures ---

@pytest.fixture(scope="session")
def event_loop(request: FixtureRequest):
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()

@pytest_asyncio.fixture(scope="session", autouse=True)
async def lifespan_db_pool_manager():
    # Use the logger instance defined at the module level
    logger.info(f"\n---> Initializing DB pool for testing (Session Scope) <---\n Target DB: {settings.DATABASE_URL}")
    # Safety checks...
    if "production" in settings.ENVIRONMENT.lower() or "prod" in settings.DB_NAME.lower():
         pytest.fail(f"ABORTING: Test configuration points to a potential production environment or database name ({settings.ENVIRONMENT}, {settings.DB_NAME}). Update .env.test.", pytrace=False)
    if settings.ENVIRONMENT != "test":
         logger.warning(f"ENVIRONMENT is not set to 'test' in .env.test. Current: '{settings.ENVIRONMENT}'"
               " Ensure you are running tests against a safe, disposable database.")

    try:
        await init_db_pool()
        logger.info(f"---> Test DB pool initialized (DB: {settings.DB_NAME}@{settings.DB_HOST}) <---")
        yield
        logger.info("\n---> Closing test DB pool (Session Scope) <---")
        await close_db_pool()
        logger.info("---> Test DB pool closed <---")
    except Exception as e:
         logger.critical(f"Failed to initialize/close test DB pool during setup: {e}", exc_info=True)
         pytest.fail(f"Failed to initialize/close test DB pool during setup: {e}", pytrace=False)


@pytest_asyncio.fixture(scope="function")
async def db_conn(lifespan_db_pool_manager) -> AsyncGenerator[asyncpg.Connection, None]:
    if not db_pool:
        logger.critical("DB Pool is not available when attempting to acquire connection in db_conn fixture.")
        pytest.fail("DB Pool not available in db_conn fixture (lifespan setup failed?).", pytrace=False)

    connection = None
    try:
        connection = await db_pool.acquire()
    except Exception as e:
         logger.critical(f"Error acquiring connection from DB pool in db_conn fixture: {e}", exc_info=True)
         pytest.fail(f"Error acquiring DB connection for test: {e}", pytrace=False)

    try:
        yield connection
    finally:
        logger.debug(f"   [DB RAW CONN] Releasing connection {id(connection)}...")
        if connection and not connection.is_closed():
            try:
                if connection.is_in_transaction():
                     logger.error(f"Test DB connection {id(connection)} still in transaction! Rolling back before release.")
                     try: await connection.rollback()
                     except Exception as rollback_e: logger.error(f"!!! ERROR during rollback before release of in-transaction connection {id(connection)}: {rollback_e} !!!", exc_info=True)
                await db_pool.release(connection, timeout=10)
                logger.debug(f"   [DB RAW CONN] Connection {id(connection)} released.")
            except Exception as e:
                 logger.error(f"!!! ERROR releasing test DB connection {id(connection)} to pool: {e} !!!", exc_info=True)
        elif connection and connection.is_closed():
             logger.warning(f"Test DB connection {id(connection)} was closed prematurely! Cannot release.")
        else:
             logger.error("Attempted to release DB connection, but connection object was None or invalid.")


@pytest_asyncio.fixture(scope="function")
async def db_tx(db_conn: asyncpg.Connection) -> AsyncGenerator[asyncpg.Connection, None]:
    print("   [DB TXN] Starting transaction...")
    try:
        tr = db_conn.transaction()
        await tr.start()
        print(f"   [DB TXN] Yielding connection (in transaction {id(tr)})...")
        yield db_conn
    except Exception as e:
        logger.error(f"Error during transaction setup or test execution within transaction: {e}", exc_info=True)
        raise e
    finally:
        print(f"   [DB TXN] Rolling back transaction {id(tr)}...")
        if db_conn and db_conn.is_in_transaction():
             try:
                await tr.rollback()
                print("   [DB TXN] Transaction rolled back.")
             except Exception as e:
                 logger.error(f"!!! ERROR during transaction rollback {id(tr)}: {e} !!!", exc_info=True)
        else:
            print("   [DB TXN] Transaction was already closed (committed or rolled back).")


@pytest_asyncio.fixture(scope="function")
async def client(db_tx: asyncpg.Connection) -> AsyncGenerator[AsyncClient, None]:
    async def override_get_db() -> AsyncGenerator[asyncpg.Connection, None]:
        yield db_tx

    # Use the backend. prefix for the dependency function path
    original_get_db = backend.app.api.deps.get_db # <--- Keep backend. prefix
    app.dependency_overrides[original_get_db] = override_get_db

    async with AsyncClient(app=app, base_url="http://testserver") as test_client:
        yield test_client

    app.dependency_overrides.clear()

# --- Test Data Fixtures ---
@pytest_asyncio.fixture(scope="function")
async def test_user1(db_tx: asyncpg.Connection) -> Dict[str, Any]:
    user_data = await create_test_user_direct(db_tx, "fixture1_tx")
    print(f"   [Fixture test_user1 ID: {user_data.get('id', 'N/A')}] Creating user (in transaction)...")
    yield user_data
    print(f"   [Fixture test_user1 ID: {user_data.get('id', 'N/A')}] Teardown (transaction rollback handles cleanup)...")


@pytest_asyncio.fixture(scope="function")
async def test_user2(db_tx: asyncpg.Connection) -> Dict[str, Any]:
    user_data = await create_test_user_direct(db_tx, "fixture2_tx")
    print(f"   [Fixture test_user2 ID: {user_data.get('id', 'N/A')}] Creating user (in transaction)...")
    yield user_data
    print(f"   [Fixture test_user2 ID: {user_data.get('id', 'N/A')}] Teardown (transaction rollback handles cleanup)...")


@pytest_asyncio.fixture(scope="function")
async def test_list1(db_tx: asyncpg.Connection, test_user1: Dict[str, Any]) -> Dict[str, Any]:
    owner_id = test_user1["id"]
    list_name = f"Test List 1 for {owner_id}_{os.urandom(3).hex()}"
    list_data = await create_test_list_direct(db_tx, owner_id, list_name, False)
    print(f"   [Fixture test_list1 ID: {list_data.get('id', 'N/A')}] Creating list (in transaction)...")
    yield list_data
    print(f"   [Fixture test_list1 ID: {list_data.get('id', 'N/A')}] Teardown (transaction rollback handles cleanup)...")


# --- Helper for Mocking Authentication ---

class MockFirebaseAuth:
    def __init__(self, token_data: Optional[FirebaseTokenData] = None, exception: Optional[Exception] = None):
        self._token_data = token_data
        self._exception = exception

    async def __call__(self, authorization: Optional[str] = Header(None, alias="Authorization")):
        if self._exception: raise self._exception
        if self._token_data: return self._token_data
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Mock Auth: No valid token provided")

@pytest.fixture(scope="function")
def mock_auth(test_user1: Dict[str, Any]):
     mock_token = FirebaseTokenData(uid=test_user1["firebase_uid"], email=test_user1["email"])
     dependency_path = "backend.app.api.deps.get_verified_token_data" # <--- Use backend. prefix

     patcher = patch(dependency_path, new_callable=lambda: MockFirebaseAuth(token_data=mock_token))
     patcher.start()
     yield
     patcher.stop()

@pytest.fixture(scope="function")
def mock_auth_invalid():
     exc = HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Mock Auth: Invalid Token")
     dependency_path = "backend.app.api.deps.get_verified_token_data" # <--- Use backend. prefix

     patcher = patch(dependency_path, new_callable=lambda: MockFirebaseAuth(exception=exc))
     patcher.start()
     yield
     patcher.stop()

@pytest.fixture(scope="function")
def mock_auth_optional(test_user1: Dict[str, Any]):
     mock_token = FirebaseTokenData(uid=test_user1["firebase_uid"], email=test_user1["email"])
     dependency_path = "backend.app.api.deps.get_optional_verified_token_data" # <--- Use backend. prefix

     patcher = patch(dependency_path, new_callable=lambda: MockFirebaseAuth(token_data=mock_token))
     patcher.start()
     yield
     patcher.stop()

@pytest.fixture(scope="function")
def mock_auth_optional_unauthenticated():
    dependency_path = "backend.app.api.deps.get_optional_verified_token_data" # <--- Use backend. prefix

    patcher = patch(dependency_path, new_callable=lambda: MockFirebaseAuth(token_data=None)) # Return None
    patcher.start()
    yield
    patcher.stop()