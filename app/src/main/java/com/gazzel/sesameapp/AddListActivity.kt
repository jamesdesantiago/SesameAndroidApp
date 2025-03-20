package com.gazzel.sesameapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AddListActivity : ComponentActivity() {
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
                AddListScreen(
                    onCancel = { finish() },
                    onCreateSuccess = { finish() }
                )
            }
        }
    }

    @Composable
    fun AddListScreen(
        onCancel: () -> Unit,
        onCreateSuccess: () -> Unit
    ) {
        val scope = rememberCoroutineScope()

        // State variables
        var listName by remember { mutableStateOf("") }
        var isPrivate by remember { mutableStateOf(false) }
        var collaboratorEmail by remember { mutableStateOf("") }
        val collaborators = remember { mutableStateListOf<String>() }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(text = "Create a New List", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // TextField for List Name
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("Name your list") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Switch for Private vs. Public
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (isPrivate) "Private" else "Public")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Collaborators Section
                Text(text = "Add collaborators", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                // Row of collaborator "chips"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    collaborators.forEach { email ->
                        CollaboratorChip(
                            email = email,
                            onRemove = {
                                collaborators.remove(email)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Field to add new collaborator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = collaboratorEmail,
                        onValueChange = { collaboratorEmail = it },
                        label = { Text("Collaborator email") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (collaboratorEmail.isNotBlank()) {
                                collaborators.add(collaboratorEmail)
                                collaboratorEmail = ""
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add collaborator")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons Row
                Row {
                    // Cancel Button
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Create Button
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val token = getValidToken()
                                    if (token != null) {
                                        val response = listService.createList(
                                            ListCreate(
                                                name = listName,
                                                description = null,
                                                isPrivate = isPrivate,
                                                collaborators = collaborators.toList()
                                            ),
                                            "Bearer $token"
                                        )
                                        if (response.isSuccessful) {
                                            // If successful, call onCreateSuccess
                                            onCreateSuccess()
                                        } else {
                                            errorMessage = "Failed to create list: ${response.code()}"
                                        }
                                    } else {
                                        errorMessage = "Not signed in"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Error creating list: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = listName.isNotBlank() && !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("Create")
                        }
                    }
                }

                // Error Message
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    /**
     * Simple chip composable for collaborator emails
     */
    @Composable
    fun CollaboratorChip(
        email: String,
        onRemove: () -> Unit
    ) {
        // Derive initials from the email
        val initials = email.split("@").firstOrNull()?.take(2)?.uppercase() ?: "??"

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = initials,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove collaborator",
                        tint = Color.White
                    )
                }
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
