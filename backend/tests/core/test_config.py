# backend/tests/core/test_config.py

import os
import pytest
from pydantic import ValidationError # Import Pydantic's validation error
from unittest.mock import patch # To mock environment variables

# Import the settings object and potentially the class itself
# IMPORTANT: To test loading under different conditions, we often need to
# manipulate the environment *before* the module is fully imported or
# clear the cache and re-trigger the loading.
from app.core.config import Settings, get_settings, BASE_DIR, DOTENV # Import DOTENV for info

# --- Test Basic Loading & Defaults ---

def test_settings_load_defaults(monkeypatch):
    """
    Test that settings load with defined default values
    when corresponding environment variables are missing.
    We use monkeypatch to reliably unset environment variables for the test scope.
    """
    # Store original values if they exist, then remove them
    monkeypatch.delenv("ENVIRONMENT", raising=False) # Delete if exists, don't fail if not
    monkeypatch.delenv("API_V1_STR", raising=False)
    monkeypatch.delenv("DB_SSL_MODE", raising=False)
    # Note: DB_HOST, DB_PORT etc. don't have defaults in the Settings class,
    # so unsetting them would cause a validation error during Settings init.
    # We test required fields in a separate test or rely on the main load test.

    # Clear the lru_cache for get_settings to force reload with modified env
    get_settings.cache_clear()
    try:
        settings_reloaded = get_settings()
        # Check default values defined in your Settings class
        assert settings_reloaded.ENVIRONMENT == "development"
        assert settings_reloaded.API_V1_STR == "/api/v1"
        assert settings_reloaded.DB_SSL_MODE == "prefer" # Check the default
    finally:
        # monkeypatch automatically restores the environment after the test
        get_settings.cache_clear() # Clear cache again to avoid affecting other tests

def test_settings_load_from_test_env_file():
    """
    Test that settings load correctly from the .env.test file.
    This relies on conftest.py having set the DOTENV_PATH environment variable
    before this test module is imported and settings are loaded initially.
    """
    # We access the already loaded settings instance from config module
    # It should have been loaded using .env.test due to conftest.py
    from app.core.config import settings

    print(f"Test running with settings loaded from: {DOTENV}") # Informative print
    assert DOTENV.endswith(".env.test") # Verify correct env file was targeted

    # Assert values known to be in your .env.test
    # Update these assertions to match your actual .env.test content
    assert settings.ENVIRONMENT == "test"
    assert settings.DB_NAME == "defaultdb" # As configured currently for testing on dev DB
    assert settings.DB_HOST is not None # Check required fields are loaded
    assert settings.DB_PORT is not None
    assert settings.DB_USER is not None
    assert settings.DB_PASSWORD is not None
    assert settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH == "service-account.json"
    # Check SENTRY_DSN based on your .env.test (e.g., might be empty)
    # assert settings.SENTRY_DSN is None or settings.SENTRY_DSN == ""

def test_settings_missing_required_env_vars(monkeypatch):
    """ Test that Settings loading fails if a required env var is missing """
    # Unset a required variable that has no default (e.g., DB_HOST)
    monkeypatch.delenv("DB_HOST", raising=False)
    # Also need to unset DATABASE_URL if it could be set directly, preventing validation
    monkeypatch.delenv("DATABASE_URL", raising=False)

    get_settings.cache_clear() # Force reload
    with pytest.raises(ValidationError) as excinfo:
        Settings() # Try to initialize directly to catch validation error
    # Check that the error message mentions the missing field
    assert "DB_HOST" in str(excinfo.value)
    assert "Field required" in str(excinfo.value)
    get_settings.cache_clear()


# --- Test Computed Properties / Validators ---

def test_database_url_computed_property_prefer():
    """ Test the construction of the DATABASE_URL with ssl=prefer """
    # Use patch.dict for temporary, isolated environment modification
    test_env = {
        "ENVIRONMENT": "test", # Need all required fields for Settings init
        "API_V1_STR": "/api/v1",
        "DB_USER": "test_u",
        "DB_PASSWORD": "test_p",
        "DB_HOST": "db.example.com",
        "DB_PORT": "5433",
        "DB_NAME": "test_db",
        "DB_SSL_MODE": "prefer", # Explicitly test 'prefer'
        "FIREBASE_SERVICE_ACCOUNT_KEY_PATH": "dummy.json",
    }
    with patch.dict(os.environ, test_env, clear=True):
        get_settings.cache_clear() # Force reload inside patch
        settings_reloaded = get_settings()
        expected_url = "postgresql://test_u:test_p@db.example.com:5433/test_db?ssl=prefer"
        assert settings_reloaded.DATABASE_URL == expected_url
    get_settings.cache_clear() # Cleanup

def test_database_url_computed_property_require():
    """ Test the construction of the DATABASE_URL with ssl=require """
    test_env = {
        "ENVIRONMENT": "test", "API_V1_STR": "/api/v1",
        "DB_USER": "test_u_req", "DB_PASSWORD": "pw", "DB_HOST": "db_req",
        "DB_PORT": "5432", "DB_NAME": "test_db_req", "DB_SSL_MODE": "require",
        "FIREBASE_SERVICE_ACCOUNT_KEY_PATH": "dummy.json",
    }
    with patch.dict(os.environ, test_env, clear=True):
        get_settings.cache_clear()
        settings_reloaded = get_settings()
        expected_url = "postgresql://test_u_req:pw@db_req:5432/test_db_req?ssl=require"
        assert settings_reloaded.DATABASE_URL == expected_url
    get_settings.cache_clear()


# --- Test File Paths ---

def test_firebase_path_interpretation():
    """ Test that the Firebase key path is read correctly from settings """
    # This test relies on the settings loaded via conftest's environment setup
    from app.core.config import settings
    assert settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH == "service-account.json"

    # Verify the absolute path construction works as expected (optional)
    # Note: BASE_DIR is the 'backend' directory in this setup
    expected_abs_path = os.path.abspath(os.path.join(BASE_DIR, "service-account.json"))

    # You could add logic in your main app or lifespan to check os.path.exists(abs_path)
    # but testing the setting value itself is usually sufficient here.
    # For example, if Firebase init fails in main.py, that indicates a path issue.

# --- Add more tests if you implement complex validators ---
# Example:
# class SettingsWithValidator(BaseSettings):
#     MY_VALUE: str
#     @validator('MY_VALUE')
#     def check_my_value(cls, v):
#         if v == "invalid": raise ValueError("Value cannot be 'invalid'")
#         return v
#
# def test_my_value_validator_invalid(monkeypatch):
#     monkeypatch.setenv("MY_VALUE", "invalid")
#     with pytest.raises(ValidationError):
#          SettingsWithValidator() # Test direct initialization
#
# def test_my_value_validator_valid(monkeypatch):
#      monkeypatch.setenv("MY_VALUE", "valid_value")
#      settings = SettingsWithValidator()
#      assert settings.MY_VALUE == "valid_value"