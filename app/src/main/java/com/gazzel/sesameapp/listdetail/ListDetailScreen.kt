// app/src/main/java/com/gazzel/sesameapp/presentation/screens/listdetail/ListDetailScreen.kt
package com.gazzel.sesameapp.presentation.screens.listdetail

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
// Import ViewModel from correct package
import com.gazzel.sesameapp.presentation.viewmodels.ListDetailsViewModel
// Import Domain models
import com.gazzel.sesameapp.domain.model.SesameList // <<< Use Domain model
import com.gazzel.sesameapp.domain.model.PlaceItem
// Import components from the sub-package
import com.gazzel.sesameapp.presentation.components.listdetail.*
// Map related imports
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.* // Use wildcard import

// <<< REMOVED the PlaceDto.toPlaceItem mapper function >>>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    navController: NavController,
    // listId is injected via SavedStateHandle into the ViewModel
    viewModel: ListDetailsViewModel = hiltViewModel() // Get Hilt ViewModel
) {
    // Observe state from ViewModel - 'detail' is now SesameList?
    val detail: SesameList? by viewModel.detail.collectAsState() // <<< Type is now SesameList?
    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val errorMessage: String? by viewModel.errorMessage.collectAsState()
    val deleteSuccess: Boolean by viewModel.deleteSuccess.collectAsState()

    // State for UI elements controlled here (dialogs, menus, overlays, tabs, search)
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var placeMenuExpanded by rememberSaveable { mutableStateOf<String?>(null) } // Key by Place ID (String)
    var viewingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNote by rememberSaveable { mutableStateOf("") } // Holds note text for viewer/editor
    var sharingPlaceId by rememberSaveable { mutableStateOf<String?>(null) } // Use Place ID (String)
    var sharingList by rememberSaveable { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("List") } // Default tab
    var query by remember { mutableStateOf("") } // Search query state

    // Camera position state
    val cameraPositionState: CameraPositionState = rememberCameraPositionState()

    // --- Effects ---

    // Navigate back on successful deletion
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            Log.d("ListDetailScreen", "Delete successful, navigating back.")
            navController.popBackStack()
            viewModel.resetDeleteSuccess() // Reset the flag in VM
        }
    }

    // Handle error messages (e.g., show Snackbar)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // TODO: Show Snackbar using ScaffoldState if needed
            Log.e("ListDetailScreen", "Error received: $errorMessage")
            // Consider auto-dismissing the error message in the ViewModel after a delay
            // kotlinx.coroutines.delay(5000)
            // viewModel.clearErrorMessage()
        }
    }

    // Update map camera when places change (Observe the places within the SesameList)
    LaunchedEffect(detail?.places) { // Observe places in SesameList
        val placeItemList = detail?.places // This is now List<PlaceItem>?
        Log.d("ListDetailScreen", "Places updated in LaunchedEffect: ${placeItemList?.size ?: 0} places")
        val firstPlaceItem = placeItemList?.firstOrNull()
        if (firstPlaceItem != null) {
            val position = LatLng(firstPlaceItem.latitude, firstPlaceItem.longitude)
            Log.d("ListDetailScreen", "Setting map to: ${firstPlaceItem.name}, lat=${position.latitude}, lng=${position.longitude}")
            // Check if camera is already near the target to avoid unnecessary animation
            if (cameraPositionState.position.target != position) {
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(position, 12f),
                    durationMs = 1000
                )
            }
        } else {
            Log.d("ListDetailScreen", "No places to set map position")
            // Optionally move camera to a default location (e.g., user's current location) if places are empty
        }
    }

    // --- UI ---
    Scaffold(
        topBar = {
            // Adapt TopBar logic to use SesameList properties
            // Show loading/error states in the TopAppBar as well
            val listTitle = when {
                isLoading -> "Loading..."
                errorMessage != null -> "Error"
                detail != null -> detail!!.title
                else -> "List Details" // Fallback title
            }

            Column { // Combine TopAppBar and Tabs
                TopAppBar(
                    title = { Text(listTitle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Only show actions if detail is loaded successfully
                        if (detail != null && !isLoading && errorMessage == null) {
                            IconButton(onClick = { Log.d("ListDetail", "Add collaborator clicked") /* TODO */ }) {
                                Icon(Icons.Default.Add, contentDescription = "Add collaborator")
                            }
                            IconButton(onClick = { sharingList = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Share list")
                            }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                        }
                    }
                    // Add colors using TopAppBarDefaults if needed
                )
                // Show tabs only when details are successfully loaded
                if (detail != null && !isLoading && errorMessage == null) {
                    ListMapTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                }
            }
        }
    ) { innerPadding ->
        Box( // Use Box to allow overlays and loading indicator
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- Main Content Area ---
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.clearErrorMessage(); fetchListDetail() }) { // Provide retry mechanism
                            Text("Retry")
                        }
                    }
                }
                detail != null -> {
                    // DETAIL LOADED: Show Main Content
                    val placeItems = detail!!.places // Directly use List<PlaceItem> from domain model

                    ListDetailMainContent(
                        places = placeItems,
                        query = query,
                        onQueryChange = { query = it },
                        selectedTab = selectedTab,
                        isLoading = false, // Loading handled above
                        errorMessage = null, // Error handled above
                        cameraPositionState = cameraPositionState,
                        onMoreClick = { placeId -> placeMenuExpanded = placeId }
                    )
                }
                else -> {
                    // State after error dismissal or if initial state is null without error/loading
                    Text(
                        "List details unavailable.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } // End Main Content When

            // --- Overlays and Dialogs ---
            // Placed within the main Box but after the content to appear on top

            val currentDetail = detail // Capture current detail state for stable composition in overlays/dialogs

            // List-level dropdown menu
            if (menuExpanded && currentDetail != null) {
                ListDropdownMenu(
                    isPrivate = !currentDetail.isPublic, // Use SesameList.isPublic
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onPrivacyToggle = { viewModel.updateListPrivacy() },
                    onEditListName = { showEditDialog = true },
                    onMergeList = { Log.d("ListDetailScreen", "Merge List clicked") /* TODO */ },
                    onDeleteList = { showDeleteConfirmDialog = true }
                )
            }

            // Place-level dropdown menu
            if (placeMenuExpanded != null && editingNotesPlaceId == null && viewingNotesPlaceId == null && currentDetail != null) {
                val placeItemForMenu = currentDetail.places.find { it.id == placeMenuExpanded }
                if (placeItemForMenu != null) {
                    PlaceDropdownMenu(
                        placeId = placeMenuExpanded!!, // Safe due to outer check
                        expanded = true,
                        onDismiss = { placeMenuExpanded = null },
                        onShare = { placeId -> sharingPlaceId = placeId },
                        onTags = { Log.d("ListDetailScreen", "Tags clicked for $placeMenuExpanded") /* TODO */ },
                        onNotes = { placeId ->
                            // Find item again to ensure consistency (though placeItemForMenu should be the same)
                            val foundItem = currentDetail.places.find { it.id == placeId }
                            currentNote = foundItem?.notes ?: "" // Get notes directly from PlaceItem
                            viewingNotesPlaceId = placeId
                        },
                        onAddToList = { Log.d("ListDetailScreen", "Add to List clicked for $placeMenuExpanded") /* TODO */ },
                        onDeleteItem = { viewModel.deletePlace(placeMenuExpanded!!) } // Use non-null placeId
                    )
                } else {
                    // Dismiss menu if the place disappeared while menu was about to show
                    LaunchedEffect(placeMenuExpanded) { placeMenuExpanded = null }
                }
            }

            // --- Note Viewer/Editor Overlays ---
            val placeForNotesItem = currentDetail?.places?.find { it.id == viewingNotesPlaceId || it.id == editingNotesPlaceId }

            if (viewingNotesPlaceId != null && placeForNotesItem != null) {
                // Load current note from the actual item when viewer opens
                LaunchedEffect(viewingNotesPlaceId) { currentNote = placeForNotesItem.notes ?: "" }
                NoteViewer(
                    note = currentNote,
                    onClose = { viewingNotesPlaceId = null },
                    onEdit = {
                        editingNotesPlaceId = viewingNotesPlaceId // Switch to editor mode
                        viewingNotesPlaceId = null
                    }
                )
            }

            if (editingNotesPlaceId != null && placeForNotesItem != null) {
                // Load current note from the actual item when editor opens
                LaunchedEffect(editingNotesPlaceId) { currentNote = placeForNotesItem.notes ?: "" }
                NoteEditor(
                    note = currentNote,
                    onNoteChange = { currentNote = it },
                    onSave = {
                        viewModel.updatePlaceNotes(editingNotesPlaceId!!, currentNote) // Call VM
                        editingNotesPlaceId = null // Close editor
                    },
                    onCancel = { editingNotesPlaceId = null } // Close editor
                )
            }

            // --- Sharing Overlays ---
            if (sharingPlaceId != null && currentDetail != null) {
                val placeItemToShare = currentDetail.places.find { it.id == sharingPlaceId }
                if (placeItemToShare != null) {
                    SharePlaceOverlay(
                        listId = currentDetail.id, // Use currentDetail ID
                        place = placeItemToShare,
                        onDismiss = { sharingPlaceId = null }
                    )
                }
            }
            if (sharingList && currentDetail != null) {
                ShareListOverlay(
                    listId = currentDetail.id, // Use currentDetail ID
                    listName = currentDetail.title, // Use currentDetail title
                    onDismiss = { sharingList = false }
                )
            }

            // --- Dialogs ---
            if (showEditDialog && currentDetail != null) {
                EditListNameDialog(
                    currentName = currentDetail.title, // Use currentDetail title
                    onSave = { newName -> viewModel.updateListName(newName) },
                    onDismiss = { showEditDialog = false }
                )
            }
            if (showDeleteConfirmDialog && currentDetail != null) {
                DeleteConfirmDialog(
                    // Pass list title for confirmation message if desired
                    // listName = currentDetail.title,
                    onConfirm = { viewModel.deleteList() },
                    onDismiss = { showDeleteConfirmDialog = false }
                )
            }
        } // End Outer Box
    } // End Scaffold
}