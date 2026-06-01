package com.barteqcz.onqa.player

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata

object MediaMetadataMapper {

    fun buildMetadata(
        title: String?,
        artist: String? = null,
        stationName: String? = null,
        artworkUri: String? = null,
        network: String? = null,
        extras: Bundle = Bundle()
    ): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(artist)
            .setAlbumArtist(stationName)
            .setArtworkUri(artworkUri?.toUri())
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                if (network != null) {
                    extras.putString("network", network)
                }
                setExtras(extras)
            }
            .build()
    }

    fun getEffectiveMetadata(
        streamTitle: String?,
        streamArtist: String?,
        stationName: String?
    ): Pair<String?, String?> {
        val sName = stationName?.trim() ?: ""
        val sTitle = streamTitle?.trim() ?: ""
        val sArtist = streamArtist?.trim() ?: ""

        val effectiveArtist = if (sArtist.equals(sName, ignoreCase = true) || sArtist.isEmpty()) null else sArtist
        val effectiveTitle = if (sTitle.equals(sName, ignoreCase = true) || sTitle.isEmpty()) null else sTitle

        return when {
            effectiveArtist != null && effectiveTitle != null -> 
                "$effectiveArtist - $effectiveTitle" to sName
            
            effectiveTitle != null -> effectiveTitle to sName
            effectiveArtist != null -> effectiveArtist to sName
            
            else -> sName to ""
        }
    }
}
