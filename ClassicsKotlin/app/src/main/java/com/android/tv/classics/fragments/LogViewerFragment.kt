package com.android.tv.classics.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.Spannable
import androidx.core.content.ContextCompat
import android.view.KeyEvent
import android.view.animation.AccelerateDecelerateInterpolator
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.yield
import kotlinx.coroutines.cancelChildren
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidanceStylist
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.tv.classics.R
import com.android.tv.classics.utils.LogManager
import timber.log.Timber

class LogViewerFragment : Fragment() {
    private var _binding: View? = null
    private val binding get() = _binding!!
    
    private var logTextView: TextView? = null
    private var logLevelSpinner: Spinner? = null
    private var scrollView: ScrollView? = null
    private var clearLogsButton: androidx.leanback.widget.ImageCardView? = null
    private var increaseFontButton: androidx.leanback.widget.ImageCardView? = null
    private var decreaseFontButton: androidx.leanback.widget.ImageCardView? = null
    private var currentLogLevel = "ERROR" // Default to ERROR level
    private var currentFontSize = 10f // Default font size
    private val minFontSize = 8f
    private val maxFontSize = 20f
    
    private val logLevels = listOf("ERROR", "WARN", "INFO", "DEBUG", "VERBOSE", "ALL") // Reorder to show ERROR first
    
    // Coroutine scope for log updates
    private val logUpdateJob = Job()
    private val logUpdateScope = CoroutineScope(Dispatchers.Main + logUpdateJob)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_log_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = view
        
        // Initialize views
        logTextView = view.findViewById(R.id.logTextView)
        logLevelSpinner = view.findViewById(R.id.logLevelSpinner)
        scrollView = view.findViewById(R.id.scrollView)
        clearLogsButton = view.findViewById(R.id.clearLogsButton)
        increaseFontButton = view.findViewById(R.id.increaseFontButton)
        decreaseFontButton = view.findViewById(R.id.decreaseFontButton)
        
        // Set up spinner
        setupSpinner()
        
        // Setup buttons
        setupClearLogsButton()
        setupFontSizeButtons()
        
        // Load saved font size
        loadSavedFontSize()
        
        // Initial load of logs
        refreshLogs()
        
        // Setup key listener for scrolling with d-pad
        setupScrolling()
    }
    
    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            logLevels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        logLevelSpinner?.adapter = adapter
        logLevelSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLogLevel = logLevels[position]
                refreshLogs()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupScrolling() {
        logTextView?.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    scrollView?.arrowScroll(View.FOCUS_DOWN)
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    scrollView?.arrowScroll(View.FOCUS_UP)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupFontSizeButtons() {
        increaseFontButton?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(48, 48)
            mainImageView.setImageResource(android.R.drawable.ic_menu_zoom)
            titleText = "Increase Font"
            
            setOnClickListener {
                changeFontSize(increase = true)
            }
            
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                animate()
                    .scaleX(if (hasFocus) 1.1f else 1.0f)
                    .scaleY(if (hasFocus) 1.1f else 1.0f)
                    .setDuration(150)
                    .start()
            }
        }

        decreaseFontButton?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(48, 48)
            mainImageView.setImageResource(android.R.drawable.ic_menu_revert)
            titleText = "Decrease Font"
            
            setOnClickListener {
                changeFontSize(increase = false)
            }
            
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                animate()
                    .scaleX(if (hasFocus) 1.1f else 1.0f)
                    .scaleY(if (hasFocus) 1.1f else 1.0f)
                    .setDuration(150)
                    .start()
            }
        }
    }

    private fun changeFontSize(increase: Boolean) {
        val newSize = if (increase) {
            minOf(currentFontSize + 2f, maxFontSize)
        } else {
            maxOf(currentFontSize - 2f, minFontSize)
        }
        
        if (newSize != currentFontSize) {
            currentFontSize = newSize
            logTextView?.textSize = currentFontSize
            saveFontSize()
            // Show feedback
            Toast.makeText(context, "Font size: ${currentFontSize.toInt()}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedFontSize() {
        val prefs = requireContext().getSharedPreferences("LogViewer", Context.MODE_PRIVATE)
        currentFontSize = prefs.getFloat("fontSize", 12f)
        logTextView?.textSize = currentFontSize
    }

    private fun saveFontSize() {
        val prefs = requireContext().getSharedPreferences("LogViewer", Context.MODE_PRIVATE)
        prefs.edit().putFloat("fontSize", currentFontSize).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel all coroutines
        logUpdateJob.cancel()
        
        // Clear references
        logTextView = null
        logLevelSpinner = null
        scrollView = null
        clearLogsButton = null
        increaseFontButton = null
        decreaseFontButton = null
        _binding = null
        
        // Clear any large data structures
        System.gc()
    }
    
    private fun setupClearLogsButton() {
        clearLogsButton?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Set card properties
            titleText = "Clear Logs"
            contentText = "Clear all log files"
            setMainImageDimensions(48, 48)
            mainImageView.setImageResource(android.R.drawable.ic_menu_delete)
            
            // Set focus change listener for visual feedback
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                animate()
                    .scaleX(if (hasFocus) 1.1f else 1.0f)
                    .scaleY(if (hasFocus) 1.1f else 1.0f)
                    .setDuration(150)
                    .start()
            }
            
            // Set key listener for better remote control handling
            setOnKeyListener { _, keyCode, event ->
                when {
                    event.action == KeyEvent.ACTION_DOWN && 
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                     keyCode == KeyEvent.KEYCODE_ENTER) -> {
                        performClick()
                        true
                    }
                    else -> false
                }
            }
            
            setOnClickListener {
                val dialog = GuidedStepSupportFragment.add(
                    parentFragmentManager,
                    ClearLogsGuidedStepFragment.newInstance()
                )
            }
        }
    }
    
    class ClearLogsGuidedStepFragment : GuidedStepSupportFragment() {
        companion object {
            fun newInstance() = ClearLogsGuidedStepFragment()
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            return GuidanceStylist.Guidance(
                "Clear Logs",
                "Are you sure you want to clear all logs?",
                null,
                null
            )
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(GuidedAction.ACTION_ID_OK)
                    .title("Clear")
                    .checked(true)  // Makes this action selected by default
                    .build()
            )
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(GuidedAction.ACTION_ID_CANCEL)
                    .title("Cancel")
                    .build()
            )
        }

        override fun onGuidedActionClicked(action: GuidedAction) {
            when (action.id) {
                GuidedAction.ACTION_ID_OK -> {
                    // First close the dialog since we're on the main thread
                    finishGuidedStepSupportFragments()
                    
                    // Then perform the clear logs operation
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            LogManager.clearLogs()
                            withContext(Dispatchers.Main) {
                                (parentFragment as? LogViewerFragment)?.refreshLogs()
                                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to clear logs")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to clear logs", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                GuidedAction.ACTION_ID_CANCEL -> {
                    finishGuidedStepSupportFragments()
                }
            }
        }
    }

    private fun refreshLogs() {
        // Cancel any previous log update job
        logUpdateJob.children.forEach { it.cancel() }
        
        logUpdateScope.launch(Dispatchers.IO) {
            try {
                val logs = LogManager.readLogs(currentLogLevel)
                
                // Check if fragment is still active
                if (!isAdded) return@launch
                
                withContext(Dispatchers.Main) {
                    logTextView?.let { textView ->
                        if (logs.isEmpty()) {
                            textView.text = "No logs found for level: $currentLogLevel"
                        } else {
                            val spannableBuilder = SpannableStringBuilder()
                            
                            // Add log count header
                            val headerText = "Showing ${logs.size} most recent logs (Level: $currentLogLevel)\n\n"
                            spannableBuilder.append(headerText)
                            
                            // Process logs in chunks to avoid memory spikes
                            logs.chunked(10).forEach { chunk ->
                                chunk.forEach { log ->
                                    spannableBuilder.append("\n")
                                    appendColoredLog(spannableBuilder, log)
                                }
                                // Allow UI to update between chunks
                                yield()
                            }
                            
                            // Clear any previous content before setting new text
                            textView.text = ""
                            textView.text = spannableBuilder
                            
                            // Scroll to bottom to show latest logs
                            scrollView?.post {
                                scrollView?.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e("Error refreshing logs", e)
                if (isAdded) {
                    withContext(Dispatchers.Main) {
                        logTextView?.text = "Error loading logs: ${e.message}"
                    }
                }
            }
        }
    }

    private fun appendColoredLog(builder: SpannableStringBuilder, log: String) {
        val start = builder.length
        builder.append(log)
        val color = when {
            log.contains(" V/") -> R.color.log_verbose
            log.contains(" D/") -> R.color.log_debug
            log.contains(" I/") -> R.color.log_info
            log.contains(" W/") -> R.color.log_warn
            log.contains(" E/") -> R.color.log_error
            else -> null
        }
        
        color?.let {
            val colorSpan = ForegroundColorSpan(ContextCompat.getColor(requireContext(), it))
            builder.setSpan(colorSpan, start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        private val TAG = LogViewerFragment::class.java.simpleName
    }
}