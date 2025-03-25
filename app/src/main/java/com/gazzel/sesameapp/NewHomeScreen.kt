package com.gazzel.sesameapp

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
fun NewHomeScreen() {
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
                        Log.d("NewHomeScreen", "Filtered ${filteredPlaces.size} places within 1km")
                    } else {
                        errorMessage = "Unable to get current location"
                    }
                } catch (e: Exception) {
                    Log.e("NewHomeScreen", "Failed to get location: ${e.message}", e)
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
                    Log.d("NewHomeScreen", "Filtered ${filteredPlaces.size} places within 1km")
                } else {
                    errorMessage = "Unable to get current location"
                }
            } catch (e: Exception) {
                Log.e("NewHomeScreen", "Failed to get location: ${e.message}", e)
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
                Log.d("NewHomeScreen", "Refreshed token: $cachedToken, expires: $tokenExpiry")
                cachedToken
            } catch (e: Exception) {
                Log.e("NewHomeScreen", "Token refresh failed: ${e.message}", e)
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
                    Log.e("NewHomeScreen", "Failed to load details for list ${list.id}: ${listDetailsResponse.message()}")
                }
            }

            Log.d("NewHomeScreen", "Loaded ${allPlaces.size} places")
            return allPlaces
        } catch (e: Exception) {
            Log.e("NewHomeScreen", "Failed to load places: ${e.message}", e)
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
            Log.d("NewHomeScreen", "Filtered ${filteredPlaces.size} places within 1km")
        }

        // Listen for data update events (place added or list deleted)
        PlaceUpdateManager.dataUpdateFlow.collectLatest { event ->
            when (event) {
                is DataUpdateEvent.PlaceAdded -> {
                    Log.d("NewHomeScreen", "Place added event received, refreshing data")
                }
                is DataUpdateEvent.ListDeleted -> {
                    Log.d("NewHomeScreen", "List deleted event received, refreshing data")
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
                Log.d("NewHomeScreen", "Filtered ${filteredPlaces.size} places within 1km after update")
            }
        }
    }

    SesameAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Title
                Text(
                    text = "Sesame",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Row of Buttons (New List, Friends, Lists)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // New List Button
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(context, AddListActivity::class.java).apply {
                                    putExtra("listId", -1)
                                    putExtra("listName", "New List")
                                }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                            .padding(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New List",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "New List",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Friends Button
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, FriendsActivity::class.java))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                            .padding(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Friends",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Friends",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Lists Button
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, ListActivity::class.java))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(70.dp)
                            .padding(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Lists",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lists",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar (Placeholder)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Find a place",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Google Map
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            color = Color.LightGray,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        // Add marker for current location
                        currentLocation?.let { location ->
                            Marker(
                                state = MarkerState(position = location),
                                title = "Your Location",
                                snippet = "You are here"
                            )
                        }

                        // Add markers for places within 1km
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

                    // Display error message if any
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// Helper function to calculate distance between two points (in kilometers)
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0 // Radius of the Earth in kilometers
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}