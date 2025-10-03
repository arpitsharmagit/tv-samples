package com.android.tv.classics

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.lifecycle.LifecycleCoroutineScope
import com.android.tv.classics.jio.store.HttpStore
import com.android.tv.classics.jio.store.PrefStore
import com.android.tv.classics.utils.FirebaseAuthManager
import com.android.tv.classics.utils.TvLauncherUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.androidnetworking.AndroidNetworking
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LiveTvApplication : Application() {
    companion object {
        const val TAG = "LiveTvApplication"
        private var mobileNumber: String? = null
        private lateinit var httpStore: HttpStore
        private lateinit var prefStore: PrefStore
        private var instance: LiveTvApplication? = null

        private var authHeaders: Map<String, Any>? = null

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
            return com.android.tv.classics.utils.FirebaseAuthManager.getUserDatabaseRef()
        }
        
        fun setMobileNumber(newMobileNumber: String?) {
            mobileNumber = newMobileNumber
            prefStore.saveData("mobileNumber", mobileNumber ?: "")
            if (mobileNumber != null) {
                // Associate the mobile number with the Firebase user
                FirebaseAuthManager.setUserMetadata(mobileNumber!!)
                initCloudSettings()
            }
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
                        Log.d(TAG, "Login Headers found for ${getMobileNumber()}")
                        @Suppress("UNCHECKED_CAST")
                        setAuthHeaders(cloudHeaders as Map<String, Any>)
                        // TvLauncherUtils.refreshToken()
                    }
                }
                
                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e(TAG, "Cloud Database Error ${getMobileNumber()}", databaseError.toException())
                }
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
        FirebaseAuthManager.init()
        
        prefStore.saveData("mobileNumber", "9310949577")
        // start httpstore
        // initialise APIs
        if (getMobileNumber() != null) {
            // Authenticate with Firebase (anonymous auth) before accessing database
            FirebaseAuthManager.signInAnonymously()
            initCloudSettings()
        }
    }
}