package com.gazzel.sesameapp

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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = MaterialTheme.shapes.large,
                    shadowElevation = 8.dp
                ) {
                    when (overlayStep) {

                        // STEP 1: Place info + "Add" only
                        OverlayStep.ShowPlaceDetails -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = selectedPlace?.displayName?.text ?: "Unknown Place",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = selectedPlace?.formattedAddress ?: "Unknown Address",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.height(16.dp))

                                // Only an "Add" button – no skip/cancel
                                Button(
                                    onClick = {
                                        // Next step: visited or want to visit
                                        overlayStep = OverlayStep.AskVisitOrNot
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Add", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        // STEP 2: "Visited" / "Want to Visit" / "Skip"
                        OverlayStep.AskVisitOrNot -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Have you visited this place already?", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            // "Visited" => Step 3
                                            visitStatus = "Visited"
                                            overlayStep = OverlayStep.AskRating
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Visited", style = MaterialTheme.typography.labelLarge)
                                    }
                                    Button(
                                        onClick = {
                                            // "Want to Visit" => add immediately
                                            visitStatus = "Want to Visit"
                                            finalizePlaceAddition()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Want to Visit", style = MaterialTheme.typography.labelLarge)
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                OutlinedButton(
                                    onClick = {
                                        // "Skip" => add with no rating/status
                                        finalizePlaceAddition()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Skip", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        // STEP 3: "Worth Visiting", "Must Visit", "Not Worth Visiting", or skip
                        OverlayStep.AskRating -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("How was it?", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(16.dp))

                                // row 1: "Worth Visiting" / "Must Visit"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            userRating = "Worth Visiting"
                                            finalizePlaceAddition()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Worth Visiting", style = MaterialTheme.typography.labelLarge)
                                    }
                                    Button(
                                        onClick = {
                                            userRating = "Must Visit"
                                            finalizePlaceAddition()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Must Visit", style = MaterialTheme.typography.labelLarge)
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // "Not Worth Visiting"
                                Button(
                                    onClick = {
                                        userRating = "Not Worth Visiting"
                                        finalizePlaceAddition()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Not Worth Visiting", style = MaterialTheme.typography.labelLarge)
                                }

                                Spacer(Modifier.height(16.dp))

                                // "Skip" => add with visitStatus="Visited" but no rating
                                OutlinedButton(
                                    onClick = {
                                        finalizePlaceAddition()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text("Skip", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        else -> {
                            // OverlayStep.Hidden => no UI
                        }
                    }
                }
            }
        }
    }

    // Autocomplete when query is > 2 chars
    LaunchedEffect(query) {
        if (query.length > 2) {
            delay(300)
            isLoading = true
            errorMessage = null
            fetchAutocompleteSuggestions(
                placesApiService,
                query,
                suggestions,
                sessionToken,
                apiKey
            ) { error ->
                errorMessage = error
            }
            isLoading = false
        } else {
            suggestions.clear()
            isLoading = false
            errorMessage = null
        }
    }
}

// ------------------ Networking Helpers ------------------
suspend fun fetchAutocompleteSuggestions(
    placesApiService: PlacesApiService,
    query: String,
    suggestions: MutableList<PlacePrediction>,
    sessionToken: String,
    apiKey: String,
    onError: (String?) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            Log.d("PlacesAPI", "Sending autocomplete request for query: $query")
            val request = AutocompleteRequest(input = query)
            val response = placesApiService.getAutocompleteSuggestions(request, apiKey)
            if (response.isSuccessful) {
                val autocompleteResponse = response.body()
                val newSuggestions = autocompleteResponse?.suggestions?.map { it.placePrediction } ?: emptyList()
                Log.d("PlacesAPI", "Received response with ${newSuggestions.size} predictions")
                withContext(Dispatchers.Main) {
                    suggestions.clear()
                    suggestions.addAll(newSuggestions)
                    onError(null)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError("Error fetching suggestions: ${response.code()} - ${response.errorBody()?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e("PlacesAPI", "Error fetching suggestions: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError("Error fetching suggestions: ${e.message}")
            }
        }
    }
}

suspend fun fetchPlaceDetails(
    placesApiService: PlacesApiService,
    placeId: String,
    apiKey: String,
    onResult: (PlaceDetailsResponse?) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val response = placesApiService.getPlaceDetails(placeId, apiKey)
            if (response.isSuccessful) {
                val placeDetails = response.body()
                withContext(Dispatchers.Main) {
                    onResult(placeDetails)
                }
            } else {
                Log.e("PlacesAPI", "Failed to fetch place details: ${response.code()} - ${response.errorBody()?.string()}")
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        } catch (e: Exception) {
            Log.e("PlacesAPI", "Exception fetching place details: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onResult(null)
            }
        }
    }
}
