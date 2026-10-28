/**
 * Main application class initializing Hilt, Timber, Osmdroid, Coil libraries, and handling process lifecycle events.
 */
package com.barteqcz.onqa

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.barteqcz.onqa.location.LocationManager
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class OnqaApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var locationManager: LocationManager

    override fun onCreate() {
        super.onCreate()
        
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Timber.plant(Timber.DebugTree())
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                locationManager.setAppForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                locationManager.setAppForeground(false)
            }
        })
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .okHttpClient {
                val cacheDirectory = File(cacheDir, "http_cache")
                val cacheSize = 50L * 1024L * 1024L
                val cache = Cache(cacheDirectory, cacheSize)

                val userAgent = "Onqa/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

                OkHttpClient.Builder()
                    .cache(cache)
                    .addInterceptor { chain ->
                        val acceptLanguage = Locale.getDefault().toLanguageTag()
                        val request = chain.request().newBuilder()
                            .header("User-Agent", userAgent)
                            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                            .header("Accept-Language", acceptLanguage)
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .crossfade(enable = true)
            .build()
    }
}
