package com.android.tv.classics.jio.store

import android.content.Context
import com.android.tv.classics.LiveTvApplication
import com.google.android.exoplayer2.ext.cronet.CronetDataSourceFactory
import com.google.android.exoplayer2.ext.cronet.CronetEngineWrapper
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util
import org.chromium.net.CronetEngine
import java.io.File
import java.util.ArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol

class HttpStore(context: Context) {
    companion object {
        private lateinit var context: Context
        lateinit var userAgent: String
        private var isLoggingEnabled = false
        private val timeUnit = TimeUnit.SECONDS
        
        fun isLoggingEnabled(): Boolean {
            return isLoggingEnabled
        }

        fun setLoggingEnabled(enabled: Boolean) {
            isLoggingEnabled = enabled
        }

        fun getHttpClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
            if (isLoggingEnabled) {
                // Commented out logging interceptors
                // builder.addInterceptor(new CustomLoggingInterceptor());
                // builder.addNetworkInterceptor(new CustomLoggingInterceptor());
                // HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
                // httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
                // builder.addInterceptor(httpLoggingInterceptor);
                // builder.addNetworkInterceptor(httpLoggingInterceptor);
            }

            val protocols = ArrayList<Protocol>().apply {
                add(Protocol.HTTP_2)
                add(Protocol.HTTP_1_1)
            }
            builder.protocols(protocols)

            builder.connectionPool(ConnectionPool(0, 1L, TimeUnit.MILLISECONDS))
            builder.retryOnConnectionFailure(true)
            builder.connectTimeout(30, timeUnit)
            builder.readTimeout(30, timeUnit)
            builder.writeTimeout(30, timeUnit)
            
            try {
                val cacheDir = LiveTvApplication.getInstance().cacheDir
                var cacheSize = 26214400L
                if (cacheDir.freeSpace < 26214400L) {
                    cacheSize = cacheDir.freeSpace - 1000
                    if (cacheSize < 0) {
                        cacheSize = 0
                    }
                }
                builder.cache(Cache(cacheDir, cacheSize))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            return builder.build()
        }

        fun buildCronetDataSourceFactory(defaultBandwidthMeter: DefaultBandwidthMeter): CronetDataSourceFactory? {
            return try {
                CronetDataSourceFactory(
                    CronetEngineWrapper(
                        CronetEngine.Builder(context)
                            .setUserAgent(userAgent).build()
                    ),
                    Executors.newSingleThreadExecutor(), 
                    defaultBandwidthMeter, 
                    userAgent
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun buildDataSourceFactory(defaultBandwidthMeter: DefaultBandwidthMeter): DataSource.Factory {
            return DefaultDataSourceFactory(
                context, 
                defaultBandwidthMeter, 
                buildHttpDataSourceFactory(defaultBandwidthMeter)
            )
        }

        fun buildHttpDataSourceFactory(defaultBandwidthMeter: DefaultBandwidthMeter): HttpDataSource.Factory {
            return DefaultHttpDataSourceFactory(userAgent, defaultBandwidthMeter)
        }

        fun UserAgent(): String {
            return userAgent
        }
    }

    init {
        HttpStore.context = context
        userAgent = Util.getUserAgent(context, "plaYtv")
    }
}