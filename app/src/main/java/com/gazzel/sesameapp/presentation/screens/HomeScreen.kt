package com.gazzel.sesameapp.presentation.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.gazzel.sesameapp.data.service.ListService
import com.gazzel.sesameapp.domain.model.ListResponse
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.data.manager.PlaceUpdateManager
import com.gazzel.sesameapp.data.manager.DataUpdateEvent
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.*

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    // State for places to display on the map
    var places by remember { mutableStateOf<List<PlaceItem>>(emptyList()) }
    var filteredPlaces by remember { mutableStateOf<List<PlaceItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    // Camera position state for the map
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f) // Default position
    }

    // Fused Location Provider Client
    val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    // Permission launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scope.launch {
                try {
                    val location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        currentLocation = LatLng(location.latitude, location.longitude)
                        // Center the map on the current location with a zoom level for 1km radius
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            14f // Zoom level to approximate 1km radius
                        )
                        // Filter places within 1km
                        filteredPlaces = places.filter { place ->
                            val distance = calculateDistance(
                                location.latitude, location.longitude,
                                place.latitude, place.longitude
                            )
                            distance <= 1.0 // 1km radius
                        }
                        Log.d("HomeScreen", "Filtered ${filteredPlaces.size} places within 1km")
                    } else {
                        errorMessage = "Unable to get current location"
                    }
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Failed to get location: ${e.message}", e)
                    errorMessage = "Failed to get location: ${e.message}"
                }
            }
        } else {
            errorMessage = "Location permission denied"
        }
    }

    // Check and request location permission
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        14f // Zoom level to approximate 1km radius
                    )
                    // Filter places within 1km
                    filteredPlaces = places.filter { place ->
                        val distance = calculateDistance(
                            location.latitude, location.longitude,
                            place.latitude, place.longitude
                        )
                        distance <= 1.0 // 1km radius
                    }
                    Log.d("HomeScreen", "Filtered ${filteredPlaces.size} places within 1km")
                } else {
                    errorMessage = "Unable to get current location"
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Failed to get location: ${e.message}", e)
                errorMessage = "Failed to get location: ${e.message}"
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Retrofit setup for ListService
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val listService = Retrofit.Builder()
        .baseUrl("https://gazzel.io/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ListService::class.java)

    // Token management
    var cachedToken: String? by remember { mutableStateOf(null) }
    var tokenExpiry: Long by remember { mutableStateOf(0) }

    suspend fun getValidToken(): String? {
        if (cachedToken != null && System.currentTimeMillis() / 1000 < tokenExpiry - 300) {
            return cachedToken
        } else {
            return try {
                val result = auth.currentUser?.getIdToken(true)?.await()
                cachedToken = result?.token
                tokenExpiry = result?.expirationTimestamp ?: 0
                Log.d("HomeScreen", "Refreshed token: $cachedToken, expires: $tokenExpiry")
                cachedToken
            } catch (e: Exception) {
                Log.e("HomeScreen", "Token refresh failed: ${e.message}", e)
                null
            }
        }
    }

    // Function to fetch lists and places
    suspend fun fetchPlaces(): List<PlaceItem> {
        try {
            val token = getValidToken() ?: throw Exception("No valid token")
            val authorizationHeader = "Bearer $token"

            // Fetch user's lists
            val listsResponse = listService.getLists(authorizationHeader)
            if (!listsResponse.isSuccessful) {
                errorMessage = "Failed to load lists: ${listsResponse.message()}"
                return emptyList()
            }

            val lists: List<ListResponse> = listsResponse.body() ?: emptyList()
            val allPlaces = mutableListOf<PlaceItem>()

            // Fetch places for each list
            for (list in lists) {
                val listDetailsResponse = listService.getListDetail(list.id, authorizationHeader)
                if (listDetailsResponse.isSuccessful) {
                    val listDetails = listDetailsResponse.body()
                    listDetails?.places?.let { placesList: List<PlaceItem> ->
                        allPlaces.addAll(placesList)
                    }
                } else {
                    Log.e("HomeScreen", "Failed to load details for list ${list.id}: ${listDetailsResponse.message()}")
                }
            }

            Log.d("HomeScreen", "Loaded ${allPlaces.size} places")
            return allPlaces
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to load places: ${e.message}", e)
            errorMessage = "Failed to load places: ${e.message}"
            return emptyList()
        }
    }

    // Fetch lists and places on composition and when a data update event occurs
    LaunchedEffect(Unit) {
        // Initial fetch
        places = fetchPlaces()
        currentLocation?.let { location ->
            filteredPlaces = places.filter { place ->
                val distance = calculateDistance(
                    location.latitude, location.longitude,
                    place.latitude, place.longitude
                )
                distance <= 1.0 // 1km radius
            }
            Log.d("HomeScreen", "Filtered ${filteredPlaces.size} places within 1km")
        }

        // Listen for data update events (place added or list deleted)
        PlaceUpdateManager.dataUpdateFlow.collectLatest { event ->
            when (event) {
                is DataUpdateEvent.PlaceAdded -> {
                    Log.d("HomeScreen", "Place added event received, refreshing data")
                }
                is DataUpdateEvent.ListDeleted -> {
                    Log.d("HomeScreen", "List deleted event received, refreshing data")
                }
            }
            places = fetchPlaces()
            currentLocation?.let { location ->
                filteredPlaces = places.filter { place ->
                    val distance = calculateDistance(
                        location.latitude, location.longitude,
                        place.latitude, place.longitude
                    )
                    distance <= 1.0 // 1km radius
                }
                Log.d("HomeScreen", "Filtered ${filteredPlaces.size} places within 1km after update")
            }
        }
    }

    SesameAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Home") },
                        actions = {
                            IconButton(onClick = {
                                context.startActivity(Intent(context, UserListsActivity::class.java))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "View Lists"
                                )
                            }
                            IconButton(onClick = {
                                context.startActivity(Intent(context, FriendsActivity::class.java))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Friends"
                                )
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            context.startActivity(Intent(context, CreateListActivity::class.java))
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create List"
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = true)
                    ) {
                        filteredPlaces.forEach { place ->
                            Marker(
                                state = MarkerState(
                                    position = LatLng(place.latitude, place.longitude)
                                ),
                                title = place.name,
                                snippet = place.address
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

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