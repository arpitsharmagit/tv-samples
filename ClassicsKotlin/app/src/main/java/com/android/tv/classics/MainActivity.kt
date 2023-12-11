/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.classics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.navigation.Navigation
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.utils.TvLauncherUtils
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.interceptors.HttpLoggingInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/** Entry point for the Android TV application */
class MainActivity : FragmentActivity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidNetworking.initialize(applicationContext)
        AndroidNetworking.enableLogging() // simply enable logging

        AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY) // enabling logging with level


        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val activity = this
        val db = TvMediaDatabase.getInstance(this)

        // Navigates to other fragments based on Intent's action
        // [MainActivity] is the main entry point for all intent filters
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEARCH) {
            val uri = intent.data ?: Uri.EMPTY
            Log.d(TAG, "Intent ${intent.action} received: $uri")
            when (uri.pathSegments.firstOrNull()) {

                // Navigates to now playing screen for chosen "program"
                "program" -> GlobalScope.launch {
                    uri.lastPathSegment?.let { db.metadata().findById(it) }?.let { metadata ->
                        Log.d(TAG, "Navigating to now playing for $metadata")
                        withContext(Dispatchers.Main) {
                            Navigation.findNavController(activity, R.id.fragment_container)
                                    .navigate(NavGraphDirections.actionToNowPlaying(metadata))
                        }
                    }
                }

                // Scrolls to chosen "channel" in browse fragment
                "channel" -> GlobalScope.launch {
                    val channelId = uri.lastPathSegment
                    Log.d(TAG, "Navigating to browser for channel $channelId")
                    withContext(Dispatchers.Main) {
                        Navigation.findNavController(activity, R.id.fragment_container)
                                .navigate(NavGraphDirections.actionToMediaBrowser()
                                        .setChannelId(channelId))
                    }
                }

                else -> Log.w(TAG, "VIEW intent received but unrecognized URI: $uri")
            }
        }
        if(LiveTvApplication.getMobileNumber() !=null && LiveTvApplication.getAuthHeaders() != null){
            Log.d(TAG, "Mobile No. "+ LiveTvApplication.getMobileNumber()+ " AuthHeaders Found.")
            TvLauncherUtils.refreshToken()
            Navigation.findNavController(activity, R.id.fragment_container)
                .navigate(NavGraphDirections.actionToMediaBrowser())

        }else{
            Navigation.findNavController(activity, R.id.fragment_container)
                .navigate(NavGraphDirections.actionMobileStep())
        }

        // NOTE: It's very important to keep our api token fresh
//        WorkManager.getInstance(baseContext).enqueue(
//                PeriodicWorkRequestBuilder<TvTokenRefresher>(60, TimeUnit.MINUTES)
//                        .setInitialDelay(30, TimeUnit.MINUTES)
//                        .setConstraints(Constraints.Builder()
//                                .setRequiredNetworkType(NetworkType.CONNECTED)
//                                .build())
//                        .build())
    }
}
