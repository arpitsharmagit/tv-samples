package com.android.tv.classics.utils

import android.graphics.Rect
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment

/**
 * Utility class to manage focus positions between fragment transitions
 * This allows TV navigation to remember focus positions when navigating between fragments
 * and provides utilities for better Android TV focus management
 */
object FocusManager {
    private val TAG = FocusManager::class.java.simpleName
    
    // Map to store focus state by destination ID
    private val focusPositions = mutableMapOf<Int, FocusState>()
    
    // Focus padding for TV navigation
    private const val TV_FOCUS_PADDING = 10
    
    /**
     * Save the current focus state for a specific fragment
     * @param fragment The fragment whose focus state is being saved
     * @param destinationId The navigation destination ID to save the state for
     */
    fun saveFocusState(fragment: Fragment, destinationId: Int) {
        val focusedView = fragment.activity?.currentFocus ?: return
        val focusedId = focusedView.id
        
        // Store position information if view has ID
        if (focusedId != View.NO_ID) {
            Log.d(TAG, "Saving focus state for destination $destinationId, view ID: $focusedId")
            focusPositions[destinationId] = FocusState(focusedId)
        }
    }
    
    /**
     * Restore focus state for a specific fragment
     * @param fragment The fragment whose focus state is being restored
     * @param destinationId The navigation destination ID to restore the state for
     * @param rootView The root view of the fragment to search for focusable views
     */
    fun restoreFocusState(fragment: Fragment, destinationId: Int, rootView: View) {
        val focusState = focusPositions[destinationId] ?: return
        
        // Find the view by ID and focus it
        val viewToFocus = rootView.findViewById<View>(focusState.viewId)
        
        if (viewToFocus != null && viewToFocus.isShown && viewToFocus.isFocusable) {
            Log.d(TAG, "Restoring focus for destination $destinationId to view ID: ${focusState.viewId}")
            viewToFocus.requestFocus()
        } else {
            Log.d(TAG, "Could not restore focus - finding first focusable view")
            findFirstFocusableView(rootView)?.requestFocus()
        }
    }
    
    /**
     * Find the first focusable view in a view hierarchy
     * @param root The root view to start searching from
     * @return The first focusable view found, or null if none found
     */
    private fun findFirstFocusableView(root: View): View? {
        if (root.isFocusable && root.isShown) return root
        
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                val focusable = findFirstFocusableView(child)
                if (focusable != null) return focusable
            }
        }
        
        return null
    }
    
    /**
     * Set up a button specifically for TV navigation
     * This enhances focus behavior for TV D-pad navigation
     * @param button The button to set up for TV navigation
     */
    fun setupButtonForTv(button: Button) {
        // Make sure button can receive focus
        button.isFocusable = true
        button.isFocusableInTouchMode = true
        
        // Add a visual indication of focus for TV navigation
        button.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Highlight the button with visual feedback when focused
                v.scaleX = 1.1f
                v.scaleY = 1.1f
                // Make button stand out a bit more
                v.elevation = 8f
            } else {
                // Reset to normal when not focused
                v.scaleX = 1.0f
                v.scaleY = 1.0f
                v.elevation = 0f
            }
        }
        
        // Custom key handling for better TV navigation
        button.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        // Simulate click on center/enter press
                        v.performClick()
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }
    
    /**
     * Set up enhanced TV focus navigation between views
     * @param sourceView The view that has focus
     * @param leftView The view to focus when left is pressed (or null)
     * @param rightView The view to focus when right is pressed (or null)
     * @param upView The view to focus when up is pressed (or null)
     * @param downView The view to focus when down is pressed (or null)
     */
    fun setupTvNavigation(
        sourceView: View,
        leftView: View? = null,
        rightView: View? = null,
        upView: View? = null,
        downView: View? = null
    ) {
        leftView?.let { sourceView.nextFocusLeftId = it.id }
        rightView?.let { sourceView.nextFocusRightId = it.id }
        upView?.let { sourceView.nextFocusUpId = it.id }
        downView?.let { sourceView.nextFocusDownId = it.id }
    }
    
    /**
     * Clear all saved focus states
     */
    fun clearAll() {
        focusPositions.clear()
    }
    
    /**
     * Data class representing a saved focus state
     */
    data class FocusState(
        val viewId: Int
    )
}