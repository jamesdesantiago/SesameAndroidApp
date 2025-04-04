package com.gazzel.sesameapp.presentation.screens.lists

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.presentation.activities.CreateListActivity
import com.gazzel.sesameapp.presentation.activities.ListDetailsActivity
import com.gazzel.sesameapp.presentation.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    navController: NavController,
    viewModel: ListsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<SesameList?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Lists") },
                actions = {
                    // Use the 'context' obtained from the parent Composable scope
                    IconButton(onClick = {
                        context.startActivity(Intent(context, CreateListActivity::class.java))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Create List")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Home.route) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Lists") },
                    label = { Text("Lists") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    // Use Person icon here, ensure import androidx.compose.material.icons.filled.Person
                    icon = { Icon(Icons.Default.Person, contentDescription = "Friends") },
                    label = { Text("Friends") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Friends.route) }
                )
            }
        }
    ) { paddingValues ->
        when (uiState) {
            is ListsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ListsUiState.Success -> {
                val userLists = (uiState as ListsUiState.Success).userLists

                if (userLists.isEmpty()) {
                    // Use the dedicated EmptyListsView composable
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyListsView(modifier = Modifier.fillMaxSize()) // Call the empty view
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp), // Apply horizontal padding here
                        verticalArrangement = Arrangement.spacedBy(12.dp), // Add space between items
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp) // Add padding top/bottom
                    ) {
                        items(userLists, key = { list -> list.id }) { list -> // Use key for better performance
                            // Call ListItem, passing the navigation logic via onItemClick
                            ListItem(
                                headlineContent = { Text(list.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, // Limit title lines
                                supportingContent = { Text(list.description, maxLines = 2, overflow = TextOverflow.Ellipsis) }, // Limit description lines
                                trailingContent = {
                                    Row {
                                        // Consider adding Share button here too if needed
                                        IconButton(onClick = {
                                            selectedList = list
                                            showDeleteDialog = true
                                        }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete") // Use outlined icon
                                        }
                                    }
                                },
                                placeCount = list.places.size,
                                updatedTimestamp = list.updatedAt,
                                onItemClick = { // Pass the navigation logic here
                                    context.startActivity(
                                        Intent(context, ListDetailsActivity::class.java).apply {
                                            putExtra("listId", list.id)
                                            putExtra("listName", list.title)
                                        }
                                    )
                                }
                                // Removed modifier = Modifier.clickable from here
                            )
                        }
                    }
                }
            }
            is ListsUiState.Error -> {
                Box( // Use Box for centering error message
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState as ListsUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp) // Add padding to error text
                    )
                }
            }
        }
    } // End Scaffold

    // --- Delete confirmation dialog (remains the same) ---
    if (showDeleteDialog && selectedList != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete List") },
            text = { Text("Are you sure you want to delete the list \"${selectedList?.title}\"?") }, // Add list title
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedList?.let { list ->
                            viewModel.deleteList(list.id)
                        }
                        showDeleteDialog = false
                        selectedList = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error) // Color delete button
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
} // End ListsScreen

// --- Completed ListItem Composable ---
@Composable
private fun ListItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit,
    placeCount: Int,
    updatedTimestamp: Long,
    onItemClick: () -> Unit, // Renamed from modifier to be explicit action
    modifier: Modifier = Modifier // Keep default modifier for potential external use
) {
    // Apply clickable to the Card itself using the passed lambda
    Card(
        modifier = modifier // Apply any external modifiers first
            .fillMaxWidth()
            .clickable(onClick = onItemClick), // Make the whole card clickable
        shape = MaterialTheme.shapes.medium, // Consistent shape
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Add subtle elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp) // Adjust padding
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top // Align top for headline/trailing
            ) {
                // Box to constrain headline width if trailing content exists
                Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    headlineContent()
                }
                Box { // Box for trailing content alignment
                    trailingContent()
                }
            }
            Spacer(modifier = Modifier.height(4.dp)) // Smaller spacer
            // Box to constrain supporting text width
            Box(modifier = Modifier.fillMaxWidth()) {
                supportingContent()
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (placeCount == 1) "$placeCount item" else "$placeCount items", // Handle pluralization
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Updated ${formatDate(updatedTimestamp)}", // Add "Updated" prefix
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} // --- End ListItem ---

// --- EmptyListsView (remains the same) ---
@Composable
private fun EmptyListsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlaylistAdd, // Changed Icon for better context
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No lists yet",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the '+' button to create your first list!", // More direct CTA
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center // Center align
        )
    }
}

// --- formatDate (remains the same) ---
private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "..." // Handle default timestamp
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(date)
}