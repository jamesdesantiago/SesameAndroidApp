# backend/app/core/config.py
import os
import ssl # Import the ssl module
from typing import Optional, Dict # Use Dict for ssl_context
from pydantic import validator, PostgresDsn # Use PostgresDsn for better validation
from pydantic_settings import BaseSettings, SettingsConfigDict # Import for Pydantic V2 style
from dotenv import load_dotenv
from functools import lru_cache

# Determine the base directory of the backend project
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Allow overriding the env file via environment variable, default to .env
DOTENV = os.getenv("DOTENV_PATH", os.path.join(BASE_DIR, '.env'))
# print(f"Loading environment variables from: {DOTENV}") # Add print for debugging
load_dotenv(dotenv_path=DOTENV, override=True) # Use override=True for test env

class Settings(BaseSettings):
    # App Environment
    ENVIRONMENT: str = "development"
    API_V1_STR: str = "/api/v1"

    # Database Configuration
    # Use Pydantic V2 style config class for reading from env vars
    model_config = SettingsConfigDict(
         case_sensitive=True,
         # env_file = DOTENV # Not needed if load_dotenv called before class definition
         env_file_encoding = 'utf-8'
     )

    # Fields required from environment (no defaults here means they MUST be in env/dotenv)
    DB_HOST: str
    DB_PORT: int
    DB_USER: str
    DB_PASSWORD: str
    DB_NAME: str
    # DB_SSL_MODE should now explicitly support 'verify-ca' or 'verify-full'
    DB_SSL_MODE: str = "prefer"
    # New setting for the CA certificate file name (should be relative to BASE_DIR/certs/)
    DB_CA_CERT_FILE: Optional[str] = None # Optional, only needed for verify-ca/verify-full

    # Use computed field for DATABASE_URL (cleaner in Pydantic V2)
    @property
    def DATABASE_URL(self) -> str:
        # Construct the base DSN string
        dsn = f"postgresql://{self.DB_USER}:{self.DB_PASSWORD}@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}?sslmode={self.DB_SSL_MODE}"

        # Conditionally add sslrootcert if SSL verification is required AND the CA file is specified
        if self.DB_SSL_MODE in ['verify-ca', 'verify-full'] and self.DB_CA_CERT_FILE:
            # Construct the absolute path to the CA certificate file
            ca_cert_path = os.path.join(BASE_DIR, 'certs', self.DB_CA_CERT_FILE)
            # Append the sslrootcert parameter to the DSN
            dsn = f"{dsn}&sslrootcert={ca_cert_path}"

        return dsn

    # Firebase - Path is relative to project root (backend/)
    FIREBASE_SERVICE_ACCOUNT_KEY_PATH: str = "service-account.json"

    # Sentry
    SENTRY_DSN: Optional[str] = None

    # Add BACKEND_CORS_ORIGINS if needed
    # BACKEND_CORS_ORIGINS: List[AnyHttpUrl] = []

# Use lru_cache to load settings only once
@lru_cache()
def get_settings() -> Settings:
    try:
        # Pydantic V2 loads from environment variables automatically based on field names
        return Settings()
    except Exception as e:
        print(f"Error loading settings: {e}")
        raise

# Create a singleton instance accessible throughout the app
settings = get_settings()