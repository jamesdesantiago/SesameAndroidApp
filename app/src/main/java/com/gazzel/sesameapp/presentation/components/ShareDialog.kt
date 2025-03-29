package com.gazzel.sesameapp.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.domain.model.SesameList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDialog(
    list: SesameList,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit
) {
    var shareLink by remember { mutableStateOf("") }
    var isGeneratingLink by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share List") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Share this list with others",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isGeneratingLink) {
                    CircularProgressIndicator()
                } else if (shareLink.isNotBlank()) {
                    OutlinedTextField(
                        value = shareLink,
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { /* TODO: Copy to clipboard */ }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            if (shareLink.isBlank()) {
                Button(
                    onClick = {
                        isGeneratingLink = true
                        // TODO: Generate share link
                        shareLink = "https://sesame.app/share/${list.id}"
                        isGeneratingLink = false
                    }
                ) {
                    Text("Generate Link")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (shareLink.isBlank()) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
} 