package com.gazzel.sesameapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.util.Log
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

// In-memory cache for list details
object ListDetailCache {
    val cache = mutableMapOf<Int, ListResponse>()
}

class ListDetailActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    private val listService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .sslSocketFactory(getUnsafeSSLSocketFactory(), getUnsafeTrustManager())
            .hostnameVerifier { _, _ -> true }
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

    private fun getUnsafeSSLSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(getUnsafeTrustManager())
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext.socketFactory
    }

    private fun getUnsafeTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            Log.e("ListDetail", "User not logged in, redirecting to login")
            // TODO: Redirect to login screen
            finish()
            return
        }

        setContent {
            SesameAppTheme {
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
        if (
            cachedToken != null &&
            System.currentTimeMillis() / 1000 < tokenExpiry - 60
        ) {
            Log.d("ListDetail", "Using cached token: $cachedToken, expires at $tokenExpiry")
            return cachedToken
        } else {
            return refreshToken()
        }
    }

    private suspend fun refreshToken(): String? {
        return try {
            if (auth.currentUser == null) {
                Log.e("ListDetail", "No current user logged in")
                return null
            }
            val result = auth.currentUser?.getIdToken(true)?.await()
            cachedToken = result?.token
            tokenExpiry = result?.expirationTimestamp ?: 0
            Log.d("ListDetail", "Refreshed token: $cachedToken, expires at $tokenExpiry")
            cachedToken
        } catch (e: Exception) {
            Log.e("ListDetail", "Failed to refresh token", e)
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
    Log.d("ListDetail", "DetailScreen recomposed")
    var detail by remember {
        mutableStateOf(
            ListDetailCache.cache[listId] ?: ListResponse(
                id = listId,
                name = initialName,
                description = null,
                isPrivate = false,
                collaborators = emptyList(),
                places = emptyList()
            )
        )
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // State for the dropdown menu (list-level)
    var menuExpanded by remember { mutableStateOf(false) }

    // State for the edit list name dialog
    var showEditDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf(detail.name) }

    // State for the delete confirmation dialog
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // State for the delete success dialog
    var showDeleteSuccessDialog by remember { mutableStateOf(false) }

    // State for the place-level dropdown menu
    var placeMenuExpanded by rememberSaveable { mutableStateOf<Int?>(null) }

    // State for viewing a note
    var viewingNotesPlaceId by rememberSaveable { mutableStateOf<Int?>(null) }
    // State for the notes editor
    var editingNotesPlaceId by rememberSaveable { mutableStateOf<Int?>(null) }
    var currentNote by rememberSaveable { mutableStateOf("") }
    // State for the place sharing overlay
    var sharingPlaceId by rememberSaveable { mutableStateOf<Int?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(listId) {
        isLoading = true
        errorMessage = null
        val token = getValidToken()
        if (token != null) {
            try {
                val response = listService.getListDetail(listId, "Bearer $token")
                Log.d("ListDetail", "API Response: ${response.code()} - ${response.body()}")
                if (response.isSuccessful) {
                    val fetchedDetail = response.body()
                    if (fetchedDetail != null) {
                        detail = fetchedDetail
                        ListDetailCache.cache[listId] = fetchedDetail
                        Log.d("ListDetail", "Fetched Detail: $fetchedDetail")
                    } else {
                        errorMessage = "No data returned from server"
                    }
                } else {
                    errorMessage = "Failed to fetch details: ${response.message()}"
                    Log.e("ListDetail", "Error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                errorMessage = "Exception: ${e.message}"
                Log.e("ListDetail", "Exception: ${e.stackTraceToString()}")
            }
        } else {
            errorMessage = "Missing token"
        }
        isLoading = false
    }

    var query by remember { mutableStateOf("") }
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(detail.places) {
        Log.d("ListDetail", "Places updated: ${detail.places?.size ?: 0} places - ${detail.places}")
        val firstPlace = detail.places?.firstOrNull()
        if (firstPlace != null) {
            Log.d("ListDetail", "Setting map to: ${firstPlace.name}, lat=${firstPlace.latitude}, lng=${firstPlace.longitude}")
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(firstPlace.latitude, firstPlace.longitude),
                12f
            )
        } else {
            Log.d("ListDetail", "No places to set map position")
        }
    }

    LaunchedEffect(showDeleteSuccessDialog) {
        if (showDeleteSuccessDialog) {
            delay(2000)
            backDispatcher?.onBackPressed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                }
            )
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = {
                        Text(
                            "Search places in this list",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isLoading,
                    enter = expandVertically(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300))
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                val filteredPlaces = detail.places?.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.address.contains(query, ignoreCase = true)
                } ?: emptyList()
                Log.d("ListDetail", "Filtered places: ${filteredPlaces.size} - $filteredPlaces")

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredPlaces) { place ->
                        PlaceRow(
                            place = place,
                            onMoreClick = { placeMenuExpanded = it }
                        )
                    }
                    if (filteredPlaces.isEmpty()) {
                        item {
                            Text(
                                text = "No places found",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        filteredPlaces.forEach { place ->
                            Log.d("ListDetail", "Adding marker: ${place.name}, lat=${place.latitude}, lng=${place.longitude}")
                            Marker(
                                state = MarkerState(
                                    position = LatLng(place.latitude, place.longitude)
                                ),
                                title = place.name,
                                snippet = place.address
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (menuExpanded) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (detail.isPrivate) "Make public" else "Make private",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = {
                                coroutineScope.launch {
                                    val token = getValidToken()
                                    if (token != null) {
                                        try {
                                            val newPrivacyStatus = !detail.isPrivate
                                            val response = listService.updateList(
                                                listId,
                                                ListUpdate(isPrivate = newPrivacyStatus),
                                                "Bearer $token"
                                            )
                                            if (response.isSuccessful) {
                                                detail = detail.copy(isPrivate = newPrivacyStatus)
                                                ListDetailCache.cache[listId] = detail
                                                Log.d("ListDetail", "Updated list privacy to: $newPrivacyStatus for list: $listId")
                                            } else {
                                                errorMessage = "Failed to update privacy: ${response.message()}"
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Exception: ${e.message}"
                                        }
                                    } else {
                                        errorMessage = "Missing token"
                                    }
                                }
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit list name", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                showEditDialog = true
                                newListName = detail.name
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Merge list", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete list", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                showDeleteConfirmDialog = true
                                menuExpanded = false
                            }
                        )
                    }
                }

                if (placeMenuExpanded != null && editingNotesPlaceId == null && viewingNotesPlaceId == null) {
                    Log.d("ListDetail", "Showing place menu for placeId=$placeMenuExpanded")
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = {
                            Log.d("ListDetail", "Dismissing place menu")
                            placeMenuExpanded = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                Log.d("ListDetail", "Share selected for placeId=$placeMenuExpanded")
                                sharingPlaceId = placeMenuExpanded
                                placeMenuExpanded = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tags", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                placeMenuExpanded = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Notes", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                Log.d("ListDetail", "Opening note viewer for placeId=$placeMenuExpanded")
                                val place = detail.places?.find { it.id == placeMenuExpanded }
                                currentNote = place?.notes ?: ""
                                Log.d("ListDetail", "Initial note value: $currentNote")
                                viewingNotesPlaceId = placeMenuExpanded
                                Log.d("ListDetail", "Set viewingNotesPlaceId=$viewingNotesPlaceId, currentNote=$currentNote")
                                placeMenuExpanded = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to different list", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                placeMenuExpanded = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete item", style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                placeMenuExpanded = null
                            }
                        )
                    }
                }

                // Note viewer
                if (viewingNotesPlaceId != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (currentNote.isNotEmpty()) {
                                Text(
                                    text = currentNote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = "No note added yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        viewingNotesPlaceId = null
                                        currentNote = ""
                                    }
                                ) {
                                    Text(
                                        "Close",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        Log.d("ListDetail", "Switching to edit mode: currentNote=$currentNote")
                                        editingNotesPlaceId = viewingNotesPlaceId
                                        viewingNotesPlaceId = null
                                        Log.d("ListDetail", "Set editingNotesPlaceId=$editingNotesPlaceId")
                                    },
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text(
                                        "Edit",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // Notes editor
                if (editingNotesPlaceId != null) {
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = currentNote,
                                onValueChange = {
                                    currentNote = it
                                    Log.d("ListDetail", "Note text changed: $currentNote")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .focusRequester(focusRequester),
                                label = { Text("Type ...") },
                                shape = MaterialTheme.shapes.small,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            LaunchedEffect(editingNotesPlaceId) {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                                Log.d("ListDetail", "Requested focus and keyboard for OutlinedTextField")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        editingNotesPlaceId = null
                                        currentNote = ""
                                    }
                                ) {
                                    Text(
                                        "Cancel",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val placeIdToUpdate = editingNotesPlaceId
                                        if (placeIdToUpdate == null) {
                                            Log.e("ListDetail", "editingNotesPlaceId is null before launching coroutine")
                                            errorMessage = "Error: No place selected to update"
                                            editingNotesPlaceId = null
                                            currentNote = ""
                                            return@Button
                                        }

                                        // Capture the current note value before clearing the state
                                        val noteToUpdate = currentNote

                                        coroutineScope.launch {
                                            val token = getValidToken()
                                            if (token != null) {
                                                try {
                                                    Log.d("ListDetail", "Testing connectivity with GET /lists")
                                                    val testResponse = listService.getLists("Bearer $token")
                                                    Log.d("ListDetail", "GET /lists response: ${testResponse.code()} - ${testResponse.body()}")

                                                    Log.d("ListDetail", "Attempting to update place: listId=$listId, placeId=$placeIdToUpdate, notes=$noteToUpdate")
                                                    val response = listService.updatePlace(
                                                        listId,
                                                        placeIdToUpdate,
                                                        PlaceUpdate(notes = noteToUpdate),
                                                        "Bearer $token"
                                                    )
                                                    if (response.isSuccessful) {
                                                        // Refresh the list details from the server
                                                        val refreshResponse = listService.getListDetail(listId, "Bearer $token")
                                                        if (refreshResponse.isSuccessful) {
                                                            val refreshedDetail = refreshResponse.body()
                                                            if (refreshedDetail != null) {
                                                                detail = refreshedDetail
                                                                ListDetailCache.cache[listId] = refreshedDetail
                                                                Log.d("ListDetail", "Refreshed list details after updating note: $refreshedDetail")
                                                            } else {
                                                                Log.e("ListDetail", "Failed to refresh list details: No data returned")
                                                            }
                                                        } else {
                                                            Log.e("ListDetail", "Failed to refresh list details: ${refreshResponse.message()} - ${refreshResponse.errorBody()?.string()}")
                                                        }

                                                        Log.d("ListDetail", "Updated notes for place $placeIdToUpdate: $noteToUpdate")
                                                    } else {
                                                        errorMessage = "Failed to update notes: ${response.message()} - ${response.errorBody()?.string()}"
                                                        Log.e("ListDetail", "Failed to update notes: ${response.message()} - ${response.errorBody()?.string()}")
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = "Exception: ${e.message ?: "Unknown error"}"
                                                    Log.e("ListDetail", "Exception while updating place", e)
                                                }
                                            } else {
                                                errorMessage = "Missing token"
                                                Log.e("ListDetail", "Missing token for updatePlace")
                                            }
                                        }

                                        editingNotesPlaceId = null
                                        currentNote = ""
                                    },
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text(
                                        "Done",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // Sharing overlay
                if (sharingPlaceId != null) {
                    val place = detail.places?.find { it.id == sharingPlaceId }
                    val clipboardManager = LocalClipboardManager.current
                    val context = LocalContext.current

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Share",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            val shareUrl = "https://sesameapp.com/place/${sharingPlaceId}?listId=$listId"
                            val shareMessage = "Check out this place on Sesame: ${place?.name} at ${place?.address}\n$shareUrl"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // Copy Link
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(shareUrl))
                                    Log.d("ListDetail", "Copied share URL to clipboard: $shareUrl")
                                    sharingPlaceId = null
                                }) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy link",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Copy link",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // WhatsApp
                                IconButton(onClick = {
                                    try {
                                        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            setPackage("com.whatsapp")
                                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                                        }
                                        context.startActivity(whatsappIntent)
                                        Log.d("ListDetail", "Opened WhatsApp with share URL: $shareUrl")
                                    } catch (e: Exception) {
                                        Log.e("ListDetail", "Failed to open WhatsApp", e)
                                        // Optionally show an error message
                                    }
                                    sharingPlaceId = null
                                }) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share via WhatsApp",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "WhatsApp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Instagram
                                IconButton(onClick = {
                                    try {
                                        val instagramIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            setPackage("com.instagram.android")
                                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                                        }
                                        context.startActivity(instagramIntent)
                                        Log.d("ListDetail", "Opened Instagram with share URL: $shareUrl")
                                    } catch (e: Exception) {
                                        Log.e("ListDetail", "Failed to open Instagram", e)
                                        // Optionally show an error message
                                    }
                                    sharingPlaceId = null
                                }) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share via Instagram",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Instagram",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // SMS
                                IconButton(onClick = {
                                    try {
                                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = android.net.Uri.parse("smsto:")
                                            putExtra("sms_body", shareMessage)
                                        }
                                        context.startActivity(smsIntent)
                                        Log.d("ListDetail", "Opened SMS with share URL: $shareUrl")
                                    } catch (e: Exception) {
                                        Log.e("ListDetail", "Failed to open SMS", e)
                                        // Optionally show an error message
                                    }
                                    sharingPlaceId = null
                                }) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Message,
                                            contentDescription = "Share via SMS",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "SMS",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Are you sure you want to delete this list?",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val token = getValidToken()
                            if (token != null) {
                                try {
                                    val response = listService.deleteList(listId, "Bearer $token")
                                    if (response.isSuccessful) {
                                        ListDetailCache.cache.remove(listId)
                                        showDeleteConfirmDialog = false
                                        showDeleteSuccessDialog = true
                                        Log.d("ListDetail", "Deleted list: $listId")
                                    } else {
                                        errorMessage = "Failed to delete list: ${response.message()}"
                                        showDeleteConfirmDialog = false
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Exception: ${e.message}"
                                    showDeleteConfirmDialog = false
                                }
                            } else {
                                errorMessage = "Missing token"
                                showDeleteConfirmDialog = false
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        "yes, delete",
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("no, cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showDeleteSuccessDialog) {
        AlertDialog(
            onDismissRequest = { /* No dismiss action, handled by LaunchedEffect */ },
            title = {
                Text(
                    text = "The list ${detail.name} has been deleted",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                newListName = detail.name
            },
            title = {
                Text(
                    text = "Edit List Name",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("List Name") },
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val token = getValidToken()
                            if (token != null) {
                                try {
                                    val response = listService.updateList(
                                        listId,
                                        ListUpdate(name = newListName),
                                        "Bearer $token"
                                    )
                                    if (response.isSuccessful) {
                                        detail = detail.copy(name = newListName)
                                        ListDetailCache.cache[listId] = detail
                                        Log.d("ListDetail", "Updated list name to: $newListName")
                                    } else {
                                        errorMessage = "Failed to update list name: ${response.message()}"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Exception: ${e.message}"
                                }
                            } else {
                                errorMessage = "Missing token"
                            }
                        }
                        showEditDialog = false
                    },
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        "Save",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        newListName = detail.name
                    }
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Composable
fun PlaceRow(
    place: PlaceItem,
    onMoreClick: (Int) -> Unit // Callback to open the place menu
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = place.address,
                style = MaterialTheme.typography.bodyMedium
            )
            if (place.visitStatus != null) {
                Text(
                    text = "Status: ${place.visitStatus}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (place.rating != null) {
                Text(
                    text = "${place.rating}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (place.notes != null) {
                Text(
                    text = place.notes,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        IconButton(onClick = {
            Log.d("ListDetail", "More icon clicked for placeId=${place.id}")
            onMoreClick(place.id)
        }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More options for place"
            )
        }
    }
}