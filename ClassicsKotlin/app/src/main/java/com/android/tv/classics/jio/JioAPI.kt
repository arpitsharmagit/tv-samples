package com.android.tv.classics.jio

import com.android.tv.classics.LiveTvApplication
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.ANRequest
import com.androidnetworking.common.ANResponse
import com.androidnetworking.common.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object JioAPI {
    private val BASE_HEADERS = mapOf(
        Constants.APP_NAME to Constants.VALUES.APP_NAME,
        Constants.DEVICE_TYPE to Constants.VALUES.DEVICE_TYPE,
        Constants.OS to Constants.VALUES.OS
    )

    private fun createHeaders(additionalHeaders: Map<String, String>): Map<String, String> {
        val headers = HashMap<String, String>()
        headers.putAll(BASE_HEADERS)
        headers.putAll(additionalHeaders)
        return headers
    }

    private fun safeParseJson(jsonString: String): JSONObject {
        return try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            JSONObject()
        }
    }

    fun sendOTP(mobileNumber: String): ANRequest<*> {
        val formattedNumber = if (mobileNumber.contains("+91")) mobileNumber else "+91$mobileNumber"
        
        val jsonObject = JSONObject()
        try {
            jsonObject.put("number", Utils.encodePhoneNumber(formattedNumber))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return AndroidNetworking.post(Constants.otpURL)
            .addJSONObjectBody(jsonObject)
            .addHeaders(BASE_HEADERS)
            .setPriority(Priority.MEDIUM)
            .build()
    }

    fun verifyOTP(phoneNumber: String, otp: String): ANRequest<*> {
        val jsonObject = safeParseJson(
            Utils.getJsonFromAssets(LiveTvApplication.getInstance().applicationContext, "login-request.json") ?: "{}"
        )
        
        try {
            jsonObject.put("number", Utils.encodePhoneNumber(phoneNumber))
            jsonObject.put("otp", otp)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return AndroidNetworking.post(Constants.verifyURL)
            .addJSONObjectBody(jsonObject)
            .addHeaders(BASE_HEADERS)
            .setPriority(Priority.MEDIUM)
            .build()
    }

    fun refreshToken(authHeaders: Map<String, Any>): ANRequest<*> {
        // Extract values safely
        val authToken = getStringValue(authHeaders, "authToken")
        val uniqueId = getStringValue(authHeaders, "uniqueId")
        val deviceId = getStringValue(authHeaders, "deviceId")
        val refreshToken = getStringValue(authHeaders, "refreshToken")
        
        val additionalHeaders = hashMapOf(
            Constants.ACCESS_TOKEN to authToken,
            Constants.UNIQUE_ID to uniqueId,
            Constants.VERSION_CODE to Constants.VALUES.VERSION_CODE
        )
        val headers = createHeaders(additionalHeaders)

        val jsonObject = JSONObject()
        try {
            jsonObject.put(Constants.APP_NAME, "RJIL_JioTV")
            jsonObject.put(Constants.DEVICE_ID, deviceId)
            jsonObject.put(Constants.REFRESH_TOKEN, refreshToken)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return AndroidNetworking.post(Constants.refreshTokenURL)
            .addJSONObjectBody(jsonObject)
            .addHeaders(headers)
            .setPriority(Priority.MEDIUM)
            .build()
    }
    
    // Helper function to safely extract string values
    private fun getStringValue(map: Map<String, Any>, key: String): String {
        val value = map[key]
        return when (value) {
            is String -> value
            null -> ""
            else -> value.toString()
        }
    }

    suspend fun getHeaderCookie(playbackUrl: String, authHeaders: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val additionalHeaders = hashMapOf(
            Constants.ACCESS_TOKEN to getStringValue(authHeaders, "authToken"),
            Constants.APP_KEY to getStringValue(authHeaders, "appkey"),
            Constants.CRM_ID to getStringValue(authHeaders, "crmid"),
            Constants.DEVICE_ID to getStringValue(authHeaders, "deviceId"),
            Constants.SESSIONID to getStringValue(authHeaders, "uniqueId"),
            Constants.SUBSCRIBER_ID to getStringValue(authHeaders, "crmid"),
            Constants.UNIQUE_ID to getStringValue(authHeaders, "uniqueId"),
            Constants.USER_GROUP to getStringValue(authHeaders, "usergroup"),
            Constants.USER_ID to getStringValue(authHeaders, "userId"),
            Constants.VERSION_CODE to Constants.VALUES.VERSION_CODE,
            Constants.DM to Constants.VALUES.DM,
            Constants.OTT_USER to "false",
            Constants.LANGUAGE_ID to Constants.VALUES.LANGUAGE_ID,
            Constants.LBCOOKIES to Constants.VALUES.LBCOOKIES,
            Constants.OS_VERSION to Constants.VALUES.OS_VERSION
        )
        val headers = createHeaders(additionalHeaders)

        val request = AndroidNetworking.get(playbackUrl)
            .addHeaders(headers)
            .doNotCacheResponse()
            .setPriority(Priority.HIGH)
            .build()

        val response = request.executeForOkHttpResponse()
        if (response.isSuccess) {
            response.okHttpResponse.header("set-cookie") ?: ""
        } else {
            ""
        }
    }

    suspend fun getPlaybackUrl(body: Map<String, String>, authHeaders: Map<String, Any>): JSONObject = withContext(Dispatchers.IO) {
        val additionalHeaders = hashMapOf(
            Constants.ACCESS_TOKEN to getStringValue(authHeaders, "authToken"),
            Constants.APP_KEY to getStringValue(authHeaders, "appkey"),
            Constants.CHANNEL_ID to (body["channel_id"] ?: ""),
            Constants.CRM_ID to getStringValue(authHeaders, "crmid"),
            Constants.DEVICE_ID to getStringValue(authHeaders, "deviceId"),
            Constants.SESSIONID to getStringValue(authHeaders, "uniqueId"),
            Constants.SUBSCRIBER_ID to getStringValue(authHeaders, "crmid"),
            Constants.UNIQUE_ID to getStringValue(authHeaders, "uniqueId"),
            Constants.USER_GROUP to getStringValue(authHeaders, "usergroup"),
            Constants.USER_ID to getStringValue(authHeaders, "userId"),
            Constants.VERSION_CODE to Constants.VALUES.VERSION_CODE,
            Constants.DM to Constants.VALUES.DM,
            Constants.OTT_USER to "false",
            Constants.LANGUAGE_ID to Constants.VALUES.LANGUAGE_ID,
            Constants.LBCOOKIES to Constants.VALUES.LBCOOKIES,
            Constants.OS_VERSION to Constants.VALUES.OS_VERSION
        )
        val headers = createHeaders(additionalHeaders)

        val request = AndroidNetworking.post(Constants.channelURL)
            .addHeaders(headers)
            .doNotCacheResponse()
            .addUrlEncodeFormBodyParameter(body)
            .setPriority(Priority.HIGH)
            .build()

        val response = request.executeForJSONObject()
        if (response.isSuccess) {
            response.result as JSONObject
        } else {
            JSONObject()
        }
    }

    suspend fun getChannels(): JSONObject = withContext(Dispatchers.IO) {
        try {
            val request = AndroidNetworking.get(Constants.channelsURL).build()
            val response = request.executeForJSONObject()
            if (response.isSuccess) {
                val result = response.result as JSONObject
                // Ensure it has a "result" array, add an empty one if missing
                if (!result.has("result")) {
                    result.put("result", JSONArray())
                }
                result
            } else {
                // Create a JSON with an empty result array
                JSONObject().apply {
                    put("result", JSONArray())
                }
            }
        } catch (e: Exception) {
            // Fallback in case of any error
            JSONObject().apply {
                put("result", JSONArray())
            }
        }
    }

    suspend fun getEPG(channelId: String): JSONObject = withContext(Dispatchers.IO) {
        try {
            val request = AndroidNetworking.get(
                Constants.epgUrl.replace("channelId", channelId)
            ).build()
            val response = request.executeForJSONObject()
            if (response.isSuccess) {
                val result = response.result as JSONObject
                // Ensure it has an "epg" array, add an empty one if missing
                if (!result.has("epg")) {
                    result.put("epg", JSONArray())
                }
                result
            } else {
                // Create a JSON with an empty epg array
                JSONObject().apply {
                    put("epg", JSONArray())
                }
            }
        } catch (e: Exception) {
            // Fallback in case of any error
            JSONObject().apply {
                put("epg", JSONArray())
            }
        }
    }
}