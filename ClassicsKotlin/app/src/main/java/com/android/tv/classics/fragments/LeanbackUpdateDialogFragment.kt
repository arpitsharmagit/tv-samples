package com.android.tv.classics.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.android.tv.classics.R
import com.android.tv.classics.utils.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Leanback GuidedStepSupportFragment implementation for app update dialog
 * This uses proper TV navigation and styling
 */
class LeanbackUpdateDialogFragment : GuidedStepSupportFragment() {
    private lateinit var appUpdateManager: AppUpdateManager
    private var updateInfo: AppUpdateManager.UpdateInfo = AppUpdateManager.UpdateInfo(false)
    private var progressJob: Job? = null
    private var isDownloading = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        const val TAG = "LeanbackUpdateDialogFragment"
        
        // Action IDs for the guided actions
        private const val ACTION_ID_UPDATE = 1L
        private const val ACTION_ID_CANCEL = 2L
        private const val ACTION_ID_PROGRESS = 3L
        
        /**
         * Create a new instance of the fragment with update info
         */
        fun newInstance(updateInfo: AppUpdateManager.UpdateInfo): LeanbackUpdateDialogFragment {
            val fragment = LeanbackUpdateDialogFragment()
            val args = Bundle()
            args.putSerializable("update_info", updateInfo)
            fragment.arguments = args
            return fragment
        }
        
        /**
         * Safely show the update dialog
         */
        fun show(activity: FragmentActivity, updateInfo: AppUpdateManager.UpdateInfo) {
            try {
                val fragment = newInstance(updateInfo)
                add(activity.supportFragmentManager, fragment)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing update dialog", e)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManager(requireContext())
        
        arguments?.getSerializable("update_info")?.let {
            updateInfo = it as AppUpdateManager.UpdateInfo
        }
    }
    
    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val title = "Update Available"
        val description = "A new version of Jasmine TV is available. " +
                "Would you like to update now?\n\nVersion ${updateInfo.versionName}\n\n" +
                if (updateInfo.releaseNotes.isNotEmpty()) {
                    "What's New:\n${updateInfo.releaseNotes}"
                } else {
                    ""
                }
        
        // Use app icon as the banner image
        return GuidanceStylist.Guidance(
            title,
            description,
            "", // No subheading
            requireContext().getDrawable(R.mipmap.ic_launcher)
        )
    }
    
    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        // Add update button
        val updateAction = GuidedAction.Builder(context)
            .id(ACTION_ID_UPDATE)
            .title("Update Now")
            .description("Install version ${updateInfo.versionName}")
            .build()
        actions.add(updateAction)
        
        // Add cancel button (unless force update)
        if (!updateInfo.forceUpdate) {
            val cancelAction = GuidedAction.Builder(context)
                .id(ACTION_ID_CANCEL)
                .title("Later")
                .build()
            actions.add(cancelAction)
        }
        
        // Add hidden progress action (will be shown during download)
        val progressAction = GuidedAction.Builder(context)
            .id(ACTION_ID_PROGRESS)
            .title("Downloading...")
            .description("0%")
            .enabled(false)
            .build()
        progressAction.isEnabled = false
        actions.add(progressAction)
    }
    
    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_ID_UPDATE -> {
                if (!isDownloading) {
                    startDownload()
                }
            }
            ACTION_ID_CANCEL -> {
                if (isDownloading) {
                    appUpdateManager.cancelDownload()
                    stopProgressTracking()
                    isDownloading = false
                }
                requireActivity().finish()
            }
        }
    }
    
    private fun startDownload() {
        // Update action appearance
        val updateAction = findActionById(ACTION_ID_UPDATE)
        if (updateAction != null) {
            updateAction.isEnabled = false
            notifyActionChanged(findActionPositionById(ACTION_ID_UPDATE))
        }
        
        // Start the download
        val downloadStarted = appUpdateManager.startUpdateDownload(updateInfo)
        
        if (downloadStarted) {
            isDownloading = true
            // Show progress in title
            val progressAction = findActionById(ACTION_ID_PROGRESS)
            if (progressAction != null) {
                progressAction.title = "Downloading..."
                notifyActionChanged(findActionPositionById(ACTION_ID_PROGRESS))
            }
            startProgressTracking()
        } else {
            // Reset UI if download failed to start
            val updateActionFailed = findActionById(ACTION_ID_UPDATE)
            if (updateActionFailed != null) {
                updateActionFailed.isEnabled = true
                notifyActionChanged(findActionPositionById(ACTION_ID_UPDATE))
            }
        }
    }
    
    private fun startProgressTracking() {
        // Cancel any existing job
        progressJob?.cancel()
        
        // Start new tracking job
        progressJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val progress = appUpdateManager.getDownloadProgress()
                
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    
                    if (progress >= 0) {
                        updateProgressAction(progress)
                    }
                }
                
                delay(500) // Update every half second
            }
        }
    }
    
    private fun updateProgressAction(progress: Int) {
        val action = findActionById(ACTION_ID_PROGRESS)
        if (action != null) {
            action.description = "$progress%"
            notifyActionChanged(findActionPositionById(ACTION_ID_PROGRESS))
        }
    }
    
    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
    
    override fun onPause() {
        super.onPause()
        // Ensure we don't continue UI updates if fragment is no longer visible
        if (!isDownloading) {
            stopProgressTracking()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        stopProgressTracking()
    }
}