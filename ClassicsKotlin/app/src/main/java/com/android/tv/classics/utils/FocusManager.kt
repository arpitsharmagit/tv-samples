package com.android.tv.classics.utils

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.util.Log

/**
 * Utility class to manage focus positions between fragment transitions
 * This allows TV navigation to remember focus positions when navigating between fragments
 */
object FocusManager {
    private val TAG = FocusManager::class.java.simpleName
    
    // Map to store focus state by destination ID
    private val focusPositions = mutableMapOf<Int, FocusState>()
    
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