package com.android.tv.classics.jio.store;

import android.content.Context;

import com.android.tv.classics.LiveTvApplication;
import com.google.android.exoplayer2.ext.cronet.CronetDataSourceFactory;
import com.google.android.exoplayer2.ext.cronet.CronetEngineWrapper;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import org.chromium.net.CronetEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
//import okhttp3.logging.HttpLoggingInterceptor;


public class HttpStore {
    private static Context context;
    public static String userAgent;

    public HttpStore(Context context){
        this.context = context;
        userAgent = Util.getUserAgent(context, "plaYtv");
    }

    private static TimeUnit timeUnit = TimeUnit.SECONDS;

    public static OkHttpClient getHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
//        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
//        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
//        builder.addInterceptor(httpLoggingInterceptor);

        ArrayList arrayList = new ArrayList();
        arrayList.add(Protocol.HTTP_2);
        arrayList.add(Protocol.HTTP_1_1);
        builder.protocols(arrayList);

        builder.connectionPool(new ConnectionPool(0, 1L, TimeUnit.MILLISECONDS));
        builder.retryOnConnectionFailure(true);
        builder.connectTimeout(30, timeUnit);
        builder.readTimeout(30, timeUnit);
        builder.writeTimeout(30, timeUnit);
        try {
            File cacheDir = LiveTvApplication.getInstance().getCacheDir();
            long j = 26214400;
            if (cacheDir.getFreeSpace() < 26214400) {
                j = cacheDir.getFreeSpace() - 1000;
                if (j < 0) {
                    j = 0;
                }
            }
            builder.cache(new Cache(cacheDir, j));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return builder.build();
    }

    public static CronetDataSourceFactory buildCronetDataSourceFactory(DefaultBandwidthMeter defaultBandwidthMeter) {
        try {
            return new CronetDataSourceFactory(new CronetEngineWrapper(
                    new CronetEngine.Builder(context)
                            .setUserAgent(userAgent).build()),
                    Executors.newSingleThreadExecutor(), defaultBandwidthMeter, userAgent);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static DataSource.Factory buildDataSourceFactory(DefaultBandwidthMeter defaultBandwidthMeter) {
        return new DefaultDataSourceFactory(context, defaultBandwidthMeter, buildCronetDataSourceFactory(defaultBandwidthMeter));
    }

    public static HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter defaultBandwidthMeter) {
        return new DefaultHttpDataSourceFactory(userAgent, defaultBandwidthMeter);
    }

    public static String UserAgent(){
        return userAgent;
    }
}

