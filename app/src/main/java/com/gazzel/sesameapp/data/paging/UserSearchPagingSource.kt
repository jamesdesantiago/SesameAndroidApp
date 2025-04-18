// File: app/src/main/java/com/gazzel/sesameapp/data/paging/UserSearchPagingSource.kt
package com.gazzel.sesameapp.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.gazzel.sesameapp.data.mapper.toDomainFriend // Correct import
import com.gazzel.sesameapp.data.remote.dto.UserDto // Use UserDto (which now has isFollowing)
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.model.Friend // Domain model
import retrofit2.HttpException
import java.io.IOException
import android.util.Log

class UserSearchPagingSource(
    private val userApiService: UserApiService,
    private val tokenProvider: TokenProvider,
    private val query: String
    // --- REMOVED currentlyFollowingIds parameter ---
) : PagingSource<Int, Friend>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Friend> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.e("UserSearchPagingSource", "Authentication token is null.")
            return LoadResult.Error(IOException("User not authenticated"))
        }
        val authorizationHeader = "Bearer $token"

        val page = params.key ?: 1
        val pageSize = params.loadSize
        Log.d("UserSearchPagingSource", "Loading page $page with size $pageSize for query '$query'")

        if (query.isBlank()) {
            Log.w("UserSearchPagingSource", "Attempted to load with blank query.")
            // Return empty page immediately if query is blank
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        return try {
            val response = userApiService.searchUsersByEmail(
                email = query,
                token = authorizationHeader,
                page = page,
                pageSize = pageSize
            )

            if (response.isSuccessful) {
                val paginatedResponse = response.body()
                if (paginatedResponse == null) {
                    Log.w("UserSearchPagingSource", "API response body is null for page $page, query '$query'")
                    return LoadResult.Page(emptyList(), if (page == 1) null else page - 1, null)
                }

                val usersDtoList: List<UserDto> = paginatedResponse.items

                // --- MAP using isFollowing from DTO via updated mapper ---
                val friendsDomain = usersDtoList.map { userDto ->
                    // The mapper will now automatically use the isFollowing field from userDto
                    userDto.toDomainFriend()
                }
                Log.d("UserSearchPagingSource", "Page $page loaded ${friendsDomain.size} search results for '$query'. Total pages: ${paginatedResponse.totalPages}")

                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (page < paginatedResponse.totalPages) page + 1 else null

                LoadResult.Page(
                    data = friendsDomain,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
            } else {
                Log.e("UserSearchPagingSource", "API Search Error: ${response.code()} - ${response.message()} for query '$query'")
                LoadResult.Error(HttpException(response))
            }

        } catch (e: IOException) {
            Log.e("UserSearchPagingSource", "IOException during search load for '$query'", e)
            LoadResult.Error(e)
        } catch (e: HttpException) {
            Log.e("UserSearchPagingSource", "HttpException during search load for '$query'", e)
            LoadResult.Error(e)
        } catch (e: Exception) {
            Log.e("UserSearchPagingSource", "Generic Exception during search load for '$query'", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Friend>): Int? {
        // Standard refresh key logic
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}