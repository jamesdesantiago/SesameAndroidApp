package com.gazzel.sesameapp

import retrofit2.Response
import retrofit2.http.*

interface ListService {
    @POST("lists")
    suspend fun createList(
        @Body list: ListCreate,
        @Header("Authorization") token: String
    ): Response<ListResponse>

    @GET("lists")
    suspend fun getLists(
        @Header("Authorization") token: String
    ): Response<List<ListResponse>>

    @DELETE("lists/{id}")
    suspend fun deleteList(
        @Path("id") listId: Int,
        @Header("Authorization") token: String
    ): Response<Unit>

    @PUT("lists/{id}")
    suspend fun updateList(
        @Path("id") listId: Int,
        @Body update: ListUpdate,
        @Header("Authorization") token: String
    ): Response<ListResponse>

    @GET("lists/{id}")
    suspend fun getListDetail(
        @Path("id") listId: Int,
        @Header("Authorization") token: String
    ): Response<ListResponse>

    @POST("lists/{list_id}/collaborators")
    suspend fun addCollaborator(
        @Path("list_id") listId: Int,
        @Body collaborator: CollaboratorAdd,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @POST("lists/{list_id}/collaborators/batch")
    suspend fun addCollaboratorsBatch(
        @Path("list_id") listId: Int,
        @Body collaborators: List<CollaboratorAdd>,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @POST("lists/{list_id}/places")
    suspend fun addPlace(
        @Path("list_id") listId: Int,
        @Body place: PlaceCreate,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    // New endpoint to update a place
    @PATCH("lists/{listId}/places/{placeId}")
    suspend fun updatePlace(
        @Path("listId") listId: Int,
        @Path("placeId") placeId: Int,
        @Body update: PlaceUpdate,
        @Header("Authorization") authHeader: String
    ): Response<Unit>
}
