package com.gazzel.sesameapp.presentation.components.listdetail

import android.content.Intent
import android.util.Log
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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
                text = "Share",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val shareUrl = "https://sesameapp.com/place/${place?.id}?listId=$listId"
            val shareMessage = "Check out this place on Sesame: ${place?.name} at ${place?.address}\n$shareUrl"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Copy Link
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(shareUrl))
                    Log.d("ListDetail", "Copied share URL to clipboard: $shareUrl")
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Copy link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Copy link",
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
                            setPackage("com.whatsapp")
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(whatsappIntent)
                        Log.d("ListDetail", "Opened WhatsApp with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open WhatsApp", e)
                        android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share via WhatsApp",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "WhatsApp",
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
                            setPackage("com.instagram.android")
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(instagramIntent)
                        Log.d("ListDetail", "Opened Instagram with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open Instagram", e)
                        android.widget.Toast.makeText(context, "Instagram not installed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share via Instagram",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Instagram",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // SMS
                IconButton(onClick = {
                    try {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("smsto:")
                            putExtra("sms_body", shareMessage)
                        }
                        context.startActivity(smsIntent)
                        Log.d("ListDetail", "Opened SMS with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open SMS", e)
                        android.widget.Toast.makeText(context, "SMS app not available", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Share via SMS",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "SMS",
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
                text = "Share List",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val shareUrl = "https://sesameapp.com/list/$listId"
            val shareMessage = "Check out this list on Sesame: $listName\n$shareUrl"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Copy Link
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(shareUrl))
                    Log.d("ListDetail", "Copied share URL to clipboard: $shareUrl")
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Copy link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Copy link",
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
                            setPackage("com.whatsapp")
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(whatsappIntent)
                        Log.d("ListDetail", "Opened WhatsApp with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open WhatsApp", e)
                        android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share via WhatsApp",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "WhatsApp",
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
                            setPackage("com.instagram.android")
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(instagramIntent)
                        Log.d("ListDetail", "Opened Instagram with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open Instagram", e)
                        android.widget.Toast.makeText(context, "Instagram not installed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share via Instagram",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Instagram",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // SMS
                IconButton(onClick = {
                    try {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("smsto:")
                            putExtra("sms_body", shareMessage)
                        }
                        context.startActivity(smsIntent)
                        Log.d("ListDetail", "Opened SMS with share URL: $shareUrl")
                    } catch (e: Exception) {
                        Log.e("ListDetail", "Failed to open SMS", e)
                        android.widget.Toast.makeText(context, "SMS app not available", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Share via SMS",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "SMS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
} 