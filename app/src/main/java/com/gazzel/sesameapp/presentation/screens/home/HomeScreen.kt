package com.gazzel.sesameapp.presentation.screens.home

// --- Keep existing imports ---
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
import androidx.compose.ui.graphics.vector.ImageVector // Import ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource // Import stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // Import R
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.presentation.components.ShareDialog // Keep ShareDialog import
import com.gazzel.sesameapp.presentation.navigation.Screen
import com.gazzel.sesameapp.presentation.viewmodels.HomeViewModel // Ensure correct VM import
import com.gazzel.sesameapp.presentation.viewmodels.HomeUiState // Ensure correct State import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showShareDialog by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<SesameList?>(null) }
    // LocalContext is not needed anymore for navigation
    // val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) }, // Use string resource
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_icon)) // Use string resource
                    }
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Default.Person, contentDescription = stringResource(R.string.cd_profile_icon)) // Use string resource
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) }, // CD handled by label
                    label = { Text(stringResource(R.string.cd_home_icon)) }, // Use string resource
                    selected = true,
                    onClick = { /* Already on Home */ }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_lists_icon)) }, // Use string resource
                    selected = false,
                    onClick = { navController.navigate(Screen.Lists.route) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_friends_icon)) }, // Use string resource
                    selected = false,
                    onClick = { navController.navigate(Screen.Friends.route) }
                )
            }
        }
    ) { paddingValues ->
        when (val state = uiState) { // Use 'state' alias
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is HomeUiState.Success -> {
                val user = state.user
                val recentLists = state.recentLists // Assuming recentLists is part of Success state

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                // Assuming viewModel handles search/filter logic based on query
                                // viewModel.searchLists(it) // Make sure VM has this if needed
                            },
                            label = { Text(stringResource(R.string.home_search_label)) }, // Use string resource
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, // Label acts as description
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        // viewModel.searchLists("") // Reset search in VM
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search_icon)) // Use string resource
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Welcome section
                    val userName = user.displayName ?: user.username ?: stringResource(R.string.home_welcome_default_user)
                    Text(
                        // text = "Welcome, ${user.displayName ?: user.username ?: "User"}!", // Before
                        text = stringResource(R.string.home_welcome, userName), // After, using placeholder
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Quick actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QuickActionButton(
                            icon = Icons.Default.Add,
                            label = stringResource(R.string.home_quick_action_new_list), // Use string resource
                            onClick = {
                                navController.navigate(Screen.CreateList.route) // Navigate to Composable
                            }
                        )
                        QuickActionButton(
                            icon = Icons.Default.Search,
                            label = stringResource(R.string.home_quick_action_search), // Use string resource
                            onClick = { showSearch = !showSearch }
                        )
                        QuickActionButton(
                            icon = Icons.Default.Share,
                            label = stringResource(R.string.home_quick_action_share), // Use string resource
                            onClick = {
                                if (recentLists.isNotEmpty()) {
                                    selectedList = recentLists.first()
                                    showShareDialog = true
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Recent lists section
                    Text(
                        text = stringResource(R.string.home_section_recent_lists), // Use string resource
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Filter lists based on search query ONLY if search is active
                    val displayedLists = if (showSearch && searchQuery.isNotBlank()) {
                        recentLists.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.description.contains(searchQuery, ignoreCase = true)
                        }
                    } else {
                        recentLists
                    }

                    if (displayedLists.isEmpty()) {
                        Text(
                            text = if (showSearch && searchQuery.isNotBlank()) "No lists match '$searchQuery'" else stringResource(R.string.home_no_recent_lists), // Dynamic empty text
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    } else {
                        LazyColumn {
                            items(displayedLists, key = { it.id }) { list -> // Use key for performance
                                ListItem(
                                    // Pass composable lambdas for text content
                                    headlineContent = { Text(list.title) },
                                    supportingContent = { Text(list.description) },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            selectedList = list
                                            showShareDialog = true
                                        }) {
                                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share_list_icon)) // Use string resource
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        navController.navigate(Screen.ListDetail.createRoute(list.id))
                                    }
                                )
                            }
                        }
                    }
                }
            }
            is HomeUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { // Added Column for Button
                        Text(
                            text = state.message, // Use state directly
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // Handle Initial State if you re-introduce it
            is HomeUiState.Initial -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.state_initializing))
                }
            }
        }
    }

    // Share Dialog (Remains the same, uses data from selectedList)
    selectedList?.let { list ->
        if (showShareDialog) {
            ShareDialog(
                list = list,
                onDismiss = { showShareDialog = false },
                onShare = { /* TODO: Implement sharing logic */ showShareDialog = false }
            )
        }
    }
}

// Updated QuickActionButton to accept String directly
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String, // Changed to String
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp) // Add padding for touch target
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label, // Use label as basic content description
            modifier = Modifier.size(40.dp) // Slightly smaller?
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall) // Smaller text?
    }
}