package com.gazzel.sesameapp.data.service

import com.gazzel.sesameapp.domain.model.ListResponse
import com.gazzel.sesameapp.domain.model.PlaceItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ListService {
    @GET("lists")
    suspend fun getLists(
        @Header("Authorization") token: String
    ): Response<List<ListResponse>>

    @GET("lists/{listId}")
    suspend fun getListDetail(
        @Path("listId") listId: String,
        @Header("Authorization") token: String
    ): Response<ListResponse>

    @POST("lists/{listId}/places")
    suspend fun addPlace(
        @Path("listId") listId: String,
        @Body place: PlaceItem,
        @Header("Authorization") token: String
    ): Response<PlaceItem>

    @PUT("lists/{listId}/places/{placeId}")
    suspend fun updatePlace(
        @Path("listId") listId: String,
        @Path("placeId") placeId: String,
        @Body place: PlaceItem,
        @Header("Authorization") token: String
    ): Response<PlaceItem>

    @DELETE("lists/{listId}/places/{placeId}")
    suspend fun deletePlace(
        @Path("listId") listId: String,
        @Path("placeId") placeId: String,
        @Header("Authorization") token: String
    ): Response<Unit>
} 