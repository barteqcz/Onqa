package com.barteqcz.onqa.player

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import com.barteqcz.onqa.data.model.RadioStation

object MediaMetadataMapper {

    fun buildMetadata(
        title: String?,
        artist: String? = null,
        stationName: String? = null,
        artworkUri: String? = null,
        artworkData: ByteArray? = null,
        network: String? = null,
        extras: Bundle = Bundle(),
    ): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(artist)
            .setAlbumArtist(stationName)
            .setArtworkUri(artworkUri?.toUri())
            .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                network?.let { extras.putString("network", it) }
                setExtras(extras)
            }
            .build()
    }

    fun getEffectiveMetadata(
        streamTitle: String?,
        streamArtist: String?,
        stationName: String?,
        allStations: List<RadioStation>? = null,
    ): Pair<String?, String?> {
        val sName = stationName?.trim().orEmpty()
        val sTitle = streamTitle?.trim().orEmpty()
        val sArtist = streamArtist?.trim().orEmpty()

        val otherStation = allStations?.find { station ->
            station.name.equals(sTitle, ignoreCase = true) && station.name != sName
        }

        if (otherStation != null) {
            return sName to ""
        }

        val effectiveArtist = sArtist.takeUnless {
            it.equals(sName, ignoreCase = true) || it.isEmpty()
        }
        val effectiveTitle = sTitle.takeUnless {
            it.equals(sName, ignoreCase = true) || it.isEmpty()
        }

        return when {
            effectiveArtist != null && effectiveTitle != null ->
                "$effectiveArtist - $effectiveTitle" to sName

            effectiveTitle != null -> effectiveTitle to sName
            effectiveArtist != null -> effectiveArtist to sName
            else -> sName to ""
        }
    }

}