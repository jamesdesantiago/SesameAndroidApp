// app/src/main/java/com/gazzel/sesameapp/data/remote/ListApiService.kt
package com.gazzel.sesameapp.data.remote

// --- Request DTOs ---
import com.gazzel.sesameapp.data.remote.dto.CollaboratorAddDto
import com.gazzel.sesameapp.data.remote.dto.ListCreateDto
import com.gazzel.sesameapp.data.remote.dto.ListUpdateDto
import com.gazzel.sesameapp.data.remote.dto.PlaceCreateDto
import com.gazzel.sesameapp.data.remote.dto.PlaceUpdateDto

// --- Response DTOs ---
// Import the DTOs we just defined/updated
import com.gazzel.sesameapp.data.remote.dto.ListDto // For items in paginated list
import com.gazzel.sesameapp.data.remote.dto.PlaceDto // For place items
import com.gazzel.sesameapp.data.remote.dto.ListDetailDto // For single list response (metadata only)
import com.gazzel.sesameapp.data.remote.dto.PaginatedListResponseDto // Wrapper for GET /lists
import com.gazzel.sesameapp.data.remote.dto.PaginatedPlaceResponseDto // Wrapper for GET /lists/{id}/places

// --- Retrofit Imports ---
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Consolidated Retrofit service interface for all List and List-related Place operations.
 */
interface ListApiService {

    // --- List CRUD Operations ---

    // POST /lists -> Returns metadata of the created list
    @POST("lists")
    suspend fun createList(
        @Header("Authorization") authorization: String,
        @Body list: ListCreateDto
    ): Response<ListDetailDto> // <<< Return ListDetailDto (metadata only)

    // GET /lists -> Returns paginated list items (ListViewResponse wrapped)
    @GET("lists")
    suspend fun getUserLists(
        @Header("Authorization") authorization: String,
        @Query("userId") userId: String? = null, // Keep if your backend still uses this filter
        @Query("public") publicOnly: Boolean? = null, // Keep if backend uses this
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): Response<PaginatedListResponseDto> // <<< Correct: Returns paginated wrapper

    // GET /lists/{listId} -> Returns metadata ONLY for a specific list
    @GET("lists/{listId}")
    suspend fun getListDetail(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int
    ): Response<ListDetailDto> // <<< Correct: Returns metadata DTO only

    // PATCH /lists/{listId} -> Returns updated metadata
    @PATCH("lists/{listId}")
    suspend fun updateList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int, // Use String if API path expects String ID
        @Body update: ListUpdateDto
    ): Response<ListDetailDto> // <<< Return ListDetailDto (metadata only)

    // DELETE /lists/{listId} -> Returns No Content
    @DELETE("lists/{listId}")
    suspend fun deleteList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int
    ): Response<Unit>

    // --- List Discovery/Query Operations ---
    // TODO: Update these if they also need pagination

    @GET("lists/public")
    suspend fun getPublicLists(
        @Header("Authorization") authorization: String? = null
        // Add pagination params & change return type to PaginatedListResponseDto if needed
    ): Response<List<ListDto>> // Assuming returns non-paginated ListDto (list view item) for now

    @GET("lists/recent")
    suspend fun getRecentLists(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int // Keep limit or change to pagination?
        // Add pagination params & change return type to PaginatedListResponseDto if needed
    ): Response<List<ListDto>> // Assuming returns non-paginated ListDto (list view item) for now

    @GET("lists/search")
    suspend fun searchLists(
        @Header("Authorization") authorization: String,
        @Query("q") query: String
        // Add pagination params & change return type to PaginatedListResponseDto if needed
    ): Response<List<ListDto>> // Assuming returns non-paginated ListDto (list view item) for now

    // --- Places Within a Specific List ---

    // GET /lists/{listId}/places -> Returns paginated places for the list
    @GET("lists/{listId}/places") // <<< NEW METHOD
    suspend fun getPlacesInList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): Response<PaginatedPlaceResponseDto> // <<< Use new paginated place DTO

    // POST /lists/{listId}/places -> Adds a place, backend returns the created PlaceItem
    @POST("lists/{listId}/places")
    suspend fun addPlace(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int,
        @Body place: PlaceCreateDto
    ): Response<PlaceDto> // <<< Changed to PlaceDto to match backend return

    // PATCH /lists/{listId}/places/{placeId} -> Updates a place, backend returns updated PlaceItem
    @PATCH("lists/{listId}/places/{placeId}")
    suspend fun updatePlace(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int,
        @Path("placeId") placeId: String, // Use String if API path expects String ID (DB ID is Int though?) -> Clarify API path param type
        @Body update: PlaceUpdateDto
    ): Response<PlaceDto> // <<< Changed to PlaceDto to match backend return

    // DELETE /lists/{listId}/places/{placeId} -> Deletes a place, returns No Content
    @DELETE("lists/{listId}/places/{placeId}") // <<< NEW METHOD
    suspend fun removePlaceFromList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int,
        @Path("placeId") placeId: String // Use String if API path expects String ID
    ): Response<Unit>

    // --- List Follow Operations ---

    @POST("lists/{listId}/follow")
    suspend fun followList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int
    ): Response<Unit>

    @DELETE("lists/{listId}/follow")
    suspend fun unfollowList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int
    ): Response<Unit>

    // --- List Collaborator Operations ---

    @POST("lists/{listId}/collaborators")
    suspend fun addCollaborator(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int,
        @Body collaborator: CollaboratorAddDto
    ): Response<Unit> // Or maybe return updated ListDetailDto? Check backend.

    @POST("lists/{listId}/collaborators/batch")
    suspend fun addCollaboratorsBatch(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: Int,
        @Body collaborators: List<CollaboratorAddDto>
    ): Response<Unit> // Or maybe return updated ListDetailDto? Check backend.

    // Add DELETE /lists/{listId}/collaborators/{userId} if needed
}