# backend/app/core/config.py
import os
from typing import Optional
from pydantic import validator, PostgresDsn # Use PostgresDsn for better validation
from pydantic_settings import BaseSettings, SettingsConfigDict # Import for Pydantic V2 style
from dotenv import load_dotenv
from functools import lru_cache

# Determine the base directory of the backend project
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Allow overriding the env file via environment variable, default to .env
DOTENV = os.getenv("DOTENV_PATH", os.path.join(BASE_DIR, '.env'))
print(f"Loading environment variables from: {DOTENV}") # Add print for debugging
load_dotenv(dotenv_path=DOTENV, override=True) # Use override=True for test env

class Settings(BaseSettings):
    # App Environment
    ENVIRONMENT: str = "development"
    API_V1_STR: str = "/api/v1"

    # Database Configuration
    DB_HOST: str
    DB_PORT: int
    DB_USER: str
    DB_PASSWORD: str
    DB_NAME: str
    DB_SSL_MODE: str = "prefer"
    # Use Pydantic V2 style config class
    model_config = SettingsConfigDict(
         case_sensitive=True,
         # env_file = DOTENV # Not needed if load_dotenv called before class definition
         env_file_encoding = 'utf-8'
     )

    # Use computed field for DATABASE_URL (cleaner in Pydantic V2)
    # Or keep the validator if preferred/using V1
    @property
    def DATABASE_URL(self) -> str:
        return f"postgresql://{self.DB_USER}:{self.DB_PASSWORD}@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}?ssl={self.DB_SSL_MODE}"

    # Firebase - Path is relative to project root (backend/)
    FIREBASE_SERVICE_ACCOUNT_KEY_PATH: str = "service-account.json"

    # Sentry
    SENTRY_DSN: Optional[str] = None

# Use lru_cache to load settings only once
@lru_cache()
def get_settings() -> Settings:
    try:
        return Settings()
    except Exception as e:
        print(f"Error loading settings: {e}")
        raise

settings = get_settings() # Create a singleton instance