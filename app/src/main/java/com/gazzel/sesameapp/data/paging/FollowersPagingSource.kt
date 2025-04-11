// app/src/main/java/com/gazzel/sesameapp/data/paging/FollowersPagingSource.kt
package com.gazzel.sesameapp.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.gazzel.sesameapp.data.mapper.toDomainFriend // Ensure correct import
import com.gazzel.sesameapp.data.model.User           // Your data layer User model
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.model.Friend // Domain model
import retrofit2.HttpException
import java.io.IOException
import android.util.Log

class FollowersPagingSource(
    private val userApiService: UserApiService,
    private val tokenProvider: TokenProvider
    // Optional: Pass in the set of IDs the current user is *following* if you
    // want to accurately mark followers who are also followed back.
    // private val currentlyFollowingIds: Set<String>
) : PagingSource<Int, Friend>() { // Key: Int (page), Value: Friend (domain model)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Friend> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.e("FollowersPagingSource", "Authentication token is null.")
            return LoadResult.Error(IOException("User not authenticated"))
        }
        val authorizationHeader = "Bearer $token"

        val page = params.key ?: 1 // Start page is 1
        val pageSize = params.loadSize
        Log.d("FollowersPagingSource", "Loading page $page with size $pageSize")

        return try {
            val response = userApiService.getFollowers( // Call paginated API for followers
                token = authorizationHeader,
                page = page,
                pageSize = pageSize
            )

            if (response.isSuccessful) {
                val paginatedResponse = response.body()
                if (paginatedResponse == null) {
                    Log.w("FollowersPagingSource", "API response body is null for page $page")
                    return LoadResult.Page(emptyList(), if (page == 1) null else page - 1, null)
                }

                val usersDto = paginatedResponse.items // This is List<User> data model
                // --- MAP User data model to Friend domain model ---
                val friendsDomain = usersDto.map { userDto ->
                    // For followers, 'isFollowing' is generally false unless you specifically check.
                    // Passing the following set is more accurate but adds complexity.
                    // Defaulting to false is simpler for a pure "followers" list view.
                    userDto.toDomainFriend(isFollowing = false)
                    // If you passed currentlyFollowingIds:
                    // userDto.toDomainFriend(isFollowing = currentlyFollowingIds.contains(userDto.id.toString()))
                }
                Log.d("FollowersPagingSource", "Page $page loaded ${friendsDomain.size} followers. Total pages: ${paginatedResponse.totalPages}")

                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (page < paginatedResponse.totalPages) page + 1 else null

                LoadResult.Page(
                    data = friendsDomain,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
            } else {
                Log.e("FollowersPagingSource", "API Error: ${response.code()} - ${response.message()}")
                LoadResult.Error(HttpException(response))
            }

        } catch (e: IOException) {
            Log.e("FollowersPagingSource", "IOException during load", e)
            LoadResult.Error(e)
        } catch (e: HttpException) {
            Log.e("FollowersPagingSource", "HttpException during load", e)
            LoadResult.Error(e)
        } catch (e: Exception) {
            Log.e("FollowersPagingSource", "Generic Exception during load", e)
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