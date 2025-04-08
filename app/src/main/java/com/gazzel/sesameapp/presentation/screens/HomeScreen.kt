package com.gazzel.sesameapp.presentation.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gazzel.sesameapp.data.manager.PlaceUpdateManager
import com.gazzel.sesameapp.data.remote.dto.PlaceDto
import com.gazzel.sesameapp.data.service.ListService
import com.gazzel.sesameapp.domain.model.ListResponse
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.presentation.activities.CreateListActivity
import com.gazzel.sesameapp.presentation.activities.FriendsActivity
import com.gazzel.sesameapp.presentation.activities.UserListsActivity
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


@OptIn(ExperimentalMaterial3Api::class) // Added OptIn for Material 3 APIs like TopAppBar
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    // State for places
    var places by remember { mutableStateOf<List<PlaceItem>>(emptyList()) }
    var filteredPlaces by remember { mutableStateOf<List<PlaceItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    // Camera position state - Initialize simply
    val cameraPositionState: CameraPositionState = rememberCameraPositionState() // <-- Initialize here

    // Fused Location Client
    val fusedLocationClient: FusedLocationProviderClient = remember { // Use remember for stability
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Permission launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Request location after permission granted
            scope.launch {
                try {
                    // TODO: Add a check for Location Availability/Settings if location is null
                    @SuppressLint("MissingPermission") // Already checked/requested
                    val location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        currentLocation = LatLng(location.latitude, location.longitude)
                        cameraPositionState.animate( // Animate camera to new location
                            update = CameraUpdateFactory.newLatLngZoom(currentLocation!!, 14f),
                            durationMs = 1000
                        )
                        // Filter places now that location is known
                        filteredPlaces = places.filter { place: PlaceItem -> // Explicit type
                            val distance = calculateDistance(
                                location.latitude, location.longitude,
                                place.latitude, place.longitude
                            )
                            distance <= 1.0 // 1km radius
                        }
                        Log.d("HomeScreen", "Location obtained and filtered ${filteredPlaces.size} places.")
                    } else {
                        errorMessage = "Unable to get current location. Is location enabled?"
                        Log.w("HomeScreen", "fusedLocationClient.lastLocation returned null.")
                    }
                } catch (e: SecurityException) { // Catch SecurityException specifically
                    Log.e("HomeScreen", "Location permission error after grant?", e)
                    errorMessage = "Permission error retrieving location."
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Failed to get location: ${e.message}", e)
                    errorMessage = "Error getting location: ${e.localizedMessage}"
                }
            }
        } else {
            errorMessage = "Location permission denied"
            // Optionally show a rationale or guide user to settings
        }
    }

    // --- Location Logic ---
    fun checkAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            scope.launch { // Launch coroutine to get location
                try {
                    @SuppressLint("MissingPermission")
                    val location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        val newLocation = LatLng(location.latitude, location.longitude)
                        // Only update state and filter if location actually changed significantly
                        if (currentLocation == null || calculateDistance(currentLocation!!.latitude, currentLocation!!.longitude, newLocation.latitude, newLocation.longitude) > 0.05) { // 50m threshold
                            currentLocation = newLocation
                            cameraPositionState.animate(
                                update = CameraUpdateFactory.newLatLngZoom(newLocation, 14f),
                                durationMs = 1000
                            )
                            filteredPlaces = places.filter { place: PlaceItem -> // Explicit type
                                val distance = calculateDistance(
                                    location.latitude, location.longitude,
                                    place.latitude, place.longitude
                                )
                                distance <= 1.0 // 1km radius
                            }
                            Log.d("HomeScreen", "Initial location obtained and filtered ${filteredPlaces.size} places.")
                        }
                    } else {
                        errorMessage = "Unable to get current location. Is location enabled?"
                        Log.w("HomeScreen", "Initial fusedLocationClient.lastLocation returned null.")
                        // Maybe request updates if last location is null? (More complex)
                    }
                } catch (e: SecurityException) {
                    Log.e("HomeScreen", "Initial check: Location permission error?", e)
                    errorMessage = "Permission error retrieving location."
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) // Re-request just in case
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Failed to get initial location", e)
                    errorMessage = "Error getting location: ${e.localizedMessage}"
                }
            }
        } else {
            // Request permission if not granted
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // --- Retrofit & Token Management (Consider moving to ViewModel) ---
    val okHttpClient = remember { // remember instances tied to composition
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    val listService = remember { // remember instances tied to composition
        Retrofit.Builder()
            .baseUrl("https://gazzel.io/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ListService::class.java)
    }
    var cachedToken: String? by remember { mutableStateOf(null) }
    var tokenExpiry: Long by remember { mutableStateOf(0L) } // Use mutableStateOf for recomposition

    suspend fun getValidToken(): String? { // Make this function stable if moved outside composable
        if (cachedToken != null && System.currentTimeMillis() / 1000 < tokenExpiry - 300) {
            return cachedToken
        } else {
            // Simplified refresh logic (better in ViewModel/Repository)
            return try {
                val user = auth.currentUser ?: return null
                val result = user.getIdToken(true).await()
                cachedToken = result.token
                tokenExpiry = result.expirationTimestamp ?: 0L
                Log.d("HomeScreen", "Refreshed token expires: $tokenExpiry")
                cachedToken
            } catch (e: Exception) {
                Log.e("HomeScreen", "Token refresh failed: ${e.message}", e)
                null
            }
        }
    }

    // --- Data Fetching ---
    suspend fun fetchPlaces(): List<PlaceItem> {
        return try {
            val token = getValidToken() ?: throw IllegalStateException("Authentication token is missing.")
            val authorizationHeader = "Bearer $token"
            val listsResponse = listService.getLists(authorizationHeader)

            if (!listsResponse.isSuccessful) {
                // ... error handling ...
                return emptyList()
            }

            val lists: List<ListResponse> = listsResponse.body() ?: emptyList()
            val allPlaceItems = mutableListOf<PlaceItem>() // Changed variable name

            for (list in lists) {
                try {
                    val listDetailsResponse = listService.getListDetail(list.id, authorizationHeader)
                    if (listDetailsResponse.isSuccessful) {
                        // ** APPLY MAPPING HERE **
                        listDetailsResponse.body()?.places?.let { placesDtoList -> // Assume places is List<PlaceDto>?
                            val itemsInList = placesDtoList.map { dto ->
                                dto.toPlaceItem(list.id) // Map DTO -> Item
                            }
                            allPlaceItems.addAll(itemsInList) // Add the mapped PlaceItems
                        }
                    } else { /* log error */ }
                } catch (listDetailError: Exception) { /* log error */ }
            }
            Log.d("HomeScreen", "Loaded and mapped ${allPlaceItems.size} places total")
            errorMessage = null
            return allPlaceItems // Return the list of PlaceItems
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to load places overall", e)
            errorMessage = "Failed to load places: ${e.localizedMessage ?: "Unknown error"}"
            return emptyList()
        }
    }

    // --- Effects ---
    LaunchedEffect(Unit) { // Runs once on composition
        checkAndFetchLocation() // Check permission and get initial location

        // Initial data fetch
        places = fetchPlaces()
        // Re-filter after fetching places if location is already available
        currentLocation?.let { loc ->
            filteredPlaces = places.filter { place: PlaceItem -> // Explicit type PlaceItem
                val distance = calculateDistance(loc.latitude, loc.longitude, place.latitude, place.longitude)
                distance <= 1.0
            }
            Log.d("HomeScreen", "Filtered places after initial fetch: ${filteredPlaces.size}")
        }

        // Listen for data update events
        PlaceUpdateManager.dataUpdateFlow.collectLatest { event ->
            Log.d("HomeScreen", "Data update event received ($event), refreshing data...")
            places = fetchPlaces()
            // Re-filter after update if location is available
            currentLocation?.let { loc ->
                filteredPlaces = places.filter { place: PlaceItem -> // Explicit type
                    val distance = calculateDistance(loc.latitude, loc.longitude, place.latitude, place.longitude)
                    distance <= 1.0
                }
                Log.d("HomeScreen", "Filtered places after update: ${filteredPlaces.size}")
            }
        }
    }

    // --- UI ---
    SesameAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar( // Use M3 TopAppBar
                        title = { Text("Home") },
                        actions = {
                            IconButton(onClick = {
                                context.startActivity(Intent(context, UserListsActivity::class.java)) // Should resolve
                            }) {
                                Icon(Icons.Default.List, "View Lists")
                            }
                            IconButton(onClick = {
                                context.startActivity(Intent(context, FriendsActivity::class.java)) // Should resolve
                            }) {
                                Icon(Icons.Default.Person, "Friends")
                            }
                        }
                        // Add colors if needed:
                        // colors = TopAppBarDefaults.topAppBarColors(
                        //     containerColor = MaterialTheme.colorScheme.primaryContainer,
                        //     titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        // )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            context.startActivity(Intent(context, CreateListActivity::class.java)) // Should resolve
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, "Create List")
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    GoogleMap( // Should resolve
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = true), // Should resolve
                        // Optional: Add onMapLoaded callback
                        // onMapLoaded = { Log.d("HomeScreen", "Map Loaded") }
                        uiSettings = MapUiSettings(myLocationButtonEnabled = true) // Optionally enable button
                    ) {
                        // Markers should render if filteredPlaces is not empty
                        filteredPlaces.forEach { place ->
                            Marker( // Should resolve
                                state = MarkerState( // Should resolve
                                    position = LatLng(place.latitude, place.longitude)
                                ),
                                title = place.name,
                                snippet = place.address
                                // Optional: Add onClick for marker
                                // onClick = { marker -> Log.d("HomeScreen", "Clicked ${marker.title}"); true }
                            )
                        }
                    }

                    // Display Error Message (e.g., Snackbar or Text)
                    if (errorMessage != null) {
                        // Example using SnackbarHost at bottom
                        // Or just a Text overlay
                        Text(
                            text = errorMessage!!, // Use non-null assertion if needed after check
                            color = Color.Red, // Or MaterialTheme.colorScheme.error
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(8.dp)
                        )
                    }
                } // End Box
            } // End Scaffold padding lambda
        } // End Surface
    } // End SesameAppTheme
}

// calculateDistance function remains the same
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 // Earth's radius in kilometers
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun PlaceDto.toPlaceItem(listId: String): PlaceItem { // Use listId if needed, or remove if not
    return PlaceItem(
        id = this.id,
        name = this.name,
        description = this.description ?: "",
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude,
        // If PlaceItem doesn't need listId for HomeScreen, you can simplify,
        // but it needs it if PlaceItem definition requires it.
        listId = listId, // Or determine differently if needed
        notes = null, // Or map if DTO has notes
        rating = this.rating, // String? from DTO
        visitStatus = null // Or map if DTO has status
    )
}