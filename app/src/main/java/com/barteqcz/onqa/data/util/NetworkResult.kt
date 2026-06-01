package com.barteqcz.onqa.data.util

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val exception: Throwable? = null, val isServerError: Boolean = false) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}
