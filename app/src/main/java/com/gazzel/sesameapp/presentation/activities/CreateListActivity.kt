package com.gazzel.sesameapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.gazzel.sesameapp.domain.model.ListCreate
import com.gazzel.sesameapp.data.service.UserListService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class CreateListActivity : ComponentActivity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            SesameAppTheme {
                CreateListScreen(
                    onCancel = { finish() },
                    onCreateSuccess = { listId, listName ->
                        val intent = Intent(this, PlacesSearchActivity::class.java).apply {
                            putExtra("listId", listId) // Now passing String
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    @Composable
    fun CreateListScreen(
        onCancel: () -> Unit,
        onCreateSuccess: (String, String) -> Unit // Changed listId to String
    ) {
        val scope = rememberCoroutineScope()
        var listName by remember { mutableStateOf("") }
        var isPrivate by remember { mutableStateOf(false) }
        var collaboratorEmail by remember { mutableStateOf("") }
        val collaborators = remember { mutableStateListOf<String>() }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create a New List",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(28.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = listName,
                                onValueChange = { listName = it },
                                label = { Text("Name your list") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = MaterialTheme.shapes.small
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isPrivate) "Private" else "Public",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Switch(
                                    checked = isPrivate,
                                    onCheckedChange = { isPrivate = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Add Collaborators",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (collaborators.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            collaborators.forEach { email ->
                                CollaboratorChip(
                                    email = email,
                                    onRemove = { collaborators.remove(email) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = collaboratorEmail,
                            onValueChange = { collaboratorEmail = it },
                            label = { Text("Collaborator email") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.secondary,
                                focusedLabelColor = MaterialTheme.colorScheme.secondary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = MaterialTheme.shapes.small
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = {
                                if (collaboratorEmail.isNotBlank()) {
                                    collaborators.add(collaboratorEmail.trim())
                                    collaboratorEmail = ""
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add collaborator",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        isLoading = true
                                        val token = getAuthToken()
                                        val request = ListCreate(
                                            name = listName,
                                            isPrivate = isPrivate,
                                            collaborators = collaborators.toList()
                                        )
                                        val response = listService.createList(
                                            list = request,
                                            token = "Bearer $token"
                                        )
                                        if (response.isSuccessful) {
                                            response.body()?.let { listResponse -> // Use safe call and let
                                                onCreateSuccess(listResponse.id, listResponse.name)
                                            } ?: run {
                                                // Handle case where body is unexpectedly null even on success
                                                errorMessage = "List created successfully, but no response data received."
                                                Log.e("CreateListActivity", "Successful response but empty body")
                                            }
                                        } else {
                                            errorMessage = "Failed to create list: ${response.message()}"
                                            Log.e("CreateListActivity", "Error: ${response.errorBody()?.string()}")
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                        Log.e("CreateListActivity", "Exception creating list", e)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            enabled = listName.isNotBlank() && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Create", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun CollaboratorChip(
        email: String,
        onRemove: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove collaborator",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    private suspend fun getAuthToken(): String {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken!!
        }

        val user = auth.currentUser ?: throw Exception("User not authenticated")
        val tokenResult = user.getIdToken(false).await()
        val token = tokenResult.token ?: throw Exception("Failed to retrieve auth token")
        cachedToken = token
        tokenExpiry = System.currentTimeMillis() + 3600000 // Token expires in 1 hour
        return token
    }
}