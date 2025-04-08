package com.gazzel.sesameapp.domain.usecase

import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.repository.ListRepository
import com.gazzel.sesameapp.domain.util.Result
import javax.inject.Inject

class UpdateListUseCase @Inject constructor(
    private val listRepository: ListRepository
) {
    // Takes the full domain object to update
    suspend operator fun invoke(list: SesameList): Result<SesameList> {
        // Repository takes the domain object and returns the updated one
        return listRepository.updateList(list)
    }
}