package com.android.tv.classics.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.android.tv.classics.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * A GuidedStepSupportFragment designed for Android TV to handle Google Sign-In and Firebase
 * authentication, including promotion of existing anonymous accounts.
 */
class GoogleSignInStepFragment : GuidedStepSupportFragment() {

    private val TAG = "GoogleSignInStepFragment"
    private val RC_SIGN_IN = 9001

    // Action IDs for the Guided Actions
    private val ACTION_SIGN_IN_GOOGLE = 1L
    private val ACTION_SIGN_OUT = 2L
    private val ACTION_ANONYMOUS_STATUS = 3L

    // Firebase Auth instance
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    // Google Sign-In Client
    private lateinit var googleSignInClient: GoogleSignInClient

    // --- Fragment Lifecycle Overrides ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Configure Google Sign-In
        // The R.string.default_web_client_id is assumed to be available from google-services.json
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            getString(R.string.app_name), // Main title
            "Sign in to synchronize your settings across all devices.", // Description
            "", // Breadcrumb
            null // Icon (optional)
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        // Initial check and creation of actions based on auth state
        checkAuthStateAndCreateActions(actions)
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_SIGN_IN_GOOGLE -> startGoogleSignIn()
            ACTION_SIGN_OUT -> signOut()
            else -> super.onGuidedActionClicked(action)
        }
    }

    // --- Authentication Flow Logic ---

    private fun checkAuthStateAndCreateActions(actions: MutableList<GuidedAction>) {
        val currentUser = firebaseAuth.currentUser

        if (currentUser == null) {
            // No user signed in, sign in anonymously (to maintain settings access)
            signInAnonymously(actions)
        } else {
            // User is already signed in (either anonymously or via Google)
            updateActionsForUser(currentUser, actions)
        }
    }

    /**
     * Signs in anonymously and then updates the Guided Actions.
     */
    private fun signInAnonymously(actions: MutableList<GuidedAction>) {
        lifecycleScope.launch {
            try {
                // Update guidance message temporarily
                updateDescription("Signing in anonymously...")
                val result = firebaseAuth.signInAnonymously().await()
                Timber.d( "Anonymous sign-in success: ${result.user?.uid}")
                updateActionsForUser(result.user, actions)
            } catch (e: Exception) {
                Timber.e( "Anonymous sign-in failed", e)
                updateDescription("Anonymous sign-in failed: ${e.message}. Please sign in with Google.")
                // If anonymous sign-in fails, still offer Google sign-in
                createSignInAction(actions)
                setActions(actions)
            }
        }
    }

    /**
     * Dynamically updates the Guided Actions based on the authenticated Firebase user.
     */
    private fun updateActionsForUser(user: FirebaseUser?, actions: MutableList<GuidedAction>) {
        actions.clear()

        if (user != null) {

            val isGoogleUser = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

            if (isGoogleUser) {
                // User signed in with Google
                val email = user.email ?: "Unknown Email"
                updateDescription("Signed in with Google as: ${user.displayName}\n($email)")

                // Add Sign Out action
                createSignOutAction(actions)

            } else if (user.isAnonymous) {
                // User is anonymous
                updateDescription("You are signed in anonymously (User ID: ${user.uid}). Sign in with Google to protect and sync your settings.")

                // Add Sign In action
                createSignInAction(actions)

                // Add Anonymous Status action (informative)
                actions.add(GuidedAction.Builder(requireContext()).id(ACTION_ANONYMOUS_STATUS).title("Currently Anonymous").description("Sign Out to restart or Sign In to promote this account.").focusable(false).build())
            }
        } else {
            // No user state, show only sign in (shouldn't happen if anonymous sign-in succeeds)
            updateDescription("No user authenticated. Please sign in.")
            createSignInAction(actions)
        }

        setActions(actions)
    }

    private fun createSignInAction(actions: MutableList<GuidedAction>) {
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SIGN_IN_GOOGLE)
                .title("Sign in with Google")
                .description("Use your Google account to secure and sync your data.")
                .build()
        )
    }

    private fun createSignOutAction(actions: MutableList<GuidedAction>) {
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SIGN_OUT)
                .title("Sign Out")
                .description("Sign out of your Google account. You will revert to anonymous status.")
                .build()
        )
    }

    private fun updateDescription(newDescription: String) {
        guidanceStylist?.descriptionView?.text = newDescription
//        guidanceStylist.updateGuidanceMetadata(guidanceStylist.guidance)
    }

    // --- Google Sign-In and Firebase Auth Logic ---

    /**
     * Starts the Google Sign-In Intent flow.
     */
    private fun startGoogleSignIn() {
        updateDescription("Launching Google Sign-In...")
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    /**
     * Handles the result of the Google Sign-In Intent.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    Timber.d( "Google sign-in succeeded: ${account.id}")
                    firebaseAuthWithGoogle(account)
                } catch (e: ApiException) {
                    Timber.e( "Google sign-in failed", e)
                    updateDescription("Google Sign-In failed (Code: ${e.statusCode}). Please try again.")
                    // Re-draw actions to allow re-trying
                    setActions(createActions())
                }
            } else {
                updateDescription("Sign-In cancelled.")
                // Re-draw actions to allow re-trying
                setActions(createActions())
            }
        }
    }

    /**
     * Exchanges the Google ID token for a Firebase credential and signs in or links to Firebase.
     */
    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        Timber.d( "firebaseAuthWithGoogle: Start")
        updateDescription("Authenticating with Firebase...")

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)

        lifecycleScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser

                if (currentUser != null && currentUser.isAnonymous) {
                    // Case 1: Anonymous to Google account promotion (linking)
                    Timber.d( "Linking anonymous user ${currentUser.uid} with Google.")
                    val authResult = currentUser.linkWithCredential(credential).await()
                    updateDescription("Account successfully linked with Google!")
                    updateActionsForUser(authResult.user, mutableListOf())
                } else {
                    // Case 2: Standard Google sign-in (or sign-up)
                    Timber.d( "Signing in with Google credential.")
                    val authResult = firebaseAuth.signInWithCredential(credential).await()
                    updateDescription("Sign-in successful!")
                    updateActionsForUser(authResult.user, mutableListOf())
                }
            } catch (e: Exception) {
                Timber.e( "Firebase authentication failed", e)

                if (e.message?.contains("credential-already-in-use") == true) {
                    updateDescription("Error: This Google account is already linked to another user. Please sign in with your linked account.")
                } else {
                    updateDescription("Firebase Auth Failed: ${e.message}")
                }
                // Re-draw actions to reflect the current state (e.g., still anonymous)
                updateActionsForUser(firebaseAuth.currentUser, mutableListOf())
            }
        }
    }

    /**
     * Signs out the user from both Google and Firebase and signs back in anonymously.
     */
    private fun signOut() {
        lifecycleScope.launch {
            updateDescription("Signing out...")

            // Firebase sign out
            firebaseAuth.signOut()

            // Google sign out
            try {
                googleSignInClient.signOut().await()
                Timber.d( "Google sign-out succeeded.")
            } catch (e: Exception) {
                Timber.e( "Google sign-out failed", e)
            }

            // After signing out, immediately sign back in anonymously
            updateDescription("Signing out. Reverting to anonymous session...")
            signInAnonymously(mutableListOf()) // This call will update actions upon success
        }
    }

    // Utility to correctly create actions list (used for re-creating the actions after an event)
    private fun createActions(): MutableList<GuidedAction> {
        val actions = mutableListOf<GuidedAction>()
        checkAuthStateAndCreateActions(actions)
        return actions
    }
}
