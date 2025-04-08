package com.gazzel.sesameapp.data.remote

import com.gazzel.sesameapp.data.remote.dto.CollaboratorAddDto
import com.gazzel.sesameapp.data.remote.dto.ListCreateDto
import com.gazzel.sesameapp.data.remote.dto.ListDto
import com.gazzel.sesameapp.data.remote.dto.ListUpdateDto
import com.gazzel.sesameapp.data.remote.dto.PlaceCreateDto
import com.gazzel.sesameapp.data.remote.dto.PlaceUpdateDto
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

    @POST("lists")
    suspend fun createList(
        @Header("Authorization") authorization: String,
        @Body list: ListCreateDto // Use Request DTO
    ): Response<ListDto> // Use Response DTO

    @GET("lists")
    suspend fun getUserLists(
        @Header("Authorization") authorization: String,
        @Query("userId") userId: String? = null, // Optional filter if API supports
        @Query("public") publicOnly: Boolean? = null // Optional filter if API supports
    ): Response<List<ListDto>> // Use Response DTO

    @GET("lists/{listId}")
    suspend fun getListDetail(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String // Consistent path param name
    ): Response<ListDto> // Use Response DTO (assuming it includes PlaceDtos)

    /**
     * Updates specific fields of a list. Use PUT if replacing the entire resource.
     */
    @PATCH("lists/{listId}") // Prefer PATCH for partial updates
    suspend fun updateList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String,
        @Body update: ListUpdateDto // Use Request DTO
    ): Response<ListDto> // Use Response DTO

    @DELETE("lists/{listId}")
    suspend fun deleteList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String
    ): Response<Unit> // Often 204 No Content

    // --- List Discovery/Query Operations ---

    @GET("lists/public") // From AppListService
    suspend fun getPublicLists(
        // Consider if Authorization is needed for public lists
        @Header("Authorization") authorization: String? = null
    ): Response<List<ListDto>>

    @GET("lists/recent") // From AppListService
    suspend fun getRecentLists(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int
    ): Response<List<ListDto>>

    @GET("lists/search") // From AppListService
    suspend fun searchLists(
        @Header("Authorization") authorization: String,
        @Query("q") query: String
    ): Response<List<ListDto>>

    // --- Place Operations (within a List context) ---

    @POST("lists/{listId}/places")
    suspend fun addPlace(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String,
        @Body place: PlaceCreateDto // Use Request DTO
    ): Response<Unit> // Assuming API returns Unit based on SearchPlacesViewModel usage

    /**
     * Updates specific fields of a place within a list.
     */
    @PATCH("lists/{listId}/places/{placeId}") // Prefer PATCH for partial updates
    suspend fun updatePlace(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String,
        @Path("placeId") placeId: String,
        @Body update: PlaceUpdateDto // Use Request DTO
    ): Response<Unit> // Or Response<PlaceDto> if API returns updated place

    @DELETE("lists/{listId}/places/{placeId}")
    suspend fun removePlaceFromList( // Combined deletePlace & removePlaceFromList
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String,
        @Path("placeId") placeId: String
    ): Response<Unit>

    // --- Follow Operations --- (From AppListService)

    @POST("lists/{listId}/follow")
    suspend fun followList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String
    ): Response<Unit>

    @DELETE("lists/{listId}/follow")
    suspend fun unfollowList(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String
    ): Response<Unit>

    // --- Collaborator Operations --- (From UserListService)

    @POST("lists/{listId}/collaborators") // Standardized path param
    suspend fun addCollaborator(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String,
        @Body collaborator: CollaboratorAddDto // Use Request DTO
    ): Response<Unit>

    @POST("lists/{listId}/collaborators/batch") // Standardized path param
    suspend fun addCollaboratorsBatch(
        @Header("Authorization") authorization: String,
        @Path("listId") listId: String,
        @Body collaborators: List<CollaboratorAddDto> // Use Request DTO
    ): Response<Unit>

    // Add remove collaborator endpoint if needed
    // @DELETE("lists/{listId}/collaborators/{userId}") ...

}