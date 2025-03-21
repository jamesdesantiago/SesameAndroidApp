package com.gazzel.sesameapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.gazzel.sesameapp.ui.theme.SesameAppTheme // Import your theme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AddListActivity : ComponentActivity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            SesameAppTheme { // Wrap with your custom theme
                AddListScreen(
                    onCancel = { finish() },
                    onCreateSuccess = { listId, listName ->
                        val intent = Intent(this, PlacesSearchActivity::class.java).apply {
                            putExtra("listId", listId)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    @Composable
    fun AddListScreen(
        onCancel: () -> Unit,
        onCreateSuccess: (Int, String) -> Unit
    ) {
        val scope = rememberCoroutineScope()
        var listName by remember { mutableStateOf("") }
        var isPrivate by remember { mutableStateOf(false) }
        var collaboratorEmail by remember { mutableStateOf("") }
        val collaborators = remember { mutableStateListOf<String>() }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background // F2F2F7 (light) or 1C1C1E (dark)
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.surface, // White (light) or 2C2C2E (dark)
                shape = MaterialTheme.shapes.large // 16.dp rounded corners
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp), // Slightly larger padding for iOS feel
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Text(
                        text = "Create a New List",
                        style = MaterialTheme.typography.titleLarge, // 22.sp, bold
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(28.dp)) // More breathing room

                    // List Name and Privacy Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), // Slightly higher for depth
                        shape = MaterialTheme.shapes.medium // 12.dp rounded corners
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = listName,
                                onValueChange = { listName = it },
                                label = { Text("Name your list") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary, // System Blue
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline, // C6C6C8 (light) or 38383A (dark)
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = MaterialTheme.shapes.small // 8.dp rounded corners
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isPrivate) "Private" else "Public",
                                    style = MaterialTheme.typography.bodyLarge, // 17.sp
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

                    // Collaborators Section
                    Text(
                        text = "Add Collaborators",
                        style = MaterialTheme.typography.titleMedium, // 20.sp, bold
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Collaborator Chips
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

                    // Add Collaborator Input
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
                                focusedBorderColor = MaterialTheme.colorScheme.secondary, // Secondary Label
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
                                .background(MaterialTheme.colorScheme.primary) // System Blue
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add collaborator",
                                tint = MaterialTheme.colorScheme.onPrimary // White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp) // Taller buttons for iOS feel
                                .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge) // 17.sp, medium
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        val token = getValidToken()
                                        if (token != null) {
                                            Log.d("FastAPI", "Creating list: $listName, isPrivate: $isPrivate")
                                            val createResponse = listService.createList(
                                                ListCreate(
                                                    name = listName,
                                                    description = null,
                                                    isPrivate = isPrivate
                                                ),
                                                "Bearer $token"
                                            )
                                            if (createResponse.isSuccessful) {
                                                val newList = createResponse.body()
                                                if (newList != null) {
                                                    if (collaborators.isNotEmpty()) {
                                                        try {
                                                            Log.d("FastAPI", "Adding ${collaborators.size} collaborators to list ${newList.id}")
                                                            val collaboratorResponse = listService.addCollaboratorsBatch(
                                                                newList.id,
                                                                collaborators.map { CollaboratorAdd(email = it) },
                                                                "Bearer $token"
                                                            )
                                                            if (!collaboratorResponse.isSuccessful) {
                                                                Log.e("FastAPI", "Failed to add collaborators: ${collaboratorResponse.code()} - ${collaboratorResponse.errorBody()?.string()}")
                                                                errorMessage = "Failed to add collaborators: ${collaboratorResponse.code()}"
                                                                return@launch
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("FastAPI", "Exception adding collaborators: ${e.message}", e)
                                                            errorMessage = "Error adding collaborators: ${e.message}"
                                                            return@launch
                                                        }
                                                    }
                                                    Log.d("FastAPI", "List created successfully: ${newList.id}")
                                                    onCreateSuccess(newList.id, newList.name)
                                                } else {
                                                    Log.e("FastAPI", "Create list response returned no data")
                                                    errorMessage = "Failed to create list: No data returned"
                                                }
                                            } else {
                                                Log.e("FastAPI", "Create list failed: ${createResponse.code()} - ${createResponse.errorBody()?.string()}")
                                                errorMessage = "Failed to create list: ${createResponse.code()}"
                                            }
                                        } else {
                                            Log.w("FastAPI", "No token available")
                                            errorMessage = "Not signed in"
                                        }
                                    } catch (e: Exception) {
                                        Log.e("FastAPI", "Exception creating list: ${e.message}", e)
                                        errorMessage = "Error creating list: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = listName.isNotBlank() && !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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

                    // Error Message
                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium, // 15.sp
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f)) // Push content up, leaving space at bottom
                }
            }
        }
    }

    @Composable
    fun CollaboratorChip(
        email: String,
        onRemove: () -> Unit
    ) {
        val initials = email.split("@").firstOrNull()?.take(2)?.uppercase() ?: "??"

        Surface(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small) // 8.dp rounded corners
                .clickable(onClick = onRemove),
            color = MaterialTheme.colorScheme.secondary, // Secondary Label
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = initials,
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.bodyMedium, // 15.sp
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove collaborator",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(18.dp)
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
            Log.d("FastAPI", "Refreshed token: $cachedToken, expires: $tokenExpiry")
            cachedToken
        } catch (e: Exception) {
            Log.e("FastAPI", "Token refresh failed: ${e.message}", e)
            null
        }
    }
}