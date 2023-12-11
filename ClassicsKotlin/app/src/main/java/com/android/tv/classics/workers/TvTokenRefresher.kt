package com.android.tv.classics.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.tv.classics.utils.TvLauncherUtils
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.interceptors.HttpLoggingInterceptor

/** Worker that refresh api token */
class TvTokenRefresher(private val context: Context, params: WorkerParameters) :
        Worker(context, params) {

    override fun doWork(): Result = try {
        AndroidNetworking.initialize(context)
        AndroidNetworking.enableLogging() // simply enable logging

        AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY) // enabling logging with level


        synchronize(context)

        Result.success()
    } catch (exc: Exception) {
        Result.failure()
    }

    companion object {
        private val TAG = TvTokenRefresher::class.java.simpleName

        @Synchronized fun synchronize(context: Context) {
            Log.i(TAG, "Starting refresh Token")
            TvLauncherUtils.refreshToken()
        }
    }
}