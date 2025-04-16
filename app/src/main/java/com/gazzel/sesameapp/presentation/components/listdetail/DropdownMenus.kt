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
import androidx.compose.ui.res.stringResource // <<< Import
import com.gazzel.sesameapp.R // <<< Import

@Composable
fun ListDropdownMenu(
    isPrivate: Boolean, // Note: Your VM logic uses !isPublic, so this should align
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
            // Consider removing fillMaxWidth if it makes the menu too wide on large screens
            // .fillMaxWidth()
            .wrapContentHeight()
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    // Use stringResource conditionally based on isPrivate
                    text = stringResource(id = if (isPrivate) R.string.menu_action_make_public else R.string.menu_action_make_private),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            onClick = {
                onPrivacyToggle()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_action_edit_list_name), style = MaterialTheme.typography.bodyMedium) }, // <<< Use stringResource
            onClick = {
                onEditListName()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_action_merge_list), style = MaterialTheme.typography.bodyMedium) }, // <<< Use stringResource
            onClick = {
                onMergeList()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_action_delete_list), style = MaterialTheme.typography.bodyMedium) }, // <<< Use stringResource
            onClick = {
                onDeleteList()
                onDismiss()
            }
        )
    }
}

@Composable
fun PlaceDropdownMenu(
    placeId: String, // Keep placeId if needed for logging or specific actions
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit, // Still pass placeId if Share logic needs it
    onTags: () -> Unit,
    onNotes: (String) -> Unit, // Still pass placeId if Notes logic needs it
    onAddToList: () -> Unit,
    onDeleteItem: () -> Unit // Action doesn't strictly need placeId here if called from correct context
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            Log.d("ListDetail", "Dismissing place menu") // Keep log if helpful
            onDismiss()
        },
        modifier = Modifier
            // .fillMaxWidth() // Consider removing
            .wrapContentHeight()
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_action_share), style = MaterialTheme.typography.bodyMedium) }, // <<< Use stringResource
            onClick = {
                Log.d("ListDetail", "Share selected for placeId=$placeId")
                onShare(placeId) // Pass ID if needed by share overlay/logic
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_action_tags), style = MaterialTheme.typography.bodyMedium) }, // <<< Use stringResource
            onClick = {
                onTags()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_action_notes), style = MaterialTheme.typography.bodyMedium) }, // <<< Use stringResource
            onClick = {
                Log.d("ListDetail", "Opening note viewer for placeId=$placeId")
                onNotes(placeId) // Pass ID if needed by notes overlay/logic
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_action_add_to_list), style = MaterialTheme.typography.bodyMedium) }, // <<< Use stringResource
            onClick = {
                onAddToList()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(id = R.string.menu_action_delete_item), style = MaterialTheme.typography.bodyMedium) }, // <<< Use stringResource
            onClick = {
                onDeleteItem() // Call the delete action
                onDismiss()
            }
        )
    }
}