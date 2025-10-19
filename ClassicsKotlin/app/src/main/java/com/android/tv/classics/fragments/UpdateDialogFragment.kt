package com.android.tv.classics.fragments

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.android.tv.classics.R
import com.android.tv.classics.utils.AppUpdateManager
import com.android.tv.classics.utils.FocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Dialog fragment to show app update information and controls
 * This uses Leanback UI components optimized for TV navigation
 */
class UpdateDialogFragment : DialogFragment() {
    private lateinit var appUpdateManager: AppUpdateManager
    private var updateInfo: AppUpdateManager.UpdateInfo = AppUpdateManager.UpdateInfo(false)
    
    private lateinit var titleTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var versionTextView: TextView
    private lateinit var notesTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTextView: TextView
    private lateinit var updateButton: Button
    private lateinit var cancelButton: Button
    private lateinit var buttonContainer: ViewGroup
    private lateinit var containerView: FrameLayout
    
    private var progressJob: Job? = null
    private var isDownloading = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        const val TAG = "UpdateDialogFragment"
        
        fun newInstance(updateInfo: AppUpdateManager.UpdateInfo): UpdateDialogFragment {
            val fragment = UpdateDialogFragment()
            val args = Bundle()
            args.putSerializable("update_info", updateInfo)
            fragment.arguments = args
            return fragment
        }
        
        /**
         * Safely show the update dialog
         * @param fragmentManager The FragmentManager to use
         * @param dialog The dialog fragment to show
         */
        fun show(fragmentManager: androidx.fragment.app.FragmentManager, dialog: UpdateDialogFragment) {
            try {
                // Use the commitAllowingStateLoss to prevent IllegalStateException
                // when showing dialog after an activity state change
                val transaction = fragmentManager.beginTransaction()
                transaction.add(dialog, TAG)
                transaction.commitAllowingStateLoss()
            } catch (e: Exception) {
                Timber.e( "Error showing update dialog", e)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManager(requireContext())
        
        arguments?.getSerializable("update_info")?.let {
            updateInfo = it as AppUpdateManager.UpdateInfo
        }
        
        // Use a custom style for better TV experience
        setStyle(STYLE_NO_FRAME, R.style.AppTheme_Dialog)
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        
        // Set up dialog properties for better performance
        dialog.window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // This prevents full screen takeover which can cause hanging
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        
        // Ensure dialog doesn't steal all input focus
        dialog.setCanceledOnTouchOutside(true)
        return dialog
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
        containerView = view as FrameLayout
        
        titleTextView = view.findViewById(R.id.update_title)
        messageTextView = view.findViewById(R.id.update_message)
        versionTextView = view.findViewById(R.id.update_version)
        notesTextView = view.findViewById(R.id.update_notes)
        progressBar = view.findViewById(R.id.update_progress)
        progressTextView = view.findViewById(R.id.update_progress_text)
        updateButton = view.findViewById(R.id.update_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        buttonContainer = view.findViewById(R.id.button_container)
        
        // Configure container for better TV UX
        setupContainerView()
        
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
        
        // Enhanced focus handling for TV
        setupEnhancedNavigation()
    }
    
    private fun setupContainerView() {
        // Configure container view for better Android TV focus behavior
        containerView.apply {
            // Prevent accidental overshoot of focus
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            // Give haptic feedback for focus changes
            isFocusable = true
            isFocusableInTouchMode = true
        }
    }
    
    private fun setupEnhancedNavigation() {
        // Set initial focus with slight delay to ensure view is fully laid out
        mainHandler.postDelayed({
            if (isAdded && !isRemoving) {
                updateButton.requestFocus()
            }
        }, 100)
        
        // Intercept key events at the dialog level for better control
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (updateButton.hasFocus() && cancelButton.visibility == View.VISIBLE) {
                            cancelButton.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (cancelButton.hasFocus()) {
                            updateButton.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (!updateInfo.forceUpdate) {
                            dismiss()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_ESCAPE -> {
                        if (!updateInfo.forceUpdate) {
                            dismiss()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }
        
        // Set proper focus traversal
        updateButton.nextFocusLeftId = R.id.cancel_button
        cancelButton.nextFocusRightId = R.id.update_button
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
        
        // Ensure buttons have proper focus states for TV
        FocusManager.setupButtonForTv(updateButton)
        FocusManager.setupButtonForTv(cancelButton)
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
                    if (!isAdded) return@withContext
                    
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