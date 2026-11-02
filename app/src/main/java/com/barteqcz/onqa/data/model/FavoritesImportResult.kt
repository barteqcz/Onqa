/**
 * Sealed interface representing the outcome of importing favorite radio stations from a backup file.
 */
package com.barteqcz.onqa.data.model

sealed interface FavoritesImportResult {
    data class Success(
        val totalCount: Int,
        val validStations: Set<String>,
        val unknownStations: Set<String>,
        val rawStations: Set<String>
    ) : FavoritesImportResult

    sealed interface Error : FavoritesImportResult {
        data object InvalidFormat : Error
        data object EmptyFile : Error
        data object FileReadError : Error
    }
}
