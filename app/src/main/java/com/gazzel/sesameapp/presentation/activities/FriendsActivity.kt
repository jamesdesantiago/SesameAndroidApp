package com.gazzel.sesameapp.presentation.activities

import android.content.Intent // Keep this if you implement redirection
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Ensure this import is present
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
// import androidx.compose.material3.Tab // Keep if Tab is used, ensure it's M3
// import androidx.compose.material3.TabRow // Keep if TabRow is used, ensure it's M3
import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.Add // Add not used here, can be removed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // Ensure this import is present
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.gazzel.sesameapp.data.service.UserProfileService // Correct Service Import
import com.gazzel.sesameapp.domain.model.Friend // Keep if Friend model is used indirectly
import com.gazzel.sesameapp.domain.model.User // Domain model User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Response // Ensure this import is present
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
// import retrofit2.create // Optional: Import if using retrofit.create<Service>()
import java.util.concurrent.TimeUnit

class FriendsActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    // --- Direct Initialization ---
    private val okHttpClient by lazy { // Keep lazy for OkHttp/Retrofit if preferred, but direct works too
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit by lazy { // Keep lazy for OkHttp/Retrofit if preferred
        Retrofit.Builder()
            .baseUrl("https://gazzel.io/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Directly create the service instance using the previously defined retrofit instance
    private val userProfileService: UserProfileService by lazy { // Keep lazy if preferred
        retrofit.create(UserProfileService::class.java)
    }
    // --- End Direct Initialization ---

    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0 // Correct type: Long

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            Log.e("FriendsActivity", "User not logged in, redirecting to login")
            // TODO: Implement proper redirection (e.g., using Navigation Component or starting LoginActivity)
            // startActivity(Intent(this, LoginActivity::class.java)) // Example
            finish()
            return
        }

        setContent {
            SesameAppTheme {
                // Pass the correctly named service instance and the function reference
                FriendsScreen(
                    userProfileService = userProfileService, // Correct parameter name
                    getValidToken = { getValidToken() } // Pass function reference
                )
            }
        }
    }

    // getValidToken and refreshToken suspend functions remain the same
    private suspend fun getValidToken(): String? {
        if (cachedToken != null && System.currentTimeMillis() / 1000 < tokenExpiry - 60) {
            // Log.d("FriendsActivity", "Using cached token") // Avoid logging token value
            return cachedToken
        } else {
            return refreshToken()
        }
    }

    private suspend fun refreshToken(): String? {
        return try {
            val user = auth.currentUser
            if (user == null) {
                Log.e("FriendsActivity", "No current user logged in for refresh")
                // Consider signing out or redirecting here?
                return null
            }
            val result = user.getIdToken(true).await()
            cachedToken = result.token
            tokenExpiry = result.expirationTimestamp ?: 0
            // Log.d("FriendsActivity", "Refreshed token, expires at $tokenExpiry") // Avoid logging token value
            cachedToken
        } catch (e: Exception) {
            Log.e("FriendsActivity", "Failed to refresh token", e)
            // Handle specific exceptions if needed (e.g., network error, auth error)
            // Maybe sign the user out if refresh fails consistently?
            null // Return null on failure
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Use conventional parameter naming (lowercase start)
fun FriendsScreen(userProfileService: UserProfileService, getValidToken: suspend () -> String?) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    var selectedTab by remember { mutableStateOf("Following") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<User>>(emptyList()) }
    var followersList by remember { mutableStateOf<List<User>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Use String key for the map, matching User.id type
    val followingStatus = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    // Use the correctly named parameter 'userProfileService'
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val token = getValidToken() ?: throw IllegalStateException("Authentication token is missing.") // More specific error
                val followingResponse = userProfileService.getFollowing("Bearer $token")
                if (followingResponse.isSuccessful) {
                    followingList = followingResponse.body() ?: emptyList()
                } else {
                    errorMessage = "Failed to load following list: ${followingResponse.code()} ${followingResponse.message()}" // Include code
                    Log.e("FriendsScreen", "GetFollowing error: ${followingResponse.code()} - ${followingResponse.errorBody()?.string()}")
                }
                val followersResponse = userProfileService.getFollowers("Bearer $token")
                if (followersResponse.isSuccessful) {
                    followersList = followersResponse.body() ?: emptyList()
                } else {
                    errorMessage = "Failed to load followers list: ${followersResponse.code()} ${followersResponse.message()}" // Include code
                    Log.e("FriendsScreen", "GetFollowers error: ${followersResponse.code()} - ${followersResponse.errorBody()?.string()}")
                }
            } catch (e: Exception) { // Catch specific exceptions if needed (IOException, HttpException)
                Log.e("FriendsScreen", "Failed to load following/followers", e)
                errorMessage = "Failed to load data: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    // Use the correctly named parameter 'userProfileService'
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.length > 2) { // Optional: Add min length for search
            scope.launch {
                try {
                    val token = getValidToken() ?: throw IllegalStateException("Authentication token is missing.")
                    val response = userProfileService.searchUsersByEmail(searchQuery, "Bearer $token")
                    if (response.isSuccessful) {
                        searchResults = response.body() ?: emptyList()
                        errorMessage = null // Clear previous errors on success
                    } else {
                        errorMessage = "Failed to search users: ${response.code()} ${response.message()}"
                        Log.e("FriendsScreen", "SearchUsers error: ${response.code()} - ${response.errorBody()?.string()}")
                        searchResults = emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("FriendsScreen", "Failed to search users", e)
                    errorMessage = "Failed to search users: ${e.localizedMessage ?: "Unknown error"}"
                    searchResults = emptyList()
                }
            }
        } else {
            searchResults = emptyList()
            // Don't clear error message here if search query is just short/empty
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
                TabRow( // Ensure TabRow and Tab imports are Material 3
                    selectedTabIndex = if (selectedTab == "Following") 0 else 1,
                    containerColor = colors.background, // Use MaterialTheme colors
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
                        text = errorMessage ?: "", // Use safe call just in case
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
                    val itemsToShow = if (searchQuery.isNotBlank()) searchResults else {
                        if (selectedTab == "Following") followingList else followersList
                    }

                    items(itemsToShow, key = { user -> user.id }) { user -> // Add key for performance
                        if (searchQuery.isNotBlank()) {
                            SearchResultItem(
                                user = user,
                                isFollowing = followingStatus[user.id] ?: followingList.any { it.id == user.id },
                                onFollowClick = {
                                    scope.launch {
                                        try {
                                            val token = getValidToken() ?: throw IllegalStateException("Authentication token is missing.")
                                            val isCurrentlyFollowing = followingStatus[user.id] ?: followingList.any { it.id == user.id }
                                            val response: Response<Unit>
                                            if (isCurrentlyFollowing) {
                                                response = userProfileService.unfollowUser(user.id, "Bearer $token")
                                            } else {
                                                response = userProfileService.followUser(user.id, "Bearer $token")
                                            }

                                            if (response.isSuccessful) {
                                                followingStatus[user.id] = !isCurrentlyFollowing
                                                // To update the main list immediately after follow/unfollow:
                                                if (!isCurrentlyFollowing) {
                                                    // Add to followingList if not already there
                                                    if (followingList.none{ it.id == user.id }) {
                                                        followingList = followingList + user
                                                    }
                                                } else {
                                                    // Remove from followingList
                                                    followingList = followingList.filterNot { it.id == user.id }
                                                }
                                            } else {
                                                errorMessage = "Failed to update following status: ${response.code()} ${response.message()}"
                                                Log.e("FriendsScreen", "Follow/Unfollow error: ${response.code()} - ${response.errorBody()?.string()}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("FriendsScreen", "Failed to update following status", e)
                                            errorMessage = "Failed to update following status: ${e.localizedMessage ?: "Unknown error"}"
                                        }
                                    }
                                }
                            )
                        } else {
                            UserListItem(user = user)
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
    onFollowClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape) // clip should resolve now
                    .background(colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                // Display first letter of email or username if available
                val initial = user.username?.firstOrNull()?.uppercaseChar()
                    ?: user.email.firstOrNull()?.uppercaseChar() ?: '?'
                Text(
                    text = initial.toString(),
                    style = typography.titleMedium,
                    color = colors.primary
                )
            }
            Column {
                // Display display name or username or email
                Text(
                    text = user.displayName ?: user.username ?: user.email,
                    style = typography.bodyMedium,
                    color = colors.onSurface
                )
                // Optionally show username if display name exists
                if (user.displayName != null && user.username != null) {
                    Text(
                        text = "@${user.username}",
                        style = typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = if (isFollowing) "Following" else "Not following",
                    style = typography.bodySmall,
                    color = if (isFollowing) colors.primary else colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Button(
            onClick = onFollowClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) colors.surfaceVariant else colors.primary,
                contentColor = if (isFollowing) colors.onSurfaceVariant else colors.onPrimary
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = if (isFollowing) "Unfollow" else "Follow",
                style = typography.labelLarge
            )
        }
    }
}

@Composable
fun UserListItem(user: User) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { /* Optional: Navigate to user profile? */ }, // Make clickable?
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape) // clip should resolve now
                .background(colors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            // Display first letter of email or username if available
            val initial = user.username?.firstOrNull()?.uppercaseChar()
                ?: user.email.firstOrNull()?.uppercaseChar() ?: '?'
            Text(
                text = initial.toString(),
                style = typography.titleMedium,
                color = colors.primary
            )
        }
        Column {
            // Display display name or username or email
            Text(
                text = user.displayName ?: user.username ?: user.email,
                style = typography.bodyMedium,
                color = colors.onSurface
            )
            // Optionally show username if display name exists
            if (user.displayName != null && user.username != null) {
                Text(
                    text = "@${user.username}",
                    style = typography.bodySmall,
                    color = colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}