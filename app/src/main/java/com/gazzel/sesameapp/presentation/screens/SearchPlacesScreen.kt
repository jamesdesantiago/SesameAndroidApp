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
import com.gazzel.sesameapp.data.service.PlaceDetailsResponse
import com.gazzel.sesameapp.data.service.GooglePlacesService
import com.gazzel.sesameapp.data.service.PlacePrediction // <-- For Autocomplete suggestions
import com.gazzel.sesameapp.data.service.AutocompleteRequest
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
    // Expect PlaceDetailsResponse from Google API
    onPlaceSelected: (placeDetails: PlaceDetailsResponse, userRating: String?, visitStatus: String?) -> Unit,
    // Use the correct service type
    googlePlacesService: GooglePlacesService,
    apiKey: String
) {
    // ------------------ State vars ------------------
    var query by remember { mutableStateOf("") }
    // Explicitly type the list state
    val suggestions = remember { mutableStateListOf<PlacePrediction>() }
    var isLoading by remember { mutableStateOf(false) }
    var isDetailLoading by remember { mutableStateOf(false) } // Separate loading for details
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val sessionToken by remember { mutableStateOf(UUID.randomUUID().toString()) } // Reuse session token
    val coroutineScope = rememberCoroutineScope()

    var selectedPlace by remember { mutableStateOf<PlaceDetailsResponse?>(null) }
    var userRating by remember { mutableStateOf<String?>(null) }
    var visitStatus by remember { mutableStateOf<String?>(null) }
    var overlayStep by remember { mutableStateOf(OverlayStep.Hidden) }

    // --- Autocomplete API Call ---
    LaunchedEffect(query) {
        // Basic debounce and length check
        if (query.length < 3) {
            suggestions.clear()
            errorMessage = null // Clear error on short query
            return@LaunchedEffect
        }
        delay(500) // Wait 500ms after user stops typing
        isLoading = true
        errorMessage = null // Clear previous error
        try {
            val request = AutocompleteRequest(input = query, sessionToken = sessionToken)
            val response = googlePlacesService.getAutocompleteSuggestions(request, apiKey)

            if (response.isSuccessful) {
                suggestions.clear()
                response.body()?.suggestions?.let { newSuggestions ->
                    // Extract the PlacePrediction part
                    suggestions.addAll(newSuggestions.map { it.placePrediction })
                }
                Log.d("SearchPlacesScreen", "Autocomplete success: ${suggestions.size} results")
            } else {
                errorMessage = "Autocomplete failed: ${response.code()} ${response.message()}"
                Log.e("SearchPlacesScreen", "Autocomplete error: ${response.code()} - ${response.errorBody()?.string()}")
                suggestions.clear()
            }
        } catch (e: Exception) {
            errorMessage = "Autocomplete error: ${e.localizedMessage ?: "Unknown error"}"
            Log.e("SearchPlacesScreen", "Autocomplete exception", e)
            suggestions.clear()
        } finally {
            isLoading = false
        }
    }

    // --- Function to Fetch Place Details ---
    suspend fun fetchPlaceDetails(placeId: String, callback: (PlaceDetailsResponse?) -> Unit) {
        isDetailLoading = true // Use separate loading state for detail fetch
        errorMessage = null
        try {
            // Specify fields needed: id,displayName,formattedAddress,location,rating (optional)
            val fields = "id,displayName,formattedAddress,location,rating"
            val response = googlePlacesService.getPlaceDetails(placeId, apiKey, fields)
            if (response.isSuccessful) {
                callback(response.body())
            } else {
                errorMessage = "Failed to get place details: ${response.code()} ${response.message()}"
                Log.e("SearchPlacesScreen", "GetDetails error: ${response.code()} - ${response.errorBody()?.string()}")
                callback(null)
            }
        } catch (e: Exception) {
            errorMessage = "Details error: ${e.localizedMessage ?: "Unknown error"}"
            Log.e("SearchPlacesScreen", "GetDetails exception", e)
            callback(null)
        } finally {
            isDetailLoading = false
        }
    }

    fun finalizePlaceAddition() { // Keep helper function
        selectedPlace?.let { place ->
            onPlaceSelected(place, userRating, visitStatus)
        }
        overlayStep = OverlayStep.Hidden
        selectedPlace = null
        userRating = null
        visitStatus = null
    }


    // ------------------ UI ------------------
    SesameAppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold( /* ... TopBar ... */ ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Search field (unchanged)
                    OutlinedTextField(/* ... */)
                    Spacer(modifier = Modifier.height(20.dp))

                    // Loading indicator (Show for autocomplete OR detail loading)
                    if (isLoading || isDetailLoading) {
                        CircularProgressIndicator(/* ... */)
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Suggestions List
                    // Use the correctly typed 'suggestions' list
                    if (suggestions.isNotEmpty() && !isLoading && !isDetailLoading) {
                        // Access .size correctly
                        Text(text = "${suggestions.size} results found", /* ... */)
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            // Iterate over PlacePrediction list
                            items(suggestions, key = { it.placeId }) { prediction: PlacePrediction ->
                                Card(/* ... */) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { // Keep clickable
                                                query = prediction.text.text // Update query display
                                                suggestions.clear() // Clear suggestions on click
                                                coroutineScope.launch {
                                                    // Call the implemented function
                                                    fetchPlaceDetails(prediction.placeId) { placeDetailsResponse ->
                                                        if (placeDetailsResponse != null) {
                                                            // Assign PlaceDetailsResponse? to selectedPlace
                                                            selectedPlace = placeDetailsResponse
                                                            userRating = null
                                                            visitStatus = null
                                                            overlayStep = OverlayStep.ShowPlaceDetails
                                                        } else {
                                                            // Error message is already set inside fetchPlaceDetails
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(16.dp)
                                    ) {
                                        // Access prediction.text.text correctly
                                        Text(text = prediction.text.text, /* ... */)
                                    }
                                }
                            }
                        }
                        // Handle 'No results' case correctly
                    } else if (!isLoading && !isDetailLoading && query.length > 2 && suggestions.isEmpty() && errorMessage == null) {
                        Text(text = "No results found", /* ... */)
                    }

                    // Error display (unchanged)
                    errorMessage?.let { /* ... */ }
                    Spacer(modifier = Modifier.weight(1f))

                    // Skip button (unchanged)
                    Button(onClick = onSkip, /* ... */) { Text("Skip", /* ... */) }
                }
            } // End Scaffold

            // --- Overlay Logic ---
            // Add your AnimatedVisibility and overlay steps here, using 'selectedPlace'
            AnimatedVisibility(
                visible = (overlayStep != OverlayStep.Hidden && selectedPlace != null),
                // ... rest of AnimatedVisibility ...
            ) {
                Surface( /* ... Overlay background ... */ ) {
                    Column( /* ... Overlay content ... */ ) {
                        // --- Step 1: Show Details + Add ---
                        if (overlayStep == OverlayStep.ShowPlaceDetails) {
                            Text(selectedPlace!!.displayName.text, style = MaterialTheme.typography.titleMedium)
                            Text(selectedPlace!!.formattedAddress, style = MaterialTheme.typography.bodyMedium)
                            // Rating from Google (optional display)
                            selectedPlace!!.rating?.let { googleRating ->
                                Text("Google Rating: ${String.format("%.1f", googleRating)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { overlayStep = OverlayStep.AskVisitOrNot }) {
                                Text("Add this place")
                            }
                            // No skip/cancel here as per requirement
                        }

                        // --- Step 2: Visited / Want to Visit ---
                        if (overlayStep == OverlayStep.AskVisitOrNot) {
                            Text("Have you been here?", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()){
                                Button(onClick = { visitStatus = "VISITED"; overlayStep = OverlayStep.AskRating }) { Text("Yes, Visited") }
                                Button(onClick = { visitStatus = "WANT_TO_VISIT"; overlayStep = OverlayStep.AskRating }) { Text("Want to Visit") }
                            }
                            TextButton(onClick = { visitStatus = null; overlayStep = OverlayStep.AskRating }) { Text("Skip") } // Skip setting visit status
                        }

                        // --- Step 3: Rating ---
                        if (overlayStep == OverlayStep.AskRating) {
                            Text("How would you rate it?", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()){
                                Button(onClick = { userRating = "MUST_VISIT"; finalizePlaceAddition() }) { Text("Must Visit!") }
                                Button(onClick = { userRating = "WORTH_VISITING"; finalizePlaceAddition() }) { Text("Worth Visiting") }
                            }
                            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()){
                                Button(onClick = { userRating = "NOT_WORTH_VISITING"; finalizePlaceAddition() }) { Text("Not Worth Visiting") }
                                TextButton(onClick = { userRating = null; finalizePlaceAddition() }) { Text("Skip Rating") }
                            }
                        }
                    }
                }
            } // End AnimatedVisibility

        } // End Outer Box
    } // End SesameAppTheme
} // End SearchPlacesScreen