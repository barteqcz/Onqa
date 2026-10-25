package com.barteqcz.onqa.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val playbackEvents: PlaybackEvents,
) {
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val requestNext = playbackEvents.requestNext
    val requestPrevious = playbackEvents.requestPrevious

    fun emitNext() {
        playbackEvents.emitNext()
    }

    fun emitPrevious() {
        playbackEvents.emitPrevious()
    }

    init {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener(
            {
                try {
                    val connectedController = controllerFuture.get()
                    if (controller == null) {
                        controller = connectedController
                        setupController(connectedController)
                    }
                } catch (exception: Exception) {
                    Timber.e(exception, "Failed to connect MediaController")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun setupController(player: MediaController) {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _state.update { currentState ->
                        currentState.copy(isPlaying = playing)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _state.update { currentState ->
                        currentState.copy(
                            isBuffering = playbackState == Player.STATE_BUFFERING,
                            isPlaying = (playbackState == Player.STATE_READY) && currentState.isPlaying,
                            playbackError = (playbackState != Player.STATE_READY) && currentState.playbackError,
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "Radio playback failed: %s", error.errorCodeName)
                    _state.update { currentState ->
                        currentState.copy(
                            playbackError = true,
                            isPlaying = false,
                            isBuffering = false,
                        )
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val metadata = player.playlistMetadata.takeIf { !it.title.isNullOrBlank() }
                        ?: player.mediaMetadata
                    syncWithMediaItem(mediaItem, metadata)
                }

                override fun onPlaylistMetadataChanged(playlistMetadata: MediaMetadata) {
                    syncWithMediaItem(player.currentMediaItem, playlistMetadata)
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    val metadata = player.playlistMetadata.takeIf { !it.title.isNullOrBlank() }
                        ?: mediaMetadata
                    syncWithMediaItem(player.currentMediaItem, metadata)
                }
            },
        )

        _state.update { currentState ->
            currentState.copy(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
            )
        }
        val initialMetadata = player.playlistMetadata.takeIf { !it.title.isNullOrBlank() }
            ?: player.mediaMetadata
        syncWithMediaItem(player.currentMediaItem, initialMetadata)
    }

    private fun syncWithMediaItem(
        item: MediaItem?,
        currentMetadata: MediaMetadata? = controller?.playlistMetadata?.takeIf { !it.title.isNullOrBlank() }
            ?: controller?.mediaMetadata
            ?: item?.mediaMetadata,
    ) {
        val stationMetadata = item?.mediaMetadata
        val title = currentMetadata?.title?.toString()
        val stationName = stationMetadata?.albumArtist?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: stationMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: stationMetadata?.title?.toString()?.takeIf { it.isNotBlank() }

        val info = StationInfo(
            url = item?.mediaId,
            name = stationName,
            logo = stationMetadata?.artworkUri?.toString(),
            network = stationMetadata?.extras?.getString(EXTRA_NETWORK_KEY),
        )
        val trackMetadata = title?.takeIf {
            it.isNotBlank() && (it != stationName)
        }

        _state.update { currentState ->
            currentState.copy(
                stationInfo = info,
                metadata = trackMetadata,
            )
        }
    }

    fun play(
        stationName: String?,
        url: String,
        logoUrl: String?,
        network: String? = null,
        forceReload: Boolean = false,
    ) {
        _state.update { currentState ->
            currentState.copy(
                metadata = if (forceReload || (currentState.stationInfo.url != url)) {
                    null
                } else {
                    currentState.metadata
                },
                playbackError = false,
                isBuffering = true,
            )
        }

        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY
            putExtra(PlaybackService.EXTRA_URL, url)
            putExtra(PlaybackService.EXTRA_NAME, stationName.orEmpty())
            putExtra(PlaybackService.EXTRA_LOGO, logoUrl)
            putExtra(PlaybackService.EXTRA_NETWORK, network)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pause() {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PAUSE
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private companion object {
        const val EXTRA_NETWORK_KEY = "network"
    }
}