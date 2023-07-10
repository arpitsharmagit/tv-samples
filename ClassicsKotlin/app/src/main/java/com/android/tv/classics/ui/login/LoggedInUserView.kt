package com.android.tv.classics.ui.login

/**
 * User details post authentication that is exposed to the UI
 */
data class LoggedInUserView(
    val displayName: String,
    val isLoggedIn: Boolean
    //... other data fields that may be accessible to the UI
)