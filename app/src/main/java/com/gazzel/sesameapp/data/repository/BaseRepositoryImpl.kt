package com.gazzel.sesameapp.data.repository

import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.repository.BaseRepository
import com.gazzel.sesameapp.domain.util.Result
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

abstract class BaseRepositoryImpl<T> protected constructor(
    protected val firestore: FirebaseFirestore,
    protected val collectionPath: String
) : BaseRepository<T> {

    protected abstract fun T.toMap(): Map<String, Any>
    protected abstract fun Map<String, Any>.toEntity(id: String): T
    protected abstract fun getId(item: T): String

    override suspend fun getById(id: String): Result<T> {
        return try {
            val doc = firestore.collection(collectionPath)
                .document(id)
                .get()
                .await()

            if (!doc.exists()) {
                return Result.error(AppException.ResourceNotFoundException("Resource not found: $id"))
            }

            Result.success(doc.data?.toEntity(doc.id) ?: throw AppException.UnknownException("Failed to parse document"))
        } catch (e: Exception) {
            Result.error(handleException(e))
        }
    }

    override suspend fun create(item: T): Result<String> {
        return try {
            val docRef = firestore.collection(collectionPath).document()
            val itemWithId = item.toMap().toMutableMap().apply {
                put("id", docRef.id)
            }
            docRef.set(itemWithId).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.error(handleException(e))
        }
    }

    override suspend fun update(item: T): Result<Unit> {
        return try {
            val id = getId(item)
            firestore.collection(collectionPath)
                .document(id)
                .set(item.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(handleException(e))
        }
    }

    override suspend fun delete(id: String): Result<Unit> {
        return try {
            firestore.collection(collectionPath)
                .document(id)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(handleException(e))
        }
    }

    override fun observeById(id: String): Flow<T?> = callbackFlow {
        val subscription = firestore.collection(collectionPath)
            .document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val item = snapshot?.data?.toEntity(snapshot.id)
                trySend(item)
            }
        awaitClose { subscription.remove() }
    }

    protected fun Query.addPagination(limit: Int, lastDocument: DocumentReference? = null): Query {
        var query = this.limit(limit.toLong())
        lastDocument?.let { query = query.startAfter(it) }
        return query
    }

    private fun handleException(e: Exception): AppException {
        return when (e) {
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (e.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND ->
                        AppException.ResourceNotFoundException(e.message ?: "Resource not found")
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        AppException.AuthenticationException(e.message ?: "Permission denied")
                    else -> AppException.DatabaseException(e.message ?: "Database error")
                }
            }
            is com.google.android.gms.tasks.RuntimeExecutionException ->
                AppException.NetworkException(e.message ?: "Network error")
            else -> AppException.UnknownException(e.message ?: "Unknown error")
        }
    }
} 