package com.gazzel.sesameapp.presentation.screens.friends

import androidx.compose.foundation.layout.* // Use wildcard
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Use wildcard
import androidx.compose.material3.* // Use wildcard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource // Import
import androidx.compose.ui.res.stringResource // Import
import androidx.compose.ui.text.style.TextAlign // Import
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // Import R
import com.gazzel.sesameapp.domain.model.Friend
import com.gazzel.sesameapp.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    navController: NavController,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.friends_title)) } // <<< Use String Resource
                // Add back navigation if this screen isn't a top-level destination
                // navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        bottomBar = {
            // Assuming this is a top-level destination accessible from bottom nav
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
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchFriends(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text(stringResource(R.string.friends_search_placeholder)) }, // <<< Use String Resource
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, // Placeholder is descriptive enough
                singleLine = true,
                trailingIcon = { // Add clear button
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.searchFriends("") // Trigger reload/clear search in VM
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search_icon)) // <<< Use String Resource
                        }
                    }
                }
            )

            when (val state = uiState) {
                is FriendsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 56.dp), // Avoid overlap with potential bottom bar space
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is FriendsUiState.Success -> {
                    val friends = state.friends
                    if (friends.isEmpty()) {
                        EmptyFriendsView(
                            modifier = Modifier.fillMaxSize().padding(bottom = 56.dp), // Avoid overlap
                            searchQuery = searchQuery
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), // Adjust padding
                            verticalArrangement = Arrangement.spacedBy(12.dp) // Slightly more space
                        ) {
                            items(friends, key = { it.id }) { friend ->
                                FriendItem(
                                    friend = friend,
                                    onFollowClick = { isFollowing ->
                                        if (isFollowing) {
                                            viewModel.unfollowUser(friend.id)
                                        } else {
                                            viewModel.followUser(friend.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                is FriendsUiState.Error -> {
                    Column( // Wrap Error in a Column for Button
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ){
                        Text(
                            text = state.message, // Keep specific message
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            if (searchQuery.isBlank()) viewModel.loadFriends() else viewModel.searchFriends(searchQuery)
                        }) {
                            Text(stringResource(R.string.button_retry)) // <<< Use String Resource
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendItem(
    friend: Friend,
    onFollowClick: (isFollowing: Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Add subtle elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp), // Adjust padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture placeholder
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer // Use theme color for placeholder
            ) {
                // TODO: Replace with actual image loading (e.g., using Coil)
                // if (friend.profilePicture != null) { AsyncImage(...) } else { Icon(...) }
                Box(contentAlignment = Alignment.Center){
                    Text(
                        text = (friend.displayName ?: friend.username).firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Friend info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = friend.displayName ?: friend.username,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Display username only if different from display name or if display name is null
                if (friend.displayName != friend.username && friend.username.isNotBlank()) {
                    Text(
                        text = "@${friend.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Use plural string for lists count
                Text(
                    text = pluralStringResource(R.plurals.friend_list_count, friend.listCount, friend.listCount), // <<< Use Plural
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp)) // Add space before button

            // Follow/Following button
            Button(
                onClick = { onFollowClick(friend.isFollowing) },
                // Adapt size and shape for better visual balance
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                shape = MaterialTheme.shapes.small, // Or shapes.medium
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (friend.isFollowing)
                        MaterialTheme.colorScheme.secondaryContainer // Use secondary container for 'Following'
                    else
                        MaterialTheme.colorScheme.primary
                ),
                // Disable button briefly if action is pending? (Requires tracking in VM/UI)
            ) {
                Text(
                    text = if (friend.isFollowing) stringResource(R.string.button_following) else stringResource(R.string.button_follow), // <<< Use String Resources
                    style = MaterialTheme.typography.labelMedium // Use appropriate style
                )
            }
        }
    }
}

@Composable
private fun EmptyFriendsView(
    modifier: Modifier = Modifier,
    searchQuery: String
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp), // More padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.People, // More relevant icon?
            contentDescription = null, // Decorative
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (searchQuery.isBlank()) stringResource(R.string.friends_empty_title) else stringResource(R.string.friends_empty_search_title), // <<< Use String Resources
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (searchQuery.isBlank())
                stringResource(R.string.friends_empty_subtitle)
            else
                stringResource(R.string.friends_empty_search_subtitle), // <<< Use String Resources
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}