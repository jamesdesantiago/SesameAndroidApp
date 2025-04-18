// presentation/screens/search/SearchPlacesScreen.kt
package com.gazzel.sesameapp.presentation.screens.search // Keep correct package

// Import R from *your* package, not android.R
import com.gazzel.sesameapp.R // <<< Correct R import

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // <<< Import stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
// Import the moved components
import com.gazzel.sesameapp.presentation.components.search.SearchPlacesOverlay // Assume these exist
import com.gazzel.sesameapp.presentation.components.search.SuggestionItem // Assume these exist
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import kotlinx.coroutines.launch

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
            val errorMessage = (uiState as SearchPlacesUiState.Error).message
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = errorMessage, // Use the message from the state
                    actionLabel = stringResource(id = R.string.snackbar_dismiss), // Use string resource
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.clearError() // Clear error state if Dismiss is tapped
                }
                // Snackbar dismisses automatically after duration if action not performed
            }
        }
    }

    // Use SesameAppTheme if you want consistent app theming
    // SesameAppTheme {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // --- Add SnackbarHost ---
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.search_places_title)) }, // <<< Use stringResource
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back_button) // <<< Use stringResource
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 16.dp), // Adjust padding as needed
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.updateQuery(it)
                    },
                    // Pass Text composable to label, use stringResource
                    label = { Text(stringResource(R.string.label_search_place)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = uiState !is SearchPlacesUiState.AddingPlace && uiState !is SearchPlacesUiState.LoadingDetails
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Handle Different UI States (Error display removed from here) ---
                when (val state = uiState) {
                    is SearchPlacesUiState.Idle -> {
                        // Pass Text composable, use stringResource
                        Text(
                            stringResource(R.string.label_start_typing_search), // Use new string ID
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is SearchPlacesUiState.Searching -> {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                        Text(
                            stringResource(R.string.search_places_searching_text, state.query), // Use stringResource with placeholder
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is SearchPlacesUiState.SuggestionsLoaded -> {
                        if (state.suggestions.isEmpty()) {
                            Text(
                                stringResource(R.string.search_places_no_suggestions, state.query), // Use stringResource with placeholder
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(state.suggestions, key = { it.placeId }) { prediction ->
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
                        val placeName = state.placeName ?: stringResource(R.string.search_places_default_place_name) // Default name
                        Text(
                            stringResource(R.string.search_places_loading_details, placeName), // Use stringResource with placeholder
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is SearchPlacesUiState.DetailsLoaded -> {
                        // State changed, overlay is now triggered by OverlayStep state change
                        // You might show a brief confirmation or just let the overlay appear
                        Text(
                            stringResource(R.string.search_places_getting_rating_status), // Use stringResource
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Added Progress indicator while waiting for user input in overlay
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                    }
                    is SearchPlacesUiState.AddingPlace -> {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 20.dp))
                        Text(
                            stringResource(R.string.search_places_adding_place, state.placeDetails.displayName.text), // Use stringResource with placeholder
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is SearchPlacesUiState.PlaceAdded -> {
                        // This state now primarily triggers navigation back
                        // You could show a quick success message here if needed before navigation
                        Text(
                            stringResource(R.string.search_places_added_success), // <<< Use stringResource
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary // Use theme color for success
                        )
                    }
                    is SearchPlacesUiState.Error -> {
                        // Error is now primarily shown in the Snackbar
                        // Optionally, show a subtle indicator here
                        Text(
                            stringResource(R.string.search_places_error_occurred), // Generic indicator
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } // End when(state)

                // --- Skip Button Area ---
                // Only show skip button if suggestions aren't filling space and not in critical states/overlay
                val showSpacer = uiState !is SearchPlacesUiState.SuggestionsLoaded || (uiState as? SearchPlacesUiState.SuggestionsLoaded)?.suggestions.isNullOrEmpty() == true
                if (showSpacer) {
                    Spacer(modifier = Modifier.weight(1f)) // Pushes button down if list is short/empty
                }

                // Show skip button when overlay is hidden and not in a blocking loading/adding state
                AnimatedVisibility(
                    visible = uiState !is SearchPlacesUiState.AddingPlace &&
                            uiState !is SearchPlacesUiState.LoadingDetails &&
                            overlayStep == OverlayStep.Hidden
                ) {
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp) // Flat button look
                    ) {
                        Text(stringResource(R.string.button_skip_adding_place)) // <<< Use stringResource
                    }
                }

            } // End Column
        } // End Scaffold

        // --- Overlay ---
        // Visibility controlled by overlayStep and ensuring details are loaded/being added
        val overlayVisible = overlayStep != OverlayStep.Hidden &&
                (uiState is SearchPlacesUiState.DetailsLoaded || uiState is SearchPlacesUiState.AddingPlace)

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Safely cast to get placeDetails, required for the overlay
            val details = (uiState as? SearchPlacesUiState.DetailsLoaded)?.placeDetails
                ?: (uiState as? SearchPlacesUiState.AddingPlace)?.placeDetails

            if (details != null) {
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
                        // Directly trigger add without waiting for rating step
                        viewModel.setRatingAndAddPlace(null)
                    }
                )
            }
        } // End AnimatedVisibility for Overlay

    } // End Outer Box
    // } // End SesameAppTheme (Optional)
} // End SearchPlacesScreen