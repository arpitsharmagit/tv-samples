package com.android.tv.classics.jio.store

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException

class CustomLoggingInterceptor : Interceptor {
    companion object {
        private const val TAG = "HttpLoggingManager"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Log Request Details
        logCurlRequest(request)

        // Proceed with the request
        var response = chain.proceed(request)

        // Log Response Details
        response = logResponse(response)

        return response
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

    private fun logCurlRequest(request: Request) {
        try {
            val curlCmd = StringBuilder("curl")
            
            // Add method if not GET
            if (request.method != "GET") {
                curlCmd.append(" -X ${request.method}")
            }
            
            // Add headers
            for (name in request.headers.names()) {
                curlCmd.append(" -H '${name}: ${request.headers[name]}'")
            }
            
            // Add body
            request.body?.let {
                val buffer = Buffer()
                it.writeTo(buffer)
                val bodyString = buffer.readUtf8()
                if (bodyString.isNotEmpty()) {
                    curlCmd.append(" -d '${bodyString.replace("'", "\\'")}'")
                }
            }
            
            // Add URL
            curlCmd.append(" '${request.url}'")
            
            Log.d(TAG, "cURL command: $curlCmd")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating cURL command", e)
        }
    }

    private fun logResponse(response: Response): Response {
        if (response.code == 200){
            return response
        }
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