// app/src/main/java/com/gazzel/sesameapp/presentation/screens/lists/ListsScreen.kt
// (Content moved from UserListsActivity and updated)
package com.gazzel.sesameapp.presentation.screens.lists

// Keep necessary imports, remove Activity, Intent, lifecycleScope, manual service/token imports
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Import needed icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
// Import domain model and navigation Screen
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.presentation.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Remove ListCache object
// object ListCache { ... }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen( // Renamed from UserListsScreen for consistency
    navController: NavController,
    viewModel: ListsViewModel = hiltViewModel() // Inject ViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<SesameList?>(null) }
    // Removed context, scope - use NavController and viewModelScope

    // Fetch lists on initial composition and when lifecycle state changes (e.g., returning to screen)
    // This replaces onResume logic
    LaunchedEffect(Unit) { // Or use lifecycle events if needed: LocalLifecycleOwner.current.lifecycle.observe...
        viewModel.refresh() // Load lists initially
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Lists") },
                actions = {
                    IconButton(onClick = {
                        // Navigate to Composable CreateListScreen
                        navController.navigate(Screen.CreateList.route)
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Create List")
                    }
                    // Sign out might belong in a Profile/Settings screen now
                    // TextButton(onClick = { /* viewModel.signOut() ? */ }) { Text("Sign Out") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = false, // This screen is 'Lists'
                    onClick = { navController.navigate(Screen.Home.route) { launchSingleTop = true; popUpTo(Screen.Home.route) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Lists") },
                    label = { Text("Lists") },
                    selected = true, // This is the Lists screen
                    onClick = { /* Already here */ }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Friends") },
                    label = { Text("Friends") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Friends.route) { launchSingleTop = true; popUpTo(Screen.Home.route) } }
                )
            }
        }
        // FAB removed as Add action is in TopAppBar
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) { // Add main content Box
            when (val state = uiState) {
                is ListsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ListsUiState.Success -> {
                    val userLists = state.userLists
                    if (userLists.isEmpty()) {
                        EmptyListsView(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical=16.dp), // Padding for list
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(userLists, key = { list -> list.id }) { list ->
                                // Use the updated ListItem composable
                                ListItem(
                                    headlineContent = { Text(list.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                    supportingContent = { Text(list.description, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                    trailingContent = {
                                        Row {
                                            // Share button (implement later if needed)
                                            // IconButton(onClick = { /* TODO */ }) { Icon(Icons.Filled.Share, "Share") }
                                            IconButton(onClick = {
                                                selectedList = list
                                                showDeleteDialog = true
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                                            }
                                        }
                                    },
                                    placeCount = list.places.size, // Assuming places are part of domain model
                                    updatedTimestamp = list.updatedAt,
                                    onItemClick = {
                                        // Navigate using NavController and route from Screen object
                                        navController.navigate(Screen.ListDetail.createRoute(list.id))
                                    }
                                )
                            }
                        }
                    }
                }
                is ListsUiState.Error -> {
                    // Display error message centered
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ){
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            } // End when state
        } // End Box
    } // End Scaffold

    // --- Delete confirmation dialog ---
    if (showDeleteDialog && selectedList != null) {
        val listToDelete = selectedList!! // Capture non-null value
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; selectedList = null },
            title = { Text("Delete List") },
            text = { Text("Are you sure you want to delete \"${listToDelete.title}\"? This cannot be undone.") }, // Clarify action
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteList(listToDelete.id) // Call ViewModel delete
                        showDeleteDialog = false
                        selectedList = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) // Use error color
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; selectedList = null }) {
                    Text("Cancel")
                }
            }
        )
    }
} // End ListsScreen

// --- ListItem Composable (Keep as previously defined) ---
@Composable
private fun ListItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit,
    placeCount: Int,
    updatedTimestamp: Long,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // ... (Implementation from previous ListsScreen example) ...
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    headlineContent()
                }
                Box {
                    trailingContent()
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
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
                    text = if (placeCount == 1) "$placeCount item" else "$placeCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Updated ${formatDate(updatedTimestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- EmptyListsView (Keep as previously defined) ---
@Composable
private fun EmptyListsView(modifier: Modifier = Modifier) {
    // ... (Implementation from previous ListsScreen example) ...
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SentimentVeryDissatisfied, // Example icon
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
            text = "Tap the '+' button in the top bar\nto create your first list!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// --- formatDate (Keep as previously defined) ---
private fun formatDate(timestamp: Long): String {
    // ... (Implementation from previous ListsScreen example) ...
    if (timestamp <= 0L) return "..."
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(date)
}