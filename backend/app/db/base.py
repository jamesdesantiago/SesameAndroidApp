# backend/app/db/base.py
import logging
import asyncio
from typing import Optional

import asyncpg
from app.core.config import settings # Import the settings instance

logger = logging.getLogger(__name__)

# Global pool variable
db_pool: Optional[asyncpg.Pool] = None

async def init_db_pool():
    """Initializes the asyncpg connection pool."""
    global db_pool
    if db_pool:
        logger.warning("Database pool already initialized.")
        return

    logger.info("Initializing asyncpg database pool...")
    retries = 5
    delay_seconds = 5
    while retries > 0:
        try:
            # Ensure DATABASE_URL is correctly loaded
            if not settings.DATABASE_URL:
                 raise ValueError("DATABASE_URL is not configured in settings.")
            logger.info(f"Attempting to connect to DB: {settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}")

            db_pool = await asyncpg.create_pool(
                dsn=settings.DATABASE_URL, # Use DSN from settings
                min_size=2,
                max_size=20,
                command_timeout=60,
                # Example setup: You might register custom type codecs here
                # setup=async def _setup(conn):
                #     await conn.set_type_codec(
                #         'jsonb',
                #         encoder=json.dumps,
                #         decoder=json.loads,
                #         schema='pg_catalog'
                #     )
            )
            # Test connection during startup
            async with db_pool.acquire() as conn:
                await conn.execute("SELECT 1")
            logger.info(f"Asyncpg database pool initialized and connection tested (min: 2, max: 20).")
            return # Success
        except (OSError, asyncpg.PostgresError, ConnectionRefusedError) as e:
            retries -= 1
            logger.warning(f"Database pool initialization failed ({e}), retrying in {delay_seconds}s ({retries} left)...")
            if retries == 0:
                logger.critical("Database pool initialization failed after multiple retries.", exc_info=True)
                db_pool = None # Ensure pool is None on failure
                raise RuntimeError("Failed to connect to database after multiple retries.") from e
            await asyncio.sleep(delay_seconds)
        except Exception as e:
            logger.critical(f"CRITICAL: Unexpected error during database pool initialization: {e}", exc_info=True)
            db_pool = None
            raise RuntimeError("Unexpected error initializing database pool.") from e

async def close_db_pool():
    """Closes the asyncpg connection pool."""
    global db_pool
    if db_pool:
        logger.info("Closing asyncpg database pool...")
        # Use wait_closed to ensure pool is fully closed before proceeding
        await db_pool.close()
        # await db_pool.wait_closed() # Consider adding if graceful shutdown is critical
        db_pool = None # Clear the reference
        logger.info("Asyncpg database pool closed.")
    else:
         logger.warning("Attempted to close DB pool, but it was not initialized.")