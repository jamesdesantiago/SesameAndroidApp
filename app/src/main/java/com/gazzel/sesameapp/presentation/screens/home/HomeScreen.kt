package com.gazzel.sesameapp.presentation.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes // <<< Import annotation for QuickActionButton
import androidx.compose.animation.AnimatedVisibility // <<< Import for search bar
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow // <<< Import for ListItem
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.presentation.navigation.Screen
import com.gazzel.sesameapp.presentation.viewmodels.HomeViewModel
import com.gazzel.sesameapp.presentation.viewmodels.HomeUiState
// Map related imports
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState() // Use VM state for search active

    // UI state for search bar visibility and query
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Map Camera State
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.7128, -74.0060), 10f)
    }

    val context = LocalContext.current

    // --- Permission Handling (Keep as is) ---
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
            viewModel.handlePermissionDenied()
        }
    }
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.loadDataAfterPermission()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    // --- End Permission Handling ---


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    if (uiState !is HomeUiState.Loading && uiState !is HomeUiState.Initial) {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) {
                                searchQuery = ""
                                viewModel.searchLists("") // Use empty query to clear search
                            }
                        }) {
                            Icon(
                                imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = stringResource(if (showSearch) R.string.cd_close_search else R.string.cd_search_icon) // <<< ADD cd_close_search
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
            // Assuming Bottom Nav is standard, kept out for brevity
            NavigationBar {
                // ... Your NavigationBarItems using stringResource for labels/contentDescriptions ...
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_home_icon)) }, // <<< Use String Resource
                    selected = true, // <<< This is Home
                    onClick = { /* Already here */ }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_lists_icon)) }, // <<< Use String Resource
                    selected = false,
                    onClick = { navController.navigate(Screen.Lists.route) { launchSingleTop = true; popUpTo(Screen.Home.route){ inclusive = true } } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_friends_icon)) }, // <<< Use String Resource
                    selected = false, // <<< Assuming Home is selected
                    onClick = { navController.navigate(Screen.Friends.route) { launchSingleTop = true; popUpTo(Screen.Home.route){ inclusive = true } } }
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchLists(it)
                    },
                    label = { Text(stringResource(R.string.home_search_label)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, // Decorative
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.searchLists("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search_icon))
                            }
                        }
                    },
                    singleLine = true
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is HomeUiState.Initial, is HomeUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is HomeUiState.Error -> {
                        Column( // Error Column (Keep as is)
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                if (state.isPermissionError && !hasLocationPermission) {
                                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                } else {
                                    viewModel.refresh()
                                }
                            }) {
                                Text(stringResource(if (state.isPermissionError) R.string.button_grant_permission else R.string.button_retry))
                            }
                        }
                    }
                    is HomeUiState.Success -> {
                        val mapData = state.data
                        LaunchedEffect(mapData.currentLocation) {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(mapData.currentLocation, 14f), 1000)
                        }

                        GoogleMap( // Map View (Keep as is)
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
                        ) {
                            mapData.nearbyPlaces.forEach { place ->
                                Marker(
                                    state = MarkerState(position = LatLng(place.latitude, place.longitude)),
                                    title = place.name,
                                    snippet = place.address
                                )
                            }
                        }

                        // Overlay Column
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                                .padding(16.dp)
                        ) {
                            val userName = mapData.user.displayName ?: mapData.user.username ?: stringResource(R.string.home_welcome_default_user)
                            Text(stringResource(R.string.home_welcome, userName), style = MaterialTheme.typography.headlineSmall)

                            Spacer(modifier = Modifier.height(16.dp))

                            Row( // Quick Actions
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly // Space actions
                            ) {
                                // Use the updated QuickActionButton signature
                                QuickActionButton(
                                    icon = Icons.Default.Add,
                                    labelResId = R.string.home_quick_action_new_list, // Pass Res ID
                                    onClick = { navController.navigate(Screen.CreateList.route) }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.Search,
                                    labelResId = R.string.home_quick_action_search, // Pass Res ID
                                    onClick = { showSearch = true } // Toggle search bar
                                )
                                QuickActionButton(
                                    icon = Icons.Default.List,
                                    labelResId = R.string.cd_lists_icon, // Pass Res ID
                                    onClick = { navController.navigate(Screen.Lists.route) }
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // --- Updated Section Title ---
                            Text(
                                // Use stringResource for "Search Results"
                                text = if (isSearchActive) stringResource(R.string.home_search_results_title) else stringResource(R.string.home_section_recent_lists),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val listToShow = if (isSearchActive) searchResults else mapData.recentLists

                            if (listToShow.isEmpty()) {
                                Text(
                                    // Use stringResource for "No lists match..."
                                    text = if (isSearchActive) stringResource(R.string.home_search_no_results, searchQuery) else stringResource(R.string.home_no_recent_lists),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(listToShow, key = { it.id }) { list ->
                                        // Use standard Material 3 ListItem
                                        ListItem(
                                            headlineContent = { Text(list.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            supportingContent = { Text(list.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
        } // End Main Column
    } // End Scaffold
}

// --- Updated QuickActionButton ---
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    @StringRes labelResId: Int, // <<< Changed parameter to Resource ID
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp) // Add some padding
    ) {
        Icon(
            imageVector = icon,
            // Use the label as content description - ensure the label string is descriptive enough
            contentDescription = stringResource(labelResId),
            modifier = Modifier.size(28.dp) // Slightly larger icon
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(labelResId), // <<< Use stringResource here
            style = MaterialTheme.typography.labelSmall // Use smaller label style
        )
    }
}