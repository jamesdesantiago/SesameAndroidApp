package com.gazzel.sesameapp.presentation.components.listdetail

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // <<< Import stringResource
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.R // <<< Import your R class
import com.gazzel.sesameapp.domain.model.PlaceItem

@Composable
fun PlaceRow(
    place: PlaceItem,
    onMoreClick: (String) -> Unit // Callback to open the place menu
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Place Name (Keep as is)
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge
            )
            // Place Address (Keep as is)
            Text(
                text = place.address,
                style = MaterialTheme.typography.bodyMedium
            )
            // Visit Status (Use string resource)
            if (place.visitStatus != null) {
                Text(
                    // Use stringResource with placeholder
                    text = stringResource(R.string.place_row_status, place.visitStatus),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Rating (Use string resource)
            if (place.rating != null) {
                Text(
                    // Use stringResource with placeholder, handle null/empty rating if needed
                    text = stringResource(R.string.place_row_rating, place.rating),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Notes (Keep as is)
            if (place.notes != null) {
                Text(
                    text = place.notes,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        IconButton(onClick = {
            Log.d("ListDetail", "More icon clicked for placeId=${place.id}")
            onMoreClick(place.id)
        }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                // Use stringResource for contentDescription
                contentDescription = stringResource(R.string.cd_more_options_place, place.name) // Pass place name
            )
        }
    }
}