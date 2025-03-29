package com.gazzel.sesameapp.presentation.activities

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.gazzel.sesameapp.presentation.viewmodels.ListDetailsViewModel
import com.gazzel.sesameapp.presentation.viewmodels.ListDetailsViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// In-memory cache for list details
object ListDetailCache {
    val cache = mutableMapOf<String, ListResponse>()
}

class ListDetailsActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    private val listService by lazy {
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
            .create(ListService::class.java)
    }

    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            Log.e("ListDetails", "User not logged in, redirecting to login")
            // TODO: Redirect to login screen
            finish()
            return
        }

        setContent {
            SesameAppTheme {
                val listId = intent.getStringExtra("listId") ?: ""
                val initialName = intent.getStringExtra("listName") ?: "List Details"

                val viewModel: ListDetailsViewModel by viewModels {
                    ListDetailsViewModelFactory(listId, initialName, listService) { getValidToken() }
                }

                DetailScreen(viewModel)
            }
        }
    }

    private suspend fun getValidToken(): String? {
        if (
            cachedToken != null &&
            System.currentTimeMillis() / 1000 < tokenExpiry - 60
        ) {
            Log.d("ListDetails", "Using cached token: $cachedToken, expires at $tokenExpiry")
            return cachedToken
        } else {
            return refreshToken()
        }
    }

    private suspend fun refreshToken(): String? {
        return try {
            if (auth.currentUser == null) {
                Log.e("ListDetails", "No current user logged in")
                return null
            }
            val result = auth.currentUser?.getIdToken(true)?.await()
            cachedToken = result?.token
            tokenExpiry = result?.expirationTimestamp ?: 0
            Log.d("ListDetails", "Refreshed token: $cachedToken, expires at $tokenExpiry")
            cachedToken
        } catch (e: Exception) {
            Log.e("ListDetails", "Failed to refresh token", e)
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: ListDetailsViewModel) {
    val detail by viewModel.detail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // State for the dropdown menu (list-level)
    var menuExpanded by remember { mutableStateOf(false) }

    // State for the edit list name dialog
    var showEditDialog by remember { mutableStateOf(false) }

    // State for the delete confirmation dialog
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // State for the delete success dialog
    var showDeleteSuccessDialog by remember { mutableStateOf(false) }

    // State for the place-level dropdown menu
    var placeMenuExpanded by rememberSaveable { mutableStateOf<String?>(null) }

    // State for viewing a note
    var viewingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    // State for the notes editor
    var editingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNote by rememberSaveable { mutableStateOf("") }
    // State for the place sharing overlay
    var sharingPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    // State for the list sharing overlay
    var sharingList by rememberSaveable { mutableStateOf(false) }

    // State for the selected tab
    var selectedTab by remember { mutableStateOf("List") }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(detail.places) {
        Log.d("ListDetails", "Places updated: ${detail.places?.size ?: 0} places - ${detail.places}")
        val firstPlace = detail.places?.firstOrNull()
        if (firstPlace != null) {
            Log.d("ListDetails", "Setting map to: ${firstPlace.name}, lat=${firstPlace.latitude}, lng=${firstPlace.longitude}")
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(firstPlace.latitude, firstPlace.longitude),
                12f
            )
        } else {
            Log.d("ListDetails", "No places to set map position")
        }
    }

    LaunchedEffect(showDeleteSuccessDialog) {
        if (showDeleteSuccessDialog) {
            delay(2000)
            backDispatcher?.onBackPressed()
        }
    }

    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column {
                ListDetailTopBar(
                    listName = detail.name,
                    onBackClick = { backDispatcher?.onBackPressed() },
                    onAddCollaboratorClick = {
                        // Placeholder for adding collaborators
                        Log.d("ListDetails", "Add collaborator clicked")
                    },
                    onShareListClick = {
                        sharingList = true
                    },
                    onMoreClick = { menuExpanded = true }
                )
                ListMapTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                ListDetailMainContent(
                    places = detail.places ?: emptyList(),
                    query = query,
                    onQueryChange = { query = it },
                    selectedTab = selectedTab,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    cameraPositionState = cameraPositionState,
                    onMoreClick = { placeMenuExpanded = it }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (menuExpanded) {
                    ListDropdownMenu(
                        isPrivate = detail.isPrivate,
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onPrivacyToggle = { viewModel.updateListPrivacy() },
                        onEditListName = { showEditDialog = true },
                        onMergeList = { /* TODO: Implement merge list */ },
                        onDeleteList = { showDeleteConfirmDialog = true }
                    )
                }

                if (placeMenuExpanded != null && editingNotesPlaceId == null && viewingNotesPlaceId == null) {
                    PlaceDropdownMenu(
                        placeId = placeMenuExpanded!!,
                        expanded = true,
                        onDismiss = { placeMenuExpanded = null },
                        onShare = { sharingPlaceId = it },
                        onTags = { /* TODO: Implement tags */ },
                        onNotes = { placeId ->
                            val place = detail.places?.find { it.id == placeId }
                            currentNote = place?.notes ?: ""
                            viewingNotesPlaceId = placeId
                        },
                        onAddToList = { /* TODO: Implement add to list */ },
                        onDeleteItem = { /* TODO: Implement delete item */ }
                    )
                }

                if (viewingNotesPlaceId != null) {
                    NoteViewer(
                        note = currentNote,
                        onClose = { viewingNotesPlaceId = null },
                        onEdit = {
                            editingNotesPlaceId = viewingNotesPlaceId
                            viewingNotesPlaceId = null
                        }
                    )
                }

                if (editingNotesPlaceId != null) {
                    NoteEditor(
                        note = currentNote,
                        onNoteChange = { currentNote = it },
                        onSave = {
                            viewModel.updatePlaceNotes(editingNotesPlaceId!!, currentNote)
                            editingNotesPlaceId = null
                        },
                        onCancel = { editingNotesPlaceId = null }
                    )
                }

                if (sharingPlaceId != null) {
                    PlaceSharingOverlay(
                        placeId = sharingPlaceId!!,
                        onClose = { sharingPlaceId = null }
                    )
                }

                if (sharingList) {
                    ListSharingOverlay(
                        listId = detail.id,
                        onClose = { sharingList = false }
                    )
                }

                if (showEditDialog) {
                    EditListNameDialog(
                        currentName = detail.name,
                        onNameChange = { viewModel.updateListName(it) },
                        onDismiss = { showEditDialog = false }
                    )
                }

                if (showDeleteConfirmDialog) {
                    DeleteListConfirmationDialog(
                        onConfirm = {
                            viewModel.deleteList()
                            showDeleteConfirmDialog = false
                            showDeleteSuccessDialog = true
                        },
                        onDismiss = { showDeleteConfirmDialog = false }
                    )
                }

                if (showDeleteSuccessDialog) {
                    DeleteListSuccessDialog()
                }
            }
        }
    }
} 