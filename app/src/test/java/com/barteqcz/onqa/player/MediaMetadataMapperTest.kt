package com.barteqcz.onqa.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMetadataMapperTest {

    @Test
    fun `getEffectiveMetadata should handle station name matching title`() {
        val (title, subtitle) = MediaMetadataMapper.getEffectiveMetadata(
            streamTitle = "Radio ZET",
            streamArtist = null,
            stationName = "Radio ZET"
        )
        assertEquals("Radio ZET", title)
        assertEquals("", subtitle)
    }

    @Test
    fun `getEffectiveMetadata should handle song title and artist`() {
        val (title, subtitle) = MediaMetadataMapper.getEffectiveMetadata(
            streamTitle = "Song Title",
            streamArtist = "Artist Name",
            stationName = "Radio ZET"
        )
        assertEquals("Artist Name - Song Title", title)
        assertEquals("Radio ZET", subtitle)
    }

    @Test
    fun `getEffectiveMetadata should handle null or blank values`() {
        val (title, subtitle) = MediaMetadataMapper.getEffectiveMetadata(
            streamTitle = null,
            streamArtist = "  ",
            stationName = "Radio ZET"
        )
        assertEquals("Radio ZET", title)
        assertEquals("", subtitle)
    }
}
