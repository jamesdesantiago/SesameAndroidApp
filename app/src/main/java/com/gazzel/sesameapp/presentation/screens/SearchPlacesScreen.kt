package com.gazzel.sesameapp.presentation.screens.search // Keep correct package

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.* // Use wildcard layout import
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.* // Use wildcard material3 import
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.data.service.PlaceDetailsResponse // Keep DTO import for Overlay
// Import the moved components
import com.gazzel.sesameapp.presentation.components.search.SearchPlacesOverlay
import com.gazzel.sesameapp.presentation.components.search.SuggestionItem
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import kotlinx.coroutines.launch // Import launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPlacesScreen(
    navController: NavController,
    viewModel: SearchPlacesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val overlayStep by viewModel.overlayStep.collectAsState()
    var query by remember { mutableStateOf("") }

    // --- Snackbar setup ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Effects for Navigation and Snackbar ---
    LaunchedEffect(uiState) {
        // Navigate back on success
        if (uiState is SearchPlacesUiState.PlaceAdded) {
            navController.popBackStack()
        }
        // Show snackbar on error
        if (uiState is SearchPlacesUiState.Error) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = (uiState as SearchPlacesUiState.Error).message,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.clearError() // Clear error state if Dismiss is tapped
                }
                // Snackbar dismisses automatically after duration if action not performed
            }
        }
    }

    SesameAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                // --- Add SnackbarHost ---
                snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.updateQuery(it)
                        },
                        label = { Text("Search for a place...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = uiState !is SearchPlacesUiState.AddingPlace && uiState !is SearchPlacesUiState.LoadingDetails
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Handle Different UI States (Error display removed from here) ---
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
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(state.suggestions, key = { it.placeId }) { prediction ->
                                        // Use the imported SuggestionItem
                                        SuggestionItem(prediction = prediction) {
                                            query = prediction.text.text
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
                        is SearchPlacesUiState.DetailsLoaded -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Getting rating/status...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is SearchPlacesUiState.AddingPlace -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                            Text("Adding ${state.placeDetails.displayName.text}...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is SearchPlacesUiState.PlaceAdded -> {
                            // Usually handled by LaunchedEffect navigation
                            Text("Place Added!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        is SearchPlacesUiState.Error -> {
                            // Display area now handled primarily by Snackbar
                            // Optionally show a persistent message if needed, but Snackbar is often better for errors.
                            // Text("An error occurred. See message below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                    } // End when(state)

                    // --- Skip Button Area ---
                    // Only show skip button if suggestions aren't filling space and not in critical states/overlay
                    if (uiState !is SearchPlacesUiState.SuggestionsLoaded || (uiState as SearchPlacesUiState.SuggestionsLoaded).suggestions.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    AnimatedVisibility(
                        visible = uiState !is SearchPlacesUiState.AddingPlace &&
                                uiState !is SearchPlacesUiState.LoadingDetails &&
                                overlayStep == OverlayStep.Hidden
                    ) {
                        Button(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth().padding(top=16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
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
                val details = (uiState as? SearchPlacesUiState.DetailsLoaded)?.placeDetails
                    ?: (uiState as? SearchPlacesUiState.AddingPlace)?.placeDetails

                if (details != null) {
                    // Use the imported SearchPlacesOverlay
                    SearchPlacesOverlay(
                        step = overlayStep,
                        placeDetails = details,
                        onVisitStatusSelected = { status -> viewModel.setVisitStatusAndProceed(status) },
                        onRatingSelected = { rating -> viewModel.setRatingAndAddPlace(rating) },
                        onDismiss = { viewModel.resetOverlayState() },
                        // Pass the new callbacks
                        onProceedToVisitStatus = { viewModel.proceedToVisitStatusStep() },
                        onAddDirectly = {
                            viewModel.setVisitStatusAndProceed(null) // Skip visit status
                            viewModel.setRatingAndAddPlace(null)    // Skip rating and add
                        }
                    )
                }
            } // End AnimatedVisibility for Overlay

        } // End Outer Box
    } // End SesameAppTheme
} // End SearchPlacesScreen

// --- Remove Helper Composables (SuggestionItem, SearchPlacesOverlay) from this file ---
// @Composable fun SuggestionItem(...) { ... } // REMOVE
// @Composable fun SearchPlacesOverlay(...) { ... } // REMOVE