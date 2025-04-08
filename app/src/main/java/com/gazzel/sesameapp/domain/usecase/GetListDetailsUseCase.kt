package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.SesameList // Assuming repo returns this
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.util.Result
import javax.inject.Inject

class GetListDetailsUseCase @Inject constructor(
    private val listRepository: ListRepository
) {
    suspend operator fun invoke(listId: String): Result<SesameList> {
        // The repository method already returns Result<SesameList>
        return listRepository.getListById(listId)
    }
}