package com.gazzel.sesameapp.data.service

import com.gazzel.sesameapp.domain.model.CollaboratorAdd
import com.gazzel.sesameapp.domain.model.ListCreate
import com.gazzel.sesameapp.domain.model.ListResponse
import com.gazzel.sesameapp.domain.model.ListUpdate
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserListService {
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
    suspend fun deleteList(@Path("id") listId: String, @Header("Authorization") token: String): Response<Unit>

    @PUT("lists/{listId}")
    suspend fun updateList(
        @Path("id") listId: String,
        @Body update: ListUpdate,
        @Header("Authorization") token: String
    ): Response<ListResponse>

    @GET("lists/{id}")
    suspend fun getListDetail(
        @Path("id") listId: String,
        @Header("Authorization") token: String
    ): Response<ListResponse>

    @POST("lists/{list_id}/collaborators")
    suspend fun addCollaborator(
        @Path("list_id") listId: String,
        @Body collaborator: CollaboratorAdd,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @POST("lists/{list_id}/collaborators/batch")
    suspend fun addCollaboratorsBatch(
        @Path("list_id") listId: String,
        @Body collaborators: List<CollaboratorAdd>,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @POST("lists/{list_id}/places")
    suspend fun addPlace(
        @Path("list_id") listId: String,
        @Body place: PlaceCreate,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    @PATCH("lists/{listId}/places/{placeId}")
    suspend fun updatePlace(
        @Path("listId") listId: String,
        @Path("placeId") placeId: String,
        @Body update: PlaceUpdate,
        @Header("Authorization") authHeader: String
    ): Response<Unit>
}

data class ListCreate(
    val name: String,
    val isPrivate: Boolean,
    val collaborators: List<String>
)

data class ListUpdate(
    val name: String? = null,
    val isPrivate: Boolean? = null
)

data class CollaboratorAdd(
    val email: String
)

data class PlaceCreate(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: String? = null
)

data class PlaceUpdate(
    val name: String? = null,
    val address: String? = null,
    val rating: String? = null,
    val notes: String? = null
) 