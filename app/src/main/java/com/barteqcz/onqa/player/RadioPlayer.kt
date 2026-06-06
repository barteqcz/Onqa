package com.barteqcz.onqa.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class StationInfo(
    val url: String? = null,
    val name: String? = null,
    val logo: String? = null,
    val network: String? = null,
)

@Singleton
class RadioPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    playbackEvents: PlaybackEvents,
) {
    private var controller: MediaController? = null
    private var lastPauseActionTime: Long = 0

    private val _isPlaying = MutableStateFlow(value = false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(value = false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackError = MutableStateFlow(value = false)
    val playbackError: StateFlow<Boolean> = _playbackError.asStateFlow()

    private val _metadata = MutableStateFlow<String?>(null)
    val metadata: StateFlow<String?> = _metadata.asStateFlow()

    private val _stationInfo = MutableStateFlow(StationInfo())
    val stationInfo: StateFlow<StationInfo> = _stationInfo.asStateFlow()

    val requestNext = playbackEvents.requestNext
    val requestPrevious = playbackEvents.requestPrevious

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    controller = controllerFuture.get()
                    controller?.let { setupController(it) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to connect MediaController")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun setupController(player: MediaController) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                val buffering = state == Player.STATE_BUFFERING
                if ((state != Player.STATE_IDLE) && (state != Player.STATE_ENDED)) {
                    _isBuffering.value = buffering
                }
                if (state == Player.STATE_READY) _playbackError.value = false
            }
            override fun onPlayerError(error: PlaybackException) {
                _playbackError.value = true
                _isPlaying.value = false
                _isBuffering.value = false
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncWithMediaItem(mediaItem)
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                syncWithMediaItem(player.currentMediaItem)
            }
        })
        
        _isPlaying.value = player.isPlaying
        _isBuffering.value = player.playbackState == Player.STATE_BUFFERING
        syncWithMediaItem(player.currentMediaItem)
    }

    private fun syncWithMediaItem(item: MediaItem?) {
        val metadata = item?.mediaMetadata
        val title = metadata?.title?.toString()
        val stationName = metadata?.albumArtist?.toString() ?: metadata?.artist?.toString()
        
        _stationInfo.value = StationInfo(
            url = item?.mediaId,
            name = stationName,
            logo = metadata?.artworkUri?.toString(),
            network = metadata?.extras?.getString("network")
        )
        
        _metadata.value = if (!title.isNullOrBlank() && (title != stationName)) title else null
    }

    fun play(stationName: String?, url: String, logoUrl: String?, network: String? = null, forceReload: Boolean = false) {
        if (_stationInfo.value.url != url) {
            _metadata.value = null
        }

        lastPauseActionTime = 0
        _playbackError.value = false
        _isBuffering.value = true
        
        val player = controller ?: return
        
        val extras = android.os.Bundle().apply {
            if (forceReload) putBoolean("force_reload", true)
        }
        
        val mediaMetadata = MediaMetadataMapper.buildMetadata(
            title = stationName,
            artist = null,
            stationName = stationName,
            artworkUri = logoUrl,
            network = network,
            extras = extras
        )

        val mediaItem = MediaItem.Builder()
            .setMediaId(url)
            .setUri(url)
            .setMediaMetadata(mediaMetadata)
            .apply {
                if (url.contains("m3u8") || url.contains(".m3u")) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        player.setMediaItem(mediaItem)
        player.seekToDefaultPosition()
        player.prepare()
        player.play()
    }

    fun pause() {
        lastPauseActionTime = System.currentTimeMillis()
        controller?.pause()
    }
}
