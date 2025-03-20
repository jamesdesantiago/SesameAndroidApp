package com.gazzel.sesameapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// In-memory cache for the list of lists.
object ListCache {
    var cachedLists: List<ListResponse>? = null
}

class HomeActivity : ComponentActivity() {
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
        lifecycleScope.launch { refreshToken() }

        setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            // Load cached lists if available.
            ListCache.cachedLists?.let { cached ->
                listsState.clear()
                listsState.addAll(cached)
            }

            // Fetch updated lists.
            LaunchedEffect(Unit) {
                fetchLists(listsState, errorMessageState)
            }

            MaterialTheme {
                HomeScreen(
                    lists = listsState,
                    errorMessage = errorMessageState.value,
                    isLoading = isLoadingState,
                    onDeleteList = { listId ->
                        scope.launch { deleteList(listId, listsState, errorMessageState) }
                    },
                    onUpdateList = { listId, newName ->
                        scope.launch { updateListName(listId, newName, listsState, errorMessageState) }
                    },
                    onTogglePrivacy = { listId, newPrivacy ->
                        scope.launch { togglePrivacy(listId, newPrivacy, listsState, errorMessageState) }
                    },
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
                        finish()
                    }
                )
            }
        }
    }

    // Refresh lists every time HomeActivity resumes.
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
                        lists.clear()
                        lists.addAll(fetchedLists)
                        // Update cache.
                        ListCache.cachedLists = fetchedLists
                        Log.d("FastAPI", "Lists updated: ${lists.size}")
                    }
                } else {
                    Log.e("FastAPI", "GET failed: ${response.code()} - ${response.errorBody()?.string()}")
                    errorMessage.value = "Failed to load lists: ${response.code()}"
                }
            } catch (e: Exception) {
                Log.e("FastAPI", "GET exception: ${e.message}")
                errorMessage.value = "Error loading lists: ${e.message}"
            }
        } else {
            Log.w("FastAPI", "No token for GET")
            errorMessage.value = "Not signed in"
        }
    }

    private suspend fun deleteList(listId: Int, lists: MutableList<ListResponse>, errorMessage: MutableState<String?>) {
        val token = getValidToken()
        if (token != null) {
            try {
                Log.d("FastAPI", "Deleting list with id: $listId")
                val response = listService.deleteList(listId, "Bearer $token")
                if (response.isSuccessful) {
                    lists.removeAll { it.id == listId }
                    ListCache.cachedLists = lists.toList()
                    Log.d("FastAPI", "List deleted successfully")
                } else {
                    Log.e("FastAPI", "DELETE failed: ${response.code()} - ${response.errorBody()?.string()}")
                    errorMessage.value = "Failed to delete list: ${response.code()}"
                }
            } catch (e: Exception) {
                Log.e("FastAPI", "DELETE exception: ${e.message}")
                errorMessage.value = "Error deleting list: ${e.message}"
            }
        } else {
            errorMessage.value = "Not signed in"
        }
    }

    private suspend fun updateListName(
        listId: Int,
        newName: String,
        lists: MutableList<ListResponse>,
        errorMessage: MutableState<String?>
    ) {
        val index = lists.indexOfFirst { it.id == listId }
        if (index == -1) {
            errorMessage.value = "List not found"
            return
        }
        val originalList = lists[index]
        // Optimistically update UI.
        lists[index] = originalList.copy(name = newName)

        val token = getValidToken()
        if (token != null) {
            try {
                Log.d("FastAPI", "Updating list with id: $listId to new name: $newName")
                val response = listService.updateList(listId, ListUpdate(name = newName, isPrivate = null), "Bearer $token")
                if (response.isSuccessful) {
                    response.body()?.let { updatedList ->
                        lists[index] = updatedList
                        ListCache.cachedLists = lists.toList()
                        Log.d("FastAPI", "List updated successfully")
                    } ?: run {
                        errorMessage.value = "Update returned no data"
                        lists[index] = originalList
                    }
                } else {
                    Log.e("FastAPI", "UPDATE failed: ${response.code()} - ${response.errorBody()?.string()}")
                    errorMessage.value = "Failed to update list: ${response.code()}"
                    lists[index] = originalList
                }
            } catch (e: Exception) {
                Log.e("FastAPI", "UPDATE exception: ${e.message}")
                errorMessage.value = "Error updating list: ${e.message}"
                lists[index] = originalList
            }
        } else {
            errorMessage.value = "Not signed in"
            lists[index] = originalList
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
            null
        }
    }

    private suspend fun togglePrivacy(
        listId: Int,
        newPrivacy: Boolean,
        lists: MutableList<ListResponse>,
        errorMessage: MutableState<String?>
    ) {
        val index = lists.indexOfFirst { it.id == listId }
        if (index == -1) {
            errorMessage.value = "List not found"
            return
        }
        val originalList = lists[index]
        // Optimistically update UI.
        lists[index] = originalList.copy(
            isPrivate = newPrivacy,
            collaborators = originalList.collaborators ?: emptyList()
        )

        val token = getValidToken()
        if (token != null) {
            try {
                Log.d("FastAPI", "Toggling privacy for list id: $listId to new value: $newPrivacy")
                // Send current list name along with new privacy value.
                val response = listService.updateList(listId, ListUpdate(name = originalList.name, isPrivate = newPrivacy), "Bearer $token")
                if (response.isSuccessful) {
                    val updatedList = response.body()
                    // If updatedList?.isPrivate is null, fallback to newPrivacy.
                    val finalPrivacy = updatedList?.isPrivate ?: newPrivacy
                    lists[index] = updatedList?.copy(collaborators = updatedList.collaborators ?: emptyList())
                        ?: originalList.copy(isPrivate = finalPrivacy)
                    ListCache.cachedLists = lists.toList()
                    Log.d("FastAPI", "Privacy updated successfully: $finalPrivacy")
                } else {
                    Log.e("FastAPI", "Privacy update failed: ${response.code()} - ${response.errorBody()?.string()}")
                    errorMessage.value = "Failed to update privacy: ${response.code()}"
                    lists[index] = originalList
                }
            } catch (e: Exception) {
                Log.e("FastAPI", "Privacy update exception: ${e.message}")
                errorMessage.value = "Error updating privacy: ${e.message}"
                lists[index] = originalList
            }
        } else {
            errorMessage.value = "Not signed in"
            lists[index] = originalList
        }
    }
}
