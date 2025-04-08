package com.gazzel.sesameapp.presentation.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.* // Use wildcard layout import
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.* // Use wildcard material3 import
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.data.service.PlaceDetailsResponse // Import DTO
import com.gazzel.sesameapp.presentation.screens.search.OverlayStep // Import OverlayStep enum

@Composable
fun SearchPlacesOverlay(
    step: OverlayStep,
    placeDetails: PlaceDetailsResponse,
    onVisitStatusSelected: (status: String?) -> Unit,
    onRatingSelected: (rating: String?) -> Unit,
    onDismiss: () -> Unit, // To close the overlay
    onProceedToVisitStatus: () -> Unit, // Callback to trigger next step
    onAddDirectly: () -> Unit // Callback for direct add
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss), // Dismiss on scrim click
        contentAlignment = Alignment.BottomCenter
    ) {
        // Prevent clicks inside the card from dismissing the overlay
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
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

                Spacer(modifier = Modifier.height(8.dp)) // Space after close button

                when (step) {
                    OverlayStep.ShowPlaceDetails -> {
                        Text(placeDetails.displayName.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(placeDetails.formattedAddress, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        placeDetails.rating?.let { googleRating ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Google Rating: ${String.format("%.1f", googleRating)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(onClick = onProceedToVisitStatus) {
                            Text("Add Rating/Status (Optional)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = onAddDirectly) {
                            Text("Add Place Directly")
                        }
                    }
                    OverlayStep.AskVisitOrNot -> {
                        Text("Have you been to ${placeDetails.displayName.text}?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()){
                            Button(onClick = { onVisitStatusSelected("VISITED") }, modifier = Modifier.weight(1f)) { Text("Yes, Visited") }
                            Button(onClick = { onVisitStatusSelected("WANT_TO_VISIT") }, modifier = Modifier.weight(1f)) { Text("Want to Visit") }
                        }
                        TextButton(onClick = { onVisitStatusSelected(null) }) { Text("Skip this step") }
                    }
                    OverlayStep.AskRating -> {
                        Text("How would you rate ${placeDetails.displayName.text}?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { // Ensure buttons fill width
                            Button(onClick = { onRatingSelected("MUST_VISIT") }, modifier = Modifier.fillMaxWidth()) { Text("⭐ Must Visit!") }
                            Button(onClick = { onRatingSelected("WORTH_VISITING") }, modifier = Modifier.fillMaxWidth()) { Text("👍 Worth Visiting") }
                            Button(onClick = { onRatingSelected("NOT_WORTH_VISITING") }, modifier = Modifier.fillMaxWidth()) { Text("👎 Not Worth Visiting") }
                            TextButton(onClick = { onRatingSelected(null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Skip Rating") }
                        }
                    }
                    OverlayStep.Hidden -> {} // Should not be visible if Hidden
                }
            }
        }
    }
}