package com.barteqcz.loqa.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.barteqcz.loqa.MainActivity
import com.barteqcz.loqa.R
import com.barteqcz.loqa.data.PlaybackEvents
import com.barteqcz.loqa.data.RadioRepository
import com.barteqcz.loqa.data.SettingsRepository
import com.barteqcz.loqa.data.model.RadioStation
import com.barteqcz.loqa.data.util.MediaMetadataMapper
import com.barteqcz.loqa.data.util.NetworkResult
import com.barteqcz.loqa.location.LocationClient
import com.google.android.gms.location.Priority
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackEvents: PlaybackEvents

    @Inject
    lateinit var locationClient: LocationClient

    @Inject
    lateinit var repository: RadioRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    private var currentStationName: String? = null
    private var currentStationLogo: String? = null
    private var currentStationUrl: String? = null
    private var currentStationNetwork: String? = null
    private var currentArtworkData: ByteArray? = null
    private var isUpdatingMetadata = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null
    private var locationJob: Job? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                exoPlayer?.pause()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "loqa_playback_channel"

        const val ACTION_PLAY = "com.barteqcz.loqa.PLAY"
        const val ACTION_PAUSE = "com.barteqcz.loqa.PAUSE"
        const val ACTION_STOP = "com.barteqcz.loqa.STOP"

        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_NETWORK = "network"

        private const val MIN_BUFFER_MS = 15000
        private const val MAX_BUFFER_MS = 50000
        private const val BUFFER_FOR_PLAYBACK_MS = 2500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000
        private const val RETRY_DELAY_MS = 5000L

        private const val FADE_IN_MS = 400L
        private const val FADE_OUT_MS = 500L
        private const val FADE_READY_TIMEOUT_MS = 15000L
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
        observeStations()
        
        if (hasLocationPermission()) {
            startBackgroundTracking()
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

    private fun startBackgroundTracking() {
        locationJob?.cancel()
        locationJob = locationClient.getLocationUpdates(
            interval = 60000L,
            minDistance = 100f,
            priority = Priority.PRIORITY_HIGH_ACCURACY,
        )
            .onEach { location ->
                repository.updateNearbyStations(location)
            }
            .launchIn(serviceScope)
    }

    private fun observeStations() {
        repository.stations
            .filterIsInstance<NetworkResult.Success<List<RadioStation>>>()
            .onEach { result ->
                checkCoverageAndSwitch(result.data)
            }
            .launchIn(serviceScope)
    }

    private fun checkCoverageAndSwitch(stations: List<RadioStation>) {
        val currentUrl = currentStationUrl ?: return
        
        val normalizedCurrent = currentUrl.trimEnd('/')
        val playingStation = stations.find { it.streamUrl?.trimEnd('/') == normalizedCurrent }
        
        val isOutOfCoverage = playingStation == null || 
                             (playingStation.coverageKm != null && playingStation.coverageKm <= 0.0) ||
                             (playingStation.distance != null && playingStation.coverageKm != null && playingStation.distance > playingStation.coverageKm)
        
        if (isOutOfCoverage && (exoPlayer?.playWhenReady == true)) {
            serviceScope.launch {
                val favorites = settingsRepository.settingsFlow.first().favoriteStations

                val nextStation = if (currentStationNetwork != null) {
                    stations.asSequence()
                        .filter { it.network == currentStationNetwork }
                        .sortedWith(
                            compareBy<RadioStation> {
                                val inCoverage = it.distance != null && it.coverageKm != null && it.distance <= it.coverageKm
                                val notZeroCoverage = it.coverageKm == null || it.coverageKm > 0.0
                                !(inCoverage && notZeroCoverage)
                            }.thenBy { it.distance ?: Double.MAX_VALUE }
                        )
                        .firstOrNull()
                } else null
                
                val fallbackStation = nextStation ?: stations.asSequence()
                    .sortedWith(
                        compareByDescending<RadioStation> { it.name in favorites }
                            .thenBy {
                                val inCoverage = it.distance != null && it.coverageKm != null && it.distance <= it.coverageKm
                                val notZeroCoverage = it.coverageKm == null || it.coverageKm > 0.0
                                !(inCoverage && notZeroCoverage)
                            }
                            .thenBy { it.distance ?: Double.MAX_VALUE }
                    )
                    .firstOrNull()

                if ((fallbackStation != null) && (fallbackStation.streamUrl != currentUrl)) {
                    fallbackStation.streamUrl?.let { url ->
                        playInternal(fallbackStation.name, url, fallbackStation.logo, fallbackStation.network)
                    }
                } else {
                    stopInternal()
                }
            }
        }
    }

    private suspend fun fadeIn(durationMs: Long = FADE_IN_MS) {
        val player = exoPlayer ?: return
        
        try {
            withTimeout(FADE_READY_TIMEOUT_MS) {
                while (player.playbackState != Player.STATE_READY || !player.isPlaying) {
                    delay(50)
                    if (player.playbackState == Player.STATE_IDLE || player.playerError != null) return@withTimeout
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
            delay(stepDuration)
            exoPlayer?.volume = (currentVol + (totalDelta * i / steps)).coerceIn(0f, 1f)
        }
        exoPlayer?.volume = 1f
    }

    private suspend fun fadeOut(durationMs: Long = FADE_OUT_MS) {
        val player = exoPlayer ?: return
        val startVol = player.volume
        if (startVol <= 0f) return
        
        val steps = 20
        val stepDuration = durationMs / steps
        for (i in steps - 1 downTo 0) {
            delay(stepDuration)
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

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        if (exoPlayer == null) {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            exoPlayer = ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            exoPlayer?.addListener(
                object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        retryPlayback()
                    }
                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        if (isUpdatingMetadata) return
                        
                        val currentItemUrl = exoPlayer?.currentMediaItem?.mediaId
                        if (currentItemUrl != currentStationUrl) return
                        
                        processMetadata(mediaMetadata)
                    }
                    override fun onMetadata(metadata: Metadata) {
                        if (exoPlayer?.currentMediaItem?.mediaId != currentStationUrl) return

                        for (i in 0 until metadata.length()) {
                            val entry = metadata[i]
                            if (entry is IcyInfo) {
                                val icyTitle = entry.title
                                if (!icyTitle.isNullOrBlank()) {
                                    updateRdsAndNotification(icyTitle)
                                }
                            }
                        }
                    }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady && exoPlayer?.playbackState == Player.STATE_IDLE) {
                            exoPlayer?.prepare()
                        }
                    }
                },
            )

            val forwardingPlayer = object : ForwardingPlayer(exoPlayer!!) {
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(COMMAND_SEEK_TO_NEXT)
                        .add(COMMAND_SEEK_TO_PREVIOUS)
                        .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .add(COMMAND_PLAY_PAUSE)
                        .build()
                }

                override fun isCommandAvailable(command: Int): Boolean {
                    return (command == COMMAND_SEEK_TO_NEXT) ||
                           (command == COMMAND_SEEK_TO_PREVIOUS) ||
                           (command == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ||
                           (command == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ||
                           (command == COMMAND_PLAY_PAUSE) ||
                           super.isCommandAvailable(command)
                }

                override fun play() {
                    val player = exoPlayer ?: return
                    val item = player.currentMediaItem
                    if (item != null) {
                        val name = item.mediaMetadata.title?.toString() ?: ""
                        val url = item.mediaId
                        val logo = item.mediaMetadata.artworkUri?.toString()
                        val network = item.mediaMetadata.extras?.getString("network")
                        val forceReload = item.mediaMetadata.extras?.getBoolean("force_reload") ?: false
                        playInternal(name, url, logo, network, forceReload)
                    } else {
                        val url = currentStationUrl
                        val name = currentStationName
                        if (url != null && name != null) {
                            playInternal(name, url, currentStationLogo, currentStationNetwork)
                        } else {
                            super.play()
                        }
                    }
                }

                override fun pause() {
                    playbackJob?.cancel()
                    playbackJob = serviceScope.launch {
                        try {
                            fadeOut()
                        } finally {
                            super.pause()
                            updateRdsAndNotification(null)
                        }
                    }
                }

                override fun seekToNext() { playbackEvents.emitNext() }
                override fun seekToPrevious() { playbackEvents.emitPrevious() }
                override fun seekToNextMediaItem() { playbackEvents.emitNext() }
                override fun seekToPreviousMediaItem() { playbackEvents.emitPrevious() }

                override fun getMediaMetadata(): MediaMetadata {
                    return getCustomMetadata(super.getMediaMetadata())
                }

                override fun getCurrentMediaItem(): MediaItem? {
                    val item = super.getCurrentMediaItem() ?: return null
                    return item.buildUpon().setMediaMetadata(getCustomMetadata(item.mediaMetadata)).build()
                }

                private fun getCustomMetadata(original: MediaMetadata): MediaMetadata {
                    val currentItemUrl = exoPlayer?.currentMediaItem?.mediaId
                    
                    if (currentItemUrl != currentStationUrl) {
                        return MediaMetadataMapper.buildMetadata(
                            title = currentStationName,
                            artist = null,
                            stationName = currentStationName,
                            artworkUri = currentStationLogo,
                            network = currentStationNetwork
                        )
                    }

                    val (displayTitle, displaySubtitle) = MediaMetadataMapper.getEffectiveMetadata(
                        streamTitle = original.title?.toString(),
                        streamArtist = original.artist?.toString(),
                        stationName = currentStationName
                    )
                    
                    return MediaMetadataMapper.buildMetadata(
                        title = displayTitle ?: currentStationName,
                        artist = displaySubtitle,
                        stationName = currentStationName,
                        artworkUri = currentStationLogo,
                        network = currentStationNetwork
                    )
                }
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            mediaSession = MediaSession.Builder(this, forwardingPlayer)
                .setSessionActivity(pendingIntent)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult {
                            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                                .add(Player.COMMAND_SEEK_TO_NEXT)
                                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                .build()
                            return MediaSession.ConnectionResult.accept(
                                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                                playerCommands,
                            )
                        }

                        override fun onAddMediaItems(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            mediaItems: MutableList<MediaItem>
                        ): ListenableFuture<MutableList<MediaItem>> {
                            val updatedItems = mediaItems.map { item ->
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
                            
                            if (firstItem != null && exoPlayer?.isPlaying == true && 
                                exoPlayer?.currentMediaItem?.mediaId != firstItem.mediaId) {
                                serviceScope.launch {
                                    fadeOut()
                                    future.set(updatedItems)
                                }
                            } else {
                                future.set(updatedItems)
                            }
                            return future
                        }
                    },
                )
                .build()
            
            mediaSession?.let { addSession(it) }
        }
    }

    private fun retryPlayback() {
        val name = currentStationName ?: return
        val url = currentStationUrl ?: return
        val logo = currentStationLogo
        val network = currentStationNetwork

        playbackJob?.cancel()
        playbackJob = serviceScope.launch {
            delay(RETRY_DELAY_MS)
            playInternal(name, url, logo, network)
        }
    }

    private fun processMetadata(mediaMetadata: MediaMetadata) {
        val (displayTitle, _) = MediaMetadataMapper.getEffectiveMetadata(
            streamTitle = mediaMetadata.title?.toString(),
            streamArtist = mediaMetadata.artist?.toString(),
            stationName = currentStationName
        )
        updateRdsAndNotification(displayTitle)
    }

    private fun updateRdsAndNotification(info: String?) {
        val stationName = currentStationName?.trim()
        
        val (displayTitle, displaySubtitle) = if (info != null) {
            MediaMetadataMapper.getEffectiveMetadata(
                streamTitle = info,
                streamArtist = null,
                stationName = stationName
            )
        } else {
            stationName to ""
        }
        
        mediaSession?.let { session ->
            val player = session.player
            val index = player.currentMediaItemIndex
            if (index == -1) return
            
            val currentItem = if (index < player.mediaItemCount) player.getMediaItemAt(index) else null
            
            if (currentItem != null) {
                val metadata = MediaMetadataMapper.buildMetadata(
                    title = displayTitle ?: stationName,
                    artist = displaySubtitle,
                    stationName = stationName,
                    artworkUri = currentStationLogo,
                    extras = Bundle().apply { 
                        currentArtworkData?.let {
                            putByteArray("artwork_data", it)
                        }
                    }
                ).buildUpon()
                    .apply {
                        currentArtworkData?.let {
                            setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }
                    .build()

                if (isUpdatingMetadata) return
                isUpdatingMetadata = true
                try {
                    if (currentItem.mediaMetadata.title?.toString() != metadata.title?.toString()) {
                        player.replaceMediaItem(index, currentItem.buildUpon().setMediaMetadata(metadata).build())
                    }
                    player.playlistMetadata = metadata
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isUpdatingMetadata = false
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
        
        initializePlayer()
        loadArtwork(logo)

        playbackJob?.cancel()
        playbackJob = serviceScope.launch {
            val player = exoPlayer ?: return@launch
            
            val shouldReload = forceReload || !isItemLoaded || !player.playWhenReady || 
                             player.playbackState == Player.STATE_IDLE || 
                             player.playbackState == Player.STATE_ENDED ||
                             player.playerError != null

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
                    extras = extras
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
                fadeIn()
            } else {
                if (player.volume < 1f) fadeIn()
            }
        }
        
        updateRdsAndNotification(null)
    }

    private fun stopInternal() {
        playbackJob?.cancel()
        currentStationUrl = null
        currentStationName = null
        currentStationLogo = null
        currentStationNetwork = null
        currentArtworkData = null
        
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
        if (url == null) {
            currentArtworkData = null
            return
        }
        serviceScope.launch {
            try {
                val request = ImageRequest.Builder(this@PlaybackService)
                    .data(url)
                    .size(600, 600)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        currentArtworkData = outputStream.toByteArray()
                        updateRdsAndNotification(null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @OptIn(UnstableApi::class)
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
        locationJob?.cancel()
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
