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
import com.android.tv.classics.databinding.ActivityMainBinding
import com.android.tv.classics.fragments.UpdateDialogFragment
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.utils.AppUpdateManager
import com.android.tv.classics.utils.TvLauncherUtils
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.interceptors.HttpLoggingInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.android.tv.classics.utils.FocusManager

/** Entry point for the Android TV application */
class MainActivity : FragmentActivity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidNetworking.initialize(applicationContext)
        AndroidNetworking.enableLogging() // simply enable logging
        AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY) // enabling logging with level

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Initialize update manager
        appUpdateManager = AppUpdateManager(this)
        appUpdateManager.initialize()
        
        // Check for updates
//         checkForAppUpdates()

        handleIntent(intent)

        // NOTE: It's very important to keep our api token fresh
//        WorkManager.getInstance(baseContext).enqueue(
//                PeriodicWorkRequestBuilder<TvTokenRefresher>(60, TimeUnit.MINUTES)
//                        .setInitialDelay(30, TimeUnit.MINUTES)
//                        .setConstraints(Constraints.Builder()
//                                .setRequiredNetworkType(NetworkType.CONNECTED)
//                                .build())
//                        .build())
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")
        setIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        val activity = this
        val db = TvMediaDatabase.getInstance(this)
        
        // Navigates to other fragments based on Intent's action
        // [MainActivity] is the main entry point for all intent filters
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEARCH) {
            val uri = intent.data ?: Uri.EMPTY
            Log.d(TAG, "Intent ${intent.action} received: $uri")
            when (uri.pathSegments.firstOrNull()) {

                // Navigates to now playing screen for chosen "program"
                "program" -> lifecycleScope.launch {
                    uri.lastPathSegment?.let { db.metadata().findById(it) }?.let { metadata ->
                        Log.d(TAG, "Navigating to now playing for $metadata")
                        withContext(Dispatchers.Main) {
                            Navigation.findNavController(activity, R.id.fragment_container)
                                    .navigate(NavGraphDirections.actionToNowPlaying(metadata))
                        }
                    }
                }

                // Scrolls to chosen "channel" in browse fragment
                "channel" -> lifecycleScope.launch {
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
        } else if(LiveTvApplication.getMobileNumber() !=null && LiveTvApplication.getAuthHeaders()
                .isNotEmpty()
        ){
            Log.d(TAG, "Mobile No. "+ LiveTvApplication.getMobileNumber()+ " AuthHeaders Found.")
            lifecycleScope.launch {
                try {
                    TvLauncherUtils.refreshToken()
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing token", e)
                }
                Navigation.findNavController(activity, R.id.fragment_container)
                    .navigate(NavGraphDirections.actionToMediaBrowser())
            }
        } else {
            Navigation.findNavController(activity, R.id.fragment_container)
                .navigate(NavGraphDirections.actionMobileStep())
        }
    }
    
    /**
     * Check for app updates from the remote server
     */
    private fun checkForAppUpdates() {
        lifecycleScope.launch {
            try {
                val updateInfo = appUpdateManager.checkForUpdates()
                
                if (updateInfo.isUpdateAvailable) {
                    Log.d(TAG, "Update available: ${updateInfo.versionName}")
                    
                    // Show update dialog to the user
                    val updateDialog = UpdateDialogFragment.newInstance(updateInfo)
                    updateDialog.show(supportFragmentManager, UpdateDialogFragment.TAG)
                } else {
                    Log.d(TAG, "No updates available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        
        // Clean up the app update manager
        appUpdateManager.destroy()
        
        // Ensure cleanup of any resources when activity is destroyed
        val navController = Navigation.findNavController(this, R.id.fragment_container)
        try {
            // Clear focus manager state
            FocusManager.clearAll()
            
            // Check if any fragment is in the back stack and clear it
            if (navController.currentDestination?.id != R.id.mobile_step_fragment && 
                navController.currentDestination?.id != R.id.media_browser_fragment) {
                navController.popBackStack(R.id.media_browser_fragment, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up fragments", e)
        }
    }
}
