# backend/app/db/base.py
import logging
import asyncio
import os # Import os to check file existence
import ssl # Import ssl (though not used for manual context, useful for constants)
from typing import Optional

import asyncpg
# Import both settings instance AND the BASE_DIR variable from the config module
from app.core.config import settings, BASE_DIR


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

    # --- Check for CA certificate file if SSL verification is required ---
    ca_cert_path = None
    if settings.DB_SSL_MODE in ['verify-ca', 'verify-full']:
        if not settings.DB_CA_CERT_FILE:
             logger.critical("DB_SSL_MODE is set to verify-ca or verify-full, but DB_CA_CERT_FILE is not specified in settings.")
             # Exit if we cannot verify SSL, as it's a security requirement
             raise RuntimeError("Database CA certificate file not configured for required SSL mode.")

        # Construct the absolute path using BASE_DIR imported directly from the config module
        ca_cert_path = os.path.join(BASE_DIR, 'certs', settings.DB_CA_CERT_FILE) # Use BASE_DIR directly

        if not os.path.exists(ca_cert_path):
             logger.critical(f"Database CA certificate file not found at expected path: {ca_cert_path}")
             # Exit if the required CA file is missing
             raise FileNotFoundError(f"Database CA certificate file not found: {ca_cert_path}")

        logger.info(f"Using Database CA certificate file: {ca_cert_path} for sslmode={settings.DB_SSL_MODE}")
    elif settings.DB_SSL_MODE not in ['disable', 'allow', 'prefer', 'require', 'verify-ca', 'verify-full']: # Added verify modes to this check
         logger.critical(f"Invalid DB_SSL_MODE configured: {settings.DB_SSL_MODE}")
         raise ValueError(f"Invalid DB_SSL_MODE configured: {settings.DB_SSL_MODE}")
    # --- End CA certificate check ---


    retries = 5
    delay_seconds = 5
    while retries > 0:
        try: # <-- Corrected: All subsequent except blocks must be associated with THIS try block
            # Ensure DATABASE_URL is correctly loaded and includes sslmode/sslrootcert if needed
            if not settings.DATABASE_URL:
                 raise ValueError("DATABASE_URL is not configured in settings.")
            # The DSN string constructed in settings.DATABASE_URL now includes sslmode and sslrootcert
            # when verification modes are used. asyncpg understands these parameters in the DSN.
            # We don't need to pass a separate `ssl` context dictionary here for just the CA file.
            logger.info(f"Attempting to connect to DB using DSN derived from settings...") # Avoid logging password in DSN

            db_pool = await asyncpg.create_pool(
                dsn=settings.DATABASE_URL, # Use the full DSN from settings
                min_size=2,
                max_size=20,
                command_timeout=60,
                # ssl=... # No longer needed for CA file if included in DSN
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

        # Corrected: Combine all relevant exceptions into a single try/except structure
        except (OSError, asyncpg.PostgresError, ConnectionRefusedError) as e:
            # Catch connection errors specifically for retry logic
            retries -= 1
            logger.warning(f"Database pool initialization failed ({type(e).__name__}: {e}), retrying in {delay_seconds}s ({retries} left)...", exc_info=False)
            if retries == 0:
                logger.critical("Database pool initialization failed after multiple retries.", exc_info=True)
                db_pool = None
                raise RuntimeError("Failed to connect to database after multiple retries.") from e
            await asyncio.sleep(delay_seconds)
        except (FileNotFoundError, ValueError) as e:
             # Catch errors raised during the CA cert file check or invalid SSL mode *within the try block*
             # Note: If these errors happen *before* the while loop, they are caught by the
             # final generic except block in main.py's lifespan, which is also fine.
             logger.critical(f"CRITICAL: Configuration error during database pool initialization: {e}", exc_info=True)
             db_pool = None
             # Don't retry on configuration errors, these need manual fix
             raise RuntimeError("Database configuration error.") from e
        except Exception as e:
            # Catch any other unexpected error during the connection/pool setup attempt
            logger.critical(f"CRITICAL: Unexpected error during database pool initialization: {e}", exc_info=True)
            db_pool = None
            raise RuntimeError("Unexpected error initializing database pool.") from e


async def close_db_pool():
    """Closes the asyncpg connection pool."""
    global db_pool
    if db_pool:
        logger.info("Closing asyncpg database pool...")
        # Use terminate() for a more forceful shutdown during application exit
        await db_pool.terminate()
        db_pool = None # Clear the reference
        logger.info("Asyncpg database pool terminated.")
    else:
         logger.warning("Attempted to close DB pool, but it was not initialized.")