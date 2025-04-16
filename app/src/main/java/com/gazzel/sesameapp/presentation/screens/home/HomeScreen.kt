package com.gazzel.sesameapp.presentation.screens.home

import android.Manifest // <<< ADD Manifest import
import android.content.pm.PackageManager // <<< ADD PackageManager import
import androidx.activity.compose.rememberLauncherForActivityResult // <<< ADD ActivityResult import
import androidx.activity.result.contract.ActivityResultContracts // <<< ADD Contract import
import androidx.compose.foundation.background // Keep background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext // <<< Need context for permission check
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat // <<< ADD ContextCompat import
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R
import com.gazzel.sesameapp.domain.model.PlaceItem // Import PlaceItem for map markers
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.presentation.components.ShareDialog
import com.gazzel.sesameapp.presentation.navigation.Screen
import com.gazzel.sesameapp.presentation.viewmodels.HomeViewModel
import com.gazzel.sesameapp.presentation.viewmodels.HomeUiState
// Map related imports
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.* // Use wildcard map compose import


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState() // Observe search results separately

    // UI state for search bar visibility and query
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // UI state for share dialog
    var showShareDialog by remember { mutableStateOf(false) }
    var selectedListForShare by remember { mutableStateOf<SesameList?>(null) }

    // Map Camera State
    val cameraPositionState = rememberCameraPositionState {
        // Default position (e.g., center of a region) before location is known
        position = CameraPosition.fromLatLngZoom(LatLng(40.7128, -74.0060), 10f) // Example: NYC
    }

    val context = LocalContext.current

    // --- Permission Handling ---
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasLocationPermission = isGranted
        if (isGranted) {
            viewModel.loadDataAfterPermission()
        } else {
            viewModel.handlePermissionDenied() // Inform VM about denial
        }
    }

    // Request permission & load data effect
    LaunchedEffect(hasLocationPermission) { // Re-run if permission status changes
        if (hasLocationPermission) {
            viewModel.loadDataAfterPermission()
        } else {
            // Optionally show rationale before launching if needed
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    // --- End Permission Handling ---


    Scaffold(
        topBar = {
            // --- TopAppBar (Modified for Search State) ---
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    // Only show search/profile if not loading/initial
                    if (uiState !is HomeUiState.Loading && uiState !is HomeUiState.Initial) {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) { // Clear search when hiding
                                searchQuery = ""
                                viewModel.searchLists("")
                            }
                        }) {
                            Icon(
                                imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search, // Toggle icon
                                contentDescription = stringResource(if (showSearch) R.string.cd_close_search else R.string.cd_search_icon)
                            )
                        }
                        IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                            Icon(Icons.Default.Person, contentDescription = stringResource(R.string.cd_profile_icon))
                        }
                    }
                }
            )
        },
        bottomBar = {
            // --- Bottom Navigation (Keep as is) ---
            NavigationBar { /* ... Navigation Items ... */ }
        }
    ) { paddingValues ->
        // --- Main Content Area ---
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) { // Main Column

            // --- Search Bar (Conditionally Visible) ---
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchLists(it) // Trigger search in VM
                    },
                    label = { Text(stringResource(R.string.home_search_label)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.searchLists("") // Clear search in VM
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search_icon))
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // --- Content based on UI State ---
            Box(modifier = Modifier.weight(1f)) { // Box to overlay map/list/loading/error

                when (val state = uiState) {
                    is HomeUiState.Initial, is HomeUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                            // Text(stringResource(if (state is HomeUiState.Initial) R.string.state_initializing else R.string.state_loading))
                        }
                    }
                    is HomeUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            // Offer retry or permission request based on error type
                            Button(onClick = {
                                if (state.isPermissionError && !hasLocationPermission) {
                                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                } else {
                                    viewModel.refresh() // General refresh
                                }
                            }) {
                                Text(stringResource(if (state.isPermissionError) R.string.button_grant_permission else R.string.button_retry)) // <<< ADD string
                            }
                        }
                    }
                    is HomeUiState.Success -> {
                        // --- Map View ---
                        val mapData = state.data
                        // Update camera position when location changes in the state
                        LaunchedEffect(mapData.currentLocation) {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(mapData.currentLocation, 14f), // Zoom closer
                                1000 // Animation duration
                            )
                        }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(isMyLocationEnabled = hasLocationPermission), // Show blue dot if permission granted
                            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission) // Enable button if permission granted
                        ) {
                            // Display nearby places on the map
                            mapData.nearbyPlaces.forEach { place ->
                                Marker(
                                    state = MarkerState(position = LatLng(place.latitude, place.longitude)),
                                    title = place.name,
                                    snippet = place.address,
                                    // Optional: Add onClick to navigate to list/place detail?
                                    // onClick = { navController.navigate(...) }
                                )
                            }
                        }

                        // --- Overlay Content (Welcome, Quick Actions, Lists) ---
                        // This Column overlays the map
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)) // Semi-transparent background
                                .padding(16.dp)
                        ) {
                            // Welcome section
                            val userName = mapData.user.displayName ?: mapData.user.username ?: stringResource(R.string.home_welcome_default_user)
                            Text(
                                text = stringResource(R.string.home_welcome, userName),
                                style = MaterialTheme.typography.headlineSmall // Adjusted size
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Quick actions
                            Row( /* ... QuickActionButton Row ... */ ) {
                                QuickActionButton(
                                    icon = Icons.Default.Add,
                                    label = stringResource(R.string.home_quick_action_new_list),
                                    onClick = { navController.navigate(Screen.CreateList.route) }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.Search, // Or Map icon?
                                    label = stringResource(R.string.home_quick_action_search), // Or "Nearby"?
                                    onClick = { /* Maybe zoom out map or show search bar? */ }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.List, // Icon for lists
                                    label = stringResource(R.string.cd_lists_icon), // Use list label
                                    onClick = { navController.navigate(Screen.Lists.route) } // Navigate to lists
                                )
                            }


                            Spacer(modifier = Modifier.height(24.dp))

                            // Recent/Search lists section
                            Text(
                                text = if (isSearchActive && searchQuery.isNotBlank()) "Search Results" else stringResource(R.string.home_section_recent_lists),
                                style = MaterialTheme.typography.titleMedium, // Adjusted size
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Determine which list to show based on search state
                            val listToShow = if (isSearchActive && searchQuery.isNotBlank()) searchResults else mapData.recentLists

                            if (listToShow.isEmpty()) {
                                Text(
                                    text = if (isSearchActive && searchQuery.isNotBlank()) "No lists match '$searchQuery'" else stringResource(R.string.home_no_recent_lists),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f)) { // Allow list to take remaining space
                                    items(listToShow, key = { it.id }) { list ->
                                        ListItem(
                                            headlineContent = { Text(list.title, maxLines=1, overflow= androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                            supportingContent = { Text(list.description, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                            // Remove trailing share button from here? Redundant with dialog.
                                            // trailingContent = { ... IconButton for Share ... },
                                            modifier = Modifier.clickable {
                                                navController.navigate(Screen.ListDetail.createRoute(list.id))
                                            }
                                        )
                                    }
                                }
                            }
                        } // End Overlay Column
                    } // End Success State
                } // End When state
            } // End Content Box
        } // End Main Column (after search bar)
    } // End Scaffold

    // Share Dialog (Keep as is)
    // selectedListForShare?.let { list -> ... ShareDialog(...) }
}

// QuickActionButton (Keep as is)
@Composable
private fun QuickActionButton(icon: ImageVector, label: String, onClick: () -> Unit) { /* ... */ }