package com.gazzel.sesameapp.presentation.components.listdetail

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ListDropdownMenu(
    isPrivate: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPrivacyToggle: () -> Unit,
    onEditListName: () -> Unit,
    onMergeList: () -> Unit,
    onDeleteList: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = if (isPrivate) "Make public" else "Make private",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            onClick = {
                onPrivacyToggle()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Edit list name", style = MaterialTheme.typography.bodyMedium) },
            onClick = {
                onEditListName()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Merge list", style = MaterialTheme.typography.bodyMedium) },
            onClick = {
                onMergeList()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete list", style = MaterialTheme.typography.bodyMedium) },
            onClick = {
                onDeleteList()
                onDismiss()
            }
        )
    }
}

@Composable
fun PlaceDropdownMenu(
    placeId: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit,
    onTags: () -> Unit,
    onNotes: (String) -> Unit,
    onAddToList: () -> Unit,
    onDeleteItem: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            Log.d("ListDetail", "Dismissing place menu")
            onDismiss()
        },
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        DropdownMenuItem(
            text = { Text("Share", style = MaterialTheme.typography.bodyMedium) },
            onClick = {
                Log.d("ListDetail", "Share selected for placeId=$placeId")
                onShare(placeId)
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Tags", style = MaterialTheme.typography.bodyMedium) },
            onClick = {
                onTags()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Notes", style = MaterialTheme.typography.bodyMedium) },
            onClick = {
                Log.d("ListDetail", "Opening note viewer for placeId=$placeId")
                onNotes(placeId)
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to different list", style = MaterialTheme.typography.bodyMedium) },
            onClick = {
                onAddToList()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete item", style = MaterialTheme.typography.bodyMedium) },
            onClick = {
                onDeleteItem()
                onDismiss()
            }
        )
    }
} 