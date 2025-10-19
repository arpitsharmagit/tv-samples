package com.android.tv.classics.activities

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.android.tv.classics.R
import com.android.tv.classics.fragments.LogViewerFragment
import timber.log.Timber

class LogViewerActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        // Example Logs to test the viewer
        Timber.v("Starting LogViewerActivity...")
        Timber.d("Database connection initialized.")
        Timber.i("User navigated to Log Viewer screen.")
        Timber.w("Memory usage approaching 80%. Check for leaks.")
    }
}