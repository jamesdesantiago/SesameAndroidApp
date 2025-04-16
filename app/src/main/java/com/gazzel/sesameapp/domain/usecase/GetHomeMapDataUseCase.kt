// domain/usecase/GetHomeMapDataUseCase.kt
package com.gazzel.sesameapp.domain.usecase

import android.util.Log
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.util.flatMap
import com.gazzel.sesameapp.domain.util.map
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import kotlin.math.* // Wildcard import for math functions

// Updated data class to hold all home screen data
data class HomeScreenData( // Renamed from HomeMapData
    val user: User,
    val recentLists: List<SesameList>,
    val currentLocation: LatLng,
    // val allPlaces: List<PlaceItem>, // Only include if needed by UI
    val nearbyPlaces: List<PlaceItem>
)

class GetHomeMapDataUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository, // <<< ADD UserRepository
    private val listRepository: ListRepository // <<< ADD ListRepository
) {
    companion object { /* Constants */ }

    suspend operator fun invoke(): Result<HomeScreenData> {
        Log.d("GetHomeDataUseCase", "Executing...")

        // Use coroutineScope for concurrent fetches where possible
        return try {
            coroutineScope {
                // Fetch user, location, and all places concurrently
                val userDeferred = async { userRepository.getCurrentUser().firstOrNull() }
                val locationDeferred = async { locationRepository.getCurrentLocation() } // Returns Result<LatLng>
                val allPlacesDeferred = async { placeRepository.getPlaces() } // Returns Result<List<PlaceItem>>
                val recentListsDeferred = async { listRepository.getRecentLists(limit = 5) } // Returns Result<List<SesameList>>

                val user = userDeferred.await()
                    ?: return@coroutineScope Result.error<HomeScreenData>(AppException.AuthException("User not found or not authenticated."))

                val locationResult = locationDeferred.await()
                val allPlacesResult = allPlacesDeferred.await()
                val recentListsResult = recentListsDeferred.await()

                // Check results and proceed if all successful
                locationResult.flatMap { currentLocation ->
                    allPlacesResult.flatMap { allPlaces ->
                        recentListsResult.map { recentLists -> // Use map for the final piece
                            Log.d("GetHomeDataUseCase", "All fetches successful. Filtering places...")
                            val nearbyPlaces = allPlaces.filter { place ->
                                calculateDistance(
                                    currentLocation.latitude, currentLocation.longitude,
                                    place.latitude, place.longitude
                                ) <= DEFAULT_RADIUS_KM
                            }
                            Log.d("GetHomeDataUseCase", "Filtering complete: ${nearbyPlaces.size} nearby.")

                            HomeScreenData( // Construct final result
                                user = user,
                                recentLists = recentLists,
                                currentLocation = currentLocation,
                                nearbyPlaces = nearbyPlaces
                            )
                        } // Propagates recentListsResult error
                    } // Propagates allPlacesResult error
                } // Propagates locationResult error
            }
        } catch (e: Exception) {
            Log.e("GetHomeDataUseCase", "Error during concurrent fetch/processing", e)
            Result.error(AppException.UnknownException("Failed to load home data", e))
        }
    }

    private fun calculateDistance(...) { /* ... implementation ... */ }
}