package com.android.tv.classics.jio.store

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import timber.log.Timber
import java.io.IOException

class CustomLoggingInterceptor : Interceptor {
    companion object {
        private const val TAG = "HttpLoggingManager"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Proceed with the request
        var response = chain.proceed(request)

        // Log Response Details
        response = logResponse(response)

        return response
    }

    private fun logRequest(request: Request) {
        Timber.d("Request URL: ${request.url}")
        Timber.d("Request Method: ${request.method}")

        // Log Headers
        for (name in request.headers.names()) {
            Timber.d("Request Header: $name = ${request.headers[name]}")
        }

        // Log Request Body
        try {
            val copyRequest = request.newBuilder().build()
            val buffer = Buffer()
            copyRequest.body?.let {
                it.writeTo(buffer)
                val requestBody = buffer.readUtf8()
                Timber.d("Request Body: $requestBody")
            }
        } catch (e: Exception) {
            Timber.e( "Error logging request body", e)
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
                if (name.equals("accesstoken", ignoreCase = true)) {
                    curlCmd.append(" -H '${name}: REDACTED'")
                    continue
                }
                curlCmd.append(" -H '${name}: ${request.headers[name]}'")
            }

            // Handle content type specific formatting
            request.body?.let {
                val contentType = it.contentType()?.toString() ?: ""
                when {
                    contentType.contains("application/x-www-form-urlencoded") -> {
                        val buffer = Buffer()
                        it.writeTo(buffer)
                        val bodyString = buffer.readUtf8()
                        curlCmd.append(" --data '${bodyString.replace("'", "\\'")}'")
                    }
                    contentType.contains("multipart/form-data") -> {
                        curlCmd.append(" -F '${it.toString()}'")
                    }
                    contentType.contains("application/json") -> {
                        val buffer = Buffer()
                        it.writeTo(buffer)
                        val bodyString = buffer.readUtf8()
                        curlCmd.append(" -H 'Content-Type: application/json'")
                        curlCmd.append(" -d '${bodyString.replace("'", "\\'")}'")
                    }
                    else -> {
                        val buffer = Buffer()
                        it.writeTo(buffer)
                        val bodyString = buffer.readUtf8()
                        curlCmd.append(" --data '${bodyString.replace("'", "\\'")}'")
                    }
                }
            }            
            
            // Add URL
            curlCmd.append(" '${request.url}'")
            
            Timber.d("$curlCmd")
        } catch (e: Exception) {
            Timber.e( "Error creating cURL command", e)
        }
    }

    private fun logResponse(response: Response): Response {
        // Log Request Details
        logCurlRequest(response.request)
        if (response.code == 200){
            return response
        }
        Timber.d("Response URL: ${response.request.url}")
        Timber.d("Response Code: ${response.code}")
        // Log Headers
        for (name in response.headers.names()) {
            Timber.d("Response Header: $name = ${response.headers[name]}")
        }

        // Log Response Body
        try {
            val responseBody = response.body
            if (responseBody != null) {
                val contentType = responseBody.contentType()
                val bodyString = responseBody.string()
                Timber.d("Response Body: $bodyString")

                // Create a new response body as the original can only be read once
                return response.newBuilder()
                    .body(ResponseBody.create(contentType, bodyString))
                    .build()
            }
        } catch (e: Exception) {
            Timber.e( "Error logging response body", e)
        }
        
        return response
    }
}