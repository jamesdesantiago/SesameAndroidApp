package com.gazzel.sesameapp.listdetail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.PlaceItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import android.util.Log

@Composable
fun PlaceRow(
    place: PlaceItem,
    onMoreClick: (Int) -> Unit // Callback to open the place menu
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