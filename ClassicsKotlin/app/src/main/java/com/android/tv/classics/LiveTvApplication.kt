package com.android.tv.classics

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.android.tv.classics.jio.store.HttpStore
import com.android.tv.classics.jio.store.PrefStore
import com.android.tv.classics.utils.ApplicationContextProvider
import com.android.tv.classics.utils.FileLoggingTree
import com.android.tv.classics.utils.FirebaseAuthManager
import com.androidnetworking.AndroidNetworking
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import timber.log.Timber

class LiveTvApplication : Application() {
    companion object {
        const val TAG = "LiveTvApplication"
        private var mobileNumber: String? = null
        private var otpIdentifier: String? = null  // v2 auth: identifier returned by sendOTP
        private lateinit var httpStore: HttpStore
        private lateinit var prefStore: PrefStore
        private var instance: LiveTvApplication? = null
        private var authHeaders: Map<String, Any>? = null

        private val authStateListener = object : FirebaseAuthManager.AuthStateListener {
            override fun onAuthStateChanged(isAuthenticated: Boolean) {
                if (isAuthenticated && getMobileNumber() != null) {
                    initCloudSettings()
                }
            }
        }

        fun getInstance(): LiveTvApplication {
            return instance ?: LiveTvApplication().also { instance = it }
        }

        fun getContext(): Context {
            return getInstance()
        }

        fun showToast(data: String) {
            Toast.makeText(getContext(), data, Toast.LENGTH_SHORT).show()
        }

        fun getHttpStore(): HttpStore {
            return httpStore
        }
        
        fun getPrefStore(): PrefStore {
            return prefStore
        }

        fun getMobileNumber(): String? {
            if (mobileNumber == null) {
                return prefStore.getData("mobileNumber")
            }
            return mobileNumber
        }
        
        fun getCloudDatabase(): DatabaseReference {
            // Use the authenticated database reference through the FirebaseAuthManager
            return FirebaseAuthManager.getUserDatabaseRef()
        }
        
        fun setMobileNumber(newMobileNumber: String?) {
            mobileNumber = newMobileNumber
            prefStore.saveData("mobileNumber", mobileNumber ?: "")
            if (mobileNumber != null) {
                // Associate the mobile number with the Firebase user
                FirebaseAuthManager.setUserMetadata(mobileNumber!!)
//                initCloudSettings()
            }
        }

        /** Stores the OTP identifier returned by JioTVPlus v2 sendOTP endpoint. */
        fun setOtpIdentifier(identifier: String) {
            otpIdentifier = identifier
            prefStore.saveData("otpIdentifier", identifier)
        }

        /** Returns the stored OTP identifier (needed for verifyOTP step). */
        fun getOtpIdentifier(): String? {
            return otpIdentifier ?: prefStore.getData("otpIdentifier")?.takeIf { it.isNotEmpty() }
        }

        fun getAuthHeaders(): Map<String, Any> {
            if (authHeaders == null) {
                return prefStore.getMap("headers") ?: emptyMap()
            }
            return authHeaders ?: emptyMap()
        }
        
        // Helper function to safely access header values as strings
        fun getAuthHeaderString(key: String, defaultValue: String = ""): String {
            val value = getAuthHeaders()[key]
            return when (value) {
                is String -> value
                null -> defaultValue
                else -> value.toString()
            }
        }
        
        // Helper function to get auth headers as a Map<String, String> for compatibility
        fun getAuthHeadersAsStringMap(): Map<String, String> {
            return getAuthHeaders().mapValues { (_, value) -> 
                when (value) {
                    is String -> value
                    null -> ""
                    else -> value.toString()
                }
            }
        }
        
        fun setAuthHeaders(newAuthHeaders: Map<String, Any>) {
            authHeaders = newAuthHeaders
            prefStore.saveMap("headers", newAuthHeaders)
        }

        private fun initCloudSettings() {
            getCloudDatabase().addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val cloudHeaders = dataSnapshot.value
                    if (cloudHeaders != null) {
                        Timber.d("Login Headers found for ${getMobileNumber()}")
                        @Suppress("UNCHECKED_CAST")
                        setAuthHeaders(cloudHeaders as Map<String, Any>)
                        // TvLauncherUtils.refreshToken()
                    }
                }
                
                override fun onCancelled(databaseError: DatabaseError) {
                    Timber.e( "Cloud Database Error ${getMobileNumber()}", databaseError.toException())
                }
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize application context provider
        ApplicationContextProvider.init(this)

        // Initialize Timber with our custom file logging tree
        if (BuildConfig.DEBUG) {
            // In debug builds, use both the debug tree and file logging tree
            Timber.plant(Timber.DebugTree())
        }
        // Always plant the file logging tree for persistent logs
        Timber.plant(FileLoggingTree(this))

        bootstrapApplication()
    }

    private fun bootstrapApplication() {
        // start prefstore
        prefStore = PrefStore(getInstance())
        httpStore = HttpStore(getInstance())

        HttpStore.setLoggingEnabled(true)

        // init network
        AndroidNetworking.initialize(applicationContext, HttpStore.getHttpClient())

        // Initialize Firebase Auth Manager
        FirebaseAuthManager.addAuthStateListener(authStateListener)
        FirebaseAuthManager.init()
        
        prefStore.saveData("mobileNumber", "9310949577")
        // start httpstore
        // initialise APIs
        if (getMobileNumber() != null) {
            initCloudSettings()
        }
    }
}