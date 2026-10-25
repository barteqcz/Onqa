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

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackError: Boolean = false,
    val metadata: String? = null,
    val stationInfo: StationInfo = StationInfo(),
)

@Singleton
class RadioPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    playbackEvents: PlaybackEvents,
) {
    private var controller: MediaController? = null
    private var lastPauseActionTime: Long = 0

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

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
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _state.update { it.copy(isPlaying = playing) }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    val buffering = state == Player.STATE_BUFFERING
                    if ((state != Player.STATE_IDLE) && (state != Player.STATE_ENDED)) {
                        _state.update { it.copy(isBuffering = buffering) }
                    }
                    if (state == Player.STATE_READY) _state.update { it.copy(playbackError = false) }
                }
                override fun onPlayerError(error: PlaybackException) {
                    _state.update { it.copy(playbackError = true, isPlaying = false, isBuffering = false) }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    syncWithMediaItem(mediaItem)
                }
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    syncWithMediaItem(player.currentMediaItem)
                }
            },
        )
        
        _state.update { it.copy(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
        ) }
        syncWithMediaItem(player.currentMediaItem)
    }

    private fun syncWithMediaItem(item: MediaItem?) {
        val metadata = item?.mediaMetadata
        val title = metadata?.title?.toString()
        val stationName = metadata?.albumArtist?.toString() ?: metadata?.artist?.toString()
        
        val info = StationInfo(
            url = item?.mediaId,
            name = stationName,
            logo = metadata?.artworkUri?.toString(),
            network = metadata?.extras?.getString("network")
        )
        
        val meta = if (!title.isNullOrBlank() && (title != stationName)) title else null

        _state.update { it.copy(stationInfo = info, metadata = meta) }
    }

    fun play(stationName: String?, url: String, logoUrl: String?, network: String? = null, forceReload: Boolean = false) {
        if (_state.value.stationInfo.url != url) {
            _state.update { it.copy(metadata = null) }
        }

        lastPauseActionTime = 0
        _state.update { it.copy(playbackError = false, isBuffering = true) }
        
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
