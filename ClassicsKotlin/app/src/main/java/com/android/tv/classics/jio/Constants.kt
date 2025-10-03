package com.android.tv.classics.jio

class Constants {
    companion object {
        const val preferenceFile = "LiveTvPref"

        const val ACCESS_TOKEN = "accesstoken"
        const val APP_KEY = "appkey"
        const val APP_NAME = "appName"
        const val APP_VERSION_CODE = "appversioncode"
        const val AUTH_TOKEN = "authToken"
        const val CAM_ID = "camid"
        const val CHANNEL_ID = "channelid"
        const val CRM_ID = "crmid"
        const val DM = "dm"
        const val DEVICE_ID = "deviceId"
        const val DEVICE_TYPE = "devicetype"
        const val IS_BROADCAST = "isbroadcast"
        const val USER_ID = "userId"
        const val LANGUAGE_ID = "languageId"
        const val LANG_ID = "langid"
        const val LBCOOKIES = "lbcookie"
        const val OS = "os"
        const val OS_VERSION = "osVersion"
        const val OTT_USER = "isott"
        const val REFRESH_TOKEN = "refreshToken"
        const val SESSIONID = "sid"
        const val SRNO = "srno"
        const val SSO_TOKEN = "ssotoken"
        const val SUBSCRIBER_ID = "subscriberId"
        const val THIRD_PARTY_APP = "thirdPartyApp"
        const val UNIQUE_ID = "uniqueId"
        const val USER_GROUP = "usergroup"
        const val USER_TYPE = "usertype"
        const val USER_AGENT = "User-Agent"
        const val VERSION_CODE = "versionCode"

        const val otpURL = "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/send"
        const val verifyURL = "https://jiotvapi.media.jio.com/userservice/apis/v2/loginotp/verify"
        const val refreshTokenURL = "https://auth.media.jio.com/tokenservice/apis/v2/refreshtoken?langId=6"
        const val channelURL = "https://jiotvapi.media.jio.com/playback/apis/v1.1/geturl?langId=6"
        const val channelsURL = "https://jiotvapi.cdn.jio.com/apis/v3.1/getMobileChannelList/get/?langId=6&os=android&devicetype=tv&usertype=JIO&version=396"
        const val imageUrl = "https://jiotv.catchup.cdn.jio.com/dare_images/images/"
        const val epgUrl = "https://jiotvapi.cdn.jio.com/apis/v1.3/getepg/get?offset=0&channel_id=channelId&langId=6";
    }

    object VALUES {
        const val VERSION_CODE = "396"
        const val VERSION_NAME = "7.1.7"
        const val OS = "android"
        const val DM = "OnePlus HD1911"
        const val OS_VERSION = "12"
        const val APP_KEY = "NzNiMDhlYzQyNjJm"
        const val DEVICE_ID = "94f739da0e91b3a6"
        const val USER_GROUP = "tvYR7NSNn7rymo3F"
        const val DEVICE_TYPE = "phone"
        const val LBCOOKIES ="1"
        const val USER_AGENT = "JioTV"
        const val LANGUAGE_ID = "6"
        const val APP_NAME = "RJIL_JioTV"
    }

    fun getAllHeaders(){

    }
}