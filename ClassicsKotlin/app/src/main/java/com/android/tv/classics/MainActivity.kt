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
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit


/** Entry point for the Android TV application */
class MainActivity : FragmentActivity() {

    private lateinit var mobileNumber: String
    private lateinit var dbRef: DatabaseReference
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private val database = FirebaseDatabase.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidNetworking.initialize(applicationContext)
//        AndroidNetworking.enableLogging() // simply enable logging
//        AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY) // enabling logging with level

        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val activity = this
        val db = TvMediaDatabase.getInstance(this)

        val isLoggedIn = LiveTvApplication.getPrefStore().getBoolean("isLoggedIn")
        if(isLoggedIn){
            //setup token listen from realtime db
            mobileNumber = LiveTvApplication.getPrefStore().getData("mobileNumber")
            dbRef = database.getReference(mobileNumber)
            dbRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val cloudHeaders =  dataSnapshot.getValue()
                    if(cloudHeaders!= null){
                        LiveTvApplication.setAuthHeaders(cloudHeaders as MutableMap<String, String>)
                    }else{
                        Navigation.findNavController(activity, R.id.fragment_container)
                            .navigate(NavGraphDirections.actionToLoginfragment())
                    }
                    Log.d(TAG, "Token read from realtime db")
                }

                override fun onCancelled(error: DatabaseError) {
                    // Failed to read value
                    Log.w(TAG, "Unable to read token from realtime db.", error.toException())
                }
            })
            refreshToken()
        }
        else{
            Navigation.findNavController(activity, R.id.fragment_container)
                .navigate(NavGraphDirections.actionToLoginfragment())
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
//        WorkManager.getInstance(baseContext).enqueue(
//                PeriodicWorkRequestBuilder<TvMediaSynchronizer>(1, TimeUnit.DAYS)
//                        .setInitialDelay(1, TimeUnit.DAYS)
//                        .setConstraints(Constraints.Builder()
//                                .setRequiredNetworkType(NetworkType.CONNECTED)
//                                .build())
//                        .build())
    }

    private fun refreshToken(){
        // Refresh Token
        LiveTvApplication.getAuthHeaders()?.let { headers ->
            JioAPI.RefreshToken(headers)
                .getAsJSONObject(object : JSONObjectRequestListener {
                    override fun onResponse(response: JSONObject) {
                        headers.put("authToken", response.getString("authToken"))
                        dbRef.setValue(headers)
                        LiveTvApplication.setAuthHeaders(headers)
                        Toast.makeText(applicationContext, "Token Refreshed ", Toast.LENGTH_SHORT)
                            .show()
                    }

                    override fun onError(error: ANError) {
                        Log.e(TAG, "Unable to Refresh Token.", error.cause)
                        Toast.makeText(
                            applicationContext,
                            "Unable to Refresh Token",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }
    }
}
