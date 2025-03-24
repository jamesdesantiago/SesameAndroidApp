package com.gazzel.sesameapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth

// In-memory cache for the list of lists.
object ListCache {
    var cachedLists: List<ListResponse>? = null
}

class ListActivity : ComponentActivity() {
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
            .create(ListService::class.java)
    }
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    // Lift state outside setContent so that onResume can refresh it.
    private val listsState: MutableList<ListResponse> = mutableStateListOf()
    private val errorMessageState = mutableStateOf<String?>(null)
    private var isLoadingState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Check if user is signed in
        if (auth.currentUser == null) {
            Log.w("ListActivity", "User not signed in, redirecting to MainActivity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Refresh token
        lifecycleScope.launch {
            try {
                refreshToken()
            } catch (e: Exception) {
                Log.e("ListActivity", "Failed to refresh token: ${e.message}")
                errorMessageState.value = "Failed to authenticate: ${e.message}"
            }
        }

        setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            // Load cached lists if available.
            ListCache.cachedLists?.let { cached ->
                try {
                    listsState.clear()
                    listsState.addAll(cached)
                } catch (e: Exception) {
                    Log.e("ListActivity", "Failed to load cached lists: ${e.message}")
                    ListCache.cachedLists = null // Clear invalid cache
                }
            }

            // Fetch updated lists when this composable first launches.
            LaunchedEffect(Unit) {
                fetchLists(listsState, errorMessageState)
            }

            // Wrap the ListScreen in your Compose theme function
            SesameAppTheme {
                ListScreen(
                    lists = listsState,
                    errorMessage = errorMessageState.value,
                    isLoading = isLoadingState,
                    onOpenList = { list ->
                        context.startActivity(
                            Intent(context, ListDetailActivity::class.java).apply {
                                putExtra("listId", list.id)
                                putExtra("listName", list.name)
                            }
                        )
                    },
                    onAddListClick = {
                        context.startActivity(Intent(context, AddListActivity::class.java))
                    },
                    onSignOut = {
                        auth.signOut()
                        ListCache.cachedLists = null // Clear the cache on sign-out
                        finish()
                    },
                    onShareList = { list ->
                        Log.d("ListActivity", "Share list clicked for listId=${list.id}, listName=${list.name}")
                    }
                )
            }
        }
    }

    // Refresh lists every time ListActivity resumes.
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
                    errorMessage.value = "Failed to load lists: ${response.code()} - $errorBody"
                }
            } catch (e: Exception) {
                Log.e("FastAPI", "GET exception: ${e.message}", e)
                errorMessage.value = "Error loading lists: ${e.message}"
            }
        } else {
            Log.w("FastAPI", "No token for GET")
            errorMessage.value = "Not signed in"
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
            Log.e("ListActivity", "Token refresh failed: ${e.message}")
            null
        }
    }
}