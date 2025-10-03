package com.android.tv.classics.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.tv.classics.utils.TvLauncherUtils
import kotlinx.coroutines.runBlocking

/** Worker that refresh api token */
class TvTokenRefresher(private val context: Context, params: WorkerParameters) :
        Worker(context, params) {

    override fun doWork(): Result = try {
        synchronize(context)
        Result.success()
    } catch (exc: Exception) {
        Result.failure()
    }

    companion object {
        private val TAG = TvTokenRefresher::class.java.simpleName

        @Synchronized fun synchronize(context: Context) {
            Log.i(TAG, "Starting refresh Token")
            // Since Worker already runs on a background thread, we use runBlocking
            runBlocking {
                try {
                    TvLauncherUtils.refreshToken()
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing token", e)
                }
            }
        }
    }
}