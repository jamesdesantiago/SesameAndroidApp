// presentation/viewmodels/HomeViewModel.kt
package com.gazzel.sesameapp.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Import Use Case and its Result Data Class
import com.gazzel.sesameapp.domain.usecase.GetHomeMapDataUseCase // <<< Use Case
import com.gazzel.sesameapp.domain.usecase.HomeScreenData // <<< Use Case Result
// Import necessary Domain models
import com.gazzel.sesameapp.domain.model.PlaceItem
import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.model.SesameList
// Import Result and extensions
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
// Import LatLng
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Updated Success state to hold the combined data object
sealed class HomeUiState {
    object Initial : HomeUiState()
    object Loading : HomeUiState()
    data class Success(val data: HomeScreenData) : HomeUiState() // <<< Holds HomeScreenData
    data class Error(val message: String, val isPermissionError: Boolean = false) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeMapDataUseCase: GetHomeMapDataUseCase // <<< Inject Use Case
    // Remove direct repository injections
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Keep search specific state if needed
    private val _searchResults = MutableStateFlow<List<SesameList>>(emptyList())
    val searchResults: StateFlow<List<SesameList>> = _searchResults.asStateFlow()
    private var isSearchActive = false // Track if search is active

    // Triggered by UI after permission grant
    fun loadDataAfterPermission() {
        if (_uiState.value is HomeUiState.Loading || _uiState.value is HomeUiState.Success) return
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            Log.d("HomeViewModel", "Calling GetHomeMapDataUseCase...")
            val result = getHomeMapDataUseCase() // Call the combined Use Case

            result.onSuccess { homeScreenData ->
                Log.d("HomeViewModel", "UseCase successful. User: ${homeScreenData.user.id}, Nearby: ${homeScreenData.nearbyPlaces.size}, Recent: ${homeScreenData.recentLists.size}")
                _uiState.value = HomeUiState.Success(homeScreenData) // Update state with combined data
                // Reset search results when refreshing main data
                _searchResults.value = emptyList()
                isSearchActive = false
            }.onError { exception ->
                Log.e("HomeViewModel", "UseCase failed: ${exception.message}", exception)
                val isPermissionError = exception.message?.contains("permission", ignoreCase = true) == true
                _uiState.value = HomeUiState.Error(exception.message ?: "Failed to load home data", isPermissionError)
            }
        }
    }

    // Search still operates on ListRepository (or a dedicated SearchUseCase)
    // This updates a separate state for now, or could update the lists within HomeUiState.Success
    @Inject lateinit var listRepository: ListRepository // Quick inject for example, better via SearchUseCase
    fun searchLists(query: String) {
        if (query.isBlank()) {
            isSearchActive = false
            // Reload initial lists if query cleared? Or just show recents from current state?
            (_uiState.value as? HomeUiState.Success)?.data?.recentLists?.let {
                _searchResults.value = it // Show recent lists when search cleared
            }
            return
        }
        isSearchActive = true
        viewModelScope.launch {
            // Indicate search loading? Maybe not change main state, just clear results?
            _searchResults.value = emptyList() // Clear previous results
            Log.d("HomeViewModel", "Searching lists for query: $query")
            val searchResult = listRepository.searchLists(query) // Or SearchListsUseCase(query)

            searchResult.onSuccess { searchLists ->
                Log.d("HomeViewModel", "Search successful, found ${searchLists.size} lists.")
                _searchResults.value = searchLists // Update search results state
            }.onError { exception ->
                Log.e("HomeViewModel", "List search failed: ${exception.message}", exception)
                _searchResults.value = emptyList() // Clear results on error
                // TODO: Emit search error event
            }
        }
    }


    fun refresh() {
        Log.d("HomeViewModel", "Refresh triggered.")
        loadHomeData()
    }

    fun handlePermissionDenied() {
        _uiState.value = HomeUiState.Error("Location permission is required.", isPermissionError = true)
    }
}