# backend/app/core/config.py
import os
from typing import Optional
from pydantic import validator
from pydantic_settings import BaseSettings
from dotenv import load_dotenv
from functools import lru_cache

# Determine the base directory of the backend project
# This assumes config.py is in backend/app/core/
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ENV_PATH = os.path.join(BASE_DIR, '.env')

# Load .env file from the project root (backend/)
load_dotenv(dotenv_path=ENV_PATH)

class Settings(BaseSettings):
    # App Environment
    ENVIRONMENT: str = "development"
    API_V1_STR: str = "/api/v1" # Example API prefix

    # Database Configuration
    DB_HOST: str
    DB_PORT: int
    DB_USER: str
    DB_PASSWORD: str
    DB_NAME: str
    DB_SSL_MODE: str = "prefer" # Default to prefer if not set
    # Construct Database URL from components
    DATABASE_URL: Optional[str] = None

    # Pydantic validator to construct DATABASE_URL
    @validator('DATABASE_URL', pre=True, always=True)
    def assemble_db_connection(cls, v: Optional[str], values: dict[str, object]) -> str:
        if isinstance(v, str):
            return v # Return if already provided explicitly
        # Ensure required DB components are present
        user = values.get('DB_USER')
        password = values.get('DB_PASSWORD')
        host = values.get('DB_HOST')
        port = values.get('DB_PORT')
        db_name = values.get('DB_NAME')
        ssl_mode = values.get('DB_SSL_MODE', 'prefer')

        if not all([user, password, host, port, db_name]):
            raise ValueError("Missing database connection details (DB_USER, DB_PASSWORD, DB_HOST, DB_PORT, DB_NAME)")

        return (
            f"postgresql://{user}:{password}"
            f"@{host}:{port}/{db_name}"
            f"?ssl={ssl_mode}"
        )

    # Firebase - Path is relative to project root (backend/)
    FIREBASE_SERVICE_ACCOUNT_KEY_PATH: str = "service-account.json"

    # Sentry
    SENTRY_DSN: Optional[str] = None

    # CORS Origins (Example - adjust as needed for your frontend)
    # BACKEND_CORS_ORIGINS: list[str] = ["http://localhost:3000", "http://localhost:8080"] # For development

    class Config:
        case_sensitive = True
        env_file = ENV_PATH # Explicitly tell pydantic-settings where to find .env
        env_file_encoding = 'utf-8'


# Use lru_cache to load settings only once
@lru_cache()
def get_settings() -> Settings:
    print(f"Loading settings from: {ENV_PATH}") # Debug print
    try:
        return Settings()
    except Exception as e:
        print(f"Error loading settings: {e}")
        raise

settings = get_settings() # Create a singleton instance