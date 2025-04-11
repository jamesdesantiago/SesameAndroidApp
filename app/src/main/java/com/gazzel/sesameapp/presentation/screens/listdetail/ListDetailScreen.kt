// app/src/main/java/com/gazzel/sesameapp/presentation/screens/listdetail/ListDetailScreen.kt
package com.gazzel.sesameapp.presentation.screens.listdetail // Ensure correct package

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn // Keep LazyColumn import
import androidx.compose.material.ExperimentalMaterialApi // For pull-refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pullrefresh.* // Import pull-refresh
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // Keep for Sharing Intents
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState // <<< Import Paging LoadState
import androidx.paging.compose.LazyPagingItems // <<< Import LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems // <<< Import collectAsLazyPagingItems
import androidx.paging.compose.items // <<< Import Paging items extension for LazyColumn
// Import ViewModel and NEW State/Action
import com.gazzel.sesameapp.presentation.screens.lists.ListDetailsViewModel
import com.gazzel.sesameapp.presentation.screens.lists.ListDetailsUiState
import com.gazzel.sesameapp.presentation.screens.lists.ListDetailAction // <<< Import Action event
// Import Domain models
import com.gazzel.sesameapp.domain.model.PlaceItem
// Import components from the sub-package
import com.gazzel.sesameapp.presentation.components.listdetail.*
// Map related imports
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.collectLatest // Import collectLatest
import kotlinx.coroutines.launch
import com.gazzel.sesameapp.R // Import R class
import kotlin.collections.get

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class) // Add ExperimentalMaterialApi
@Composable
fun ListDetailScreen(
    navController: NavController,
    viewModel: ListDetailsViewModel = hiltViewModel()
) {
    // --- Collect States ---
    val uiState by viewModel.uiState.collectAsState() // For metadata, loading, main errors
    val actionError by viewModel.actionError.collectAsState() // For action errors (delete, update)
    // Collect places PagingData
    val lazyPlaceItems: LazyPagingItems<PlaceItem> =
        viewModel.placesPagerFlow.collectAsLazyPagingItems()

    // --- Local UI State (menus, dialogs, etc. - Keep these) ---
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var placeMenuExpanded by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNote by rememberSaveable { mutableStateOf("") }
    var sharingPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var sharingList by rememberSaveable { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("List") }
    var query by remember { mutableStateOf("") } // Local search/filter query

    // --- Snackbar & Scope Setup ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current // Keep for sharing intents

    // --- Camera Position State ---
    val cameraPositionState: CameraPositionState = rememberCameraPositionState {
        // Default position (e.g., center of US or world) until first place loads
        position = CameraPosition.fromLatLngZoom(LatLng(39.8283, -98.5795), 3f)
    }

    // --- Effects ---

    // Handle Navigation for DeleteSuccess (List deleted)
    LaunchedEffect(uiState) {
        if (uiState is ListDetailsUiState.DeleteSuccess) {
            Log.d("ListDetailScreen", "List Delete successful state detected, navigating back.")
            // Consider showing a Snackbar confirmation before navigating back
            // scope.launch { snackbarHostState.showSnackbar("List deleted successfully.") }
            navController.popBackStack()
        }
    }

    // Show Snackbar for Action Errors (Update/Delete Place/List failures)
    LaunchedEffect(actionError) {
        actionError?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long // Give more time for error messages
                )
            }
            viewModel.clearActionError() // Clear error after showing
        }
    }

    // Listen for events from ViewModel (like triggering pager refresh)
    LaunchedEffect(Unit) { // Use Unit to run once and keep listening
        viewModel.actionEvent.collectLatest { action ->
            when (action) {
                is ListDetailAction.RefreshPlaces -> {
                    Log.d("ListDetailScreen", "Received RefreshPlaces event, calling refresh() on LazyPagingItems.")
                    lazyPlaceItems.refresh()
                }
            }
        }
    }


    // --- Pull to Refresh State for Places List ---
    val isPlaceListRefreshing = lazyPlaceItems.loadState.refresh is LoadState.Loading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isPlaceListRefreshing,
        onRefresh = {
            Log.d("ListDetailScreen", "Pull-to-refresh triggered.")
            // Refresh both metadata and places Pager
            viewModel.fetchListMetadata() // Re-fetch list details
            lazyPlaceItems.refresh()      // Refresh places Pager
        }
    )

    // --- Main UI ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
        // TopAppBar defined conditionally based on metadata load state
    ) { innerPadding ->

        // Render based on the main UI state (metadata loading/error/success)
        when (val metadataState = uiState) {
            is ListDetailsUiState.InitialLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.loading_list_details), modifier = Modifier.padding(top = 60.dp))
                }
            }
            is ListDetailsUiState.MetadataError -> {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(metadataState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.fetchListMetadata() }) { Text(stringResource(R.string.button_retry)) }
                }
            }
            is ListDetailsUiState.Success -> {
                // --- Metadata Loaded Successfully - Show Main Content ---
                val listMetadata = metadataState.listMetadata

                Scaffold( // Nested scaffold for TopAppBar
                    snackbarHost = { SnackbarHost(snackbarHostState) }, // Needed again if overlays show snackbars
                    topBar = {
                        Column {
                            ListDetailTopBar(
                                listName = listMetadata.title,
                                onBackClick = { navController.navigateUp() },
                                onAddCollaboratorClick = { Log.d("ListDetail", "Add collaborator clicked") /* TODO: Implement */ },
                                onShareListClick = { sharingList = true }, // Trigger list share overlay
                                onMoreClick = { menuExpanded = true } // Open list dropdown
                            )
                            ListMapTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                        }
                    }
                ) { successPadding -> // Use different padding name

                    Box( // Box to contain main content, pull-refresh, and overlays
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(successPadding) // Apply nested padding
                            .pullRefresh(pullRefreshState) // Apply pull-refresh modifier
                    ) {
                        // --- Main Content Column (Search + List/Map) ---
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField( // Search Bar
                                value = query,
                                onValueChange = { query = it }, // Local filtering applied below
                                label = { Text(stringResource(R.string.list_detail_search_label)) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = { if(query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, stringResource(R.string.cd_clear_search_icon)) } }
                            )

                            // Filter currently loaded items based on local query
                            val filteredPlaceItems = remember(query, lazyPlaceItems.itemSnapshotList) {
                                val currentItems = lazyPlaceItems.itemSnapshotList.items
                                if (query.isBlank()) currentItems else currentItems.filter { place ->
                                    place.name.contains(query, ignoreCase = true) ||
                                            place.address.contains(query, ignoreCase = true) ||
                                            (place.notes ?: "").contains(query, ignoreCase = true)
                                }
                            }

                            // --- Handle Place Paging Load States for List/Map ---
                            val placesLoadState = lazyPlaceItems.loadState

                            // Show central loading indicator only during initial place load when list is empty
                            if (placesLoadState.refresh is LoadState.Loading && lazyPlaceItems.itemCount == 0) {
                                Box(modifier=Modifier.fillMaxSize(), contentAlignment = Alignment.Center){ CircularProgressIndicator() }
                            }
                            // Show central error only during initial place load error when list is empty
                            else if (placesLoadState.refresh is LoadState.Error && lazyPlaceItems.itemCount == 0) {
                                val error = (placesLoadState.refresh as LoadState.Error).error
                                Column(modifier=Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.error_loading_places_detail, error.localizedMessage ?: stringResource(R.string.error_unknown)), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { lazyPlaceItems.retry() }) { Text(stringResource(R.string.button_retry)) }
                                }
                            }
                            // Show Empty state only after successful load with 0 items
                            else if (placesLoadState.refresh is LoadState.NotLoading && placesLoadState.append.endOfPaginationReached && lazyPlaceItems.itemCount == 0) {
                                Box(modifier=Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center){
                                    Text(stringResource(R.string.list_detail_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // --- Show List or Map ---
                            else {
                                when (selectedTab) {
                                    "List" -> {
                                        LazyColumn(modifier = Modifier.weight(1f)) { // Use weight
                                            // Use Paging items extension
                                            items(
                                                count = lazyPlaceItems.itemCount,
                                                key = { index -> lazyPlaceItems.peek(index)?.id ?: index } // Use Place ID as key
                                            ) { index ->
                                                val place = lazyPlaceItems[index]
                                                place?.let {
                                                    // Check against locally filtered list before rendering
                                                    if (filteredPlaceItems.any { fp -> fp.id == it.id }) {
                                                        PlaceRow(
                                                            place = it,
                                                            onMoreClick = { placeId -> placeMenuExpanded = placeId }
                                                        )
                                                        Divider() // Add divider between items
                                                    }
                                                } ?: run {
                                                    // Optional: Show a placeholder item while loading
                                                    // Box(modifier = Modifier.fillMaxWidth().height(70.dp).background(Color.LightGray))
                                                }
                                            }

                                            // Append loading/error states at the bottom
                                            item {
                                                if (placesLoadState.append is LoadState.Loading) {
                                                    Box(Modifier.fillMaxWidth().padding(16.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                                                } else if (placesLoadState.append is LoadState.Error) {
                                                    val error = (placesLoadState.append as LoadState.Error).error
                                                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                                                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(stringResource(R.string.error_loading_more_places, error.localizedMessage ?: stringResource(R.string.error_unknown)), color = MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                                            Button(onClick = { lazyPlaceItems.retry() }, modifier=Modifier.padding(top=4.dp)) { Text(stringResource(R.string.button_retry)) }
                                                        }
                                                    }
                                                }
                                            }
                                        } // end LazyColumn
                                    } // end "List" tab case

                                    "Map" -> {
                                        Box(modifier = Modifier.weight(1f)) { // Use weight
                                            GoogleMap(
                                                modifier = Modifier.fillMaxSize(),
                                                cameraPositionState = cameraPositionState
                                                // properties = MapProperties(isMyLocationEnabled = true) // Enable if location permission handled
                                            ) {
                                                // Use the locally filtered list for markers
                                                filteredPlaceItems.forEach { place ->
                                                    Marker(
                                                        state = MarkerState(position = LatLng(place.latitude, place.longitude)),
                                                        title = place.name,
                                                        snippet = place.address
                                                    )
                                                }
                                            }
                                            // Consider a subtle loading indicator overlay for the map if needed
                                            if (placesLoadState.refresh is LoadState.Loading && lazyPlaceItems.itemCount == 0){
                                                CircularProgressIndicator(Modifier.align(Alignment.Center))
                                            }
                                        }
                                    } // end "Map" tab case
                                } // end when(selectedTab)
                            } // end else (show list/map content)
                        } // end Main Content Column

                        // --- Overlays and Dialogs (Conditionally shown) ---
                        // Get current snapshot of items for overlay data
                        val currentPlaceItemsSnapshot = lazyPlaceItems.itemSnapshotList.items

                        // List-level dropdown menu (uses listMetadata)
                        if (menuExpanded) { /* ... ListDropdownMenu - same as before ... */ }

                        // Place-level dropdown menu (uses currentPlaceItemsSnapshot)
                        if (placeMenuExpanded != null && editingNotesPlaceId == null && viewingNotesPlaceId == null) {
                            val placeItemForMenu = currentPlaceItemsSnapshot.find { it.id == placeMenuExpanded }
                            if (placeItemForMenu != null) {
                                PlaceDropdownMenu(
                                    placeId = placeMenuExpanded!!, expanded = true,
                                    onDismiss = { placeMenuExpanded = null },
                                    onShare = { placeId -> sharingPlaceId = placeId },
                                    onTags = { Log.d("ListDetailScreen", "Tags clicked") },
                                    onNotes = { placeId ->
                                        val foundItem = currentPlaceItemsSnapshot.find { it.id == placeId }
                                        currentNote = foundItem?.notes ?: ""
                                        viewingNotesPlaceId = placeId
                                    },
                                    onAddToList = { Log.d("ListDetailScreen", "Add to List clicked") },
                                    onDeleteItem = { viewModel.deletePlace(placeMenuExpanded!!) } // Call VM delete
                                )
                            } else {
                                LaunchedEffect(placeMenuExpanded) { placeMenuExpanded = null } // Close if item disappeared
                            }
                        }

                        // Note Viewer/Editor Overlays (uses currentPlaceItemsSnapshot)
                        val placeForNotesItem = currentPlaceItemsSnapshot.find { it.id == viewingNotesPlaceId || it.id == editingNotesPlaceId }
                        if (viewingNotesPlaceId != null && placeForNotesItem != null) {
                            LaunchedEffect(viewingNotesPlaceId) { currentNote = placeForNotesItem.notes ?: "" }
                            NoteViewer(note = currentNote, onClose = { viewingNotesPlaceId = null }, onEdit = { editingNotesPlaceId = viewingNotesPlaceId; viewingNotesPlaceId = null })
                        }
                        if (editingNotesPlaceId != null && placeForNotesItem != null) {
                            LaunchedEffect(editingNotesPlaceId) { currentNote = placeForNotesItem.notes ?: "" }
                            NoteEditor(note = currentNote, onNoteChange = { currentNote = it }, onSave = { viewModel.updatePlaceNotes(editingNotesPlaceId!!, currentNote); editingNotesPlaceId = null }, onCancel = { editingNotesPlaceId = null })
                        }

                        // Sharing Overlays (uses listMetadata and currentPlaceItemsSnapshot)
                        if (sharingPlaceId != null) {
                            val placeItemToShare = currentPlaceItemsSnapshot.find { it.id == sharingPlaceId }
                            SharePlaceOverlay(listId = listMetadata.id, place = placeItemToShare, onDismiss = { sharingPlaceId = null })
                        }
                        if (sharingList) {
                            ShareListOverlay(listId = listMetadata.id, listName = listMetadata.title, onDismiss = { sharingList = false })
                        }

                        // Dialogs (use listMetadata)
                        if (showEditDialog) { EditListNameDialog(currentName = listMetadata.title, onSave = { viewModel.updateListName(it); showEditDialog = false }, onDismiss = { showEditDialog = false }) }
                        if (showDeleteConfirmDialog) { DeleteConfirmDialog(onConfirm = { viewModel.deleteList(); showDeleteConfirmDialog = false }, onDismiss = { showDeleteConfirmDialog = false }) }


                        // --- Pull Refresh Indicator (Visual element) ---
                        PullRefreshIndicator(
                            refreshing = isPlaceListRefreshing, // Use the state derived from LazyPagingItems
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter) // Position indicator at the top
                        )

                    } // End Main Content Box
                } // End Nested Scaffold for Success state
            } // End Success case

            is ListDetailsUiState.DeleteSuccess -> { // Handled by LaunchedEffect for navigation
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { /* Optional message */ }
            }
        } // End when(metadataState)
    } // End Outer Scaffold
} // End ListDetailScreen

// Add required strings to strings.xml if not already present:
/*
<string name="loading_list_details">Loading List Details…</string>
<string name="list_detail_search_label">Search in this list</string>
<string name="error_loading_places">Error loading places</string>
<string name="error_loading_places_detail">Error loading places: %1$s</string>
<string name="list_detail_empty">This list is empty. Add some places!</string>
<string name="error_loading_more_places">Error loading more: %1$s</string>
*/