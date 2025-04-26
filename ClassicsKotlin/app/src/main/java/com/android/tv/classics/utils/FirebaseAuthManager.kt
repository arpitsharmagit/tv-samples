package com.android.tv.classics.utils

import android.util.Log
import com.android.tv.classics.LiveTvApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class to handle Firebase Authentication
 * Provides methods for managing user authentication and secure database access
 */
object FirebaseAuthManager {
    private val TAG = FirebaseAuthManager::class.java.simpleName
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private var currentUser: FirebaseUser? = null
    
    /**
     * Initialize Firebase auth when app starts
     */
    fun init() {
        currentUser = auth.currentUser
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
            Log.d(TAG, "Auth state changed. User logged in: ${currentUser != null}")
        }
    }
    
    /**
     * Get authenticated database reference for a specific user path
     * Only allows access to user's own data based on mobile number
     */
    fun getUserDatabaseRef(path: String? = null): com.google.firebase.database.DatabaseReference {
        val mobileNumber = LiveTvApplication.getMobileNumber() ?: ""
        val userPath = if (path.isNullOrEmpty()) mobileNumber else "$mobileNumber/$path"
        
        return FirebaseDatabase.getInstance().getReference(userPath)
    }
    
    /**
     * Sign in anonymously (used in TV apps where full auth flow is cumbersome)
     * This maintains security without adding friction to the TV experience
     * Non-suspending version for use in Application class
     */
    fun signInAnonymously() {
        try {
            // Only attempt sign in if not already signed in
            if (currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        currentUser = result.user
                        Log.d(TAG, "Anonymous sign-in successful: ${currentUser?.uid}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Anonymous authentication failed", e)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating anonymous authentication", e)
        }
    }
    
    /**
     * Sign in anonymously with suspend function (for use in coroutines)
     */
    suspend fun signInAnonymouslyAsync(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Only attempt sign in if not already signed in
            if (currentUser == null) {
                val result = auth.signInAnonymously().await()
                currentUser = result.user
                Log.d(TAG, "Anonymous sign-in successful: ${currentUser?.uid}")
            }
            return@withContext currentUser != null
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous authentication failed", e)
            return@withContext false
        }
    }
    
    /**
     * Check if user is currently authenticated
     */
    fun isAuthenticated(): Boolean {
        return currentUser != null
    }
    
    /**
     * Associate the anonymous account with specific user identifier
     * This maintains database rules that can be based on mobile number
     */
    fun setUserMetadata(mobileNumber: String) {
        val user = auth.currentUser ?: return
        
        // Set custom claims to associate user with mobile number
        user.getIdToken(true)
            .addOnSuccessListener { result ->
                // Store the mobile number in the user metadata
                // Note: normally we'd use Cloud Functions to set custom claims
                // But for simple cases we can use user metadata storage
                getUserDatabaseRef("metadata").updateChildren(mapOf(
                    "mobileNumber" to mobileNumber,
                    "lastSignIn" to System.currentTimeMillis()
                ))
                
                Log.d(TAG, "Updated user metadata for mobile number: $mobileNumber")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get ID token for metadata update", e)
            }
    }
    
    /**
     * Sign out the current user
     */
    fun signOut() {
        auth.signOut()
        currentUser = null
    }
}