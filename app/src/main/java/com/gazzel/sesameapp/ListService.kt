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
}
