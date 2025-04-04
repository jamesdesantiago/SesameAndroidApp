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
            newListName = currentName
        },
        title = {
            Text(
                text = "Edit List Name",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            OutlinedTextField(
                value = newListName,
                onValueChange = { newListName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("List Name") },
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(newListName)
                },
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    "Save",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    newListName = currentName
                }
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = "Are you sure you want to delete this list?",
                style = MaterialTheme.typography.titleMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                },
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    "yes, delete",
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss() }
            ) {
                Text("no, cancel", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun DeleteSuccessDialog(
    listName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* No dismiss action, handled by LaunchedEffect */ },
        title = {
            Text(
                text = "The list $listName has been deleted",
                style = MaterialTheme.typography.titleMedium
            )
        },
        confirmButton = {},
        dismissButton = {}
    )
} 