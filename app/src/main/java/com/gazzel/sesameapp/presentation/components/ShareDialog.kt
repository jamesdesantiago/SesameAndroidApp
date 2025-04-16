package com.gazzel.sesameapp.presentation.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
// Import ContentCopy icon for better clarity
import androidx.compose.material.icons.filled.ContentCopy // <<< Use ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// Import Platform dependencies
import androidx.compose.ui.platform.LocalClipboardManager // <<< Import ClipboardManager
import androidx.compose.ui.platform.LocalContext // <<< Import LocalContext
import androidx.compose.ui.res.stringResource // <<< Import stringResource
import androidx.compose.ui.text.AnnotatedString // <<< Import AnnotatedString
import androidx.compose.ui.unit.dp
// Import R class from your app
import com.gazzel.sesameapp.R // <<< Import R
import com.gazzel.sesameapp.domain.model.SesameList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Note: ExperimentalMaterial3Api is implicitly used by AlertDialog, Button etc.
@Composable
fun ShareDialog(
    list: SesameList,
    onDismiss: () -> Unit
    // onShare callback removed as sharing happens via generated link/intent now
) {
    var shareLink by remember { mutableStateOf("") }
    var isGeneratingLink by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Functionality moved inside composable or to helper ---
    // TODO: Replace placeholder link generation with actual logic
    // This might involve a ViewModel call if link generation is complex/async
    fun generateShareLink() {
        scope.launch {
            isGeneratingLink = true
            // Simulate network call or complex generation
            delay(500) // Placeholder delay
            shareLink = "https://sesameapp.gazzel.io/list/${list.id}" // Example link structure
            isGeneratingLink = false
        }
    }

    fun copyToClipboard() {
        clipboardManager.setText(AnnotatedString(shareLink))
        // Show feedback to the user
        Toast.makeText(context, context.getString(R.string.link_copied_feedback), Toast.LENGTH_SHORT).show()
        // Optionally dismiss dialog after copy
        // onDismiss()
    }

    // Use LaunchedEffect to generate link when dialog opens if needed immediately,
    // or trigger via button click as currently implemented.
    // LaunchedEffect(list.id) { generateShareLink() } // Example: Generate on open


    AlertDialog(
        onDismissRequest = onDismiss,
        // Use stringResource for Title
        title = { Text(stringResource(id = R.string.share_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    // Use stringResource for Body text
                    text = stringResource(id = R.string.share_dialog_body),
                    style = MaterialTheme.typography.bodyMedium // Use bodyMedium for consistency
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isGeneratingLink) {
                    CircularProgressIndicator()
                } else if (shareLink.isNotBlank()) {
                    OutlinedTextField(
                        value = shareLink,
                        onValueChange = { }, // Read-only, no change needed
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(id = R.string.share_link_label)) }, // Added label string
                        trailingIcon = {
                            IconButton(onClick = { copyToClipboard() }) { // Call copy function
                                Icon(
                                    Icons.Filled.ContentCopy, // Use ContentCopy icon
                                    contentDescription = stringResource(id = R.string.cd_copy_link) // Use stringResource
                                )
                            }
                        }
                    )
                } else {
                    // Optional: Placeholder text when link isn't generated yet
                    Text(stringResource(id = R.string.share_dialog_generate_prompt)) // Add this string
                }
            }
        },
        confirmButton = {
            if (shareLink.isBlank()) {
                Button(
                    onClick = { generateShareLink() }, // Call generate function
                    enabled = !isGeneratingLink // Disable while generating
                ) {
                    // Use stringResource for Button text
                    Text(stringResource(id = R.string.button_generate_link))
                }
            } else {
                // Standard "Close" button once link is shown
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.dialog_button_close)) // Use stringResource
                }
            }
        },
        dismissButton = {
            // Show Cancel only before link is generated
            if (shareLink.isBlank()) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isGeneratingLink // Disable while generating
                ) {
                    // Use stringResource for Button text
                    Text(stringResource(id = R.string.dialog_button_cancel))
                }
            }
            // No dismiss button needed once link is generated (Confirm button becomes Close)
        }
    )
}