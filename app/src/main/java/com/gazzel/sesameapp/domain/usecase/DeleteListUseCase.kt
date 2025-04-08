package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.util.Result
import javax.inject.Inject

class DeleteListUseCase @Inject constructor(
    private val listRepository: ListRepository
) {
    suspend operator fun invoke(listId: String): Result<Unit> {
        return listRepository.deleteList(listId)
    }
}