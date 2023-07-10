package com.android.tv.classics.data.model

/**
 * Data class that captures user information for logged in users retrieved from LoginRepository
 */
data class LoggedInUser(
    val isOTPSent: String,
    val userId: String,
    val displayName: String
)