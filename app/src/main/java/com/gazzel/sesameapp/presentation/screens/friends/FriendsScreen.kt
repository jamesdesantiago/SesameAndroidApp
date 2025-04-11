// app/src/main/java/com/gazzel/sesameapp/presentation/screens/friends/FriendsScreen.kt
package com.gazzel.sesameapp.presentation.screens.friends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi // For pull-refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pullrefresh.* // Import pull-refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items // Use Paging specific items
import com.gazzel.sesameapp.R
import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.presentation.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class) // Add ExperimentalMaterialApi for pull-refresh
@Composable
fun FriendsScreen(
    navController: NavController,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    // Collect PagingData using the specific extension function
    val lazyPagingItems: LazyPagingItems<Friend> =
        viewModel.displayDataFlow.collectAsLazyPagingItems()

    // Collect other states from ViewModel
    val actionState by viewModel.actionState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    // UI specific state
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show snackbar for follow/unfollow errors
    LaunchedEffect(actionState) {
        if (actionState is FriendActionState.Error) {
            val message = (actionState as FriendActionState.Error).message
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short,
                    actionLabel = "Dismiss" // Optional dismiss action
                )
            }
            viewModel.clearError() // Clear error state after showing
        }
    }

    // Pull to refresh state
    val isRefreshing = lazyPagingItems.loadState.refresh is LoadState.Loading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            Log.d("FriendsScreen", "Pull to refresh triggered.")
            lazyPagingItems.refresh() // Call refresh on the paging items
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.friends_title)) }
                // Optional: Add navigation icon if needed
                // navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        bottomBar = {
            // Optional: Keep bottom nav if this is a main destination
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_home_icon)) },
                    selected = false,
                    onClick = { navController.navigate(Screen.Home.route) { launchSingleTop = true; popUpTo(Screen.Home.route){ inclusive = true } } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_lists_icon)) },
                    selected = false,
                    onClick = { navController.navigate(Screen.Lists.route) { launchSingleTop = true; popUpTo(Screen.Home.route){ inclusive = true } } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(stringResource(R.string.cd_friends_icon)) },
                    selected = true, // This is the Friends screen
                    onClick = { /* Already here */ }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply Scaffold padding
        ) {
            // --- Tabs for Following/Followers ---
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                FriendTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            // Capitalize tab name for display
                            Text(tab.name.lowercase().replaceFirstChar { it.titlecase() })
                            // TODO: Consider adding counts here if available/needed
                        }
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) }, // Update VM state
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text(stringResource(R.string.friends_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) { // Clear via VM
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search_icon))
                        }
                    }
                }
            )

            // --- Paging Load State Handling in a Box with PullRefresh ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState) // Apply pullRefresh modifier here
            ) {
                val loadState = lazyPagingItems.loadState

                // --- Error State (Initial Load / Refresh) ---
                if (loadState.refresh is LoadState.Error) {
                    val error = (loadState.refresh as LoadState.Error).error
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp).align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.CloudOff, contentDescription=null, modifier=Modifier.size(48.dp), tint=MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            // Provide a user-friendly message, potentially masking technical details
                            text = stringResource(R.string.error_loading_friends_detail, error.localizedMessage ?: stringResource(R.string.error_unknown)),
                            color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { lazyPagingItems.retry() }) { Text(stringResource(R.string.button_retry)) }
                    }
                }
                // --- Empty State ---
                // Shown only after a successful refresh/load results in zero items
                else if (loadState.refresh is LoadState.NotLoading && loadState.append.endOfPaginationReached && lazyPagingItems.itemCount == 0) {
                    EmptyFriendsView(
                        modifier = Modifier.fillMaxSize().padding(16.dp), // Padding applied here
                        searchQuery = searchQuery // Pass search query for contextual message
                    )
                }
                // --- Data Loaded State ---
                // Show list if refresh is done OR if we already have items (even if refresh is loading)
                // This prevents the list disappearing during pull-to-refresh
                else if (loadState.refresh is LoadState.NotLoading || lazyPagingItems.itemCount > 0) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), // Padding for list items
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Use Paging specific items extension
                        items(
                            count = lazyPagingItems.itemCount,
                            key = { index -> lazyPagingItems.peek(index)?.id /* Use friend ID as key */ } // Use Friend ID as key
                        ) { index ->
                            val friend = lazyPagingItems[index]
                            friend?.let { // Render only if friend data is loaded
                                FriendItem( // Your existing FriendItem composable
                                    friend = it,
                                    onFollowClick = { isFollowing ->
                                        if (isFollowing) {
                                            viewModel.unfollowUser(it.id)
                                        } else {
                                            viewModel.followUser(it.id)
                                        }
                                        // For immediate UI feedback on button, could trigger refresh:
                                        // scope.launch { delay(250); lazyPagingItems.refresh() }
                                    }
                                )
                            } ?: run {
                                // Optional: Show a placeholder composable here
                                // FriendItemPlaceholder()
                            }
                        }

                        // --- Append Loading State ---
                        if (loadState.append is LoadState.Loading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { // Add vertical padding
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }

                        // --- Append Error State ---
                        if (loadState.append is LoadState.Error) {
                            val error = (loadState.append as LoadState.Error).error
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = stringResource(R.string.error_loading_more_friends, error.localizedMessage ?: stringResource(R.string.error_unknown)),
                                            color = MaterialTheme.colorScheme.error,
                                            style=MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(onClick = { lazyPagingItems.retry() }, modifier=Modifier.padding(top=4.dp)) {
                                            Text(stringResource(R.string.button_retry))
                                        }
                                    }
                                }
                            }
                        }
                    } // End LazyColumn
                } // End else (Data Loaded or Empty)

                // --- Initial Load / Refresh Loading State (Spinner in center) ---
                // Show centered spinner ONLY during initial load/refresh when there are no items yet
                // and it's not covered by the pull-refresh indicator's spinner.
                if (loadState.refresh is LoadState.Loading && lazyPagingItems.itemCount == 0 && !isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // --- Pull Refresh Indicator (visual indicator at the top) ---
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter) // Position indicator at the top
                )
            } // End Box with pullRefresh
        } // End Column
    } // End Scaffold
} // End FriendsScreen Composable


// FriendItem composable (ensure it uses R string resources)
@Composable
private fun FriendItem(
    friend: Friend,
    onFollowClick: (isFollowing: Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center){
                    Text(
                        text = (friend.displayName ?: friend.username).firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    // TODO: Add Coil for image loading friend.profilePicture
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName ?: friend.username,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (friend.displayName != friend.username && friend.username.isNotBlank()) {
                    Text(
                        text = "@${friend.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = pluralStringResource(R.plurals.friend_list_count, friend.listCount, friend.listCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onFollowClick(friend.isFollowing) },
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (friend.isFollowing) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (friend.isFollowing) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimary
                ),
            ) {
                Text(
                    text = if (friend.isFollowing) stringResource(R.string.button_following)
                    else stringResource(R.string.button_follow),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// EmptyFriendsView composable (ensure it uses R string resources)
@Composable
private fun EmptyFriendsView(
    modifier: Modifier = Modifier,
    searchQuery: String
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if(searchQuery.isBlank()) Icons.Filled.People else Icons.Filled.SearchOff, // Change icon for search
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (searchQuery.isBlank()) stringResource(R.string.friends_empty_title)
            else stringResource(R.string.friends_empty_search_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (searchQuery.isBlank()) stringResource(R.string.friends_empty_subtitle)
            else stringResource(R.string.friends_empty_search_subtitle, searchQuery), // Pass query to search subtitle if needed
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// Add required string resources to res/values/strings.xml:
/*
<string name="error_loading_friends">Failed to load friends</string>
<string name="error_loading_friends_detail">Error loading friends: %1$s</string>
<string name="error_loading_more_friends">Error loading more: %1$s</string>
<string name="error_unknown">Unknown error</string>
*/