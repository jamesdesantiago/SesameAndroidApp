package com.gazzel.sesameapp.presentation.components.listdetail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // <<< Import
import com.gazzel.sesameapp.R // <<< Import

@Composable
fun EditListNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newListName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = {
            onDismiss()
            // Resetting state here is fine, but could also be handled by caller if needed
            // newListName = currentName
        },
        title = {
            Text(
                text = stringResource(id = R.string.dialog_edit_list_name_title), // <<< Use stringResource
                style = MaterialTheme.typography.titleMedium // Consider TitleLarge for consistency
            )
        },
        text = {
            OutlinedTextField(
                value = newListName,
                onValueChange = { newListName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.label_list_name)) }, // <<< Use stringResource
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true // Good practice for names
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    // Optional: Add validation here before saving if needed
                    onSave(newListName.trim()) // Trim whitespace
                },
                shape = MaterialTheme.shapes.large, // Consider medium for consistency
                enabled = newListName.isNotBlank() // Disable save if name is blank
            ) {
                Text(
                    stringResource(id = R.string.button_save), // <<< Use stringResource
                    // color = MaterialTheme.colorScheme.onPrimary, // Default should be fine
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    // No need to reset state here if onDismissRequest handles it
                }
            ) {
                Text(stringResource(id = R.string.dialog_button_cancel)) // <<< Use stringResource
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    listName: String = "this list", // Add optional listName parameter
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = stringResource(id = R.string.dialog_delete_list_title), // Use generic title string
                style = MaterialTheme.typography.titleLarge // Use TitleLarge for dialog titles
            )
        },
        // Use the text string that accepts the list name
        text = {
            Text(
                stringResource(id = R.string.dialog_delete_list_text, listName), // <<< Use stringResource with param
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                },
                shape = MaterialTheme.shapes.medium, // Consistent shape
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError // Ensure text is readable
                )
            ) {
                Text(
                    // Use the specific "Yes, Delete" string or the generic "Delete"
                    stringResource(id = R.string.dialog_delete_confirm_button_yes), // <<< Use specific stringResource
                    // color = MaterialTheme.colorScheme.onError, // Already set by ButtonDefaults
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss() }
            ) {
                // Use the specific "No, Cancel" string or the generic "Cancel"
                Text(stringResource(id = R.string.dialog_delete_cancel_button_no)) // <<< Use specific stringResource
            }
        }
    )
}

@Composable
fun DeleteSuccessDialog(
    listName: String,
    onDismiss: () -> Unit // Even if not used by buttons, needed for Alert's contract potentially
) {
    // This dialog usually disappears automatically via LaunchedEffect in the calling screen.
    // Adding buttons might not be necessary unless you want explicit user action.
    AlertDialog(
        onDismissRequest = { /* Usually handled by state change causing recomposition */ onDismiss() },
        title = {
            Text(
                // Use stringResource with placeholder
                text = stringResource(id = R.string.dialog_delete_success_title, listName), // <<< Use stringResource
                style = MaterialTheme.typography.titleMedium
            )
        },
        confirmButton = {
            // Typically no confirm button needed here
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_button_close)) // Add a close button if needed
            }
        },
        dismissButton = {} // No dismiss button usually needed
    )
}