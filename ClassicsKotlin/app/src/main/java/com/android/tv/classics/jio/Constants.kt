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
        const val verifyURL = "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/verify"
        const val refreshTokenURL = "https://auth.media.jio.com/tokenservice/apis/v1/refreshtoken?langId=6"
        const val channelURL = "https://jiotvapi.media.jio.com/playback/apis/v1/geturl?langId=6"
        const val channelsURL = "https://jiotvapi.cdn.jio.com/apis/v3.0/getMobileChannelList/get/?langId=6&os=android&devicetype=phone&usertype=JIO&version=330&langId=6"
        const val imageUrl = "https://jiotv.catchup.cdn.jio.com/dare_images/images/"
    }

    fun getAllHeaders(){

    }
}