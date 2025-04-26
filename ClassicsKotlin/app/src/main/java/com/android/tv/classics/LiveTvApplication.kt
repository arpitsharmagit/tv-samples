package com.android.tv.classics

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.NonNull
import com.android.tv.classics.jio.store.HttpStore
import com.android.tv.classics.jio.store.PrefStore
import com.android.tv.classics.utils.HttpLoggingManager
import com.android.tv.classics.utils.TvLauncherUtils
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.interceptors.HttpLoggingInterceptor
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
        private var authHeaders: Map<String, String>? = null

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
            return FirebaseDatabase.getInstance()
                .getReference(getMobileNumber() ?: "")
        }
        
        fun setMobileNumber(newMobileNumber: String?) {
            mobileNumber = newMobileNumber
            prefStore.saveData("mobileNumber", mobileNumber ?: "")
            if (mobileNumber != null) {
                initCloudSettings()
            }
        }

        fun getAuthHeaders(): Map<String, String> {
            if (authHeaders == null) {
                return prefStore.getMap("headers") ?: emptyMap()
            }
            return authHeaders ?: emptyMap()
        }
        
        fun setAuthHeaders(newAuthHeaders: Map<String, String>) {
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
                        setAuthHeaders(cloudHeaders as Map<String, String>)
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

    fun bootstrapApplication() {
        // start prefstore
        prefStore = PrefStore(getInstance())
        httpStore = HttpStore(getInstance())

        HttpStore.setLoggingEnabled(true)

        // init network
        AndroidNetworking.initialize(applicationContext, HttpStore.getHttpClient())

        prefStore.saveData("mobileNumber", "9310949577")
        // start httpstore
        // initialise APIs
        if (getMobileNumber() != null) {
            initCloudSettings()
        }
    }
}