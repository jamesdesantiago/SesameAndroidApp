package com.gazzel.sesameapp.domain.model

data class PaginationParams(
    val page: Int = 1,
    val pageSize: Int = 20,
    val sortBy: String? = null,
    val sortOrder: SortOrder = SortOrder.DESC
)

enum class SortOrder {
    ASC, DESC
} 