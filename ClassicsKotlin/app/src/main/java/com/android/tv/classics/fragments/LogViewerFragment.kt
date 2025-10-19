package com.android.tv.classics.fragments

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
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidanceStylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.tv.classics.R
import com.android.tv.classics.utils.LogManager
import timber.log.Timber

class LogViewerFragment : Fragment() {
    private lateinit var logTextView: TextView
    private lateinit var logLevelSpinner: Spinner
    private lateinit var scrollView: ScrollView
    private lateinit var clearLogsButton: androidx.leanback.widget.ImageCardView
    private var currentLogLevel = "ERROR" // Default to ERROR level
    
    private val logLevels = listOf("ERROR", "WARN", "INFO", "DEBUG", "VERBOSE", "ALL") // Reorder to show ERROR first

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_log_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        logTextView = view.findViewById(R.id.logTextView)
        logLevelSpinner = view.findViewById(R.id.logLevelSpinner)
        scrollView = view.findViewById(R.id.scrollView)
        clearLogsButton = view.findViewById(R.id.clearLogsButton)
        
        // Set up spinner
        setupSpinner()
        
        // Setup clear logs button
        setupClearLogsButton()
        
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
        
        logLevelSpinner.adapter = adapter
        logLevelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentLogLevel = logLevels[position]
                refreshLogs()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupScrolling() {
        logTextView.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    scrollView.arrowScroll(View.FOCUS_DOWN)
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    scrollView.arrowScroll(View.FOCUS_UP)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupClearLogsButton() {
        clearLogsButton.apply {
            titleText = "Clear Logs"
            contentText = "Clear all log files"
            setMainImageDimensions(48, 48)
            mainImageView.setImageResource(android.R.drawable.ic_menu_delete)
            
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val logs = LogManager.readLogs(currentLogLevel)
                withContext(Dispatchers.Main) {
                    if (logs.isEmpty()) {
                        logTextView.text = "No logs found for level: $currentLogLevel"
                    } else {
                        val spannableBuilder = SpannableStringBuilder()
                        
                        // Add log count header
                        val headerText = "Showing ${logs.size} most recent logs (Level: $currentLogLevel)\n\n"
                        spannableBuilder.append(headerText)
                        
                        // Add logs
                        logs.forEachIndexed { index, log ->
                            if (index > 0) spannableBuilder.append("\n")
                            appendColoredLog(spannableBuilder, log)
                        }
                        logTextView.text = spannableBuilder
                        // Scroll to bottom to show latest logs
                        scrollView.post {
                            scrollView.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e("Error refreshing logs", e)
                withContext(Dispatchers.Main) {
                    logTextView.text = "Error loading logs: ${e.message}"
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