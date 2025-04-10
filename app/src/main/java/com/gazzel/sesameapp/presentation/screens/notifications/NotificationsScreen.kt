package com.gazzel.sesameapp.presentation.screens.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.* // Wildcard
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications // Import icon for empty state
import androidx.compose.material3.* // Wildcard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // Import
import androidx.compose.ui.text.style.TextAlign // Import
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // Import R
import com.gazzel.sesameapp.domain.model.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Recommended: Add a SnackbarHostState for potential future error messages (e.g., failure to mark as read)
    // val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        // snackbarHost = { SnackbarHost(snackbarHostState) }, // Add if using snackbars
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_screen_title)) }, // <<< Use String Res
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_button)) // <<< Use String Res
                    }
                }
                // TODO: Add "Mark all as read" action?
                // actions = { TextButton(onClick = { viewModel.markAllAsRead() }) { Text("Mark All Read") } }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) { // Use variable
            is NotificationsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is NotificationsUiState.Success -> {
                val notifications = state.notifications

                if (notifications.isEmpty()) {
                    EmptyNotificationsView(modifier = Modifier.fillMaxSize().padding(paddingValues)) // <<< Use dedicated empty view
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Increased spacing
                    ) {
                        items(notifications, key = { it.id }) { notification -> // Add key
                            NotificationItem(
                                notification = notification,
                                onNotificationClick = { viewModel.markAsRead(notification.id) }
                                // TODO: Add swipe-to-delete?
                            )
                        }
                    }
                }
            }
            is NotificationsUiState.Error -> {
                // Use a Column with Retry button for error state
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message, // Keep specific message
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadNotifications() }) { // Retry loading
                        Text(stringResource(R.string.button_retry)) // <<< Use String Res
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: Notification,
    onNotificationClick: () -> Unit
) {
    // Consider using relative time formatting (e.g., "5 min ago", "Yesterday") for better UX
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNotificationClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Add subtle elevation
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) {
                MaterialTheme.colorScheme.surface // Less emphasis for read items
            } else {
                MaterialTheme.colorScheme.primaryContainer // Highlight unread items
            }
        ),
        shape = MaterialTheme.shapes.medium // Consistent shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleMedium,
                // Dim read titles slightly
                color = LocalContentColor.current.copy(alpha = if (notification.isRead) 0.7f else 1f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = if (notification.isRead) 0.7f else 1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formatDate(notification.timestamp, dateFormat), // Use helper
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (notification.isRead) 0.6f else 0.8f)
            )
        }
    }
}

// Extracted Empty State Composable
@Composable
private fun EmptyNotificationsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications, // Use relevant icon
            contentDescription = null, // Decorative
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.notifications_empty_title), // <<< Use String Res
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.notifications_empty_subtitle), // <<< Use String Res
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// Formatting helper (can be moved to a Util file)
private fun formatDate(timestamp: Long, formatter: SimpleDateFormat): String {
    return try {
        formatter.format(Date(timestamp))
    } catch (e: Exception) {
        "..." // Fallback
    }
}