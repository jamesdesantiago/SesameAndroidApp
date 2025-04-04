package com.gazzel.sesameapp.data.repository

import com.gazzel.sesameapp.data.mapper.toDomain
import com.gazzel.sesameapp.data.mapper.toDto
import com.gazzel.sesameapp.data.model.ListDto
import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.util.Result
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListRepositoryImpl @Inject constructor(
    firestore: FirebaseFirestore
) : BaseRepositoryImpl<SesameList>(firestore, "lists"), ListRepository {

    override fun getUserLists(userId: String): Flow<List<SesameList>> {
        return firestore.collection("lists")
            .whereEqualTo("userId", userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(SesameList::class.java)?.copy(id = doc.id)
                }
            }
    }

    override fun getPublicLists(): Flow<List<SesameList>> {
        return firestore.collection("lists")
            .whereEqualTo("isPublic", true)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(SesameList::class.java)?.copy(id = doc.id)
                }
            }
    }

    override suspend fun getListById(id: String): SesameList? {
        return firestore.collection("lists")
            .document(id)
            .get()
            .await()
            .toObject(SesameList::class.java)
            ?.copy(id = id)
    }

    override suspend fun createList(list: SesameList): String {
        val docRef = firestore.collection("lists").document()
        val newList = list.copy(id = docRef.id)
        docRef.set(newList).await()
        return docRef.id
    }

    override suspend fun updateList(list: SesameList) {
        firestore.collection("lists")
            .document(list.id)
            .set(list)
            .await()
    }

    override suspend fun deleteList(id: String) {
        firestore.collection("lists")
            .document(id)
            .delete()
            .await()
    }

    override suspend fun getRecentLists(limit: Int): List<SesameList> {
        return firestore.collection("lists")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(SesameList::class.java)?.copy(id = doc.id)
            }
    }

    override suspend fun searchLists(query: String): List<SesameList> {
        return firestore.collection("lists")
            .whereGreaterThanOrEqualTo("title", query)
            .whereLessThanOrEqualTo("title", query + "\uf8ff")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(SesameList::class.java)?.copy(id = doc.id)
            }
    }

    override fun SesameList.toMap(): Map<String, Any> {
        return this.toDto().let { dto ->
            mapOf<String, Any>(
                "id" to dto.id,
                "title" to dto.title,
                "description" to (dto.description ?: ""),  // Provide a default for null
                "userId" to dto.userId,
                "createdAt" to dto.createdAt,
                "updatedAt" to dto.updatedAt,
                "isPublic" to dto.isPublic,
                "placeCount" to dto.placeCount,
                "followerCount" to dto.followerCount
            )
        }
    }



    override fun Map<String, Any>.toEntity(id: String): SesameList {
        return ListDto(
            id = id,
            title = get("title") as? String ?: "",
            description = get("description") as? String,
            userId = get("userId") as? String ?: "",
            createdAt = get("createdAt") as? com.google.firebase.Timestamp ?: com.google.firebase.Timestamp.now(),
            updatedAt = get("updatedAt") as? com.google.firebase.Timestamp ?: com.google.firebase.Timestamp.now(),
            isPublic = get("isPublic") as? Boolean ?: false,
            placeCount = get("placeCount") as? Int ?: 0,
            followerCount = get("followerCount") as? Int ?: 0
        ).toDomain()
    }

    override fun getId(item: SesameList): String = item.id

    override suspend fun addPlaceToList(listId: String, placeId: String): Result<Unit> {
        return try {
            firestore.collection("lists")
                .document(listId)
                .collection("places")
                .document(placeId)
                .set(mapOf("addedAt" to System.currentTimeMillis()))
                .await()
            
            // Update place count
            firestore.collection("lists")
                .document(listId)
                .update("placeCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(handleException(e))
        }
    }

    override suspend fun removePlaceFromList(listId: String, placeId: String): Result<Unit> {
        return try {
            firestore.collection("lists")
                .document(listId)
                .collection("places")
                .document(placeId)
                .delete()
                .await()
            
            // Update place count
            firestore.collection("lists")
                .document(listId)
                .update("placeCount", com.google.firebase.firestore.FieldValue.increment(-1))
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(handleException(e))
        }
    }

    override suspend fun followList(listId: String): Result<Unit> {
        return try {
            firestore.collection("lists")
                .document(listId)
                .update("followerCount", com.google.firebase.firestore.FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(handleException(e))
        }
    }

    override suspend fun unfollowList(listId: String): Result<Unit> {
        return try {
            firestore.collection("lists")
                .document(listId)
                .update("followerCount", com.google.firebase.firestore.FieldValue.increment(-1))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(handleException(e))
        }
    }
} 