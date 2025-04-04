package com.gazzel.sesameapp.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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