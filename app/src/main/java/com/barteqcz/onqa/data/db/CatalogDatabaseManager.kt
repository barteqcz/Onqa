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

    fun isDatabasePresent(): Boolean {
        val dbFile = context.getDatabasePath("catalog.sqlite")
        return dbFile.exists() && dbFile.length() > 0
    }

    suspend fun downloadCatalog(): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = radioApiService.downloadDatabase()
            val dbFile = context.getDatabasePath("catalog.sqlite")
            dbFile.parentFile?.mkdirs()
            val tempFile = File(dbFile.parentFile, "catalog.sqlite.tmp")

            tempFile.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                closeDatabase()
                if (dbFile.exists()) {
                    dbFile.delete()
                }
                val renamed = tempFile.renameTo(dbFile)
                if (renamed || dbFile.exists()) {
                    settingsRepository.updateLastCatalogUpdate(System.currentTimeMillis())
                    Timber.i("Successfully downloaded and updated catalog.sqlite")
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Timber.e(e, "Failed to download catalog.sqlite")
            false
        }
    }

    suspend fun checkAutoUpdate(frequency: CatalogUpdateFrequency, lastUpdate: Long) {
        if (frequency == CatalogUpdateFrequency.MANUAL) return
        val now = System.currentTimeMillis()
        val intervalMs = frequency.days * 24 * 60 * 60 * 1000L
        if (now - lastUpdate >= intervalMs) {
            Timber.i("Triggering auto-update for catalog.sqlite (Frequency: ${frequency.name})")
            downloadCatalog()
        }
    }

    suspend fun getNearbyStations(lat: Double, lon: Double): List<RadioStation> = withContext(Dispatchers.IO) {
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
