package com.gazzel.sesameapp.presentation.screens.home

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.presentation.components.ShareDialog
import com.gazzel.sesameapp.presentation.navigation.Screen

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sesame") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Lists") },
                    label = { Text("Lists") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Lists.route) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.People, contentDescription = "Friends") },
                    label = { Text("Friends") },
                    selected = false,
                    onClick = { navController.navigate(Screen.Friends.route) }
                )
            }
        }
    ) { paddingValues ->
        when (uiState) {
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
                val user = (uiState as HomeUiState.Success).user
                val recentLists = (uiState as HomeUiState.Success).recentLists
                
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
                                viewModel.searchLists(it)
                            },
                            label = { Text("Search lists") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { 
                                        searchQuery = ""
                                        viewModel.searchLists("")
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Welcome section
                    Text(
                        text = "Welcome, ${user.displayName ?: user.username ?: "User"}!",
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
                            label = "New List",
                            onClick = { navController.navigate(Screen.CreateList.route) }
                        )
                        QuickActionButton(
                            icon = Icons.Default.Search,
                            label = "Search",
                            onClick = { showSearch = !showSearch }
                        )
                        QuickActionButton(
                            icon = Icons.Default.Share,
                            label = "Share",
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
                        text = "Recent Lists",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (recentLists.isEmpty()) {
                        Text(
                            text = "No recent lists",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn {
                            items(recentLists) { list ->
                                ListItem(
                                    headlineContent = { Text(list.title) },
                                    supportingContent = { Text(list.description) },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            selectedList = list
                                            showShareDialog = true
                                        }) {
                                            Icon(Icons.Default.Share, contentDescription = "Share")
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
                    Text(
                        text = (uiState as HomeUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    // Share Dialog
    selectedList?.let { list ->
        if (showShareDialog) {
            ShareDialog(
                list = list,
                onDismiss = { showShareDialog = false },
                onShare = { link ->
                    // TODO: Implement sharing functionality
                    showShareDialog = false
                }
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label)
    }
} 