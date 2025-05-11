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
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.Navigation
import com.android.tv.classics.databinding.ActivityMainBinding
import com.android.tv.classics.fragments.LeanbackUpdateDialogFragment
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
        private const val PERMISSION_REQUEST_CODE = 100
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
        
        // Check and request permissions
        requestRequiredPermissions()
        
        // Initialize update manager
        appUpdateManager = AppUpdateManager(this)
        appUpdateManager.initialize()
        
        // Check for updates
        // checkForAppUpdates()

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
    
    /**
     * Request storage permissions required for app update functionality
     */
    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            
            val hasWritePermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasReadPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasWritePermission || !hasReadPermission) {
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all permissions were granted
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            
            if (!allGranted) {
                Log.w(TAG, "Storage permissions not granted - updates may not work properly")
                LiveTvApplication.showToast("Storage permissions required for app updates")
            }
        }
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
        // Start the update check with a slight delay to allow the UI to initialize
        lifecycleScope.launch {
            // Short delay before checking for updates to avoid UI conflicts
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.delay(2000) // 2 second delay to let the main UI settle
            }
            
            try {
                val updateInfo = appUpdateManager.checkForUpdates()
                
                if (updateInfo.isUpdateAvailable) {
                    Log.d(TAG, "Update available: ${updateInfo.versionName}")
                    
                    // Make sure we're on the main thread
                    withContext(Dispatchers.Main) {
                        // Ensure the activity is still active before showing the dialog
                        if (!isFinishing && !isDestroyed) {
                            // Show update dialog to the user using Leanback GuidedStepFragment
                            LeanbackUpdateDialogFragment.show(this@MainActivity, updateInfo)
                        } else {
                            Log.d(TAG, "Activity no longer active, skipping update dialog")
                        }
                    }
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
