package com.gazzel.sesameapp.presentation.activities

// --- Android & System Imports ---
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// --- Google Maps Compose Imports --- VERIFY THESE ARE CORRECT ---
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState // Specific import
import com.google.maps.android.compose.GoogleMap // Specific import
import com.google.maps.android.compose.MapProperties // Specific import
import com.google.maps.android.compose.MapUiSettings // Specific import
import com.google.maps.android.compose.Marker // Specific import
import com.google.maps.android.compose.MarkerState // Specific import
import com.google.maps.android.compose.rememberCameraPositionState // Specific import

// --- Firebase Auth ---
import com.google.firebase.auth.FirebaseAuth

// --- Your Project Imports --- VERIFY ALL PATHS AND FILE/CLASS NAMES ---
import com.gazzel.sesameapp.R // If needed
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.gazzel.sesameapp.presentation.viewmodels.ListDetailsViewModel
import com.gazzel.sesameapp.presentation.viewmodels.ListDetailsViewModelFactory
import com.gazzel.sesameapp.domain.model.ListResponse
import com.gazzel.sesameapp.data.service.UserListService
import com.gazzel.sesameapp.presentation.components.listdetail.ListDetailTopBar
import com.gazzel.sesameapp.presentation.components.listdetail.ListMapTabs
import com.gazzel.sesameapp.presentation.components.listdetail.ListDetailMainContent
import com.gazzel.sesameapp.presentation.components.listdetail.ListDropdownMenu
import com.gazzel.sesameapp.presentation.components.listdetail.PlaceDropdownMenu
import com.gazzel.sesameapp.presentation.components.listdetail.NoteViewer
import com.gazzel.sesameapp.presentation.components.listdetail.NoteEditor
import com.gazzel.sesameapp.presentation.components.listdetail.SharePlaceOverlay
import com.gazzel.sesameapp.presentation.components.listdetail.ShareListOverlay
import com.gazzel.sesameapp.presentation.components.listdetail.EditListNameDialog
import com.gazzel.sesameapp.presentation.components.listdetail.DeleteConfirmDialog
import com.gazzel.sesameapp.presentation.components.listdetail.DeleteSuccessDialog
import com.gazzel.sesameapp.data.model.PlaceDto // For mapping
import com.gazzel.sesameapp.domain.model.PlaceItem

// --- Networking & Coroutines ---
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// --- Mapper Function (if doing UI workaround) ---
private fun PlaceDto.toPlaceItem(listId: String): PlaceItem {
    return PlaceItem(
        id = this.id,
        name = this.name,
        description = this.description ?: "",
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude,
        listId = listId,
        notes = null, // Default notes for mapping DTO -> Item
        rating = this.rating,
        visitStatus = null
    )
}

class ListDetailsActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    // --- Direct Initialization (or keep lazy if preferred, but ensure correctness) ---
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gazzel.io/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Use UserListService and correct initialization
    private val userListService: UserListService by lazy {
        retrofit.create(UserListService::class.java)
    }
    // --- End Initialization ---


    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0 // Correct type: Long

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            Log.e("ListDetails", "User not logged in, redirecting to login")
            // TODO: Redirect to login screen properly
            finish()
            return
        }

        // --- ViewModel Initialization ---
        val listId = intent.getStringExtra("listId") ?: ""
        val initialName = intent.getStringExtra("listName") ?: "List Details"

        // Ensure factory expects UserListService and pass the correct instance
        val viewModel: ListDetailsViewModel by viewModels {
            ListDetailsViewModelFactory(
                listId = listId,
                initialName = initialName,
                listService = userListService,
                getValidToken = ::getValidToken // Pass reference with matching name
            )
        }
        // --- End ViewModel Initialization ---


        setContent {
            SesameAppTheme {
                // Pass the initialized viewModel
                DetailScreen(viewModel = viewModel)
            }
        }
    }

    // getValidToken and refreshToken methods remain the same
    private suspend fun getValidToken(): String? {
        if (cachedToken != null && System.currentTimeMillis() / 1000 < tokenExpiry - 60) {
            // Log.d("ListDetails", "Using cached token")
            return cachedToken
        } else {
            return refreshToken()
        }
    }

    private suspend fun refreshToken(): String? {
        return try {
            val user = auth.currentUser
            if (user == null) {
                Log.e("ListDetails", "No current user logged in for refresh")
                return null
            }
            val result = user.getIdToken(true).await()
            cachedToken = result.token
            tokenExpiry = result.expirationTimestamp ?: 0
            // Log.d("ListDetails", "Refreshed token expires at $tokenExpiry")
            cachedToken
        } catch (e: Exception) {
            Log.e("ListDetails", "Failed to refresh token", e)
            null // Return null on failure
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Pass ViewModel instance
fun DetailScreen(viewModel: ListDetailsViewModel) {
    // Observe state from ViewModel
    val detail by viewModel.detail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // State for UI elements controlled here
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteSuccessDialog by remember { mutableStateOf(false) }
    var placeMenuExpanded by rememberSaveable { mutableStateOf<String?>(null) } // Key by Place ID (String)
    var viewingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingNotesPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentNote by rememberSaveable { mutableStateOf("") }
    var sharingPlaceId by rememberSaveable { mutableStateOf<String?>(null) } // Use Place ID (String)
    var sharingList by rememberSaveable { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("List") } // Default tab

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // Remember camera position state
    val cameraPositionState: CameraPositionState = rememberCameraPositionState()

    // Update map camera when places are loaded
    LaunchedEffect(detail.places) {
        Log.d("ListDetails", "Places updated: ${detail.places?.size ?: 0} places - ${detail.places}")
        // Use PlaceItem properties (latitude, longitude)
        val firstPlaceItem = detail.places?.firstOrNull()
        if (firstPlaceItem != null) {
            val position = LatLng(firstPlaceItem.latitude, firstPlaceItem.longitude)
            Log.d("ListDetails", "Setting map to: ${firstPlaceItem.name}, lat=${position.latitude}, lng=${position.longitude}")
            // Animate camera for smoother transition
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(position, 12f), // Zoom level
                durationMs = 1000 // Animation duration
            )
        } else {
            Log.d("ListDetails", "No places to set map position")
        }
    }

    // Navigate back after delete success message
    LaunchedEffect(showDeleteSuccessDialog) {
        if (showDeleteSuccessDialog) {
            DeleteSuccessDialog(
                listName = detail.name,
                onDismiss = { showDeleteSuccessDialog = false },
                onSuccess = {} // <-- ADD this empty lambda
            )
        }
    }

    var query by remember { mutableStateOf("") } // Search query state

    Scaffold(
        topBar = {
            Column { // Combine TopAppBar and Tabs
                ListDetailTopBar( // Use imported composable
                    listName = detail.name, // Use name from state
                    onBackClick = { backDispatcher?.onBackPressed() },
                    onAddCollaboratorClick = { /* TODO */ Log.d("ListDetails", "Add collaborator") },
                    onShareListClick = { sharingList = true }, // Trigger list share overlay
                    onMoreClick = { menuExpanded = true } // Open list dropdown
                )
                ListMapTabs( // Use imported composable
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box( // Use Box to allow overlays on top of content
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Content Area (List or Map)
            ListDetailMainContent( // Use imported composable
                // Pass places from state (should be List<PlaceItem>?)
                places = detail.places?.map { dto -> dto.toPlaceItem(detail.id) } ?: emptyList(),
                query = query,
                onQueryChange = { query = it },
                selectedTab = selectedTab,
                isLoading = isLoading,
                errorMessage = errorMessage,
                cameraPositionState = cameraPositionState,
                // Pass place ID (String) to onMoreClick
                onMoreClick: (placeId: String) -> Unit // Correct lambda
            )

            // --- Overlays and Dialogs ---
            // Position Box fills the screen behind dialogs, allowing dropdowns to anchor correctly
            Box(
                modifier = Modifier
                    .fillMaxSize()
                // .padding(16.dp) // Padding might interfere with dropdown positioning
                // contentAlignment = Alignment.BottomCenter // Alignment might not be needed if Box fills size
            ) {
                // List-level dropdown menu
                if (menuExpanded) {
                    ListDropdownMenu( // Use imported composable
                        isPrivate = detail.isPrivate, // Use privacy status from state
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onPrivacyToggle = { viewModel.updateListPrivacy() }, // Call ViewModel function
                        onEditListName = { showEditDialog = true },
                        onMergeList = { /* TODO */ },
                        onDeleteList = { showDeleteConfirmDialog = true }
                    )
                }

                // Place-level dropdown menu
                // Ensure placeMenuExpanded is not null and other overlays are hidden
                if (placeMenuExpanded != null && editingNotesPlaceId == null && viewingNotesPlaceId == null) {
                    PlaceDropdownMenu( // Use imported composable
                        placeId = placeMenuExpanded!!, // Pass the non-null place ID
                        expanded = true, // Controlled by the if condition
                        onDismiss = { placeMenuExpanded = null },
                        onShare = { placeId: String -> sharingPlaceId = placeId }, // Correct lambda: Set state for place share overlay
                        onTags = { /* TODO */ },
                        // Update lambda parameter type and logic
                        onNotes = { placeId: String -> // Explicitly String
                            // Find the correct PlaceItem
                            val placeItem = detail.places?.find { it.id == placeId }
                            currentNote = placeItem?.notes ?: "" // Get notes from PlaceItem
                            viewingNotesPlaceId = placeId // Set state to show note viewer
                        },
                        onAddToList = { /* TODO */ },
                        onDeleteItem = { /* TODO: Call ViewModel delete place method */ }
                    )
                }

                // Note Viewer Overlay
                if (viewingNotesPlaceId != null) {
                    NoteViewer( // Use imported composable
                        note = currentNote,
                        onClose = { viewingNotesPlaceId = null },
                        onEdit = {
                            editingNotesPlaceId = viewingNotesPlaceId // Switch to editor mode
                            viewingNotesPlaceId = null
                        }
                    )
                }

                // Note Editor Overlay
                if (editingNotesPlaceId != null) {
                    NoteEditor( // Use imported composable
                        note = currentNote,
                        onNoteChange = { currentNote = it },
                        onSave = {
                            viewModel.updatePlaceNotes(editingNotesPlaceId!!, currentNote) // Call ViewModel
                            editingNotesPlaceId = null // Close editor
                        },
                        onCancel = { editingNotesPlaceId = null } // Close editor
                    )
                }

                // Place Sharing Overlay
                if (sharingPlaceId != null) {
                    val placeDtoToShare = detail.places?.find { it.id == sharingPlaceId }
                    val placeItemToShare = placeDtoToShare?.toPlaceItem(detail.id) // Define AND map
                    SharePlaceOverlay(
                        listId = detail.id,
                        place = placeItemToShare, // Pass mapped PlaceItem?
                        onDismiss = { sharingPlaceId = null }
                    )
                }

                // List Sharing Overlay
                if (sharingList) {
                    ShareListOverlay( // Use imported composable (check name)
                        listId = detail.id,
                        listName = detail.name,
                        onDismiss = { sharingList = false } // Close overlay
                    )
                }

                // Dialogs
                if (showEditDialog) {
                    EditListNameDialog( // Use imported composable
                        currentName = detail.name,
                        onSave = { newName ->
                            viewModel.updateListName(newName) // Call ViewModel function
                            showEditDialog = false // Close dialog on save
                        },
                        onDismiss = { showEditDialog = false }
                    )
                }

                if (showDeleteConfirmDialog) {
                    DeleteConfirmDialog( // Use CORRECT imported composable name
                        onConfirm = {
                            viewModel.deleteList() // Call ViewModel function
                            showDeleteConfirmDialog = false
                            showDeleteSuccessDialog = true // Trigger success message
                        },
                        onDismiss = { showDeleteConfirmDialog = false }
                    )
                }

                if (showDeleteSuccessDialog) {
                    DeleteSuccessDialog(
                        listName = detail.name,
                        onDismiss = { showDeleteSuccessDialog = false }
                        // Remove onSuccess = {} if the definition doesn't have it
                    )
                }
            }
        }
    }
}