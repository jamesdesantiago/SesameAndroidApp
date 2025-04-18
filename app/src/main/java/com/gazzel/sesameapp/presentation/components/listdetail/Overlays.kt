package com.gazzel.sesameapp.presentation.components.listdetail

import android.content.Intent
import android.util.Log
import android.widget.Toast // Keep the original Toast import
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource // <<< Import stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.R // <<< Import your R class
import com.gazzel.sesameapp.domain.model.PlaceItem

@Composable
fun SharePlaceOverlay(
    listId: String,
    place: PlaceItem?,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                // Use stringResource for Title
                text = stringResource(R.string.share_overlay_title), // Use generic share title
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Share URL and Message preparation (keep as is)
            val shareUrl = "https://sesameapp.com/place/${place?.id ?: "unknown"}?listId=$listId" // Handle null place?.id
            val placeName = place?.name ?: "this place"
            val placeAddress = place?.address ?: "an address"
            val shareMessage = "Check out $placeName on Sesame: $placeAddress\n$shareUrl" // Simplified message for example

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Copy Link
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(shareUrl))
                    // Optional: Show feedback via Toast or Snackbar
                    Toast.makeText(context, R.string.link_copied_feedback, Toast.LENGTH_SHORT).show() // Use string resource
                    Log.d("ListDetail", "Copied share URL to clipboard: $shareUrl")
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check, // Using Check for Copy might be confusing, consider Icons.Default.ContentCopy
                            // Use stringResource for contentDescription
                            contentDescription = stringResource(R.string.cd_copy_link),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            // Use stringResource for Text
                            text = stringResource(R.string.share_copy_link),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // WhatsApp
                IconButton(onClick = {
                    try {
                        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            setPackage("com.whatsapp") // Keep package name hardcoded (standard practice)
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(whatsappIntent)
                        Log.d("ListDetail", "Opened WhatsApp with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open WhatsApp", e)
                        // Use stringResource for Toast message
                        Toast.makeText(context, stringResource(R.string.share_error_app_not_installed, "WhatsApp"), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            // Use stringResource for contentDescription with placeholder
                            contentDescription = stringResource(R.string.cd_share_via_app, "WhatsApp"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            // Use stringResource for Text
                            text = stringResource(R.string.share_via_whatsapp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Instagram
                IconButton(onClick = {
                    try {
                        val instagramIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            setPackage("com.instagram.android") // Keep package name hardcoded
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(instagramIntent)
                        Log.d("ListDetail", "Opened Instagram with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open Instagram", e)
                        // Use stringResource for Toast message
                        Toast.makeText(context, stringResource(R.string.share_error_app_not_installed, "Instagram"), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            // Use stringResource for contentDescription with placeholder
                            contentDescription = stringResource(R.string.cd_share_via_app, "Instagram"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            // Use stringResource for Text
                            text = stringResource(R.string.share_via_instagram),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // SMS
                IconButton(onClick = {
                    try {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("smsto:") // Standard scheme
                            putExtra("sms_body", shareMessage)
                        }
                        context.startActivity(smsIntent)
                        Log.d("ListDetail", "Opened SMS with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open SMS", e)
                        // Use stringResource for Toast message
                        Toast.makeText(context, stringResource(R.string.share_error_sms_unavailable), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            // Use stringResource for contentDescription with placeholder
                            contentDescription = stringResource(R.string.cd_share_via_app, "SMS"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            // Use stringResource for Text
                            text = stringResource(R.string.share_via_sms),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShareListOverlay(
    listId: String,
    listName: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                // Use stringResource for Title
                text = stringResource(R.string.share_dialog_title), // Use Share List title
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Share URL and Message preparation (keep as is)
            val shareUrl = "https://sesameapp.com/list/$listId"
            val shareMessage = "Check out this list on Sesame: $listName\n$shareUrl"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Copy Link
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(shareUrl))
                    // Optional: Show feedback via Toast or Snackbar
                    Toast.makeText(context, R.string.link_copied_feedback, Toast.LENGTH_SHORT).show() // Use string resource
                    Log.d("ListDetail", "Copied share URL to clipboard: $shareUrl")
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check, // Using Check for Copy might be confusing, consider Icons.Default.ContentCopy
                            // Use stringResource for contentDescription
                            contentDescription = stringResource(R.string.cd_copy_link),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            // Use stringResource for Text
                            text = stringResource(R.string.share_copy_link),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // WhatsApp
                IconButton(onClick = {
                    try {
                        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            setPackage("com.whatsapp") // Keep package name hardcoded (standard practice)
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(whatsappIntent)
                        Log.d("ListDetail", "Opened WhatsApp with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open WhatsApp", e)
                        // Use stringResource for Toast message
                        Toast.makeText(context, stringResource(R.string.share_error_app_not_installed, "WhatsApp"), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            // Use stringResource for contentDescription with placeholder
                            contentDescription = stringResource(R.string.cd_share_via_app, "WhatsApp"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            // Use stringResource for Text
                            text = stringResource(R.string.share_via_whatsapp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Instagram
                IconButton(onClick = {
                    try {
                        val instagramIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            setPackage("com.instagram.android") // Keep package name hardcoded
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(instagramIntent)
                        Log.d("ListDetail", "Opened Instagram with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open Instagram", e)
                        // Use stringResource for Toast message
                        Toast.makeText(context, stringResource(R.string.share_error_app_not_installed, "Instagram"), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            // Use stringResource for contentDescription with placeholder
                            contentDescription = stringResource(R.string.cd_share_via_app, "Instagram"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            // Use stringResource for Text
                            text = stringResource(R.string.share_via_instagram),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // SMS
                IconButton(onClick = {
                    try {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("smsto:") // Standard scheme
                            putExtra("sms_body", shareMessage)
                        }
                        context.startActivity(smsIntent)
                        Log.d("ListDetail", "Opened SMS with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open SMS", e)
                        // Use stringResource for Toast message
                        Toast.makeText(context, stringResource(R.string.share_error_sms_unavailable), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            // Use stringResource for contentDescription with placeholder
                            contentDescription = stringResource(R.string.cd_share_via_app, "SMS"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            // Use stringResource for Text
                            text = stringResource(R.string.share_via_sms),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}