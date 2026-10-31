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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogDatabaseManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val radioApiService: RadioApiService,
    private val settingsRepository: SettingsRepository,
) {
    private var dbInstance: SQLiteDatabase? = null
    private val databaseLock = ReentrantReadWriteLock()
    private val downloadMutex = Mutex()
    private var activeDownloadJob: Job? = null

    private fun getDatabase(dbFile: File): SQLiteDatabase? {
        synchronized(this) {
            dbInstance?.takeIf(SQLiteDatabase::isOpen)?.let { return it }
            runCatching { dbInstance?.close() }
            dbInstance = null
            return try {
                SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).also {
                    dbInstance = it
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to open catalog database")
                null
            }
        }
    }

    fun closeDatabase() = databaseLock.write {
        synchronized(this) {
            runCatching { dbInstance?.close() }
                .onFailure { Timber.w(it, "Failed to close catalog database") }
            dbInstance = null
        }
    }

    private fun isValidSqliteDatabase(file: File): Boolean {
        return (file.exists() && file.length() >= 100) && try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                }
            }
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

        databaseLock.write {
            synchronized(this@CatalogDatabaseManager) {
                closeDatabase()

                val backupFile = File(dbFile.parentFile, "catalog_previous.sqlite")
            if (backupFile.exists() && !backupFile.delete()) {
                Timber.e("Failed to remove previous catalog backup")
                return@withContext false
            }

            val hadExistingDatabase = dbFile.exists()
            if (hadExistingDatabase && !dbFile.renameTo(backupFile)) {
                Timber.e("Failed to preserve current catalog before applying update")
                return@withContext false
            }

            val installed = pendingFile.renameTo(dbFile) && isValidSqliteDatabase(dbFile)
            if (!installed) {
                Timber.e("Failed to install pending catalog database; restoring previous version")
                try { dbFile.delete() } catch (_: Exception) {}
                if (hadExistingDatabase && backupFile.exists() && !backupFile.renameTo(dbFile)) {
                    Timber.e("Failed to restore previous catalog database; backup remains at ${backupFile.path}")
                }
                return@withContext false
            }

            if (backupFile.exists() && !backupFile.delete()) {
                Timber.w("Catalog update succeeded, but backup could not be removed: ${backupFile.path}")
            }
            Timber.i("Successfully applied pending catalog.sqlite update")
                true
            }
        }
    }

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    fun cancelDownload() {
        synchronized(this) {
            try {
                activeDownloadJob?.cancel(CancellationException("User cancelled catalog download"))
            } catch (_: Exception) {
            }
            activeDownloadJob = null
        }
        _isDownloading.value = false
    }

    suspend fun downloadCatalog(): Boolean = withContext(Dispatchers.IO) {
        if (!downloadMutex.tryLock()) {
            Timber.d("Catalog download already in progress. Skipping duplicate request.")
            return@withContext false
        }

        synchronized(this@CatalogDatabaseManager) {
            activeDownloadJob = coroutineContext[Job]
        }

        _isDownloading.value = true
        val dbFile = context.getDatabasePath("catalog.sqlite")
        val pendingFile = context.getDatabasePath("catalog_pending.sqlite")
        dbFile.parentFile?.mkdirs()
        val tempFile = File(dbFile.parentFile, "catalog_pending.sqlite.tmp")

        try {
            if (tempFile.exists()) {
                try { tempFile.delete() } catch (_: Exception) {}
            }

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
                    val applied = applyPendingUpdate()
                    if (!applied) {
                        return@withContext false
                    }

                    settingsRepository.updateLastCatalogUpdate(System.currentTimeMillis())
                    return@withContext true
                }
            } else {
                Timber.w("Downloaded database temp file is invalid or zero bytes")
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }
            false
        } catch (e: CancellationException) {
            Timber.i("Catalog download was cancelled by user")
            runCatching { tempFile.delete() }
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to download catalog database")
            runCatching { tempFile.delete() }
            false
        } finally {
            synchronized(this@CatalogDatabaseManager) {
                activeDownloadJob = null
            }
            _isDownloading.value = false
            downloadMutex.unlock()
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
        if (hasPendingUpdate()) {
            val applied = applyPendingUpdate()
            if (applied) {
                settingsRepository.updateLastCatalogUpdate(System.currentTimeMillis())
            }
        }

        val dbFile = context.getDatabasePath("catalog.sqlite")
        if (!dbFile.exists() || dbFile.length() == 0L) {
            val success = downloadCatalog()
            if (!success || !dbFile.exists()) {
                return@withContext emptyList()
            }
        }

        databaseLock.read {
        val database = getDatabase(dbFile) ?: return@read emptyList()
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
    }

    suspend fun getAllStationNames(): Set<String> = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("catalog.sqlite")
        if (!dbFile.exists() || dbFile.length() == 0L) {
            return@withContext emptySet()
        }

        databaseLock.read {
        val database = getDatabase(dbFile) ?: return@read emptySet()
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
}
