// Create File: app/src/main/java/com/gazzel/sesameapp/data/paging/PlaceInListPagingSource.kt
package com.gazzel.sesameapp.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.gazzel.sesameapp.data.mapper.toPlaceItem // <<< Ensure this mapper exists (PlaceDto -> PlaceItem)
import com.gazzel.sesameapp.data.remote.dto.PlaceDto // <<< Import your PlaceDto
import com.gazzel.sesameapp.data.remote.ListApiService // <<< Import the correct API service
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.model.PlaceItem // <<< Your Domain Model for places in lists
import retrofit2.HttpException
import java.io.IOException
import android.util.Log

/**
 * PagingSource for loading places within a specific list.
 * Uses the dedicated /lists/{listId}/places endpoint.
 */
class PlaceInListPagingSource(
    private val listApiService: ListApiService,
    private val tokenProvider: TokenProvider,
    private val listId: String // The ID of the list whose places are being loaded
) : PagingSource<Int, PlaceItem>() { // Key: Int (page number), Value: PlaceItem (Domain Model)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PlaceItem> {
        // --- 1. Authentication ---
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.e("PlaceInListPagingSource", "Authentication token is null for list $listId.")
            return LoadResult.Error(IOException("User not authenticated"))
        }
        val authorizationHeader = "Bearer $token"

        // --- 2. Determine Page Number ---
        val page = params.key ?: 1 // Start page is 1
        val pageSize = params.loadSize
        Log.d("PlaceInListPagingSource", "Loading page $page, size $pageSize for list $listId")

        // --- 3. Basic Input Validation ---
        if (listId.isBlank()) {
            Log.e("PlaceInListPagingSource", "Invalid listId provided.")
            return LoadResult.Error(IllegalArgumentException("List ID cannot be blank"))
        }

        // --- 4. API Call ---
        return try {
            val response = listApiService.getPlacesInList( // Call the dedicated endpoint
                authorization = authorizationHeader,
                listId = listId,
                page = page,
                pageSize = pageSize
            )

            // --- 5. Handle API Response ---
            if (response.isSuccessful) {
                val paginatedResponse = response.body()
                if (paginatedResponse == null) {
                    Log.w("PlaceInListPagingSource", "API response body is null for page $page, list $listId")
                    // Treat null body as empty page / end of list
                    return LoadResult.Page(
                        data = emptyList(),
                        prevKey = if (page == 1) null else page - 1,
                        nextKey = null // Assume end if body is null
                    )
                }

                val placesDto = paginatedResponse.items // List<PlaceDto>
                // --- 6. Map DTOs to Domain Models ---
                val placesDomain = placesDto.map { placeDto ->
                    // Ensure you have a mapper for this conversion in PlaceMapper.kt
                    // It should handle converting Int ID (from DTO) to String ID if needed by Domain model
                    placeDto.toPlaceItem(listId = listId)
                }
                Log.d("PlaceInListPagingSource", "Page $page loaded ${placesDomain.size} places for list $listId. Total pages: ${paginatedResponse.totalPages}")

                // --- 7. Determine Next/Previous Keys ---
                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (page < paginatedResponse.totalPages) page + 1 else null
                Log.d("PlaceInListPagingSource", "PrevKey: $prevKey, NextKey: $nextKey for list $listId")

                // --- 8. Return LoadResult.Page ---
                LoadResult.Page(
                    data = placesDomain,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
            } else {
                // --- 9. Handle API Error ---
                Log.e("PlaceInListPagingSource", "API Error: ${response.code()} - ${response.message()} for list $listId")
                LoadResult.Error(HttpException(response))
            }

            // --- 10. Handle Exceptions ---
        } catch (e: IOException) {
            Log.e("PlaceInListPagingSource", "IOException during load for list $listId", e)
            LoadResult.Error(e)
        } catch (e: HttpException) {
            Log.e("PlaceInListPagingSource", "HttpException during load for list $listId", e)
            LoadResult.Error(e)
        } catch (e: IllegalArgumentException) { // Catch specific validation errors
            Log.e("PlaceInListPagingSource", "IllegalArgumentException: ${e.message}", e)
            LoadResult.Error(e)
        } catch (e: Exception) {
            Log.e("PlaceInListPagingSource", "Generic Exception during load for list $listId", e)
            LoadResult.Error(e)
        }
    }

    // --- getRefreshKey (Standard Implementation) ---
    override fun getRefreshKey(state: PagingState<Int, PlaceItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    // --- IMPORTANT: Ensure Mapper Exists ---
    // In data/mapper/PlaceMapper.kt
    /*
    import com.gazzel.sesameapp.data.remote.dto.PlaceDto // Your Place DTO
    import com.gazzel.sesameapp.domain.model.PlaceItem // Your Domain PlaceItem

    fun PlaceDto.toPlaceItem(listId: String): PlaceItem {
        return PlaceItem(
            id = this.id.toString(), // Convert Int ID from DTO to String for Domain Model? Adjust if types match.
            name = this.name,
            description = "", // Assuming description isn't in this DTO/API response for places
            address = this.address,
            latitude = this.latitude,
            longitude = this.longitude,
            listId = listId, // Assign the list ID passed in
            notes = this.notes, // Map notes
            rating = this.rating, // Map rating
            visitStatus = this.visitStatus // Map visit status
        )
    }
    */
}