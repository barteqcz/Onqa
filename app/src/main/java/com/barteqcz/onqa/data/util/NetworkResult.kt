/**
 * Sealed class representing network and data operation states (Success, Error, Loading).
 */
package com.barteqcz.onqa.data.util

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}
