// File: app/src/main/java/com/gazzel/sesameapp/data/paging/FollowersPagingSource.kt
package com.gazzel.sesameapp.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.gazzel.sesameapp.data.mapper.toDomainFriend // Ensure correct import
// Import the NEW UserDto
import com.gazzel.sesameapp.data.remote.dto.UserDto // <<< CHANGE
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.model.Friend // Domain model
import retrofit2.HttpException
import java.io.IOException
import android.util.Log

class FollowersPagingSource(
    private val userApiService: UserApiService,
    private val tokenProvider: TokenProvider
    // Optional: Pass currentlyFollowingIds if needed for accurate 'isFollowing' status
    // private val currentlyFollowingIds: Set<String>
) : PagingSource<Int, Friend>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Friend> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.e("FollowersPagingSource", "Authentication token is null.")
            return LoadResult.Error(IOException("User not authenticated"))
        }
        val authorizationHeader = "Bearer $token"

        val page = params.key ?: 1
        val pageSize = params.loadSize
        Log.d("FollowersPagingSource", "Loading page $page with size $pageSize")

        return try {
            // API call now returns PaginatedUserResponseDto which contains List<UserDto>
            val response = userApiService.getFollowers(
                token = authorizationHeader,
                page = page,
                pageSize = pageSize
            )

            if (response.isSuccessful) {
                val paginatedResponse = response.body()
                if (paginatedResponse == null) {
                    Log.w("FollowersPagingSource", "API response body is null for page $page")
                    // Return empty page if body is null
                    return LoadResult.Page(emptyList(), if (page == 1) null else page - 1, null)
                }

                // paginatedResponse.items is now List<UserDto>
                val usersDtoList: List<UserDto> = paginatedResponse.items

                // --- MAP List<UserDto> to List<Friend> ---
                val friendsDomain = usersDtoList.map { userDto ->
                    // For followers, isFollowing is typically false unless checked against another source
                    // val isFollowing = currentlyFollowingIds?.contains(userDto.id) ?: false
                    userDto.toDomainFriend(isFollowing = false)
                }
                Log.d("FollowersPagingSource", "Page $page loaded ${friendsDomain.size} followers. Total pages: ${paginatedResponse.totalPages}")

                val prevKey = if (page == 1) null else page - 1
                // Determine nextKey based on totalPages from the paginated response
                val nextKey = if (page < paginatedResponse.totalPages) page + 1 else null

                LoadResult.Page(
                    data = friendsDomain,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
            } else {
                Log.e("FollowersPagingSource", "API Error: ${response.code()} - ${response.message()}")
                LoadResult.Error(HttpException(response)) // Propagate API error
            }

        } catch (e: IOException) {
            Log.e("FollowersPagingSource", "IOException during load", e)
            LoadResult.Error(e)
        } catch (e: HttpException) {
            Log.e("FollowersPagingSource", "HttpException during load", e)
            LoadResult.Error(e)
        } catch (e: Exception) { // Catch any other unexpected errors
            Log.e("FollowersPagingSource", "Generic Exception during load", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Friend>): Int? {
        // Standard implementation
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}