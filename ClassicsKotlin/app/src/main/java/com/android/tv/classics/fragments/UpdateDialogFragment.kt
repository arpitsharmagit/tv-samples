package com.android.tv.classics.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
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
 * Dialog fragment to show app update information and controls
 */
class UpdateDialogFragment : DialogFragment() {
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var updateInfo: AppUpdateManager.UpdateInfo
    
    private lateinit var titleTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var versionTextView: TextView
    private lateinit var notesTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTextView: TextView
    private lateinit var updateButton: Button
    private lateinit var cancelButton: Button
    
    private var progressJob: Job? = null
    private var isDownloading = false
    
    companion object {
        const val TAG = "UpdateDialogFragment"
        
        fun newInstance(updateInfo: AppUpdateManager.UpdateInfo): UpdateDialogFragment {
            val fragment = UpdateDialogFragment()
            fragment.updateInfo = updateInfo
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManager(requireContext())
        
        // Use a custom style for the dialog
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_update_dialog, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        titleTextView = view.findViewById(R.id.update_title)
        messageTextView = view.findViewById(R.id.update_message)
        versionTextView = view.findViewById(R.id.update_version)
        notesTextView = view.findViewById(R.id.update_notes)
        progressBar = view.findViewById(R.id.update_progress)
        progressTextView = view.findViewById(R.id.update_progress_text)
        updateButton = view.findViewById(R.id.update_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        
        // Set up UI with update info
        setupUi()
        
        // Set up click listeners
        updateButton.setOnClickListener {
            if (isDownloading) {
                // If already downloading, do nothing
                return@setOnClickListener
            }
            
            startDownload()
        }
        
        cancelButton.setOnClickListener {
            if (isDownloading) {
                // If downloading, cancel the download
                appUpdateManager.cancelDownload()
                stopProgressTracking()
                isDownloading = false
                dismiss()
            } else {
                // Otherwise just dismiss the dialog
                dismiss()
            }
        }
        
        // Make the dialog uncancelable if it's a force update
        isCancelable = !updateInfo.forceUpdate
        
        // Hide the cancel button if it's a force update
        if (updateInfo.forceUpdate) {
            cancelButton.visibility = View.GONE
        }
    }
    
    private fun setupUi() {
        titleTextView.text = "Update Available"
        messageTextView.text = "A new version of Jasmine TV is available. Would you like to update now?"
        versionTextView.text = "Version ${updateInfo.versionName}"
        
        if (updateInfo.releaseNotes.isNotEmpty()) {
            notesTextView.text = updateInfo.releaseNotes
            notesTextView.visibility = View.VISIBLE
        } else {
            notesTextView.visibility = View.GONE
        }
        
        // Hide progress initially
        progressBar.visibility = View.GONE
        progressTextView.visibility = View.GONE
    }
    
    private fun startDownload() {
        // Update UI for download state
        updateButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressTextView.visibility = View.VISIBLE
        progressBar.progress = 0
        progressTextView.text = "Preparing download..."
        
        // Start the download
        val downloadStarted = appUpdateManager.startUpdateDownload(updateInfo)
        
        if (downloadStarted) {
            isDownloading = true
            updateButton.text = "Downloading..."
            startProgressTracking()
        } else {
            // Reset UI if download failed to start
            updateButton.isEnabled = true
            progressBar.visibility = View.GONE
            progressTextView.visibility = View.GONE
            progressTextView.text = "Failed to start download"
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
                    if (progress >= 0) {
                        progressBar.progress = progress
                        progressTextView.text = "Downloading: $progress%"
                    }
                }
                
                delay(500) // Update every half second
            }
        }
    }
    
    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopProgressTracking()
    }
}