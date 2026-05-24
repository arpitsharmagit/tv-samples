package com.android.tv.classics.jio

import com.android.tv.classics.jio.models.v2.ChannelV2
import com.android.tv.classics.jio.models.v2.ChannelsResponseV2
import com.android.tv.classics.jio.models.v2.CurrentProgramV2
import com.android.tv.classics.jio.models.v2.EpgResponseV2
import com.android.tv.classics.jio.models.v2.EpgShowV2
import com.android.tv.classics.jio.models.v2.PlaybackResponseV2
import com.androidnetworking.AndroidNetworking
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder

/**
 * JioTVPlus v2 API singleton.
 *
 * Auth flow (3 steps):
 *  1. [sendOTP]       – POST to STB OTP endpoint; returns identifier string.
 *  2. [verifyOTP]     – POST to STB verify OTP; returns ssoToken + uniqueId + subscriberId.
 *  3. [exchangeToken] – POST to exchange ssoToken for authToken + refreshToken.
 *
 * Content:
 *  - [getChannels]       – GET live channels map (no language filter applied here).
 *  - [getEPGBatch]       – GET EPG for one or more channels with offset support.
 *  - [getGenreFilters]   – GET genre/language filter list (no auth needed).
 *  - [getTVGuideFilters] – GET curated TV guide filters (requires auth).
 *  - [getPlaybackUrl]    – POST device capabilities to get HLS/DASH stream URLs.
 */
object JioAPIv2 {

    private val gson = Gson()

    // ── Base headers shared by all v2 requests ───────────────────────────────────

    // Spec shows x-appname: "JioTVPlus" (without RJIL_ prefix) for ALL content/playback endpoints
    private val BASE_HEADERS = mapOf(
        ConstantsV2.X_APPNAME        to ConstantsV2.Values.APP_NAME_AUTH,  // "JioTVPlus"
        ConstantsV2.X_API_SIGNATURES to ConstantsV2.Values.API_SIGNATURES,
        ConstantsV2.X_FEATURE_CODE   to ConstantsV2.Values.FEATURE_CODE,
        ConstantsV2.X_PLATFORM       to ConstantsV2.Values.PLATFORM_STB,
        "user-agent"                  to ConstantsV2.Values.USER_AGENT
    )

    /** Auth request headers (used only for sendOTP / verifyOTP). */
    private val AUTH_REQUEST_HEADERS = mapOf(
        "app-name"  to ConstantsV2.Values.APP_NAME,
        ConstantsV2.X_API_KEY    to ConstantsV2.Values.API_KEY,
        ConstantsV2.X_PLATFORM   to ConstantsV2.Values.PLATFORM_STB,
        ConstantsV2.X_APPNAME    to ConstantsV2.Values.APP_NAME_AUTH,  // "JioTVPlus" for auth endpoints
        "user-agent" to ConstantsV2.Values.USER_AGENT
    )

    // ── Auth headers appended to content / playback requests ────────────────────

    /** Builds the set of per-request auth headers from the stored session map. */
    private fun authHeaders(session: Map<String, Any>): Map<String, String> {
        fun str(key: String) = when (val v = session[key]) {
            is String -> v
            null      -> ""
            else      -> v.toString()
        }
        return mapOf(
            ConstantsV2.X_ACCESS_TOKEN   to str(ConstantsV2.KEY_AUTH_TOKEN),
            ConstantsV2.DEVICEID         to str(ConstantsV2.KEY_DEVICE_ID),
            ConstantsV2.UNIQUEID         to str(ConstantsV2.KEY_UNIQUE_ID),
            ConstantsV2.SUBID            to str(ConstantsV2.KEY_SUBSCRIBER_ID),
            ConstantsV2.RESTRICTION      to ConstantsV2.Values.RESTRICTION
        )
    }

    // ── Step 1: Send OTP ─────────────────────────────────────────────────────────

    /**
     * Sends an OTP to the given mobile number.
     * @param number  10-digit number (without +91 prefix).
     * @return identifier string on success, null on failure.
     */
    suspend fun sendOTP(number: String): String? = withContext(Dispatchers.IO) {
        try {
            val formattedNumber = if (number.startsWith("+91")) number.substring(3) else number

            val request = AndroidNetworking.post(ConstantsV2.Urls.SEND_OTP)
                .addHeaders(AUTH_REQUEST_HEADERS)
                .addHeaders(mapOf(
                    "number"       to formattedNumber,
                    "identifierid" to ""
                ))
                .setPriority(com.androidnetworking.common.Priority.MEDIUM)
                .build()

            val response = request.executeForString()
            if (response.isSuccess) {
                val json = JSONObject(response.result?.toString() ?: "{}")
                Timber.d("sendOTP response: $json")
                json.optString("identifier").takeIf { it.isNotEmpty() }
            } else {
                Timber.e("sendOTP failed: ${response.error?.errorBody}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "sendOTP exception")
            null
        }
    }

    // ── Step 2: Verify OTP ───────────────────────────────────────────────────────

    /**
     * Verifies the OTP entered by the user.
     * @return JSONObject containing `ssoToken`, `sessionAttributes.user.unique`, and `subscriberId`
     *         on success, or an empty JSONObject on failure.
     */
    suspend fun verifyOTP(
        identifier: String,
        otp: String,
        deviceId: String = ConstantsV2.Values.DEVICE_ID
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("identifier", identifier)
                put("otp", otp)
                put("upgradeAuth", "Y")
                put("rememberUser", "T")
                put("deviceInfo", JSONObject().apply {
                    put("consumptionDeviceName", ConstantsV2.Values.MODEL)
                    put("info", JSONObject().apply {
                        put("type", "android")
                        put("platform", JSONObject().apply {
                            put("name", ConstantsV2.Values.MODEL)
                        })
                        put("androidId", deviceId)
                    })
                })
            }

            val request = AndroidNetworking.post(ConstantsV2.Urls.VERIFY_OTP)
                .addHeaders(AUTH_REQUEST_HEADERS)
                .addJSONObjectBody(body)
                .setPriority(com.androidnetworking.common.Priority.MEDIUM)
                .build()

            val response = request.executeForJSONObject()
            if (response.isSuccess) {
                val json = response.result as JSONObject
                Timber.d("verifyOTP success, ssoToken present: ${json.has("ssoToken")}")
                json
            } else {
                Timber.e("verifyOTP failed: ${response.error?.errorBody}")
                JSONObject()
            }
        } catch (e: Exception) {
            Timber.e(e, "verifyOTP exception")
            JSONObject()
        }
    }

    // ── Step 3: Exchange Token ───────────────────────────────────────────────────

    /**
     * Exchanges an ssoToken for a JioTVPlus authToken + refreshToken.
     * @param ssoToken     from [verifyOTP] response.
     * @param subscriberId from [verifyOTP] sessionAttributes.user.subscriberId.
     * @param number       10-digit mobile number (without +91 prefix).
     * @param deviceId     device identifier.
     * @return JSONObject with `authToken`, `refreshToken`, `userId`, `subscriberId`.
     */
    suspend fun exchangeToken(
        ssoToken: String,
        subscriberId: String,
        number: String,
        deviceId: String = ConstantsV2.Values.DEVICE_ID
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            val rawNumber = if (number.startsWith("+91")) number else "+91$number"
            val encodedNumber = Utils.encodePhoneNumber(rawNumber)

            val headers = mapOf(
                "appname"                 to ConstantsV2.Values.APP_NAME,
                "os"                      to "android",
                "subscriberid"            to subscriberId,
                "persistentrefreshtoken"  to "true",
                ConstantsV2.X_PLATFORM    to ConstantsV2.Values.PLATFORM_TV,
                "deviceid"                to deviceId,
                "ssotoken"                to ssoToken,
                "devicetype"              to ConstantsV2.Values.DEVICE_TYPE_TV,
                "cache-control"           to "no-cache",
                ConstantsV2.X_API_SIGNATURES to ConstantsV2.Values.API_SIGNATURES,
                ConstantsV2.X_FEATURE_CODE   to ConstantsV2.Values.FEATURE_CODE,
                ConstantsV2.X_APPNAME        to ConstantsV2.Values.APP_NAME,
                "user-agent"              to ConstantsV2.Values.USER_AGENT
            )

            val body = JSONObject().apply {
                put("number", encodedNumber)
            }

            val request = AndroidNetworking.post(ConstantsV2.Urls.EXCHANGE_TOKEN)
                .addHeaders(headers)
                .addJSONObjectBody(body)
                .setPriority(com.androidnetworking.common.Priority.HIGH)
                .build()

            val response = request.executeForJSONObject()
            if (response.isSuccess) {
                val json = response.result as JSONObject
                Timber.d("exchangeToken success, authToken present: ${json.has("authToken")}")
                json
            } else {
                Timber.e("exchangeToken failed: ${response.error?.errorBody}")
                JSONObject()
            }
        } catch (e: Exception) {
            Timber.e(e, "exchangeToken exception")
            JSONObject()
        }
    }

    // ── Live Channels ────────────────────────────────────────────────────────────

    /**
     * Fetches the full live channels list.
     * Parses manually via JSONObject to avoid Gson TypeToken issues with Kotlin 1.4.
     * @return Parsed [ChannelsResponseV2] on success, or a response with an empty data map.
     */
    suspend fun getChannels(session: Map<String, Any>): ChannelsResponseV2 = withContext(Dispatchers.IO) {
        try {
            val headers = BASE_HEADERS + authHeaders(session) + mapOf(
                ConstantsV2.X_PAGE       to "Metadata",
                ConstantsV2.CACHE_CONTROL to "no-cache"
            )

            val request = AndroidNetworking.get(ConstantsV2.Urls.CHANNELS)
                .addHeaders(headers)
                .doNotCacheResponse()
                .setPriority(com.androidnetworking.common.Priority.MEDIUM)
                .build()

            val response = request.executeForString()
            if (response.isSuccess) {
                parseChannelsResponse(response.result?.toString() ?: "")
            } else {
                Timber.e("getChannels failed: ${response.error?.errorBody}")
                ChannelsResponseV2()
            }
        } catch (e: Exception) {
            Timber.e(e, "getChannels exception")
            ChannelsResponseV2()
        }
    }

    /** Manually parses the channels JSON to avoid Gson TypeToken / Kotlin 1.4 issues. */
    private fun parseChannelsResponse(json: String): ChannelsResponseV2 {
        return try {
            val root = JSONObject(json)
            val code = root.optInt("code", 0)
            val message = root.optString("message", "")
            val dataObj = root.optJSONObject("data") ?: return ChannelsResponseV2(code, message)

            val channelMap = mutableMapOf<String, ChannelV2>()
            val keys = dataObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val ch = dataObj.getJSONObject(key)
                val genreArr = ch.optJSONArray("genres")
                val genres = if (genreArr != null) {
                    (0 until genreArr.length()).map { genreArr.getString(it) }
                } else emptyList()

                val cpObj = ch.optJSONObject("currentProgram")
                val currentProgram = if (cpObj != null) {
                    CurrentProgramV2(
                        title      = cpObj.optString("title"),
                        thumbnail  = cpObj.optString("thumbnail"),
                        startEpoch = cpObj.optLong("startEpoch"),
                        endEpoch   = cpObj.optLong("endEpoch"),
                        updatedAt  = cpObj.optString("updatedAt")
                    )
                } else null

                channelMap[key] = ChannelV2(
                    contentId     = ch.optString("contentId"),
                    name          = ch.optString("name"),
                    subtitle      = ch.optString("subtitle"),
                    still         = ch.optString("still"),
                    thumbnail     = ch.optString("thumbnail"),
                    stillFallback = ch.optString("stillFallback"),
                    description   = ch.optString("description"),
                    language      = ch.optString("language"),
                    genres        = genres,
                    quality       = ch.optString("quality"),
                    channelNumber = ch.optInt("channelNumber"),
                    order         = ch.optInt("order"),
                    isPremium     = ch.optBoolean("isPremium"),
                    restriction   = ch.optInt("restriction"),
                    onAir         = ch.optBoolean("onAir"),
                    pause         = ch.optBoolean("pause"),
                    provider      = ch.optString("provider"),
                    vendor        = ch.optString("vendor"),
                    currentProgram = currentProgram
                )
            }
            Timber.d("parseChannelsResponse: parsed ${channelMap.size} channels")
            ChannelsResponseV2(code, message, channelMap)
        } catch (e: Exception) {
            Timber.e(e, "parseChannelsResponse failed")
            ChannelsResponseV2()
        }
    }

    // ── EPG (batch) ──────────────────────────────────────────────────────────────

    /**
     * Fetches EPG for one or more channels.
     * @param channelIds  List of channel content IDs (e.g. ["300001", "300002"]).
     * @param offsets     Day offsets relative to today: [0] = today, [1] = tomorrow, etc.
     * @return Parsed [EpgResponseV2] where `data` is keyed by channelId.
     */
    suspend fun getEPGBatch(
        channelIds: List<String>,
        offsets: List<Int> = listOf(0),
        session: Map<String, Any>
    ): EpgResponseV2 = withContext(Dispatchers.IO) {
        try {
            val headers = BASE_HEADERS + authHeaders(session) + mapOf(
                ConstantsV2.X_PAGE        to "Metadata",
                ConstantsV2.CACHE_CONTROL to "max-age=1800"
            )

            // Build query params manually — they need URL-encoded JSON arrays
            val contentIdsJson = "[${channelIds.joinToString(",") { "\"$it\"" }}]"
            val offsetsJson    = "[${offsets.joinToString(",")}]"

            val url = "${ConstantsV2.Urls.EPG}" +
                "?contentIds=${URLEncoder.encode(contentIdsJson, "UTF-8")}" +
                "&offsets=${URLEncoder.encode(offsetsJson, "UTF-8")}"

            val request = AndroidNetworking.get(url)
                .addHeaders(headers)
                .setPriority(com.androidnetworking.common.Priority.HIGH)
                .build()

            val response = request.executeForString()
            if (response.isSuccess) {
                parseEpgResponse(response.result?.toString() ?: "")
            } else {
                Timber.e("getEPGBatch failed: ${response.error?.errorBody}")
                EpgResponseV2()
            }
        } catch (e: Exception) {
            Timber.e(e, "getEPGBatch exception")
            EpgResponseV2()
        }
    }

    /** Manually parses the EPG JSON: {"code":200,"data":{"channelId":[{show},...]}} */
    private fun parseEpgResponse(json: String): EpgResponseV2 {
        return try {
            val root = JSONObject(json)
            val code = root.optInt("code", 0)
            val message = root.optString("message", "")
            val dataObj = root.optJSONObject("data") ?: return EpgResponseV2(code, message)

            val epgMap = mutableMapOf<String, List<EpgShowV2>>()
            val keys = dataObj.keys()
            while (keys.hasNext()) {
                val channelId = keys.next()
                val showsArr = dataObj.optJSONArray(channelId) ?: continue
                val shows = (0 until showsArr.length()).map { i ->
                    val s = showsArr.getJSONObject(i)
                    EpgShowV2(
                        programId   = s.optString("programId"),
                        title       = s.optString("title"),
                        description = s.optString("description"),
                        startEpoch  = s.optLong("startEpoch"),
                        endEpoch    = s.optLong("endEpoch"),
                        thumbnail   = s.optString("thumbnail"),
                        programDate = s.optString("programDate"),
                        showId      = s.optString("showId")
                    )
                }
                epgMap[channelId] = shows
            }
            EpgResponseV2(code, message, epgMap)
        } catch (e: Exception) {
            Timber.e(e, "parseEpgResponse failed")
            EpgResponseV2()
        }
    }

    // ── Genre Filters ────────────────────────────────────────────────────────────

    /**
     * Fetches genre and language filter options. Does not require auth.
     * @return JSONObject with `data.filters` array.
     */
    suspend fun getGenreFilters(): JSONObject = withContext(Dispatchers.IO) {
        try {
            val headers = mapOf(
                ConstantsV2.X_PAGE           to "Metadata",
                ConstantsV2.X_APPNAME        to ConstantsV2.Values.APP_NAME,
                ConstantsV2.X_API_SIGNATURES to ConstantsV2.Values.API_SIGNATURES,
                ConstantsV2.X_FEATURE_CODE   to ConstantsV2.Values.FEATURE_CODE,
                "cache-control"              to "no-cache",
                "user-agent"                 to ConstantsV2.Values.USER_AGENT
            )

            val request = AndroidNetworking.get(ConstantsV2.Urls.GENRE_FILTERS)
                .addHeaders(headers)
                .setPriority(com.androidnetworking.common.Priority.LOW)
                .build()

            val response = request.executeForJSONObject()
            if (response.isSuccess) response.result as JSONObject else JSONObject()
        } catch (e: Exception) {
            Timber.e(e, "getGenreFilters exception")
            JSONObject()
        }
    }

    // ── TV Guide Filters ─────────────────────────────────────────────────────────

    /**
     * Fetches curated TV guide filter options (requires auth).
     * @return JSONObject with `data.curatedFilters`, `data.languages`, and `data.genres`.
     */
    suspend fun getTVGuideFilters(session: Map<String, Any>): JSONObject = withContext(Dispatchers.IO) {
        try {
            val headers = BASE_HEADERS + authHeaders(session) + mapOf(
                ConstantsV2.X_PAGE       to "Metadata",
                ConstantsV2.CACHE_CONTROL to "no-cache"
            )

            val request = AndroidNetworking.get(ConstantsV2.Urls.TVGUIDE_FILTERS)
                .addHeaders(headers)
                .setPriority(com.androidnetworking.common.Priority.LOW)
                .build()

            val response = request.executeForJSONObject()
            if (response.isSuccess) response.result as JSONObject else JSONObject()
        } catch (e: Exception) {
            Timber.e(e, "getTVGuideFilters exception")
            JSONObject()
        }
    }

    // ── Playback ─────────────────────────────────────────────────────────────────

    /**
     * Fetches the playback stream URLs for a live channel.
     *
     * The v2 playback response embeds time-limited tokens directly into the m3u8/mpd URLs,
     * so no separate CDN cookie step is required.
     *
     * @param channelId The content ID of the channel (e.g. "300001").
     * @return Parsed [PlaybackResponseV2].
     */
    suspend fun getPlaybackUrl(
        channelId: String,
        session: Map<String, Any>
    ): PlaybackResponseV2 = withContext(Dispatchers.IO) {
        try {
            fun str(key: String) = when (val v = session[key]) {
                is String -> v; null -> ""; else -> v.toString()
            }

            val headers = BASE_HEADERS + mapOf(
                ConstantsV2.X_PAGE           to "Player",
                ConstantsV2.X_ACCESS_TOKEN   to str(ConstantsV2.KEY_AUTH_TOKEN),
                ConstantsV2.DEVICEID         to str(ConstantsV2.KEY_DEVICE_ID),
                ConstantsV2.UNIQUEID         to str(ConstantsV2.KEY_UNIQUE_ID),
                ConstantsV2.SUBID            to str(ConstantsV2.KEY_SUBSCRIBER_ID),
                ConstantsV2.RMN              to str(ConstantsV2.KEY_RMN),
                ConstantsV2.RESTRICTION      to ConstantsV2.Values.RESTRICTION,
                ConstantsV2.CACHE_CONTROL    to "no-cache"
            )

            val deviceId = str(ConstantsV2.KEY_DEVICE_ID).ifEmpty { ConstantsV2.Values.DEVICE_ID }

            val body = JSONObject().apply {
                put("bitrateProfile", ConstantsV2.Values.BITRATE_PROFILE)
                put("model",          ConstantsV2.Values.MODEL)
                put("manufacturer",   ConstantsV2.Values.MANUFACTURER)
                put("osVersion",      ConstantsV2.Values.OS_VERSION)
                put("serialNo",       deviceId)
                put("is4kSupport",    false)
                put("hevcSupport",    true)
                put("dolbySupport",   false)
                put("appVersion",     ConstantsV2.Values.APP_VERSION)
            }

            val url = "${ConstantsV2.Urls.PLAYBACK_BASE}/$channelId"

            val request = AndroidNetworking.post(url)
                .addHeaders(headers)
                .addJSONObjectBody(body)
                .doNotCacheResponse()
                .setPriority(com.androidnetworking.common.Priority.HIGH)
                .build()

            val response = request.executeForString()
            if (response.isSuccess) {
                gson.fromJson(java.io.StringReader(response.result?.toString() ?: ""), PlaybackResponseV2::class.java)
                    ?: PlaybackResponseV2()
            } else {
                Timber.e("getPlaybackUrl failed for $channelId: ${response.error?.errorBody}")
                PlaybackResponseV2()
            }
        } catch (e: Exception) {
            Timber.e(e, "getPlaybackUrl exception for $channelId")
            PlaybackResponseV2()
        }
    }

    // ── Token Refresh ────────────────────────────────────────────────────────────

    /**
     * Refreshes the auth token using the existing refresh token.
     * Uses the same v2 refreshtoken endpoint but with the updated header structure.
     * @return Updated authToken string, or null on failure.
     */
    suspend fun refreshToken(session: Map<String, Any>): String? = withContext(Dispatchers.IO) {
        fun str(key: String) = when (val v = session[key]) {
            is String -> v; null -> ""; else -> v.toString()
        }

        try {
            // The tokenservice v2 endpoint uses legacy header names ("accesstoken", "uniqueId")
            // identical to the v1 JioTV refresh flow — x-accesstoken is NOT accepted here.
            val headers = mapOf(
                "accesstoken"                to str(ConstantsV2.KEY_AUTH_TOKEN),
                "uniqueId"                   to str(ConstantsV2.KEY_UNIQUE_ID),
                ConstantsV2.X_APPNAME        to ConstantsV2.Values.APP_NAME_AUTH,  // JioTVPlus
                ConstantsV2.X_PLATFORM       to ConstantsV2.Values.PLATFORM_TV,    // jiotvplus-androidtv
                ConstantsV2.X_API_SIGNATURES to ConstantsV2.Values.API_SIGNATURES,
                ConstantsV2.X_FEATURE_CODE   to ConstantsV2.Values.FEATURE_CODE,
                "cache-control"              to "no-cache",
                "user-agent"                 to ConstantsV2.Values.USER_AGENT
            )

            val body = JSONObject().apply {
                put("appName",      ConstantsV2.Values.APP_NAME)
                put("deviceId",     str(ConstantsV2.KEY_DEVICE_ID))
                put("refreshToken", str(ConstantsV2.KEY_REFRESH_TOKEN))
            }

            val request = AndroidNetworking.post(Constants.refreshTokenURL)
                .addHeaders(headers)
                .addJSONObjectBody(body)
                .setPriority(com.androidnetworking.common.Priority.HIGH)
                .build()

            val response = request.executeForJSONObject()
            if (response.isSuccess) {
                val json = response.result as JSONObject
                // v2 response: top-level authToken, or nested in data
                when {
                    json.has("authToken") -> json.getString("authToken")
                    json.has("data") && json.getJSONObject("data").has("authToken") ->
                        json.getJSONObject("data").getString("authToken")
                    else -> { Timber.w("refreshToken: no authToken in response"); null }
                }
            } else {
                Timber.e("refreshToken failed: ${response.error?.errorBody}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "refreshToken exception")
            null
        }
    }
}
