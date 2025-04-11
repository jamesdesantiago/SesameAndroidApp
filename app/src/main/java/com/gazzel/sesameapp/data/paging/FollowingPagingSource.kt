// app/src/main/java/com/gazzel/sesameapp/data/paging/FollowingPagingSource.kt
package com.gazzel.sesameapp.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.gazzel.sesameapp.data.mapper.toDomainFriend // Make sure import is correct
import com.gazzel.sesameapp.data.model.User           // Make sure import is correct
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.model.Friend
import retrofit2.HttpException
import java.io.IOException
import android.util.Log

class FollowingPagingSource(
    private val userApiService: UserApiService,
    private val tokenProvider: TokenProvider
) : PagingSource<Int, Friend>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Friend> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.e("FollowingPagingSource", "Authentication token is null.")
            return LoadResult.Error(IOException("User not authenticated"))
        }
        val authorizationHeader = "Bearer $token"

        val page = params.key ?: 1
        val pageSize = params.loadSize
        Log.d("FollowingPagingSource", "Loading page $page with size $pageSize")

        return try {
            val response = userApiService.getFollowing(
                token = authorizationHeader,
                page = page,
                pageSize = pageSize
            )

            if (response.isSuccessful) {
                val paginatedResponse = response.body()
                if (paginatedResponse == null) {
                    Log.w("FollowingPagingSource", "API response body is null for page $page")
                    return LoadResult.Page(emptyList(), if (page == 1) null else page - 1, null)
                }

                val usersDto = paginatedResponse.items
                // Map using the UserMapper function
                val friendsDomain = usersDto.map { userDto ->
                    userDto.toDomainFriend(isFollowing = true) // isFollowing is true here
                }
                Log.d("FollowingPagingSource", "Page $page loaded ${friendsDomain.size} following. Total pages: ${paginatedResponse.totalPages}")

                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (page < paginatedResponse.totalPages) page + 1 else null

                LoadResult.Page(data = friendsDomain, prevKey = prevKey, nextKey = nextKey)
            } else {
                Log.e("FollowingPagingSource", "API Error: ${response.code()} - ${response.message()}")
                LoadResult.Error(HttpException(response))
            }
        } catch (e: IOException) {
            Log.e("FollowingPagingSource", "IOException during load", e)
            LoadResult.Error(e)
        } catch (e: HttpException) {
            Log.e("FollowingPagingSource", "HttpException during load", e)
            LoadResult.Error(e)
        } catch (e: Exception) {
            Log.e("FollowingPagingSource", "Generic Exception during load", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Friend>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}