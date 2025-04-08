// app/src/main/java/com/gazzel/sesameapp/presentation/screens/search/SearchPlacesScreen.kt
// (Content moved and updated)
package com.gazzel.sesameapp.presentation.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
// Import DTOs used in state/overlay
import com.gazzel.sesameapp.data.service.PlaceDetailsResponse
import com.gazzel.sesameapp.data.service.PlacePrediction
import com.gazzel.sesameapp.ui.theme.SesameAppTheme // Ensure correct theme import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPlacesScreen(
    navController: NavController,
    viewModel: SearchPlacesViewModel = hiltViewModel() // Inject ViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val overlayStep by viewModel.overlayStep.collectAsState()
    var query by remember { mutableStateOf("") } // Local state ONLY for TextField binding

    // Navigate back when PlaceAdded state is reached
    LaunchedEffect(uiState) {
        if (uiState is SearchPlacesUiState.PlaceAdded) {
            // Optionally show a success message (e.g., Snackbar) before navigating
            navController.popBackStack()
            // Optionally reset VM state if needed after navigation
            // viewModel.resetStateAfterAddition()
        }
    }

    SesameAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Add Place to List") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) { // Use popBackStack
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
                        .padding(horizontal = 16.dp, vertical = 16.dp), // Consistent padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it // Update local state for TextField display
                            viewModel.updateQuery(it) // Trigger VM logic (debouncing, API call)
                        },
                        label = { Text("Search for a place...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = uiState !is SearchPlacesUiState.AddingPlace && uiState !is SearchPlacesUiState.LoadingDetails // Disable during critical ops
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Handle Different UI States ---
                    when (val state = uiState) {
                        is SearchPlacesUiState.Idle -> {
                            Text("Start typing to search...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is SearchPlacesUiState.Searching -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Searching for \"${state.query}\"...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is SearchPlacesUiState.SuggestionsLoaded -> {
                            if (state.suggestions.isEmpty()) {
                                Text("No suggestions found for \"${state.query}\".", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                // Text("${state.suggestions.size} suggestions:", style = MaterialTheme.typography.bodyMedium) // Optional count
                                // Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(state.suggestions, key = { it.placeId }) { prediction ->
                                        SuggestionItem(prediction = prediction) {
                                            // Update text field directly on click for immediate feedback
                                            query = prediction.text.text
                                            // Tell VM to fetch details
                                            viewModel.selectPlace(prediction)
                                        }
                                    }
                                }
                            }
                        }
                        is SearchPlacesUiState.LoadingDetails -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Loading details for ${state.placeName ?: "place"}...", style = MaterialTheme.typography.bodyMedium)
                        }
                        // DetailsLoaded & AddingPlace states don't show list content, rely on overlay or loading indicator
                        is SearchPlacesUiState.DetailsLoaded -> {
                            // Text("Selected: ${state.placeDetails.displayName.text}", style = MaterialTheme.typography.bodyLarge) // Info shown in overlay
                            // Text(state.placeDetails.formattedAddress, style = MaterialTheme.typography.bodyMedium)
                            // Show a general loading or wait state while overlay is active
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Getting rating/status...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is SearchPlacesUiState.AddingPlace -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Adding ${state.placeDetails.displayName.text}...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is SearchPlacesUiState.PlaceAdded -> {
                            // Usually handled by LaunchedEffect navigation
                            Text("Place Added!", style = MaterialTheme.typography.bodyLarge, color = Color.Green)
                        }
                        is SearchPlacesUiState.Error -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally){
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(onClick = { viewModel.clearError() }) { // Add retry/dismiss
                                    Text("Dismiss")
                                }
                            }
                        }
                    } // End when(state)

                    // Spacer pushes skip button down only if suggestions aren't filling the space
                    if (uiState !is SearchPlacesUiState.SuggestionsLoaded || (uiState as SearchPlacesUiState.SuggestionsLoaded).suggestions.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                    }


                    // Skip button (visible unless place is being added or details loading)
                    AnimatedVisibility(
                        visible = uiState !is SearchPlacesUiState.AddingPlace &&
                                uiState !is SearchPlacesUiState.LoadingDetails &&
                                overlayStep == OverlayStep.Hidden // Also hide if overlay is up
                    ) {
                        Button(
                            onClick = { navController.popBackStack() }, // Just go back
                            modifier = Modifier.fillMaxWidth().padding(top=16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp) // Optional flat look
                        ) {
                            Text("Skip Adding Place")
                        }
                    }

                } // End Column
            } // End Scaffold

            // --- Overlay ---
            val overlayVisible = overlayStep != OverlayStep.Hidden &&
                    (uiState is SearchPlacesUiState.DetailsLoaded || uiState is SearchPlacesUiState.AddingPlace)

            AnimatedVisibility(
                visible = overlayVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // Get details safely ONLY if in the correct state
                val details = (uiState as? SearchPlacesUiState.DetailsLoaded)?.placeDetails
                    ?: (uiState as? SearchPlacesUiState.AddingPlace)?.placeDetails

                if (details != null) {
                    SearchPlacesOverlay( // Pass necessary data and actions
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

// --- Helper Composables (SuggestionItem, SearchPlacesOverlay) remain the same ---
// (Copy them from the original SearchPlacesScreen.kt if they weren't separate files)
@Composable
fun SuggestionItem(prediction: PlacePrediction, onClick: () -> Unit) {
    // ... (implementation from previous SearchPlacesScreen.kt)
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
    // ... (implementation from previous SearchPlacesScreen.kt)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss), // Dismiss on scrim click
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {} // Block clicks on card
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button for the overlay
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }

                when (step) {
                    OverlayStep.ShowPlaceDetails -> {
                        Text(placeDetails.displayName.text, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(placeDetails.formattedAddress, style = MaterialTheme.typography.bodyMedium)
                        placeDetails.rating?.let { googleRating ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Google Rating: ${String.format("%.1f", googleRating)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        // Directly proceed to next step via button click -> Now handled by VM state change
                        Button(onClick = { /* VM triggers next step */ viewModel.proceedToVisitStatusStep() }) {
                            Text("Add Rating/Status (Optional)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Add button to directly add without rating/status
                        TextButton(onClick = { viewModel.setVisitStatusAndProceed(null) /* Skips to rating */ ; viewModel.setRatingAndAddPlace(null) /* Skips rating and adds */}) {
                            Text("Add Directly")
                        }
                    }
                    OverlayStep.AskVisitOrNot -> {
                        Text("Have you been to ${placeDetails.displayName.text}?", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()){
                            Button(onClick = { onVisitStatusSelected("VISITED") }, modifier = Modifier.weight(1f)) { Text("Yes, Visited") }
                            Button(onClick = { onVisitStatusSelected("WANT_TO_VISIT") }, modifier = Modifier.weight(1f)) { Text("Want to Visit") }
                        }
                        TextButton(onClick = { onVisitStatusSelected(null) }) { Text("Skip this step") }
                    }
                    OverlayStep.AskRating -> {
                        Text("How would you rate ${placeDetails.displayName.text}?", style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRatingSelected("MUST_VISIT") }, modifier = Modifier.fillMaxWidth()) { Text("⭐ Must Visit!") }
                            Button(onClick = { onRatingSelected("WORTH_VISITING") }, modifier = Modifier.fillMaxWidth()) { Text("👍 Worth Visiting") }
                            Button(onClick = { onRatingSelected("NOT_WORTH_VISITING") }, modifier = Modifier.fillMaxWidth()) { Text("👎 Not Worth Visiting") }
                            TextButton(onClick = { onRatingSelected(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip Rating") }
                        }
                    }
                    else -> {} // Hidden state
                }
            }
        }
    }
}