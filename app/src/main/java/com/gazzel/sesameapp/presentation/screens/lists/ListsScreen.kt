// app/src/main/java/com/gazzel/sesameapp/presentation/screens/lists/ListsScreen.kt
package com.gazzel.sesameapp.presentation.screens.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource // <<< Import pluralStringResource
import androidx.compose.ui.res.stringResource    // <<< Import stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // <<< Import R
import com.gazzel.sesameapp.domain.model.SesameList
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

    // Load lists initially
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lists_title)) }, // <<< Use String Resource
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.CreateList.route) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_create_list)) // <<< Use String Resource
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_home_icon)) }, // <<< Use String Resource
                    selected = false,
                    onClick = { navController.navigate(Screen.Home.route) { launchSingleTop = true; popUpTo(Screen.Home.route) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_lists_icon)) }, // <<< Use String Resource
                    selected = true,
                    onClick = { /* Already here */ }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_friends_icon)) }, // <<< Use String Resource
                    selected = false,
                    onClick = { navController.navigate(Screen.Friends.route) { launchSingleTop = true; popUpTo(Screen.Home.route) } }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                is ListsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ListsUiState.Success -> {
                    val userLists = state.userLists
                    if (userLists.isEmpty()) {
                        // Pass string resource IDs to EmptyListsView
                        EmptyListsView(
                            modifier = Modifier.fillMaxSize(),
                            titleResId = R.string.lists_empty_title, // <<< Pass Res ID
                            subtitleResId = R.string.lists_empty_subtitle // <<< Pass Res ID
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(userLists, key = { list -> list.id }) { list ->
                                // Pass data to the updated ListItem composable
                                ListsListItem( // <<< Renamed for clarity
                                    title = list.title,
                                    description = list.description,
                                    placeCount = list.places.size,
                                    updatedTimestamp = list.updatedAt,
                                    onItemClick = {
                                        navController.navigate(Screen.ListDetail.createRoute(list.id))
                                    },
                                    onDeleteClick = {
                                        selectedList = list
                                        showDeleteDialog = true
                                    }
                                    // Add onShareClick if needed
                                )
                            }
                        }
                    }
                }
                is ListsUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            state.message, // Keep specific error from VM
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.button_retry)) // <<< Use String Resource
                        }
                    }
                }
            } // End when state
        } // End Box
    } // End Scaffold

    // --- Delete confirmation dialog ---
    if (showDeleteDialog && selectedList != null) {
        val listToDelete = selectedList!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; selectedList = null },
            title = { Text(stringResource(R.string.dialog_delete_list_title)) }, // <<< Use String Resource
            // Use formatted string for text
            text = { Text(stringResource(R.string.dialog_delete_list_text, listToDelete.title)) }, // <<< Use String Resource
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteList(listToDelete.id)
                        showDeleteDialog = false
                        selectedList = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dialog_delete_confirm_button)) // <<< Use String Resource
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; selectedList = null }) {
                    Text(stringResource(R.string.dialog_delete_cancel_button)) // <<< Use String Resource
                }
            }
        )
    }
} // End ListsScreen

// --- Renamed ListItem to ListsListItem ---
@Composable
private fun ListsListItem( // <<< Renamed
    title: String,
    description: String,
    placeCount: Int,
    updatedTimestamp: Long,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit, // <<< Added delete callback
    modifier: Modifier = Modifier
    // Add onShareClick: () -> Unit if implementing share
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
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
                // Use Text directly with provided strings
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, // Slightly larger title
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                // Trailing Icons
                Row {
                    // IconButton(onClick = { /* onShareClick() */ }) { // Uncomment if Share implemented
                    //    Icon(Icons.Filled.Share, stringResource(R.string.cd_share_list_icon))
                    // }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_list)) // <<< Use String Resource
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Use Text directly with provided strings
            Text(
                text = description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp) // Give space for 2 lines
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Use pluralStringResource for item count
                Text(
                    text = pluralStringResource(R.plurals.list_item_count, placeCount, placeCount), // <<< Use Plural
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.list_updated_date, formatDate(updatedTimestamp)), // <<< Use formatted string
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Updated EmptyListsView to use Res IDs ---
@Composable
private fun EmptyListsView(
    modifier: Modifier = Modifier,
    titleResId: Int,
    subtitleResId: Int
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SentimentVeryDissatisfied, // Keep or choose another
            contentDescription = null, // Decorative
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(titleResId), // <<< Use Res ID
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(subtitleResId), // <<< Use Res ID
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// --- formatDate (Keep as is) ---
private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "..." // Or handle differently
    val date = Date(timestamp)
    // Consider using more relative time formatting for recent items
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(date)
}