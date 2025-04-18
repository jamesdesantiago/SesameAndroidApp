package com.gazzel.sesameapp.presentation.components.listdetail

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Correct import for LazyColumn items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // <<< ADD stringResource import
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.R // <<< ADD R import
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

@Composable
fun ListDetailMainContent(
    places: List<PlaceItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTab: String,
    isLoading: Boolean,
    errorMessage: String?,
    cameraPositionState: CameraPositionState,
    onMoreClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = {
                Text(
                    // "Search places in this list", // Before
                    text = stringResource(R.string.list_detail_search_label), // After
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage, // Keep errorMessage as it comes from ViewModel/Error state
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300))
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Filtering logic remains the same
        val filteredPlaces = places.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.address.contains(query, ignoreCase = true) ||
                    (it.notes ?: "").contains(query, ignoreCase = true) // Also filter by notes
        }
        Log.d("ListDetail", "Filtered places: ${filteredPlaces.size}") // Simplified log

        when (selectedTab) { // Keep using "List"/"Map" for internal state logic
            "List" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Use items overload that takes a key for better performance
                    items(filteredPlaces, key = { it.id }) { place ->
                        PlaceRow(
                            place = place,
                            onMoreClick = onMoreClick
                        )
                    }
                    // Empty state check
                    if (filteredPlaces.isEmpty() && !isLoading) { // Only show if not loading
                        item {
                            Text(
                                // text = "No places found", // Before
                                text = stringResource(R.string.list_detail_no_places_found), // After
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
            "Map" -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        filteredPlaces.forEach { place ->
                            // Log kept for debugging if needed
                            // Log.d("ListDetail", "Adding marker: ${place.name}, lat=${place.latitude}, lng=${place.longitude}")
                            Marker(
                                state = MarkerState(
                                    position = LatLng(place.latitude, place.longitude)
                                ),
                                title = place.name,
                                snippet = place.address
                            )
                        }
                    }
                    // Optional: Add a loading indicator over the map if needed while places load initially
                }
            }
        }
    }
}