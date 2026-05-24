package com.android.tv.classics.jio

/**
 * Constants for the JioTVPlus v2 API layer.
 * The v2 API uses new base hosts, headers, and auth flow compared to the original JioTV v1.
 */
object ConstantsV2 {

    // ── Request Header Names ─────────────────────────────────────────────────────
    const val X_APPNAME        = "x-appname"
    const val X_API_KEY        = "x-api-key"
    const val X_PLATFORM       = "x-platform"
    const val X_API_SIGNATURES = "x-apisignatures"
    const val X_FEATURE_CODE   = "x-feature-code"
    const val X_ACCESS_TOKEN   = "x-accesstoken"
    const val X_PAGE           = "x-page"
    const val SUBID            = "subid"
    const val RMN              = "rmn"          // base64-encoded "+91{number}"
    const val RESTRICTION      = "restriction"
    const val DEVICEID         = "deviceid"
    const val UNIQUEID         = "uniqueid"
    const val CACHE_CONTROL    = "cache-control"

    // ── Auth Session Keys (stored in PrefStore) ──────────────────────────────────
    const val KEY_AUTH_TOKEN    = "authToken"
    const val KEY_REFRESH_TOKEN = "refreshToken"
    const val KEY_USER_ID       = "userId"
    const val KEY_SUBSCRIBER_ID = "subscriberId"
    const val KEY_UNIQUE_ID     = "uniqueId"
    const val KEY_SSO_TOKEN     = "ssoToken"
    const val KEY_IDENTIFIER    = "identifier"   // returned by sendOTP, needed for verifyOTP
    const val KEY_DEVICE_ID     = "deviceId"
    const val KEY_RMN           = "rmn"

    // ── App / Platform Values ────────────────────────────────────────────────────
    object Values {
        const val APP_NAME        = "RJIL_JioTVPlus"
        const val APP_NAME_AUTH   = "JioTVPlus"         // x-appname for sendOTP / verifyOTP
        const val API_KEY         = "l7xx61fae40fe3af4c93b02792ae12422a82"
        const val PLATFORM_STB    = "smartandroidtv"        // for metadata / playback requests
        const val PLATFORM_TV     = "jiotvplus-androidtv"   // for exchangeToken
        const val API_SIGNATURES  = "h789p19tv2"
        const val FEATURE_CODE    = "ce1eb674jdkc"
        const val APP_VERSION     = "2.5.6_2067"
        const val USER_AGENT      = "okhttp/5.0.0"
        const val DEVICE_TYPE_TV  = "tv"
        const val DEVICE_ID       = "3a8556f08a8128fe"
        const val BITRATE_PROFILE = "xxhdpi"
        const val MANUFACTURER    = "unknown"
        const val MODEL           = "sdk_google_atv_x86"
        const val OS_VERSION      = "9"
        const val RESTRICTION     = "0"
    }

    // ── Endpoints ────────────────────────────────────────────────────────────────
    object Urls {
        // Auth
        const val SEND_OTP       = "https://tv.media.jio.com/apis/v3.2/stbotplogin/sendotp"
        const val VERIFY_OTP     = "https://tv.media.jio.com/apis/v3.2/stbotplogin/verifyotp"
        const val EXCHANGE_TOKEN = "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/exchangetoken"

        // Content metadata (no auth for genre filters; auth required for others)
        const val CHANNELS        = "https://content-jiotvplus.media.jio.com/metadata/v2/livechannels"
        const val EPG             = "https://content-jiotvplus.media.jio.com/metadata/v2/livechannels/epg"
        const val GENRE_FILTERS   = "https://content-jiotvplus.media.jio.com/metadata/v2/livechannels/filters/genre"
        const val TVGUIDE_FILTERS = "https://content-jiotvplus.media.jio.com/metadata/v2/livechannels/tvguidefilters"

        // Playback — append channelId: "$PLAYBACK_BASE/{channelId}"
        const val PLAYBACK_BASE   = "https://api-jiotvplus.media.jio.com/playback/v2"
    }

    // ── Language names to filter on (v2 uses string names, not integer IDs) ─────
    val ALLOWED_LANGUAGES = setOf("Hindi", "English", "Tamil")
}
