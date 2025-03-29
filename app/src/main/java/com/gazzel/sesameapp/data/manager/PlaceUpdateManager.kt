package com.gazzel.sesameapp.data.manager

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class DataUpdateEvent {
    object PlaceAdded : DataUpdateEvent()
    object ListDeleted : DataUpdateEvent()
}

object PlaceUpdateManager {
    private val _dataUpdateFlow = MutableSharedFlow<DataUpdateEvent>(replay = 1)
    val dataUpdateFlow = _dataUpdateFlow.asSharedFlow()

    fun notifyPlaceAdded() {
        _dataUpdateFlow.tryEmit(DataUpdateEvent.PlaceAdded)
    }

    fun notifyListDeleted() {
        _dataUpdateFlow.tryEmit(DataUpdateEvent.ListDeleted)
    }
} 