package com.gazzel.sesameapp.data.manager

// Interface defining the contract for SecurityManager
interface ISecurityManager {
    fun encrypt(data: String): String
    fun decrypt(encryptedData: String): String
    fun secureStore(key: String, value: String)
    fun secureRetrieve(key: String): String?
}