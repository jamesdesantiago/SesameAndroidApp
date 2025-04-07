// presentation/screens/listdetail/ListDetailScreen.kt
package com.gazzel.sesameapp.presentation.screens.listdetail // Correct package

import androidx.compose.runtime.* // Import common compose functions
import androidx.hilt.navigation.compose.hiltViewModel // Import Hilt VM composable
import androidx.navigation.NavController // Import NavController
// ... other necessary imports for UI elements (Scaffold, Box, Column, etc.)
// ... imports for your custom components (TopBar, Tabs, Dialogs, etc.)
// ... imports for Map components (GoogleMap, rememberCameraPositionState etc.)
// ... imports for PlaceItem, PlaceDto, etc.
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.gazzel.sesameapp.presentation.viewmodels.ListDetailsViewModel
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.data.model.PlaceDto // If using the DTO mapping workaround
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import android.util.Log
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner // For back press
import androidx.compose.runtime.saveable.rememberSaveable
import com.gazzel.sesameapp.presentation.components.listdetail.* // Import all components from the package


// Mapper DTO -> Item (if still needed here, better in ViewModel or Mapper layer)
private fun PlaceDto.toPlaceItem(listId: String): PlaceItem {
    return PlaceItem(
        id = this.id, name = this.name, description = this.description ?: "",
        address = this.address, latitude = this.latitude, longitude = this.longitude,
        listId = listId, notes = null, rating = this.rating, visitStatus = null
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    navController: NavController,
    // listId is injected via SavedStateHandle into the ViewModel
    viewModel: ListDetailsViewModel = hiltViewModel() // Get Hilt ViewModel
) {
    // Observe state from ViewModel
    val detail by viewModel.detail.collectAsState() // detail is now ListResponse?
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()

    // State for UI elements controlled here (same as before)
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    // Remove showDeleteSuccessDialog state, use deleteSuccess from ViewModel
    var placeMenuExpanded by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNote by rememberSaveable { mutableStateOf("") }
    var sharingPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var sharingList by rememberSaveable { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("List") }
    val cameraPositionState: CameraPositionState = rememberCameraPositionState()
    var query by remember { mutableStateOf("") }

    // --- Effects ---

    // Navigate back on successful deletion
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            // Optionally show a Snackbar/Toast message here
            navController.popBackStack()
            viewModel.resetDeleteSuccess() // Reset the flag in VM
        }
    }

    // Update map camera when places change (adjust for ListResponse?)
    LaunchedEffect(detail?.places) {
        val placesDto = detail?.places // Assuming places are still List<PlaceDto>?
        Log.d("ListDetailScreen", "Places updated: ${placesDto?.size ?: 0} places")
        val firstPlaceDto = placesDto?.firstOrNull()
        if (firstPlaceDto != null) {
            val position = LatLng(firstPlaceDto.latitude, firstPlaceDto.longitude)
            Log.d("ListDetailScreen", "Setting map to: ${firstPlaceDto.name}, lat=${position.latitude}, lng=${position.longitude}")
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(position, 12f),
                durationMs = 1000
            )
        } else {
            Log.d("ListDetailScreen", "No places to set map position")
        }
    }

    // --- UI ---
    Scaffold(
        topBar = {
            if (detail != null) { // Only show TopBar if detail is loaded
                Column {
                    ListDetailTopBar(
                        listName = detail!!.name ?: "List Details", // Use safe call or default
                        onBackClick = { navController.navigateUp() },
                        onAddCollaboratorClick = { /* TODO */ Log.d("ListDetail", "Add collaborator") },
                        onShareListClick = { sharingList = true },
                        onMoreClick = { menuExpanded = true }
                    )
                    ListMapTabs(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Show loading indicator
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            // Show error message
            else if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
                // Optional: Add a retry button
                Button(onClick = { /* viewModel.fetchListDetail() */ /* Re-enable fetch */ }, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text("Retry")
                }
            }
            // Show content only when detail is not null and not loading/error
            else if (detail != null) {
                ListDetailMainContent(
                    // Map DTOs to Items for the MainContent composable
                    places = detail!!.