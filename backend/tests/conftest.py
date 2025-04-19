# backend/tests/conftest.py

import os
import pytest
import pytest_asyncio
import asyncio
import asyncpg
from httpx import AsyncClient
from typing import AsyncGenerator, Dict, Any
from dotenv import load_dotenv
from unittest.mock import patch, AsyncMock # For mocking auth later

# --- Set Test Environment ---
# Force loading .env.test by setting DOTENV_PATH *before* importing settings/app
# Assumes config.py checks for this environment variable
base_dir = os.path.dirname(os.path.dirname(__file__)) # backend/ directory
test_env_path = os.path.join(base_dir, '.env.test')
print(f"\n---> Setting DOTENV_PATH for pytest session: {test_env_path} <---")
os.environ['DOTENV_PATH'] = test_env_path

# Now import app components which will use the test settings
# Use try/except for better error message during test collection if imports fail
try:
    from app.main import app
    from app.core.config import settings # Settings will load from .env.test now
    from app.db.base import db_pool, init_db_pool, close_db_pool
    from app.api import deps
    from app.schemas.token import FirebaseTokenData # Needed for auth mocking type hints
except ImportError as e:
    pytest.fail(f"Failed to import app components during conftest setup. "
                f"Is PYTHONPATH set correctly or are you running pytest from the 'backend' directory? Error: {e}",
                pytrace=False)
except Exception as e:
     pytest.fail(f"Error during app component import after setting test environment: {e}", pytrace=False)

# --- Test Client and DB Fixtures ---

@pytest.fixture(scope="session")
def event_loop(request):
    """Create an instance of the default event loop for each test case session."""
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()

@pytest_asyncio.fixture(scope="session", autouse=True)
async def lifespan_db_pool_manager():
    """
    Manages the DB pool connection for the entire test session using app lifespan.
    Connects to the database specified in .env.test (dev DB for now).
    Includes safety check against production DB names.
    """
    print(f"\n---> Initializing DB pool for testing (Session Scope) <---\n Target DB: {settings.DATABASE_URL}")
    # Safety check - Adjust "prod_db_name" if your prod name is different
    if settings.DB_NAME == "prod_db_name" or "production" in settings.DB_NAME.lower():
         pytest.fail(f"ABORTING: Test configuration points to a production database name ({settings.DB_NAME}). Update .env.test.", pytrace=False)
    if settings.ENVIRONMENT != "test":
         pytest.fail(f"ABORTING: ENVIRONMENT is not set to 'test' in .env.test. Current: '{settings.ENVIRONMENT}'", pytrace=False)

    try:
        await init_db_pool() # Uses settings loaded from .env.test
        print(f"---> Test DB pool initialized (DB: {settings.DB_NAME}@{settings.DB_HOST}) <---")
        yield # Test session runs here
        print("\n---> Closing test DB pool (Session Scope) <---")
        await close_db_pool()
        print("---> Test DB pool closed <---")
    except Exception as e:
         pytest.fail(f"Failed to initialize/close test DB pool: {e}\n"
                     f"Check DB connection string in .env.test and database server status.", pytrace=False)

@pytest_asyncio.fixture(scope="function")
async def db_conn(lifespan_db_pool_manager) -> AsyncGenerator[asyncpg.Connection, None]:
    """
    Provides a direct connection from the pool for a test function.
    !!! WARNING: NO AUTOMATIC ROLLBACK. Tests modify the database specified in .env.test !!!
    !!! MANUAL CLEANUP VIA FIXTURES/TEARDOWN IS REQUIRED !!!
    """
    if not db_pool:
        pytest.fail("DB Pool not available in db_conn fixture (lifespan issue?).", pytrace=False)

    print(f"   [DB NO_TXN] Acquiring connection from pool {id(db_pool)}...")
    connection = await db_pool.acquire()
    print(f"   [DB NO_TXN] Yielding connection {id(connection)}.")
    try:
        yield connection # Hand the raw connection to the test/fixture
    finally:
        print(f"   [DB NO_TXN] Releasing connection {id(connection)}...")
        try:
            await db_pool.release(connection, timeout=10) # Add timeout
            print(f"   [DB NO_TXN] Connection released.")
        except Exception as e:
             # Log error during release but don't obscure original test failure
             print(f"\n!!! ERROR releasing test DB connection: {e} !!!\n")


@pytest_asyncio.fixture(scope="function")
async def client(db_conn: asyncpg.Connection) -> AsyncGenerator[AsyncClient, None]:
    """
    Provides an httpx client configured for the test app.
    Overrides the 'get_db' dependency to use the function-scoped db_conn fixture.
    """
    async def override_get_db() -> AsyncGenerator[asyncpg.Connection, None]:
        # Provides the connection acquired by the db_conn fixture for this test function
        yield db_conn

    original_get_db = deps.get_db # Store original if needed elsewhere
    app.dependency_overrides[deps.get_db] = override_get_db
    # Use base_url="http://testserver" standard practice with httpx+FastAPI test client
    async with AsyncClient(app=app, base_url="http://testserver") as test_client:
        print("  [HTTP Client START]")
        yield test_client
        print("  [HTTP Client END]")

    app.dependency_overrides.clear() # Clean up overrides after test function finishes

# --- Test Data Fixtures (Examples with Manual Cleanup) ---

@pytest_asyncio.fixture(scope="function")
async def test_user1(db_conn: asyncpg.Connection) -> AsyncGenerator[Dict[str, Any], None]:
    """ Creates a user for a test and cleans it up afterwards. """
    suffix = os.urandom(4).hex()
    email = f"test1_{suffix}@example.com"
    fb_uid = f"test_firebase_uid_1_{suffix}"
    username = f"testuser1_{suffix}"
    display_name = f"Test User 1 {suffix}"
    user_id = None # Initialize user_id

    try:
        # Insert user
        print(f"   [Fixture test_user1] Creating user {username}...")
        user_id = await db_conn.fetchval(
            """
            INSERT INTO users (email, firebase_uid, username, display_name, created_at, updated_at)
            VALUES ($1, $2, $3, $4, NOW(), NOW())
            ON CONFLICT (email) DO UPDATE SET updated_at = NOW() -- Example conflict handling
            RETURNING id
            """,
            email, fb_uid, username, display_name
        )
        if not user_id: user_id = await db_conn.fetchval("SELECT id FROM users WHERE email = $1", email)
        if not user_id: pytest.fail(f"Failed to create or find test user {email} in fixture.")

        print(f"   [Fixture test_user1 ID: {user_id}] ")
        # Yield data needed by tests
        yield {"id": user_id, "email": email, "firebase_uid": fb_uid, "username": username }

    finally:
        # --- MANUAL CLEANUP ---
        if user_id is not None:
            print(f"   [Fixture test_user1 Teardown] Deleting user {user_id}...")
            try:
                # IMPORTANT: Add deletions for related data IF NOT using ON DELETE CASCADE
                # await db_conn.execute("DELETE FROM user_follows WHERE follower_id = $1 OR followed_id = $1", user_id)
                # await db_conn.execute("DELETE FROM list_collaborators WHERE user_id = $1", user_id)
                # await db_conn.execute("DELETE FROM notifications WHERE user_id = $1", user_id)
                # await db_conn.execute("DELETE FROM lists WHERE owner_id = $1", user_id) # If not cascaded
                await db_conn.execute("DELETE FROM users WHERE id = $1", user_id)
                print(f"   [Fixture test_user1 Teardown] Deleted user {user_id}.")
            except Exception as e:
                print(f"   [Fixture test_user1 Teardown] !!! ERROR deleting user {user_id}: {e} !!!")


@pytest_asyncio.fixture(scope="function")
async def test_user2(db_conn: asyncpg.Connection) -> AsyncGenerator[Dict[str, Any], None]:
    """ Creates a second distinct user for a test and cleans it up afterwards. """
    suffix = os.urandom(4).hex()
    email = f"test2_{suffix}@example.com"
    fb_uid = f"test_firebase_uid_2_{suffix}"
    username = f"testuser2_{suffix}"
    user_id = None
    try:
        user_id = await db_conn.fetchval(
            "INSERT INTO users (email, firebase_uid, username) VALUES ($1, $2, $3) RETURNING id",
            email, fb_uid, username
        )
        if not user_id: pytest.fail(f"Failed to create test user {email} in fixture.")
        print(f"   [Fixture test_user2 ID: {user_id}] ")
        yield {"id": user_id, "email": email, "firebase_uid": fb_uid, "username": username}
    finally:
        if user_id:
            print(f"   [Fixture test_user2 Teardown] Deleting user {user_id}...")
            try:
                # Add related data cleanup if needed
                await db_conn.execute("DELETE FROM users WHERE id = $1", user_id)
                print(f"   [Fixture test_user2 Teardown] Deleted user {user_id}.")
            except Exception as e:
                print(f"   [Fixture test_user2 Teardown] !!! ERROR deleting user {user_id}: {e} !!!")


@pytest_asyncio.fixture(scope="function")
async def test_list1(db_conn: asyncpg.Connection, test_user1: Dict[str, Any]) -> AsyncGenerator[Dict[str, Any], None]:
    """ Creates a list owned by test_user1 and cleans it up afterwards. """
    owner_id = test_user1["id"]
    list_name = f"Test List 1 for {owner_id}_{os.urandom(3).hex()}"
    list_id = None
    try:
        list_id = await db_conn.fetchval(
            "INSERT INTO lists (owner_id, name, is_private) VALUES ($1, $2, $3) RETURNING id",
            owner_id, list_name, False # Example: Public list
        )
        if not list_id: pytest.fail(f"Failed to create test list for user {owner_id}")
        print(f"   [Fixture test_list1 ID: {list_id}] ")
        yield {"id": list_id, "owner_id": owner_id, "name": list_name}
    finally:
        if list_id:
             print(f"   [Fixture test_list1 Teardown] Deleting list {list_id}...")
             try:
                 # Assumes places/collaborators are deleted via ON DELETE CASCADE
                 await db_conn.execute("DELETE FROM lists WHERE id = $1", list_id)
                 print(f"   [Fixture test_list1 Teardown] Deleted list {list_id}.")
             except Exception as e:
                 print(f"   [Fixture test_list1 Teardown] !!! ERROR deleting list {list_id}: {e} !!!")


# --- Helper for Mocking Authentication ---

class MockFirebaseAuth:
    """ Simple class to help mock firebase auth dependency """
    def __init__(self, token_data: Optional[FirebaseTokenData] = None, exception: Optional[Exception] = None):
        self._token_data = token_data
        self._exception = exception

    async def __call__(self, authorization: Optional[str] = Header(None, alias="Authorization")):
        print(f"  [Auth Mock] Called with Authorization: {'Present' if authorization else 'Missing'}")
        if self._exception:
            print(f"  [Auth Mock] Raising predefined exception: {type(self._exception).__name__}")
            raise self._exception
        if self._token_data:
            print(f"  [Auth Mock] Returning mock token data for UID: {self._token_data.uid}")
            return self._token_data
        # Default behavior if no token/exception specified: raise credentials error
        print(f"  [Auth Mock] No token data/exception set, raising 401.")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Mock Auth: No valid token provided")

@pytest.fixture(scope="function")
def mock_auth(test_user1: Dict[str, Any]):
     """ Creates a patcher for the auth dependency for a specific user """
     mock_token = FirebaseTokenData(uid=test_user1["firebase_uid"], email=test_user1["email"])
     dependency_path = "app.api.deps.get_verified_token_data"

     print(f"  [Fixture mock_auth] Patching {dependency_path} for user {test_user1['id']}")
     patcher = patch(dependency_path, new_callable=lambda: MockFirebaseAuth(token_data=mock_token))
     patcher.start()
     yield # Test runs with auth mocked
     print(f"  [Fixture mock_auth] Stopping patch for {dependency_path}")
     patcher.stop()

@pytest.fixture(scope="function")
def mock_auth_invalid():
     """ Creates a patcher for auth that raises an invalid token error """
     exc = HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Mock Auth: Invalid Token")
     dependency_path = "app.api.deps.get_verified_token_data"

     print(f"  [Fixture mock_auth_invalid] Patching {dependency_path} to raise {type(exc).__name__}")
     patcher = patch(dependency_path, new_callable=lambda: MockFirebaseAuth(exception=exc))
     patcher.start()
     yield
     print(f"  [Fixture mock_auth_invalid] Stopping patch for {dependency_path}")
     patcher.stop()