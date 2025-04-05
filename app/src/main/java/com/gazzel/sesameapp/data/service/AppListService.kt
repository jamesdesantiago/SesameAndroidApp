// app/src/main/java/com/gazzel/sesameapp/data/service/AppListService.kt (New or Consolidated File)
package com.gazzel.sesameapp.data.service

import com.gazzel.sesameapp.domain.model.* // Import domain models/DTOs
import retrofit2.Response
import retrofit2.http.*

interface AppListService {

    // --- List Operations ---
    @POST("lists")
    suspend fun createList(
        @Header("Authorization") token: String,
        @Body list: ListCreate
    ): Response<ListResponse> // Assuming API returns the created list

    @GET("lists")
    suspend fun getUserLists(
        @Header("Authorization") token: String,
        // Add query parameters if your API supports filtering by user, public, etc.
        // @Query("userId") userId: String? = null,
        // @Query("public") publicOnly: Boolean? = null
    ): Response<List<ListResponse>> // API returns a list

    @GET("lists/public") // Example endpoint for public lists
    suspend fun getPublicLists(
        @Header("Authorization") token: String // Or maybe no token needed?
    ): Response<List<ListResponse>>

    @GET("lists/{id}")
    suspend fun getListDetail(
        @Header("Authorization") token: String,
        @Path("id") listId: String
    ): Response<ListResponse> // Includes places DTOs presumably

    @PUT("lists/{id}")
    suspend fun updateList(
        @Header("Authorization") token: String,
        @Path("id") listId: String,
        @Body update: ListUpdate
    ): Response<ListResponse> // Assuming API returns the updated list

    @DELETE("lists/{id}")
    suspend fun deleteList(
        @Header("Authorization") token: String,
        @Path("id") listId: String
    ): Response<Unit> // Often 204 No Content

    @GET("lists/recent") // Example endpoint
    suspend fun getRecentLists(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int
    ): Response<List<ListResponse>>

    @GET("lists/search") // Example endpoint
    suspend fun searchLists(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Response<List<ListResponse>>

    // --- Place Operations (if handled by List service) ---
    // Note: These might belong in a PlaceService if places are managed independently
    // and just linked here. Assuming they are managed within lists for now.
    @POST("lists/{listId}/places")
    suspend fun addPlaceToList(
        @Header("Authorization") token: String,
        @Path("listId") listId: String,
        @Body place: PlaceCreate // DTO for creating a place *in this list*
    ): Response<PlaceItem> // Return the created PlaceItem DTO/Model

    @DELETE("lists/{listId}/places/{placeId}")
    suspend fun removePlaceFromList(
        @Header("Authorization") token: String,
        @Path("listId") listId: String,
        @Path("placeId") placeId: String
    ): Response<Unit>

    // --- Follow Operations ---
    @POST("lists/{listId}/follow") // Example endpoint
    suspend fun followList(
        @Header("Authorization") token: String,
        @Path("listId") listId: String
    ): Response<Unit>

    @DELETE("lists/{listId}/follow") // Example endpoint
    suspend fun unfollowList(
        @Header("Authorization") token: String,
        @Path("listId") listId: String
    ): Response<Unit>

    // Add other collaborator endpoints if needed...
}