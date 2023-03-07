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
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.android.tv.classics.jio.JioAPI
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.workers.TvMediaSynchronizer
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.error.ANError
import com.androidnetworking.interceptors.HttpLoggingInterceptor
import com.androidnetworking.interfaces.JSONArrayRequestListener
import com.androidnetworking.interfaces.JSONObjectRequestListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit


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

        val isLoggedIn = LiveTvApplication.getPrefStore().getBoolean("isLoggedIn")
        if(isLoggedIn){
            // Refersh Token
                LiveTvApplication.getAuthHeaders()?.let { headers ->
                    JioAPI.RefreshToken(headers)
                        .getAsJSONObject(object : JSONObjectRequestListener {
                            override fun onResponse(response: JSONObject) {
                                headers.put("authToken", response.getString("authToken"))
                                LiveTvApplication.setAuthHeaders(headers)
                                Toast.makeText(activity, "Token Refreshed ", Toast.LENGTH_SHORT)
                                    .show()
                            }

                            override fun onError(error: ANError) {
                                Toast.makeText(
                                    activity,
                                    "Unable to Refresh Token",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
            }
        }
        else{
            Navigation.findNavController(activity, R.id.fragment_container)
                .navigate(NavGraphDirections.actionToLogin())
        }

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

        // Syncs the home screen channels hourly
        // NOTE: It's very important to keep our content fresh in the user's home screen
        WorkManager.getInstance(baseContext).enqueue(
                PeriodicWorkRequestBuilder<TvMediaSynchronizer>(1, TimeUnit.DAYS)
                        .setInitialDelay(1, TimeUnit.DAYS)
                        .setConstraints(Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build())
                        .build())
    }

}
