package com.gazzel.sesameapp.data.manager

// Interface defining the contract for DeviceIdManager
interface IDeviceIdManager {
    fun getDeviceId(): String
    fun resetDeviceId()
}