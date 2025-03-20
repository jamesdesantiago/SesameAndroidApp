package com.gazzel.sesameapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import com.gazzel.sesameapp.ListResponse
import com.gazzel.sesameapp.ListCreate
import com.gazzel.sesameapp.ListUpdate

// In-memory cache for list details.
object ListDetailCache {
    val cache = mutableMapOf<Int, ListResponse>()
}

class ListDetailActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val listService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(750, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContent {
            MaterialTheme {
                // Retrieve the list ID and initial list name passed from HomeActivity.
                val listId = intent.getIntExtra("listId", -1)
                val initialName = intent.getStringExtra("listName") ?: "List Details"
                DetailScreen(
                    listId = listId,
                    initialName = initialName,
                    listService = listService,
                    getValidToken = { getValidToken() }
                )
            }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    listId: Int,
    initialName: String,
    listService: ListService,
    getValidToken: suspend () -> String?
) {
    // Initialize detail from cache if available; otherwise use the initial data.
    var detail by remember {
        mutableStateOf(
            ListDetailCache.cache[listId] ?: ListResponse(
                id = listId,
                name = initialName,
                description = null,
                isPrivate = true,
                collaborators = emptyList()
            )
        )
    }

    // Fetch updated details from the backend.
    LaunchedEffect(listId) {
        val token = getValidToken()
        if (token != null) {
            val response = listService.getListDetail(listId, "Bearer $token")
            if (response.isSuccessful) {
                val fetchedDetail = response.body()
                if (fetchedDetail != null && fetchedDetail != detail) {
                    detail = fetchedDetail
                    ListDetailCache.cache[listId] = fetchedDetail
                }
            }
        }
    }

    // Get the back dispatcher to handle the back button.
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = detail.name ?: "Unnamed List") },
                navigationIcon = {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = "List Details for list ID: $listId\nName: ${detail.name ?: "Unnamed List"}",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
