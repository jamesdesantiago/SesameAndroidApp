# backend/tests/core/test_config.py

import os
import pytest
from pydantic import ValidationError
from unittest.mock import patch
import unittest.mock # Import for ANY

# Import the settings object and potentially the class itself
# Rely on pytest.ini to set path correctly
from backend.app.core.config import Settings, get_settings, BASE_DIR, DOTENV # Use backend. prefix

# --- Test Basic Loading & Defaults ---

def test_settings_load_defaults(monkeypatch):
    """
    Test that settings load with defined default values
    when corresponding environment variables are missing.
    """
    # Store original values if they exist, then remove them
    monkeypatch.delenv("ENVIRONMENT", raising=False)
    monkeypatch.delenv("API_V1_STR", raising=False)
    monkeypatch.delenv("DB_SSL_MODE", raising=False)
    # Ensure required variables are present (even if dummy values)
    monkeypatch.setenv("DB_HOST", "dummy_host")
    monkeypatch.setenv("DB_PORT", "5432")
    monkeypatch.setenv("DB_USER", "dummy_user")
    monkeypatch.setenv("DB_PASSWORD", "dummy_pw")
    monkeypatch.setenv("DB_NAME", "dummy_db")
    monkeypatch.setenv("FIREBASE_SERVICE_ACCOUNT_KEY_PATH", "dummy.json")

    # Clear the lru_cache for get_settings to force reload with modified env
    get_settings.cache_clear()
    try:
        settings_reloaded = get_settings()
        # Check default values defined in your Settings class
        assert settings_reloaded.ENVIRONMENT == "development"
        assert settings_reloaded.API_V1_STR == "/api/v1"
        assert settings_reloaded.DB_SSL_MODE == "prefer"
    finally:
        # monkeypatch automatically restores the environment after the test
        get_settings.cache_clear() # Clear cache again to avoid affecting other tests

def test_settings_load_from_test_env_file():
    """
    Test that settings load correctly from the .env.test file.
    This relies on pytest.ini or environment setup before test run.
    """
    from backend.app.core.config import settings # Reload to ensure test env loaded

    print(f"Test running with settings loaded from: {DOTENV}") # Informative print
    # FIX: Assertion needs to be more robust if DOTENV isn't absolute
    assert DOTENV.endswith(".env.test") # Verify correct env file was targeted

    assert settings.ENVIRONMENT == "test"
    assert settings.DB_NAME == "defaultdb" # As configured in your test env
    assert settings.DB_HOST is not None
    assert settings.DB_PORT is not None
    assert settings.DB_USER is not None
    assert settings.DB_PASSWORD is not None
    assert settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH == "service-account.json"
    assert settings.DB_SSL_MODE == "verify-ca" # As updated in your test env
    assert settings.DB_CA_CERT_FILE == "ca-certificate.crt" # As updated


def test_settings_missing_required_env_vars(monkeypatch):
    """ Test that Settings loading fails if a required env var is missing """
    # Unset a required variable that has no default (e.g., DB_HOST)
    monkeypatch.delenv("DB_HOST", raising=False)
    # Also need to unset DATABASE_URL if it could be set directly, preventing validation
    monkeypatch.delenv("DATABASE_URL", raising=False)
    # Ensure other required fields are present to isolate the error
    monkeypatch.setenv("DB_PORT", "5432")
    monkeypatch.setenv("DB_USER", "dummy_user")
    monkeypatch.setenv("DB_PASSWORD", "dummy_pw")
    monkeypatch.setenv("DB_NAME", "dummy_db")
    monkeypatch.setenv("FIREBASE_SERVICE_ACCOUNT_KEY_PATH", "dummy.json")

    get_settings.cache_clear() # Force reload
    with pytest.raises(ValidationError) as excinfo:
        Settings() # Try to initialize directly to catch validation error
    # Check that the error message mentions the missing field
    assert "DB_HOST" in str(excinfo.value)
    assert "Field required" in str(excinfo.value)
    get_settings.cache_clear()


# --- Test Computed Properties / Validators ---

def test_database_url_computed_property_prefer():
    """ Test the construction of the DATABASE_URL with sslmode=prefer """
    test_env = {
        "ENVIRONMENT": "test",
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
        # FIX: Changed ssl= to sslmode=
        expected_url = "postgresql://test_u:test_p@db.example.com:5433/test_db?sslmode=prefer"
        assert settings_reloaded.DATABASE_URL == expected_url
    get_settings.cache_clear() # Cleanup

def test_database_url_computed_property_require():
    """ Test the construction of the DATABASE_URL with sslmode=require """
    test_env = {
        "ENVIRONMENT": "test", "API_V1_STR": "/api/v1",
        "DB_USER": "test_u_req", "DB_PASSWORD": "pw", "DB_HOST": "db_req",
        "DB_PORT": "5432", "DB_NAME": "test_db_req", "DB_SSL_MODE": "require",
        "FIREBASE_SERVICE_ACCOUNT_KEY_PATH": "dummy.json",
    }
    with patch.dict(os.environ, test_env, clear=True):
        get_settings.cache_clear()
        settings_reloaded = get_settings()
        # FIX: Changed ssl= to sslmode=
        expected_url = "postgresql://test_u_req:pw@db_req:5432/test_db_req?sslmode=require"
        assert settings_reloaded.DATABASE_URL == expected_url
    get_settings.cache_clear()

def test_database_url_computed_property_verify_ca():
    """ Test the construction of the DATABASE_URL with sslmode=verify-ca """
    test_env = {
        "ENVIRONMENT": "test", "API_V1_STR": "/api/v1",
        "DB_USER": "test_u_ca", "DB_PASSWORD": "pw_ca", "DB_HOST": "db_ca",
        "DB_PORT": "5432", "DB_NAME": "test_db_ca", "DB_SSL_MODE": "verify-ca",
        "DB_CA_CERT_FILE": "my_ca.crt", # Specify the CA file
        "FIREBASE_SERVICE_ACCOUNT_KEY_PATH": "dummy.json",
    }
    with patch.dict(os.environ, test_env, clear=True):
        get_settings.cache_clear()
        settings_reloaded = get_settings()
        # Expect sslmode AND sslrootcert parameters
        # Calculate expected path (adjust BASE_DIR logic if needed, assuming it's correct here)
        expected_ca_path = os.path.join(BASE_DIR, 'certs', 'my_ca.crt')
        expected_url = f"postgresql://test_u_ca:pw_ca@db_ca:5432/test_db_ca?sslmode=verify-ca&sslrootcert={expected_ca_path}"
        assert settings_reloaded.DATABASE_URL == expected_url
    get_settings.cache_clear()


# --- Test File Paths ---

def test_firebase_path_interpretation():
    """ Test that the Firebase key path is read correctly from settings """
    # This test relies on the settings loaded via conftest's environment setup
    from backend.app.core.config import settings
    # Assuming .env.test defines FIREBASE_SERVICE_ACCOUNT_KEY_PATH
    assert settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH == "service-account.json"

    # Verify the absolute path construction works as expected
    # BASE_DIR is backend/ directory in this setup
    expected_abs_path = os.path.abspath(os.path.join(BASE_DIR, "service-account.json"))
    # You might want to check if the actual path used in main.py matches this
    # Example: test that os.path.join(project_root, settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH) works
    project_root = os.path.dirname(BASE_DIR)
    constructed_path = os.path.join(project_root, settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH)
    assert os.path.isabs(constructed_path) # Check if it constructs an absolute path