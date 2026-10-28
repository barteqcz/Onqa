/**
 * SQLite database manager for the radio station catalog, responsible for downloading, updating, and executing location queries.
 */
package com.barteqcz.onqa.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import com.barteqcz.onqa.data.model.CatalogUpdateFrequency
import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.remote.RadioApiService
import com.barteqcz.onqa.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogDatabaseManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val radioApiService: RadioApiService,
    private val settingsRepository: SettingsRepository,
) {
    private var dbInstance: SQLiteDatabase? = null

    private fun getDatabase(dbFile: File): SQLiteDatabase? {
        synchronized(this) {
            if (dbInstance != null && dbInstance!!.isOpen) {
                return dbInstance
            }
            return try {
                dbInstance = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                dbInstance
            } catch (e: Exception) {
                Timber.e(e, "Failed to open catalog database")
                null
            }
        }
    }

    fun closeDatabase() {
        synchronized(this) {
            try {
                dbInstance?.close()
            } catch (_: Exception) {
            }
            dbInstance = null
        }
    }

    private fun isValidSqliteDatabase(file: File): Boolean {
        return !(!file.exists() || file.length() < 100) && try {
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            val isOk = db.isOpen
            db.close()
            isOk
        } catch (e: Exception) {
            Timber.w(e, "Database file ${file.name} failed validity test")
            false
        }
    }

    fun isDatabasePresent(): Boolean {
        val dbFile = context.getDatabasePath("catalog.sqlite")
        val pendingFile = context.getDatabasePath("catalog_pending.sqlite")
        return (dbFile.exists() && dbFile.length() > 0 && isValidSqliteDatabase(dbFile)) ||
               (pendingFile.exists() && pendingFile.length() > 0 && isValidSqliteDatabase(pendingFile))
    }

    fun hasPendingUpdate(): Boolean {
        val pendingFile = context.getDatabasePath("catalog_pending.sqlite")
        if (!pendingFile.exists() || pendingFile.length() == 0L) return false
        val isValid = isValidSqliteDatabase(pendingFile)
        if (!isValid) {
            try { pendingFile.delete() } catch (_: Exception) {}
        }
        return isValid
    }

    suspend fun applyPendingUpdate(): Boolean = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("catalog.sqlite")
        val pendingFile = context.getDatabasePath("catalog_pending.sqlite")
        if (!pendingFile.exists() || pendingFile.length() == 0L) {
            return@withContext false
        }

        if (!isValidSqliteDatabase(pendingFile)) {
            Timber.w("Pending catalog file is corrupt or incomplete. Removing it.")
            try { pendingFile.delete() } catch (_: Exception) {}
            return@withContext false
        }

        synchronized(this@CatalogDatabaseManager) {
            closeDatabase()
            if (dbFile.exists()) {
                dbFile.delete()
            }
            val renamed = pendingFile.renameTo(dbFile)
            if (renamed || dbFile.exists()) {
                Timber.i("Successfully applied pending catalog.sqlite update")
                return@withContext true
            } else {
                Timber.e("Failed to rename pending file to catalog.sqlite")
                try { pendingFile.delete() } catch (_: Exception) {}
                return@withContext false
            }
        }
    }

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    suspend fun downloadCatalog(): Boolean = withContext(Dispatchers.IO) {
        _isDownloading.value = true
        val dbFile = context.getDatabasePath("catalog.sqlite")
        val pendingFile = context.getDatabasePath("catalog_pending.sqlite")
        dbFile.parentFile?.mkdirs()
        val tempFile = File(dbFile.parentFile, "catalog_pending.sqlite.tmp")

        try {
            val body = radioApiService.downloadDatabase()

            tempFile.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }

            if (tempFile.exists() && tempFile.length() > 0 && isValidSqliteDatabase(tempFile)) {
                if (pendingFile.exists()) {
                    pendingFile.delete()
                }
                val renamed = tempFile.renameTo(pendingFile)
                if (renamed || pendingFile.exists()) {
                    Timber.i("Successfully downloaded catalog update to catalog_pending.sqlite")
                    // If active database does not exist yet (first run), apply immediately
                    if (!dbFile.exists() || dbFile.length() == 0L) {
                        applyPendingUpdate()
                        settingsRepository.updateLastCatalogUpdate(System.currentTimeMillis())
                    }
                    return@withContext true
                }
            } else {
                Timber.w("Downloaded database temp file is invalid or zero bytes")
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }
            false
        } catch (e: Exception) {
            Timber.e(e, "Failed to download catalog database")
            if (tempFile.exists()) {
                try { tempFile.delete() } catch (_: Exception) {}
            }
            false
        } finally {
            _isDownloading.value = false
        }
    }

    suspend fun checkAutoUpdate(frequency: CatalogUpdateFrequency, lastUpdate: Long): Boolean {
        if (frequency == CatalogUpdateFrequency.MANUAL) return false
        val now = System.currentTimeMillis()
        val intervalMs = frequency.days * 24 * 60 * 60 * 1000L
        if (now - lastUpdate >= intervalMs) {
            Timber.i("Triggering auto-update for catalog database (Frequency: ${frequency.name})")
            return downloadCatalog()
        }
        return false
    }

    suspend fun getNearbyStations(lat: Double, lon: Double): List<RadioStation> = withContext(Dispatchers.IO) {
        if (hasPendingUpdate() && dbInstance == null) {
            applyPendingUpdate()
            settingsRepository.updateLastCatalogUpdate(System.currentTimeMillis())
        }

        val dbFile = context.getDatabasePath("catalog.sqlite")
        if (!dbFile.exists() || dbFile.length() == 0L) {
            val success = downloadCatalog()
            if (!success || !dbFile.exists()) {
                return@withContext emptyList()
            }
        }

        val database = getDatabase(dbFile) ?: return@withContext emptyList()
        val stationsList = mutableListOf<RadioStation>()

        val query = """
            SELECT 
                s.id AS station_id,
                s.name AS station_name,
                s.stream_url,
                s.stream_url_hq,
                s.logo,
                s.network,
                t.id AS transmitter_id,
                t.name AS transmitter_name,
                t.latitude AS transmitter_lat,
                t.longitude AS transmitter_lon,
                st.display_order,
                st.coverage_km
            FROM stations s
            JOIN station_transmitters st ON s.id = st.station_id
            JOIN transmitters t ON st.transmitter_id = t.id
        """.trimIndent()

        try {
            database.rawQuery(query, null).use { cursor ->
                val colStationName = cursor.getColumnIndexOrThrow("station_name")
                val colStreamUrl = cursor.getColumnIndexOrThrow("stream_url")
                val colStreamUrlHq = cursor.getColumnIndexOrThrow("stream_url_hq")
                val colLogo = cursor.getColumnIndexOrThrow("logo")
                val colNetwork = cursor.getColumnIndexOrThrow("network")
                val colTransmitterId = cursor.getColumnIndexOrThrow("transmitter_id")
                val colTransmitterName = cursor.getColumnIndexOrThrow("transmitter_name")
                val colTransmitterLat = cursor.getColumnIndexOrThrow("transmitter_lat")
                val colTransmitterLon = cursor.getColumnIndexOrThrow("transmitter_lon")
                val colDisplayOrder = cursor.getColumnIndexOrThrow("display_order")
                val colCoverageKm = cursor.getColumnIndexOrThrow("coverage_km")

                val locationResults = FloatArray(1)

                while (cursor.moveToNext()) {
                    val stationName = cursor.getString(colStationName)
                    val streamUrl = cursor.getString(colStreamUrl)
                    val streamUrlHq = cursor.getString(colStreamUrlHq)
                    val logo = cursor.getString(colLogo)
                    val network = cursor.getString(colNetwork)

                    val transmitterId = if (cursor.isNull(colTransmitterId)) null else cursor.getInt(colTransmitterId)
                    val transmitterName = if (cursor.isNull(colTransmitterName)) null else cursor.getString(colTransmitterName)
                    val displayOrder = if (cursor.isNull(colDisplayOrder)) 0 else cursor.getInt(colDisplayOrder)
                    val coverageKm = if (cursor.isNull(colCoverageKm)) null else cursor.getDouble(colCoverageKm)

                    val distanceKm: Double? = if (!cursor.isNull(colTransmitterLat) && !cursor.isNull(colTransmitterLon)) {
                        val tLat = cursor.getDouble(colTransmitterLat)
                        val tLon = cursor.getDouble(colTransmitterLon)
                        Location.distanceBetween(lat, lon, tLat, tLon, locationResults)
                        (locationResults[0] / 1000.0)
                    } else {
                        null
                    }

                    stationsList.add(
                        RadioStation(
                            name = stationName,
                            streamUrl = streamUrl,
                            streamUrlHq = streamUrlHq,
                            logo = logo,
                            transmitterName = transmitterName,
                            transmitterId = transmitterId,
                            network = network,
                            distance = distanceKm,
                            displayOrder = displayOrder,
                            coverageKm = coverageKm
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying catalog database")
        }

        stationsList
    }

    suspend fun getAllStationNames(): Set<String> = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("catalog.sqlite")
        if (!dbFile.exists() || dbFile.length() == 0L) {
            return@withContext emptySet()
        }

        val database = getDatabase(dbFile) ?: return@withContext emptySet()
        val stationNames = mutableSetOf<String>()

        try {
            database.rawQuery("SELECT DISTINCT name FROM stations", null).use { cursor ->
                val colName = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(colName)) {
                        stationNames.add(cursor.getString(colName))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching station names from catalog database")
        }

        stationNames
    }
}
