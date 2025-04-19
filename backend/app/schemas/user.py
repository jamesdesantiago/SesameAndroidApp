# backend/app/schemas/user.py
from pydantic import BaseModel, Field, EmailStr
from typing import Optional, List
from datetime import datetime

# --- User Schemas ---

# Base user schema - core identifiable information
class UserBase(BaseModel):
    id: int = Field(..., description="Unique database identifier for the user")
    email: EmailStr = Field(..., description="User's email address")
    username: Optional[str] = Field(None, description="User's unique username (if set)")
    # Include fields commonly returned by user endpoints
    display_name: Optional[str] = Field(None, alias="displayName", description="User's display name") # Alias example
    profile_picture: Optional[str] = Field(None, alias="profilePicture", description="URL of the user's profile picture") # Alias example

    class Config:
        orm_mode = True
        # model_config = {"from_attributes": True} # For Pydantic V2+
        # If using aliases for db fields -> Pydantic model fields:
        allow_population_by_field_name = True # Use alias="db_field_name" in Field

# Schema specifically for follow/search results, adding follow status
class UserFollowInfo(UserBase):
    is_following: Optional[bool] = Field(None, description="Indicates if the authenticated user is following this user")

# Schema for setting username (request body for POST /users/set-username)
class UsernameSet(BaseModel):
    username: str = Field(
        ...,
        min_length=1,
        max_length=30,
        pattern=r'^[a-zA-Z0-9._]+$', # Keep pattern constraint
        description="Desired unique username"
    )

# Schema for checking username status (response for GET /users/check-username)
class UsernameCheckResponse(BaseModel):
    needsUsername: bool = Field(..., description="True if the user needs to set a username, False otherwise")

# Schema for username set response (response for POST /users/set-username)
class UsernameSetResponse(BaseModel):
    message: str = Field(..., description="Success message confirming username update")

# Schema for updating user profile (request body for PATCH /users/me/profile)
class UserProfileUpdate(BaseModel):
    display_name: Optional[str] = Field(None, alias="displayName", min_length=1, max_length=50, description="New display name")
    profile_picture: Optional[str] = Field(None, alias="profilePicture", description="New profile picture URL") # Add validation if it should be a URL

# --- Notification Schemas ---
class NotificationItem(BaseModel):
    id: int = Field(..., description="Unique identifier for the notification")
    title: str = Field(..., description="Title of the notification")
    message: str = Field(..., description="Content/message of the notification")
    is_read: bool = Field(..., alias="isRead", description="Whether the notification has been read")
    timestamp: datetime = Field(..., description="Timestamp when the notification was created")

    class Config:
        orm_mode = True
        allow_population_by_field_name = True
        # model_config = {"from_attributes": True, "populate_by_name": True} # For Pydantic V2+

# Schema for paginated notification response (response for GET /notifications)
class PaginatedNotificationResponse(BaseModel):
    items: List[NotificationItem] = Field(..., description="The list of notifications on the current page")
    page: int = Field(..., ge=1, description="The current page number")
    page_size: int = Field(..., ge=1, description="Number of items per page")
    total_items: int = Field(..., ge=0, description="Total number of notifications matching the query")
    total_pages: int = Field(..., ge=0, description="Total number of pages available")

# Schema for paginated user/friend response (e.g., GET /users/following, /users/followers, /users/search)
class PaginatedUserResponse(BaseModel):
    items: List[UserFollowInfo] = Field(..., description="The list of users on the current page")
    page: int = Field(..., ge=1, description="The current page number")
    page_size: int = Field(..., ge=1, description="Number of items per page")
    total_items: int = Field(..., ge=0, description="Total number of users matching the query")
    total_pages: int = Field(..., ge=0, description="Total number of pages available")

# --- Privacy Settings Schemas ---
class PrivacySettingsBase(BaseModel):
    profile_is_public: bool = Field(True, description="Whether the user's profile is public")
    lists_are_public: bool = Field(True, description="Default visibility for new lists created by the user")
    allow_analytics: bool = Field(True, description="Whether the user allows analytics data collection")

# Response for GET /users/me/settings
class PrivacySettingsResponse(PrivacySettingsBase):
     class Config:
        orm_mode = True
        # model_config = {"from_attributes": True} # For Pydantic V2+

# Request body for PATCH /users/me/settings
class PrivacySettingsUpdate(BaseModel):
    profile_is_public: Optional[bool] = Field(None, description="Update profile visibility")
    lists_are_public: Optional[bool] = Field(None, description="Update default list visibility")
    allow_analytics: Optional[bool] = Field(None, description="Update analytics preference")