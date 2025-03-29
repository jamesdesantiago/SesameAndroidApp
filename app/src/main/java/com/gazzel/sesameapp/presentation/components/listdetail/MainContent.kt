package com.gazzel.sesameapp.presentation.components.listdetail

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.LatLng
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
    onMoreClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = {
                Text(
                    "Search places in this list",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
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

        val filteredPlaces = places.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.address.contains(query, ignoreCase = true)
        }
        Log.d("ListDetail", "Filtered places: ${filteredPlaces.size} - $filteredPlaces")

        when (selectedTab) {
            "List" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPlaces) { place ->
                        PlaceRow(
                            place = place,
                            onMoreClick = onMoreClick
                        )
                    }
                    if (filteredPlaces.isEmpty()) {
                        item {
                            Text(
                                text = "No places found",
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
                            Log.d("ListDetail", "Adding marker: ${place.name}, lat=${place.latitude}, lng=${place.longitude}")
                            Marker(
                                state = MarkerState(
                                    position = LatLng(place.latitude, place.longitude)
                                ),
                                title = place.name,
                                snippet = place.address
                            )
                        }
                    }
                }
            }
        }
    }
} 