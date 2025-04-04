package com.gazzel.sesameapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.gazzel.sesameapp.data.manager.PlaceUpdateManager
import com.gazzel.sesameapp.data.service.UserListService
import com.gazzel.sesameapp.domain.model.ListResponse
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ListCache {
    var cachedLists: List<ListResponse>? = null
}

class UserListsActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val listService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(750, TimeUnit.MILLISECONDS)
            .writeTimeout(500, TimeUnit.MILLISECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://gazzel.io/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserListService::class.java)
    }
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    private val listsState: MutableList<ListResponse> = mutableStateListOf()
    private val errorMessageState = mutableStateOf<String?>(null)
    private var isLoadingState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            Log.w("UserListsActivity", "User not signed in, redirecting to MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                refreshToken()
            } catch (e: Exception) {
                Log.e("UserListsActivity", "Failed to refresh token: ${e.message}")
                errorMessageState.value = "Failed to authenticate: ${e.message}"
            }
        }

        setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            ListCache.cachedLists?.let { cached ->
                try {
                    listsState.clear()
                    listsState.addAll(cached)
                } catch (e: Exception) {
                    Log.e("UserListsActivity", "Failed to load cached lists: ${e.message}")
                    ListCache.cachedLists = null
                }
            }

            LaunchedEffect(Unit) {
                fetchLists(listsState, errorMessageState)
            }

            SesameAppTheme {
                UserListsScreen(
                    lists = listsState,
                    errorMessage = errorMessageState.value,
                    isLoading = isLoadingState,
                    onOpenList = { list ->
                        context.startActivity(
                            Intent(context, ListDetailsActivity::class.java).apply {
                                putExtra("listId", list.id)
                                putExtra("listName", list.name)
                            }
                        )
                    },
                    onAddListClick = {
                        context.startActivity(Intent(context, CreateListActivity::class.java))
                    },
                    onSignOut = {
                        auth.signOut()
                        ListCache.cachedLists = null
                        finish()
                    },
                    onShareList = { list ->
                        Log.d("UserListsActivity", "Share list clicked for listId=${list.id}, listName=${list.name}")
                    },
                    onDeleteList = { listId: String ->
                        scope.launch {
                            val token = getValidToken()
                            if (token != null) {
                                try {
                                    val response = listService.deleteList(
                                        listId = listId,
                                        token = "Bearer $token"
                                    )
                                    if (response.isSuccessful) {
                                        Log.d("UserListsActivity", "List $listId deleted successfully")
                                        PlaceUpdateManager.notifyListDeleted()
                                        listsState.removeIf { it.id == listId }
                                        ListCache.cachedLists = listsState.toList()
                                    } else {
                                        Log.e("UserListsActivity", "Failed to delete list: ${response.code()} - ${response.errorBody()?.string()}")
                                        errorMessageState.value = "Failed to delete list: ${response.message()}"
                                    }
                                } catch (e: Exception) {
                                    Log.e("UserListsActivity", "Exception deleting list: ${e.message}", e)
                                    errorMessageState.value = "Failed to delete list: ${e.message}"
                                }
                            } else {
                                errorMessageState.value = "Not signed in"
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            fetchLists(listsState, errorMessageState)
        }
    }

    private suspend fun fetchLists(lists: MutableList<ListResponse>, errorMessage: MutableState<String?>) {
        val token = getValidToken()
        if (token != null) {
            Log.d("FastAPI", "Fetching lists with token: $token")
            try {
                val response = listService.getLists("Bearer $token")
                if (response.isSuccessful) {
                    val fetchedLists = response.body() ?: emptyList()
                    if (fetchedLists != lists.toList()) {
                        val newLists = fetchedLists.toMutableList()
                        lists.clear()
                        lists.addAll(newLists)
                        ListCache.cachedLists = newLists
                        Log.d("FastAPI", "Lists updated: ${lists.size}")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("FastAPI", "GET failed: ${response.code()} - $errorBody")
                    errorMessageState.value = "Failed to load lists: ${response.code()} - $errorBody"
                }
            } catch (e: Exception) {
                Log.e("FastAPI", "GET exception: ${e.message}", e)
                errorMessageState.value = "Error loading lists: ${e.message}"
            }
        } else {
            Log.w("FastAPI", "No token for GET")
            errorMessageState.value = "Not signed in"
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private suspend fun getValidToken(): String? {
        return if (cachedToken != null && System.currentTimeMillis() / 1000 < tokenExpiry - 300) {
            cachedToken
        } else {
            refreshToken()
        }
    }

    private suspend fun refreshToken(): String? {
        return try {
            val result = auth.currentUser?.getIdToken(true)?.await()
            cachedToken = result?.token
            tokenExpiry = result?.expirationTimestamp ?: 0
            cachedToken
        } catch (e: Exception) {
            Log.e("UserListsActivity", "Token refresh failed: ${e.message}")
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListsScreen(
    lists: List<ListResponse>,
    errorMessage: String?,
    isLoading: Boolean,
    onOpenList: (ListResponse) -> Unit,
    onAddListClick: () -> Unit,
    onSignOut: () -> Unit,
    onShareList: (ListResponse) -> Unit,
    onDeleteList: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Lists") },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text("Sign Out", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddListClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add List"
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(lists) { list ->
                        ListItem(
                            headlineContent = { Text(list.name) },
                            supportingContent = {
                                Text(
                                    "${list.places?.size ?: 0} places"
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onShareList(list) }) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share List"
                                        )
                                    }
                                    IconButton(onClick = { onDeleteList(list.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete List"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onOpenList(list) }
                        )
                    }
                }
            }

            errorMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(message)
                }
            }
        }
    }
} 