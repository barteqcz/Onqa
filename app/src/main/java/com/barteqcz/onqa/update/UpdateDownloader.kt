package com.barteqcz.onqa.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class UpdateDownloader : Service() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url != null) {
            startDownload(url)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String) {
        createNotificationChannel()
        val notification = createNotification(0)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            try {
                val file = downloadFile(url)
                if (file != null) {
                    installApk(file)
                }
            } catch (e: Exception) {
                Timber.e(e, "Download failed")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun downloadFile(url: String): File? {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) return null
        
        val body = response.body
        val totalBytes = body.contentLength()
        val destinationFile = File(externalCacheDir, "update.apk")
        
        body.byteStream().use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead: Long = 0
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val progress = (totalRead * 100 / totalBytes).toInt()
                        updateNotification(progress, progress == 100)
                    }
                }
            }
        }
        return destinationFile
    }

    private fun updateNotification(progress: Int, isComplete: Boolean = false) {
        val notification = createNotification(progress, isComplete)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(progress: Int, isComplete: Boolean = false) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(if (isComplete) "Update downloaded" else "Downloading update")
        .setSmallIcon(if (isComplete) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(!isComplete)
        .setProgress(100, progress, !isComplete && progress == 0)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "update_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_URL = "extra_url"

        fun start(context: Context, url: String) {
            val intent = Intent(context, UpdateDownloader::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
