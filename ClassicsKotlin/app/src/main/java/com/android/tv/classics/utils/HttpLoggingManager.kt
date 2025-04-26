package com.android.tv.classics.utils

import android.util.Log
import com.android.tv.classics.jio.store.HttpStore
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.interceptors.HttpLoggingInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException

object HttpLoggingManager {
    private const val TAG = "HttpLoggingManager"
    private var isLoggingEnabled = false

    fun setLoggingEnabled(enabled: Boolean) {
        isLoggingEnabled = enabled
        
        // Configure logging based on the switch
        if (enabled) {
            // Enable detailed logging
            AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY)
            
            // Add custom interceptor for more detailed logging
            val client = HttpStore.getHttpClient()
            val newInterceptors = client.interceptors.toMutableList()
            newInterceptors.add(CustomLoggingInterceptor())
        }
//        else {
//            // Disable logging
//            AndroidNetworking.disableLogging()
//        }
    }

    fun isLoggingEnabled(): Boolean {
        return isLoggingEnabled
    }

    private class CustomLoggingInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            if (!isLoggingEnabled) {
                return chain.proceed(chain.request())
            }

            val request = chain.request()

            // Log Request Details
            logRequest(request)

            // Proceed with the request
            val response = chain.proceed(request)

            // Log Response Details
            return logResponse(response)
        }

        private fun logRequest(request: Request) {
            Log.d(TAG, "Request URL: ${request.url}")
            Log.d(TAG, "Request Method: ${request.method}")

            // Log Headers
            for (name in request.headers.names()) {
                Log.d(TAG, "Request Header: $name = ${request.headers[name]}")
            }

            // Log Request Body
            try {
                val copyRequest = request.newBuilder().build()
                val buffer = Buffer()
                copyRequest.body?.let {
                    it.writeTo(buffer)
                    val requestBody = buffer.readUtf8()
                    Log.d(TAG, "Request Body: $requestBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error logging request body", e)
            }
        }

        private fun logResponse(response: Response): Response {
            Log.d(TAG, "Response URL: ${response.request.url}")
            Log.d(TAG, "Response Code: ${response.code}")

            // Log Headers
            for (name in response.headers.names()) {
                Log.d(TAG, "Response Header: $name = ${response.headers[name]}")
            }

            // Log Response Body
            try {
                val responseBody = response.body
                if (responseBody != null) {
                    val contentType = responseBody.contentType()
                    val bodyString = responseBody.string()
                    Log.d(TAG, "Response Body: $bodyString")

                    // Create a new response body as the original can only be read once
                    return response.newBuilder()
                        .body(ResponseBody.create(contentType, bodyString))
                        .build()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error logging response body", e)
            }
            
            return response
        }
    }
}