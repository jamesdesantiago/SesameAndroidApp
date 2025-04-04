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
import androidx.compose.ui.unit.dp
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
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = place.address,
                style = MaterialTheme.typography.bodyMedium
            )
            if (place.visitStatus != null) {
                Text(
                    text = "Status: ${place.visitStatus}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (place.rating != null) {
                Text(
                    text = "${place.rating}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
                contentDescription = "More options for place"
            )
        }
    }
} 