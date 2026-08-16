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

import android.app.AlertDialog
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
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.navigation.Navigation
import com.android.tv.classics.databinding.ActivityMainBinding
import com.android.tv.classics.fragments.LeanbackUpdateDialogFragment
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.utils.AppUpdateManager
import com.android.tv.classics.utils.TvLauncherUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.android.tv.classics.auth.GoogleSignInStepFragment
import com.android.tv.classics.utils.FocusManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import timber.log.Timber

/** Entry point for the Android TV application */
class MainActivity : FragmentActivity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PERMISSION_REQUEST_CODE = 100
        private lateinit var auth: FirebaseAuth
        private lateinit var authStateListener: FirebaseAuth.AuthStateListener
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var appUpdateManager: AppUpdateManager
    /** True after we've navigated to content for the first time (guards against re-nav on resume) */
    private var hasShownContent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        setupAuthStateListener()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Check and request permissions
        requestRequiredPermissions()
        
        // Initialize update manager
        appUpdateManager = AppUpdateManager(this)
        appUpdateManager.initialize()
        
        // Check for updates on startup (silent — only shows dialog if update is available)
        checkForAppUpdates()



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
     * Sets up the listener to monitor changes in the user's authentication state.
     */
    private fun setupAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser

            // Check if the user is signed in AND is NOT an anonymous user (promoted to Google)
            val isGoogleSignedIn = user != null && user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

            if (isGoogleSignedIn) {
                // User is fully authenticated via Google. Proceed to main content.
                Timber.d( "User ${user?.uid} signed in with Google. Showing main content.")
                showMainContent()
            } else {
                // User is either null (not signed in) or only anonymously signed in.
                // We show the GuidedStepSupportFragment to prompt for Google Sign-In.
                Timber.d( "User is not fully signed in. Showing authentication flow.")
                showAuthFragment()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Attach the listener when the activity starts
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        // Detach the listener when the activity stops to prevent memory leaks
        auth.removeAuthStateListener(authStateListener)
    }

    /**
     * Displays the main content fragment (e.g., the BrowseSupportFragment).
     * This is called only when the user is successfully signed in via Google.
     */
    private fun showMainContent() {

        // Dismiss any GuidedStepSupportFragments (auth screen) if they are currently visible
        supportFragmentManager.findFragmentByTag("AUTH_STEP")?.let { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        }

        // Check if the main content is already loaded to avoid recreation
        if (supportFragmentManager.findFragmentByTag("MAIN_CONTENT") == null) {
            handleIntent(intent)
        }
    }

    /**
     * Displays the Google Sign-In GuidedStepSupportFragment.
     */
    private fun showAuthFragment() {
        // Check if the GuidedStepSupportFragment is already showing
        if (supportFragmentManager.findFragmentByTag("AUTH_STEP") == null) {
            val authStepFragment = GoogleSignInStepFragment()

            // Use GuidedStepSupportFragment.add() to display it modally on top of existing content
            GuidedStepSupportFragment.add(
                supportFragmentManager,
                authStepFragment,
                R.id.fragment_container
            )
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
                Timber.w( "Storage permissions not granted - updates may not work properly")
                LiveTvApplication.showToast("Storage permissions required for app updates")
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("onNewIntent called")
        setIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        val activity = this
        val db = TvMediaDatabase.getInstance(this)

        // Guard: if we've already shown content (e.g. app resumed from Home button),
        // don't re-navigate — the existing fragment back stack is still valid.
        if (hasShownContent) {
            val currentDestId = try {
                Navigation.findNavController(activity, R.id.fragment_container)
                    .currentDestination?.id
            } catch (e: Exception) { null }
            if (currentDestId == R.id.media_browser_fragment ||
                currentDestId == R.id.now_playing_fragment) {
                Timber.d("App resumed from background, keeping current destination")
                return
            }
        }
        
        // Navigates to other fragments based on Intent's action
        // [MainActivity] is the main entry point for all intent filters
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEARCH) {
            val uri = intent.data ?: Uri.EMPTY
            Timber.d("Intent ${intent.action} received: $uri")
            when (uri.pathSegments.firstOrNull()) {

                // Navigates to now playing screen for chosen "program"
                "program" -> lifecycleScope.launch {
                    uri.lastPathSegment?.let { db.metadata().findById(it) }?.let { metadata ->
                        Timber.d("Navigating to now playing for $metadata")
                        withContext(Dispatchers.Main) {
                            Navigation.findNavController(activity, R.id.fragment_container)
                                    .navigate(NavGraphDirections.actionToNowPlaying(metadata))
                        }
                    }
                }

                // Scrolls to chosen "channel" in browse fragment
                "channel" -> lifecycleScope.launch {
                    val channelId = uri.lastPathSegment
                    Timber.d("Navigating to browser for channel $channelId")
                    withContext(Dispatchers.Main) {
                        Navigation.findNavController(activity, R.id.fragment_container)
                                .navigate(NavGraphDirections.actionToMediaBrowser()
                                        .setChannelId(channelId))
                    }
                }

                else -> Timber.w( "VIEW intent received but unrecognized URI: $uri")
            }
            hasShownContent = true
        } else if(LiveTvApplication.getMobileNumber() !=null && LiveTvApplication.getAuthHeaders()
                .isNotEmpty()
        ){
            Timber.d("Mobile No. "+ LiveTvApplication.getMobileNumber()+ " AuthHeaders Found.")
            hasShownContent = true
            // Navigate to browser immediately — don't block on token refresh
            Navigation.findNavController(activity, R.id.fragment_container)
                .navigate(NavGraphDirections.actionToMediaBrowser())
            lifecycleScope.launch {
                try {
                    TvLauncherUtils.refreshToken()
                } catch (e: Exception) {
                    Timber.e( "Error refreshing token", e)
                }
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
                    Timber.d("Update available: ${updateInfo.versionName}")
                    
                    // Make sure we're on the main thread
                    withContext(Dispatchers.Main) {
                        // Ensure the activity is still active before showing the dialog
                        if (!isFinishing && !isDestroyed) {
                            // Show update dialog to the user using Leanback GuidedStepFragment
                            LeanbackUpdateDialogFragment.show(this@MainActivity, updateInfo)
                        } else {
                            Timber.d("Activity no longer active, skipping update dialog")
                        }
                    }
                } else {
                    Timber.d("No updates available")
                }
            } catch (e: Exception) {
                Timber.e( "Error checking for updates", e)
            }
        }
    }
    
    override fun onBackPressed() {
        val navController = try {
            Navigation.findNavController(this, R.id.fragment_container)
        } catch (e: Exception) { null }

        val currentDest = navController?.currentDestination?.id
        if (currentDest == R.id.media_browser_fragment) {
            AlertDialog.Builder(this)
                .setTitle("Exit JioTV?")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Exit") { _, _ -> finishAffinity() }
                .setNegativeButton("Stay") { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy called")
        
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
            Timber.e( "Error cleaning up fragments", e)
        }
    }
}
