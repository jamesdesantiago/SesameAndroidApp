// app/src/main/java/com/gazzel/sesameapp/presentation/screens/listdetail/ListDetailScreen.kt
package com.gazzel.sesameapp.presentation.screens.listdetail

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add // Keep needed icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
// Import ViewModel and NEW State
import com.gazzel.sesameapp.presentation.viewmodels.ListDetailsViewModel
import com.gazzel.sesameapp.presentation.viewmodels.ListDetailsUiState // <<< IMPORT NEW STATE
// Import Domain models
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.model.PlaceItem
// Import components from the sub-package
import com.gazzel.sesameapp.presentation.components.listdetail.*
// Map related imports
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch // For Snackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    navController: NavController,
    viewModel: ListDetailsViewModel = hiltViewModel()
) {
    // --- Collect the SINGLE uiState ---
    val uiState by viewModel.uiState.collectAsState()

    // --- Local UI State (menus, dialogs, overlays, tabs, search) - Keep these ---
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
    var query by remember { mutableStateOf("") }

    // --- Snackbar Setup ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Camera Position State ---
    val cameraPositionState: CameraPositionState = rememberCameraPositionState()

    // --- Effects - Triggered by uiState changes ---

    // Handle Navigation for DeleteSuccess
    LaunchedEffect(uiState) {
        if (uiState is ListDetailsUiState.DeleteSuccess) {
            Log.d("ListDetailScreen", "Delete successful state detected, navigating back.")
            navController.popBackStack()
            // No need to call reset in VM, state transition handled it.
        }
        // Show Snackbar on Error
        if (uiState is ListDetailsUiState.Error) {
            val errorState = uiState as ListDetailsUiState.Error
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorState.message,
                    duration = SnackbarDuration.Short
                )
            }
            // Optionally clear the error in VM after showing, or let next action replace it
            // viewModel.clearErrorState() // Add this if you want errors to be dismissable
        }
    }

    // Update map camera only when in Success state and places change
    LaunchedEffect(uiState) {
        if (uiState is ListDetailsUiState.Success) {
            val listDetail = (uiState as ListDetailsUiState.Success).list
            val placeItemList = listDetail.places
            Log.d("ListDetailScreen", "Success State: Places updated: ${placeItemList.size} places")
            val firstPlaceItem = placeItemList.firstOrNull()
            if (firstPlaceItem != null) {
                val position = LatLng(firstPlaceItem.latitude, firstPlaceItem.longitude)
                Log.d("ListDetailScreen", "Setting map to: ${firstPlaceItem.name}, lat=${position.latitude}, lng=${position.longitude}")
                if (cameraPositionState.position.target != position) {
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newLatLngZoom(position, 12f),
                        durationMs = 1000
                    )
                }
            } else {
                Log.d("ListDetailScreen", "No places in list to set map position")
            }
        }
    }

    // --- Main UI Structure ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) } // Add SnackbarHost
        // TopBar is now defined conditionally within the 'when' block
    ) { innerPadding ->

        // Use 'when' to render based on the uiState
        when (val state = uiState) {
            is ListDetailsUiState.Loading -> {
                // --- Loading State UI ---
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ListDetailsUiState.Success -> {
                // --- Success State UI ---
                val listDetail = state.list // Extract data

                // Re-declare Scaffold here to include TopAppBar conditionally for Success state
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }, // Need it here too for overlays
                    topBar = {
                        Column { // Combine TopAppBar and Tabs
                            ListDetailTopBar(
                                listName = listDetail.title, // Use data from state
                                onBackClick = { navController.navigateUp() },
                                onAddCollaboratorClick = { Log.d("ListDetail", "Add collaborator clicked") /* TODO */ },
                                onShareListClick = { sharingList = true },
                                onMoreClick = { menuExpanded = true }
                            )
                            ListMapTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                        }
                    }
                ) { successPadding -> // Use a different name for nested padding

                    Box( // Box to contain main content and overlays/dialogs
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(successPadding) // Apply padding from nested Scaffold
                    ) {
                        // --- Main Content (List/Map) ---
                        ListDetailMainContent(
                            places = listDetail.places, // Pass non-null places
                            query = query,
                            onQueryChange = { query = it },
                            selectedTab = selectedTab,
                            isLoading = false, // Handled by outer 'when'
                            errorMessage = null, // Handled by outer 'when' / Snackbar
                            cameraPositionState = cameraPositionState,
                            onMoreClick = { placeId -> placeMenuExpanded = placeId }
                        )

                        // --- Overlays and Dialogs (Conditionally shown based on local UI state) ---
                        // These should only be composed/visible when in the Success state

                        val currentDetailForOverlays = listDetail // Use non-null listDetail

                        // List-level dropdown menu
                        if (menuExpanded) { // No need to check currentDetailForOverlays != null here
                            ListDropdownMenu(
                                isPrivate = !currentDetailForOverlays.isPublic,
                                expanded = menuExpanded,
                                onDismiss = { menuExpanded = false },
                                onPrivacyToggle = { viewModel.updateListPrivacy() },
                                onEditListName = { showEditDialog = true },
                                onMergeList = { Log.d("ListDetailScreen", "Merge List clicked") /* TODO */ },
                                onDeleteList = { showDeleteConfirmDialog = true }
                            )
                        }

                        // Place-level dropdown menu
                        if (placeMenuExpanded != null && editingNotesPlaceId == null && viewingNotesPlaceId == null) {
                            val placeItemForMenu = currentDetailForOverlays.places.find { it.id == placeMenuExpanded }
                            if (placeItemForMenu != null) {
                                PlaceDropdownMenu(
                                    placeId = placeMenuExpanded!!,
                                    expanded = true,
                                    onDismiss = { placeMenuExpanded = null },
                                    onShare = { placeId -> sharingPlaceId = placeId },
                                    onTags = { Log.d("ListDetailScreen", "Tags clicked for $placeMenuExpanded") /* TODO */ },
                                    onNotes = { placeId ->
                                        val foundItem = currentDetailForOverlays.places.find { it.id == placeId }
                                        currentNote = foundItem?.notes ?: ""
                                        viewingNotesPlaceId = placeId
                                    },
                                    onAddToList = { Log.d("ListDetailScreen", "Add to List clicked for $placeMenuExpanded") /* TODO */ },
                                    onDeleteItem = { viewModel.deletePlace(placeMenuExpanded!!) }
                                )
                            } else {
                                LaunchedEffect(placeMenuExpanded) { placeMenuExpanded = null }
                            }
                        }

                        // Note Viewer/Editor Overlays
                        val placeForNotesItem = currentDetailForOverlays.places.find { it.id == viewingNotesPlaceId || it.id == editingNotesPlaceId }

                        if (viewingNotesPlaceId != null && placeForNotesItem != null) {
                            LaunchedEffect(viewingNotesPlaceId) { currentNote = placeForNotesItem.notes ?: "" }
                            NoteViewer(
                                note = currentNote,
                                onClose = { viewingNotesPlaceId = null },
                                onEdit = {
                                    editingNotesPlaceId = viewingNotesPlaceId
                                    viewingNotesPlaceId = null
                                }
                            )
                        }

                        if (editingNotesPlaceId != null && placeForNotesItem != null) {
                            LaunchedEffect(editingNotesPlaceId) { currentNote = placeForNotesItem.notes ?: "" }
                            NoteEditor(
                                note = currentNote,
                                onNoteChange = { currentNote = it },
                                onSave = {
                                    viewModel.updatePlaceNotes(editingNotesPlaceId!!, currentNote)
                                    editingNotesPlaceId = null
                                },
                                onCancel = { editingNotesPlaceId = null }
                            )
                        }

                        // Sharing Overlays
                        if (sharingPlaceId != null) {
                            val placeItemToShare = currentDetailForOverlays.places.find { it.id == sharingPlaceId }
                            if (placeItemToShare != null) {
                                SharePlaceOverlay(
                                    listId = currentDetailForOverlays.id,
                                    place = placeItemToShare,
                                    onDismiss = { sharingPlaceId = null }
                                )
                            }
                        }
                        if (sharingList) {
                            ShareListOverlay(
                                listId = currentDetailForOverlays.id,
                                listName = currentDetailForOverlays.title,
                                onDismiss = { sharingList = false }
                            )
                        }

                        // Dialogs
                        if (showEditDialog) {
                            EditListNameDialog(
                                currentName = currentDetailForOverlays.title,
                                onSave = { newName -> viewModel.updateListName(newName) },
                                onDismiss = { showEditDialog = false }
                            )
                        }
                        if (showDeleteConfirmDialog) {
                            DeleteConfirmDialog(
                                onConfirm = { viewModel.deleteList() },
                                onDismiss = { showDeleteConfirmDialog = false }
                            )
                        }

                    } // End Main Content Box (for Success State)
                } // End nested Scaffold (for Success State)
            } // End Success case

            is ListDetailsUiState.Error -> {
                // --- Error State UI ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding) // Apply outer padding
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message, // Get message from state
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.fetchListDetail() }) { // Provide retry
                        Text("Retry")
                    }
                }
            }
            is ListDetailsUiState.DeleteSuccess -> {
                // --- Delete Success State UI (usually brief/navigated away) ---
                // This state is primarily for triggering the navigation effect.
                // You can show a temporary message if desired.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding), // Apply outer padding
                    contentAlignment = Alignment.Center
                ) {
                    // Text("List Deleted", style = MaterialTheme.typography.headlineMedium)
                    // LaunchedEffect handles the navigation
                }
            }
        } // End when(state)
    } // End Outer Scaffold
} // End ListDetailScreen