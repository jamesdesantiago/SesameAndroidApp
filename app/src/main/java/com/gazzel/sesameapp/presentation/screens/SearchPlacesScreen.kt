package com.gazzel.sesameapp.presentation.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.gazzel.sesameapp.data.service.PlacesApiService
import com.gazzel.sesameapp.domain.model.PlaceDetailsResponse
import com.gazzel.sesameapp.domain.model.PlacePrediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// Step-tracking enum for multi-step overlay
enum class OverlayStep {
    Hidden,            // overlay not visible
    ShowPlaceDetails,  // Step 1: place info + "Add" (no skip/cancel)
    AskVisitOrNot,     // Step 2: "Visited" / "Want to Visit" / "Skip"
    AskRating          // Step 3: "Worth Visiting", "Must Visit", "Not Worth Visiting", "Skip"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPlacesScreen(
    onSkip: () -> Unit,
    onPlaceSelected: (PlaceDetailsResponse, String?, String?) -> Unit,
    placesApiService: PlacesApiService,
    apiKey: String
) {
    // ------------------ State vars ------------------
    var query by remember { mutableStateOf("") }
    val suggestions = remember { mutableStateListOf<PlacePrediction>() }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val sessionToken = remember { UUID.randomUUID().toString() }
    val coroutineScope = rememberCoroutineScope()

    // For the place & user inputs
    var selectedPlace by remember { mutableStateOf<PlaceDetailsResponse?>(null) }
    var userRating by remember { mutableStateOf<String?>(null) }
    var visitStatus by remember { mutableStateOf<String?>(null) }

    // Multi-step overlay state
    var overlayStep by remember { mutableStateOf(OverlayStep.Hidden) }

    /**
     * Helper to finalize the addition of place and reset overlay state.
     */
    fun finalizePlaceAddition() {
        selectedPlace?.let { place ->
            onPlaceSelected(place, userRating, visitStatus)
        }
        // reset
        overlayStep = OverlayStep.Hidden
        selectedPlace = null
        userRating = null
        visitStatus = null
    }

    // ------------------ UI ------------------
    SesameAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "Search Places",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onSkip) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Search field
                    OutlinedTextField(
                        value = query,
                        onValueChange = { newQuery ->
                            query = newQuery
                            errorMessage = null
                            overlayStep = OverlayStep.Hidden
                            selectedPlace = null
                        },
                        label = { Text("Search places", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = MaterialTheme.shapes.medium
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Loading indicator
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Suggestions
                    if (suggestions.isNotEmpty() && !isLoading) {
                        Text(
                            text = "${suggestions.size} results found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(suggestions) { prediction ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                query = prediction.text.text
                                                coroutineScope.launch {
                                                    fetchPlaceDetails(
                                                        placesApiService,
                                                        prediction.placeId,
                                                        apiKey
                                                    ) { place ->
                                                        if (place != null) {
                                                            // Start overlay at step 1
                                                            selectedPlace = place
                                                            userRating = null
                                                            visitStatus = null
                                                            overlayStep = OverlayStep.ShowPlaceDetails
                                                        } else {
                                                            errorMessage = "Failed to fetch place details"
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = prediction.text.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!isLoading && query.length > 2 && suggestions.isEmpty() && errorMessage == null) {
                        // No suggestions
                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // Error
                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // "Skip" button at the bottom (go back)
                    Button(
                        onClick = onSkip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Skip", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // --------------------- Multi-step Overlay ---------------------
            AnimatedVisibility(
                visible = (overlayStep != OverlayStep.Hidden),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                // ... existing code ...
            }
        }
    }
} 