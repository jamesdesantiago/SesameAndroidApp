package com.gazzel.sesameapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.isSystemInDarkTheme
import com.gazzel.sesameapp.Friend
import com.gazzel.sesameapp.User
import com.gazzel.sesameapp.UserService

class FriendsActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    private val userService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://gazzel.io/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserService::class.java)
    }

    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            Log.e("FriendsActivity", "User not logged in, redirecting to login")
            // TODO: Redirect to login screen
            finish()
            return
        }

        setContent {
            SesameAppTheme {
                FriendsScreen(userService = userService, getValidToken = { getValidToken() })
            }
        }
    }

    private suspend fun getValidToken(): String? {
        if (cachedToken != null && System.currentTimeMillis() / 1000 < tokenExpiry - 60) {
            Log.d("FriendsActivity", "Using cached token: $cachedToken, expires at $tokenExpiry")
            return cachedToken
        } else {
            return refreshToken()
        }
    }

    private suspend fun refreshToken(): String? {
        return try {
            if (auth.currentUser == null) {
                Log.e("FriendsActivity", "No current user logged in")
                return null
            }
            val result = auth.currentUser?.getIdToken(true)?.await()
            cachedToken = result?.token
            tokenExpiry = result?.expirationTimestamp ?: 0
            Log.d("FriendsActivity", "Refreshed token: $cachedToken, expires at $tokenExpiry")
            cachedToken
        } catch (e: Exception) {
            Log.e("FriendsActivity", "Failed to refresh token", e)
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(userService: UserService, getValidToken: suspend () -> String?) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    var selectedTab by remember { mutableStateOf("Following") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<User>>(emptyList()) }
    var followersList by remember { mutableStateOf<List<User>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val followingStatus = remember { mutableStateMapOf<Int, Boolean>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val token = getValidToken() ?: throw Exception("No valid token")
                val followingResponse = userService.getFollowing("Bearer $token")
                if (followingResponse.isSuccessful) {
                    followingList = followingResponse.body() ?: emptyList()
                } else {
                    errorMessage = "Failed to load following list: ${followingResponse.message()}"
                }
                val followersResponse = userService.getFollowers("Bearer $token")
                if (followersResponse.isSuccessful) {
                    followersList = followersResponse.body() ?: emptyList()
                } else {
                    errorMessage = "Failed to load followers list: ${followersResponse.message()}"
                }
            } catch (e: Exception) {
                Log.e("FriendsScreen", "Failed to load following/followers", e)
                errorMessage = "Failed to load data: ${e.message}"
            }
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            scope.launch {
                try {
                    val token = getValidToken() ?: throw Exception("No valid token")
                    val response = userService.searchUsersByEmail(searchQuery, "Bearer $token")
                    if (response.isSuccessful) {
                        searchResults = response.body() ?: emptyList()
                        errorMessage = null
                    } else {
                        errorMessage = "Failed to search users: ${response.message()}"
                        searchResults = emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("FriendsScreen", "Failed to search users", e)
                    errorMessage = "Failed to search users: ${e.message}"
                    searchResults = emptyList()
                }
            }
        } else {
            searchResults = emptyList()
            errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends", style = typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.primary,
                    titleContentColor = colors.onPrimary
                )
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                TabRow(
                    selectedTabIndex = if (selectedTab == "Following") 0 else 1,
                    containerColor = colors.background,
                    contentColor = colors.primary
                ) {
                    Tab(
                        selected = selectedTab == "Following",
                        onClick = { selectedTab = "Following" },
                        text = { Text("Following (${followingList.size})", style = typography.labelLarge) }
                    )
                    Tab(
                        selected = selectedTab == "Followers",
                        onClick = { selectedTab = "Followers" },
                        text = { Text("Followers (${followersList.size})", style = typography.labelLarge) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    placeholder = {
                        Text(
                            text = "Search by email",
                            style = typography.bodyMedium,
                            color = colors.onSurface.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = typography.bodyMedium.copy(color = colors.onSurface),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = colors.surfaceVariant,
                        focusedContainerColor = colors.surfaceVariant,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outline,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface
                    ),
                    singleLine = true
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = typography.bodyMedium,
                        color = colors.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                        items(searchResults) { user ->
                            SearchResultItem(
                                user = user,
                                isFollowing = followingStatus[user.id] ?: followingList.any { it.id == user.id },
                                onFollowClick = {
                                    scope.launch {
                                        try {
                                            val token = getValidToken() ?: throw Exception("No valid token")
                                            val response = if (followingStatus[user.id] ?: followingList.any { it.id == user.id }) {
                                                userService.unfollowUser(user.id, "Bearer $token")
                                            } else {
                                                userService.followUser(user.id, "Bearer $token")
                                            }
                                            if (response.isSuccessful) {
                                                followingStatus[user.id] =
                                                    !(followingStatus[user.id] ?: followingList.any { it.id == user.id })
                                                val followingResponse = userService.getFollowing("Bearer $token")
                                                if (followingResponse.isSuccessful) {
                                                    followingList = followingResponse.body() ?: emptyList()
                                                }
                                            } else {
                                                errorMessage = "Failed to update follow status: ${response.message()}"
                                            }
                                        } catch (e: Exception) {
                                            Log.e("FriendsScreen", "Failed to follow/unfollow user", e)
                                            errorMessage = "Failed to follow/unfollow: ${e.message}"
                                        }
                                    }
                                },
                                onAddClick = {
                                    scope.launch {
                                        try {
                                            val token = getValidToken() ?: throw Exception("No valid token")
                                            val response = userService.sendFriendRequest(user.id, "Bearer $token")
                                            if (response.isSuccessful) {
                                                Log.d("FriendsScreen", "Friend request sent to user: ${user.username}")
                                            } else {
                                                errorMessage = "Failed to send friend request: ${response.message()}"
                                            }
                                        } catch (e: Exception) {
                                            Log.e("FriendsScreen", "Failed to send friend request", e)
                                            errorMessage = "Failed to send friend request: ${e.message}"
                                        }
                                    }
                                },
                                onProfileClick = {
                                    Log.d("FriendsScreen", "Profile clicked for user: ${user.username}")
                                }
                            )
                        }
                    } else if (searchQuery.isNotBlank() && searchResults.isEmpty() && errorMessage == null) {
                        item {
                            Text(
                                text = "No users found",
                                style = typography.bodyMedium,
                                color = colors.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val listToShow = if (selectedTab == "Following") followingList else followersList
                        items(listToShow) { user ->
                            FriendItem(
                                friend = Friend(
                                    initials = (user.username?.take(2)?.uppercase() ?: user.email.take(2).uppercase()),
                                    username = user.username?.let { "@$it" } ?: "@${user.email}",
                                    isFollowing = selectedTab == "Following" || followersList.any { it.id == user.id }
                                )
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SearchResultItem(
    user: User,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    onAddClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color.Gray
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (user.username?.take(2)?.uppercase() ?: user.email.take(2).uppercase()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = user.username?.let { "@$it" } ?: "@${user.email}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable { onProfileClick() }
                    .padding(vertical = 8.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onFollowClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) Color.Transparent else Color.Black,
                    contentColor = if (isFollowing) Color.Black else Color.White
                ),
                border = if (isFollowing) ButtonDefaults.outlinedButtonBorder else null,
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = if (isFollowing) "following" else "follow",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add friend",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun FriendItem(friend: Friend) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = when (friend.initials) {
                    "MH" -> Color(0xFFFFC1CC)
                    "LH" -> Color(0xFFADD8E6)
                    "RH" -> Color(0xFF87CEEB)
                    "JM" -> Color(0xFF87CEFA)
                    "MT" -> Color(0xFFD3D3D3)
                    "MJ" -> Color(0xFFFFD700)
                    "YT" -> Color(0xFFFFA500)
                    "LM" -> Color(0xFF90EE90)
                    else -> Color.Gray
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = friend.initials,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = friend.username,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Button(
            onClick = { /* No action yet */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (friend.isFollowing) Color.Transparent else Color.Black,
                contentColor = if (friend.isFollowing) Color.Black else Color.White
            ),
            border = if (friend.isFollowing) ButtonDefaults.outlinedButtonBorder else null,
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = if (friend.isFollowing) "following" else "follow",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}