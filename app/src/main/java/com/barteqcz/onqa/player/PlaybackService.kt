package com.barteqcz.onqa.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.barteqcz.onqa.MainActivity
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.repository.RadioRepository
import com.barteqcz.onqa.data.repository.SettingsRepository
import com.barteqcz.onqa.data.util.NetworkResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackEvents: PlaybackEvents

    @Inject
    lateinit var repository: RadioRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var coverageManager: StationCoverageManager

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    private var currentStationName: String? = null
    private var currentStationLogo: String? = null
    private var currentStationUrl: String? = null
    private var currentStationNetwork: String? = null
    private var currentArtworkData: ByteArray? = null
    private var lastRdsTitle: String? = null
    private var isUpdatingMetadata = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null
    private var artworkJob: Job? = null
    private var retryAttempt = 0
    private var stallJob: Job? = null

    private fun startStallWatchdog() {
        stallJob?.cancel()
        stallJob = serviceScope.launch {
            delay(10000.milliseconds) // 10 seconds timeout for buffering stall
            val player = exoPlayer ?: return@launch
            if ((player.playbackState == Player.STATE_BUFFERING) && !player.isPlaying) {
                Timber.w("Playback stalled in buffering state for 10s. Triggering automatic reconnect...")
                retryPlayback()
            }
        }
    }

    private fun stopStallWatchdog() {
        stallJob?.cancel()
        stallJob = null
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                exoPlayer?.pause()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "onqa_playback_channel"

        const val ACTION_PLAY = "com.barteqcz.onqa.PLAY"
        const val ACTION_PAUSE = "com.barteqcz.onqa.PAUSE"
        const val ACTION_STOP = "com.barteqcz.onqa.STOP"

        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_NETWORK = "network"

        private const val SESSION_ACTION_PREVIOUS = "ACTION_PREV"
        private const val SESSION_ACTION_NEXT = "ACTION_NEXT"

        private const val RETRY_DELAY_MS = 5000L

        private const val FADE_IN_MS = 400L
        private const val FADE_OUT_MS = 500L
        private const val FADE_READY_TIMEOUT_MS = 15000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
        observeStations()

        if (hasLocationPermission()) {
            try {
                repository.startLocationTracking()
            } catch (_: SecurityException) {
                Timber.w("Location permission not granted or revoked.")
            }
        }

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, filter)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun observeStations() {
        serviceScope.launch {
            repository.stations
                .filterIsInstance<NetworkResult.Success<List<RadioStation>>>()
                .collectLatest { (data) ->
                    checkCoverageAndSwitch(data)
                }
        }
    }

    private suspend fun checkCoverageAndSwitch(stations: List<RadioStation>) {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem
        val url = currentItem?.mediaId ?: currentStationUrl
        val metadata = currentItem?.mediaMetadata
        val name = metadata?.title?.toString() ?: metadata?.artist?.toString() ?: currentStationName
        val network = metadata?.extras?.getString("network") ?: currentStationNetwork

        val result = coverageManager.checkCoverage(
            stations = stations,
            currentUrl = url,
            currentName = name,
            currentNetwork = network,
        )

        when (result) {
            is StationCoverageManager.CoverageResult.SwitchTo -> {
                playInternal(result.name, result.url, result.logo, result.network, forceReload = result.forceReload)
            }
            StationCoverageManager.CoverageResult.StopPlayback -> {
                stopInternal()
            }
            StationCoverageManager.CoverageResult.KeepCurrent -> {
            }
        }
    }

    private suspend fun fadeIn(durationMs: Long = FADE_IN_MS) {
        val player = exoPlayer ?: return

        try {
            withTimeout(FADE_READY_TIMEOUT_MS.milliseconds) {
                while ((player.playbackState != Player.STATE_READY) || (!player.isPlaying)) {
                    delay(50.milliseconds)
                    if ((player.playbackState == Player.STATE_IDLE) || (player.playerError != null)) return@withTimeout
                }
            }
        } catch (_: Exception) {
        }

        val currentVol = exoPlayer?.volume ?: 0f
        if (currentVol >= 1f) return

        val steps = 20
        val stepDuration = durationMs / steps
        val totalDelta = 1f - currentVol

        for (i in 1..steps) {
            delay(stepDuration.milliseconds)
            exoPlayer?.volume = (currentVol + ((totalDelta * i) / steps)).coerceIn(0f, 1f)
        }
        exoPlayer?.volume = 1f
    }

    private suspend fun fadeOut(durationMs: Long = FADE_OUT_MS) {
        val player = exoPlayer ?: return
        val startVol = player.volume
        if (startVol <= 0f) return

        val steps = 20
        val stepDuration = durationMs / steps
        for (i in (steps - 1) downTo 0) {
            delay(stepDuration.milliseconds)
            exoPlayer?.volume = (i.toFloat() / steps) * startVol
        }
        exoPlayer?.volume = 0f
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun initializePlayer() {
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
                .setAllowCrossProtocolRedirects(true)

            val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            exoPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            exoPlayer?.addListener(
                object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        stopStallWatchdog()
                        retryPlayback()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                startStallWatchdog()
                            }
                            Player.STATE_READY -> {
                                stopStallWatchdog()
                                retryAttempt = 0
                            }
                            Player.STATE_ENDED, Player.STATE_IDLE -> {
                                stopStallWatchdog()
                            }
                            else -> {}
                        }
                    }
                    override fun onMetadata(metadata: Metadata) {
                        if (isUpdatingMetadata) return

                        val player = exoPlayer ?: return
                        val currentItemUrl = player.currentMediaItem?.mediaId
                        if (currentItemUrl != currentStationUrl) return

                        for (i in 0 until metadata.length()) {
                            when (val entry = metadata[i]) {
                                is IcyInfo -> {
                                    val title = entry.title
                                    Timber.d("In-stream ICY metadata received for $currentStationName: $title")
                                    if (!title.isNullOrBlank()) {
                                        processStreamTitle(title)
                                    }
                                }
                                is TextInformationFrame -> {
                                    val value = entry.values.firstOrNull()
                                    Timber.d("In-stream ID3 metadata received for $currentStationName: $value")
                                    if (!value.isNullOrBlank()) {
                                        processStreamTitle(value)
                                    }
                                }
                                is IcyHeaders -> {
                                    Timber.d("In-stream ICY headers received: station=${entry.name}, genre=${entry.genre}")
                                }
                            }
                        }
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        if (isUpdatingMetadata) return

                        val player = exoPlayer ?: return
                        val currentItemUrl = player.currentMediaItem?.mediaId
                        if (currentItemUrl != currentStationUrl) return

                        if ((player.isPlaying) || (player.playbackState == Player.STATE_BUFFERING)) {
                            processMetadata(mediaMetadata)
                        } else {
                            updateRdsAndNotification(null)
                        }
                    }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady && (exoPlayer?.playbackState == Player.STATE_IDLE)) {
                            exoPlayer?.prepare()
                        }
                        updateRdsAndNotification(null)
                    }
                },
            )

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            mediaSession = MediaSession.Builder(this, exoPlayer!!)
                .setSessionActivity(pendingIntent)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult {
                            val availableSessionCommands = SessionCommands.Builder()
                                .add(SessionCommand(SESSION_ACTION_PREVIOUS, Bundle.EMPTY))
                                .add(SessionCommand(SESSION_ACTION_NEXT, Bundle.EMPTY))
                                .build()

                            return MediaSession.ConnectionResult.accept(
                                availableSessionCommands,
                                session.player.availableCommands,
                            )
                        }

                        override fun onAddMediaItems(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            mediaItems: MutableList<MediaItem>,
                        ): ListenableFuture<MutableList<MediaItem>> {
                            val updatedItems = mediaItems.asSequence().map { item ->
                                val url = item.mediaId
                                item.buildUpon()
                                    .setUri(url.toUri())
                                    .apply {
                                        if (url.contains("m3u8") || url.contains(".m3u")) {
                                            setMimeType(MimeTypes.APPLICATION_M3U8)
                                        } else if (url.contains(".mpd")) {
                                            setMimeType(MimeTypes.APPLICATION_MPD)
                                        }
                                    }
                                    .build()
                            }.toMutableList()

                            val future = SettableFuture.create<MutableList<MediaItem>>()
                            val firstItem = updatedItems.firstOrNull()

                            if ((firstItem != null) && (exoPlayer?.isPlaying == true) &&
                                (exoPlayer?.currentMediaItem?.mediaId != firstItem.mediaId)) {
                                serviceScope.launch {
                                    fadeOut()
                                    future.set(updatedItems)
                                }
                            } else {
                                future.set(updatedItems)
                            }
                            return future
                        }

                        override fun onCustomCommand(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            customCommand: SessionCommand,
                            args: Bundle,
                        ): ListenableFuture<SessionResult> {
                            if (customCommand.customAction == SESSION_ACTION_PREVIOUS) {
                                skipStation(-1)
                            } else if (customCommand.customAction == SESSION_ACTION_NEXT) {
                                skipStation(1)
                            }
                            return SettableFuture.create<SessionResult>().apply {
                                set(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                        }
                    },
                )
                .build()

            val prevButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setDisplayName("Previous")
                .setSessionCommand(SessionCommand(SESSION_ACTION_PREVIOUS, Bundle.EMPTY))
                .build()

            val nextButton = CommandButton.Builder(CommandButton.ICON_NEXT)
                .setDisplayName("Next")
                .setSessionCommand(SessionCommand(SESSION_ACTION_NEXT, Bundle.EMPTY))
                .build()

            mediaSession?.setCustomLayout(listOf(prevButton, nextButton))

            mediaSession?.let { addSession(it) }
        }
    }

    private fun retryPlayback() {
        val name = currentStationName ?: return
        val url = currentStationUrl ?: return
        val logo = currentStationLogo
        val network = currentStationNetwork

        retryAttempt++
        val delayMs = (RETRY_DELAY_MS * minOf(retryAttempt, 5)).coerceAtMost(15000L)
        Timber.w("Playback error occurred. Automatically retrying connection (attempt $retryAttempt) in ${delayMs}ms...")

        playbackJob?.cancel()
        playbackJob = serviceScope.launch {
            delay(delayMs.milliseconds)
            if (currentStationUrl == url) {
                playInternal(name, url, logo, network, forceReload = true)
            }
        }
    }

    private fun processStreamTitle(rawTitle: String) {
        val (displayTitle, _) = MediaMetadataMapper.getEffectiveMetadata(
            streamTitle = rawTitle,
            streamArtist = null,
            stationName = currentStationName,
        )
        updateRdsAndNotification(displayTitle)
    }

    private fun processMetadata(mediaMetadata: MediaMetadata) {
        val title = mediaMetadata.title?.toString()
        val artist = mediaMetadata.artist?.toString()

        if (title.isNullOrBlank() && artist.isNullOrBlank()) return

        val (displayTitle, _) = MediaMetadataMapper.getEffectiveMetadata(
            streamTitle = title,
            streamArtist = artist,
            stationName = currentStationName,
        )
        updateRdsAndNotification(displayTitle)
    }

    private fun updateRdsAndNotification(info: String?) {
        val stationName = currentStationName?.trim()
        val stationUrl = currentStationUrl ?: return
        val player = exoPlayer ?: return

        info?.let {
            lastRdsTitle = it
        }

        val isActuallyPlaying = (player.playWhenReady) || (player.playbackState == Player.STATE_BUFFERING)
        val titleToProcess = if (isActuallyPlaying) (lastRdsTitle ?: info) else null

        val allStations = (repository.stations.value as? NetworkResult.Success)?.data
        val (displayTitle, displaySubtitle) = MediaMetadataMapper.getEffectiveMetadata(
            streamTitle = titleToProcess,
            streamArtist = null,
            stationName = stationName,
            allStations = allStations,
        )

        mediaSession?.let { session ->
            val player = session.player
            val index = player.currentMediaItemIndex
            if (index == -1) return

            val currentItem = if (index < player.mediaItemCount) player.getMediaItemAt(index) else null

            if ((currentItem != null) && (currentItem.mediaId == stationUrl)) {
                val metadata = MediaMetadataMapper.buildMetadata(
                    title = displayTitle ?: stationName,
                    artist = displaySubtitle,
                    stationName = stationName,
                    artworkUri = currentStationLogo,
                    artworkData = currentArtworkData,
                    network = currentStationNetwork,
                )

                if (isUpdatingMetadata) return

                val currentMetadata = currentItem.mediaMetadata
                val isMetadataDifferent = (currentMetadata.title != metadata.title) ||
                        (currentMetadata.artist != metadata.artist) ||
                        (!currentMetadata.artworkData.contentEquals(metadata.artworkData)) ||
                        (currentMetadata.artworkUri != metadata.artworkUri)

                if (isMetadataDifferent) {
                    isUpdatingMetadata = true
                    try {
                        Timber.d("Updating media item & playlist metadata for $stationName: ${metadata.title}. Has artwork: ${currentArtworkData != null}")
                        player.replaceMediaItem(index, currentItem.buildUpon().setMediaMetadata(metadata).build())
                        player.playlistMetadata = metadata
                    } catch (e: Exception) {
                        Timber.e(e, "Error updating metadata")
                    } finally {
                        isUpdatingMetadata = false
                    }
                }
            }
        }
    }

    private fun playInternal(name: String, url: String, logo: String?, network: String?, forceReload: Boolean = false) {
        val isItemLoaded = exoPlayer?.currentMediaItem?.mediaId == url

        currentStationName = name
        currentStationLogo = logo
        currentStationUrl = url
        currentStationNetwork = network
        currentArtworkData = null
        lastRdsTitle = null

        initializePlayer()
        loadArtwork(logo)

        playbackJob?.cancel()
        playbackJob = serviceScope.launch {
            val player = exoPlayer ?: return@launch

            val shouldReload = forceReload || (!isItemLoaded) || (!player.playWhenReady) ||
                    (player.playbackState == Player.STATE_IDLE) ||
                    (player.playbackState == Player.STATE_ENDED) ||
                    (player.playerError != null)

            if (shouldReload) {
                if (player.isPlaying) {
                    fadeOut()
                }

                val extras = Bundle().apply {
                    putString("network", network)
                    if (forceReload) putBoolean("force_reload", true)
                }

                val mediaMetadata = MediaMetadataMapper.buildMetadata(
                    title = name,
                    stationName = name,
                    artworkUri = logo,
                    network = network,
                    extras = extras,
                )

                val mediaItem = MediaItem.Builder()
                    .setMediaId(url)
                    .setUri(url)
                    .setMediaMetadata(mediaMetadata)
                    .apply {
                        if (url.contains("m3u8") || url.contains(".m3u")) {
                            setMimeType(MimeTypes.APPLICATION_M3U8)
                        } else if (url.contains(".mpd")) {
                            setMimeType(MimeTypes.APPLICATION_MPD)
                        }
                    }
                    .build()

                player.setMediaItem(mediaItem)
                player.stop()
                player.volume = 0f
                player.prepare()
                player.play()
                updateRdsAndNotification(null)
                fadeIn()
            } else {
                if (player.volume < 1f) fadeIn()
                updateRdsAndNotification(null)
            }
        }
    }

    private fun skipStation(delta: Int) {
        serviceScope.launch {
            val allStations = (repository.stations.value as? NetworkResult.Success)?.data ?: return@launch
            if (allStations.isEmpty()) return@launch

            val player = exoPlayer
            val currentUrl = player?.currentMediaItem?.mediaId ?: currentStationUrl
            val currentIndex = allStations.indexOfFirst {
                it.matchesUrl(currentUrl) || it.name.equals(currentStationName, ignoreCase = true)
            }

            val nextIndex = when {
                currentIndex == -1 -> 0
                delta > 0 -> (currentIndex + 1) % allStations.size
                else -> if (currentIndex == 0) allStations.lastIndex else currentIndex - 1
            }

            val s = allStations[nextIndex]
            val useHq = settingsRepository.settingsFlow.first().useHqStream
            val url = s.getStreamUrl(useHq) ?: s.streamUrl ?: return@launch
            playInternal(s.name, url, s.logo, s.network, forceReload = true)

            if (delta > 0) playbackEvents.emitNext() else playbackEvents.emitPrevious()
        }
    }

    private fun stopInternal() {
        playbackJob?.cancel()
        stopStallWatchdog()
        currentStationUrl = null
        currentStationName = null
        currentStationLogo = null
        currentStationNetwork = null
        currentArtworkData = null
        lastRdsTitle = null

        playbackJob = serviceScope.launch {
            try {
                fadeOut()
            } finally {
                exoPlayer?.let {
                    it.stop()
                    it.clearMediaItems()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun loadArtwork(url: String?) {
        artworkJob?.cancel()
        if (url == null) {
            currentArtworkData = null
            return
        }
        artworkJob = serviceScope.launch {
            try {
                Timber.d("Loading artwork from: $url")
                val request = ImageRequest.Builder(this@PlaybackService)
                    .data(url)
                    .size(500, 500)
                    .crossfade(enable = false)
                    .allowHardware(enable = false)
                    .build()
                when (val result = imageLoader.execute(request)) {
                    is SuccessResult -> {
                        val bitmap = result.drawable.toBitmap(width = 500, height = 500)
                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        currentArtworkData = outputStream.toByteArray()
                        updateRdsAndNotification(null)
                    }
                    is coil.request.ErrorResult -> {
                        Timber.e(result.throwable, "Failed to load artwork from $url")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading artwork")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return super.onStartCommand(intent, flags, startId)
                val name = intent.getStringExtra(EXTRA_NAME) ?: ""
                val logo = intent.getStringExtra(EXTRA_LOGO)
                val network = intent.getStringExtra(EXTRA_NETWORK)
                playInternal(name, url, logo, network)
            }
            ACTION_PAUSE -> {
                mediaSession?.player?.pause()
            }
            ACTION_STOP -> stopInternal()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopInternal()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}