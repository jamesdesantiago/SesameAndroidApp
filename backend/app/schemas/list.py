# backend/app/schemas/list.py
from pydantic import BaseModel, Field, EmailStr
from typing import Optional, List
from datetime import datetime # Keep if needed for timestamps in detailed models

# Import PlaceItem if needed within list responses (currently avoided in detail responses)
# from .place import PlaceItem # Not currently needed based on API design

# --- List Schemas ---

# Base schema with common fields
class ListBase(BaseModel):
    name: str = Field(..., min_length=1, max_length=100, description="The name of the list")
    description: Optional[str] = Field(None, max_length=500, description="An optional description for the list")
    isPrivate: bool = Field(False, description="Whether the list is private (True) or public (False)")

# Schema for creating a new list (request body for POST /lists)
class ListCreate(ListBase):
    # Inherits fields from ListBase
    # Add collaborators if they can be set on creation
    # collaborators: List[EmailStr] = []
    pass # No extra fields needed based on current API

# Schema for the response when viewing items in a paginated list (e.g., GET /lists, discovery endpoints)
class ListViewResponse(BaseModel):
    id: int = Field(..., description="Unique database identifier for the list")
    name: str = Field(..., description="The name of the list")
    description: Optional[str] = Field(None, description="The list description")
    isPrivate: bool = Field(..., description="List privacy status")
    place_count: int = Field(0, description="Number of places currently in the list")

    class Config:
        # Allows mapping directly from db records if field names match or using aliases
        # Pydantic V1:
        orm_mode = True
        # Pydantic V2:
        # model_config = {"from_attributes": True}

# Schema for the response when getting detailed metadata for ONE list (e.g., GET /lists/{id})
class ListDetailResponse(BaseModel):
    id: int = Field(..., description="Unique database identifier for the list")
    name: str = Field(..., description="The name of the list")
    description: Optional[str] = Field(None, description="The list description")
    isPrivate: bool = Field(..., description="List privacy status")
    collaborators: List[EmailStr] = Field([], description="List of collaborator email addresses") # Assuming emails

    class Config:
        # Pydantic V1:
        orm_mode = True
        # Pydantic V2:
        # model_config = {"from_attributes": True}

# Schema for updating an existing list (request body for PATCH /lists/{id})
class ListUpdate(BaseModel):
    name: Optional[str] = Field(None, min_length=1, max_length=100, description="New name for the list")
    isPrivate: Optional[bool] = Field(None, description="New privacy status for the list")

# Schema for adding a collaborator (request body for POST /lists/{id}/collaborators)
class CollaboratorAdd(BaseModel):
    email: EmailStr = Field(..., description="Email address of the collaborator to add")

# Schema for the paginated response wrapper for lists (e.g., GET /lists, discovery endpoints)
class PaginatedListResponse(BaseModel):
    items: List[ListViewResponse] = Field(..., description="The list of list items on the current page")
    page: int = Field(..., ge=1, description="The current page number")
    page_size: int = Field(..., ge=1, description="Number of items per page")
    total_items: int = Field(..., ge=0, description="Total number of lists matching the query")
    total_pages: int = Field(..., ge=0, description="Total number of pages available")