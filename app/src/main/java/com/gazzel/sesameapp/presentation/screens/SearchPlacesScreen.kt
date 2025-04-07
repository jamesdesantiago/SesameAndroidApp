// presentation/screens/SearchPlacesScreen.kt --> RENAME to presentation/screens/search/SearchPlacesScreen.kt
package com.gazzel.sesameapp.presentation.screens.search // Adjust package

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.* // Import layout components
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.* // Import Material 3 components
import androidx.compose.runtime.* // Import Compose runtime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel // Import Hilt VM
import androidx.navigation.NavController // Import NavController
import com.gazzel.sesameapp.data.service.PlaceDetailsResponse
import com.gazzel.sesameapp.data.service.PlacePrediction
import com.gazzel.sesameapp.ui.theme.SesameAppTheme // Import your theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPlacesScreen(
    navController: NavController,
    viewModel: SearchPlacesViewModel = hiltViewModel() // Inject ViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val overlayStep by viewModel.overlayStep.collectAsState()
    var query by remember { mutableStateOf("") } // Local state for TextField binding

    // Navigate back when PlaceAdded state is reached
    LaunchedEffect(uiState) {
        if (uiState is SearchPlacesUiState.PlaceAdded) {
            // Optionally show a success message (e.g., Snackbar)
            navController.popBackStack()
        }
    }

    SesameAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Add Place to List") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it // Update local state for TextField
                            viewModel.updateQuery(it) // Trigger VM logic
                        },
                        label = { Text("Search for a place...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = uiState !is SearchPlacesUiState.AddingPlace // Disable while adding
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Handle Different UI States ---
                    when (val state = uiState) {
                        is SearchPlacesUiState.Idle -> {
                            // Show placeholder or prompt
                            Text("Start typing to search for places.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is SearchPlacesUiState.Searching -> {
                            // Show loading indicator while searching
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Searching...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is SearchPlacesUiState.SuggestionsLoaded -> {
                            if (state.suggestions.isEmpty()) {
                                Text("No suggestions found for \"${state.query}\".", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text("${state.suggestions.size} suggestions found:", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(state.suggestions, key = { it.placeId }) { prediction ->
                                        SuggestionItem(prediction = prediction) {
                                            query = prediction.text.text // Update text field on click
                                            viewModel.selectPlace(prediction) // Trigger detail fetch
                                        }
                                    }
                                }
                            }
                        }
                        is SearchPlacesUiState.LoadingDetails -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Loading details for ${state.placeName ?: "place"}...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is SearchPlacesUiState.DetailsLoaded -> {
                            // Details are loaded, waiting for overlay interaction
                            Text("Selected: ${state.placeDetails.displayName.text}", style = MaterialTheme.typography.bodyLarge)
                            Text(state.placeDetails.formattedAddress, style = MaterialTheme.typography.bodyMedium)
                            // Overlay will handle the next steps
                        }
                        is SearchPlacesUiState.AddingPlace -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Adding ${state.placeDetails.displayName.text} to list...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is SearchPlacesUiState.PlaceAdded -> {
                            // Usually handled by LaunchedEffect navigation, but can show temporary text
                            Text("Place Added!", style = MaterialTheme.typography.bodyLarge, color = Color.Green) // Example color
                        }
                        is SearchPlacesUiState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                            Button(onClick = { viewModel.clearError() }) { // Add retry/dismiss
                                Text("Dismiss")
                            }
                        }
                    } // End when(state)

                    Spacer(modifier = Modifier.weight(1f)) // Push skip button down

                    // Skip button (visible unless place is being added)
                    AnimatedVisibility(visible = uiState !is SearchPlacesUiState.AddingPlace && overlayStep == OverlayStep.Hidden) {
                        Button(
                            onClick = { navController.popBackStack() }, // Just go back if skipped
                            modifier = Modifier.fillMaxWidth().padding(top=16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp) // Optional flat look
                        ) {
                            Text("Skip Adding Place")
                        }
                    }

                } // End Column
            } // End Scaffold

            // --- Overlay Visibility and Content ---
            AnimatedVisibility(
                visible = overlayStep != OverlayStep.Hidden && (uiState is SearchPlacesUiState.DetailsLoaded || uiState is SearchPlacesUiState.AddingPlace) ,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // Get details ONLY if in the correct state to avoid crashes
                val details = (uiState as? SearchPlacesUiState.DetailsLoaded)?.placeDetails
                    ?: (uiState as? SearchPlacesUiState.AddingPlace)?.placeDetails

                if (details != null) {
                    SearchPlacesOverlay(
                        step = overlayStep,
                        placeDetails = details,
                        onVisitStatusSelected = { status -> viewModel.setVisitStatusAndProceed(status) },
                        onRatingSelected = { rating -> viewModel.setRatingAndAddPlace(rating) },
                        onDismiss = { viewModel.resetOverlayState() } // Action to hide overlay
                    )
                }
            } // End AnimatedVisibility for Overlay

        } // End Outer Box
    } // End SesameAppTheme
} // End SearchPlacesScreen

// --- Helper Composables ---

@Composable
fun SuggestionItem(prediction: PlacePrediction, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            text = prediction.text.text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun SearchPlacesOverlay(
    step: OverlayStep,
    placeDetails: PlaceDetailsResponse,
    onVisitStatusSelected: (status: String?) -> Unit,
    onRatingSelected: (rating: String?) -> Unit,
    onDismiss: () -> Unit // To close the overlay
) {
    // Scrim background to dim the underlying screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss), // Dismiss on scrim click
        contentAlignment = Alignment.BottomCenter // Align card to bottom
    ) {
        // Prevent clicks on the card from propagating to the scrim
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {} // Block clicks
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Content based on OverlayStep ---
                when (step) {
                    OverlayStep.ShowPlaceDetails -> { // Changed from AskVisitOrNot trigger
                        Text(placeDetails.displayName.text, style = MaterialTheme.typography.titleMedium)
                        Text(placeDetails.formattedAddress, style = MaterialTheme.typography.bodyMedium)
                        placeDetails.rating?.let { googleRating ->
                            Text("Google Rating: ${String.format("%.1f", googleRating)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // Button moved out, triggered by VM now
                    }
                    OverlayStep.AskVisitOrNot -> {
                        Text("Have you been here?", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()){
                            Button(onClick = { onVisitStatusSelected("VISITED") }, modifier = Modifier.weight(1f)) { Text("Yes, Visited") }
                            Button(onClick = { onVisitStatusSelected("WANT_TO_VISIT") }, modifier = Modifier.weight(1f)) { Text("Want to Visit") }
                        }
                        TextButton(onClick = { onVisitStatusSelected(null) }) { Text("Skip this step") }
                    }
                    OverlayStep.AskRating -> {
                        Text("How would you rate it?", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { // Use Column for better layout
                            Button(onClick = { onRatingSelected("MUST_VISIT") }, modifier = Modifier.fillMaxWidth()) { Text("⭐ Must Visit!") }
                            Button(onClick = { onRatingSelected("WORTH_VISITING") }, modifier = Modifier.fillMaxWidth()) { Text("👍 Worth Visiting") }
                            Button(onClick = { onRatingSelected("NOT_WORTH_VISITING") }, modifier = Modifier.fillMaxWidth()) { Text("👎 Not Worth Visiting") }
                            TextButton(onClick = { onRatingSelected(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip Rating") }
                        }
                    }
                    else -> {} // Hidden state, should not show overlay content
                } // End when(step)
            } // End Column
        } // End Card
    } // End Box (Scrim)
}