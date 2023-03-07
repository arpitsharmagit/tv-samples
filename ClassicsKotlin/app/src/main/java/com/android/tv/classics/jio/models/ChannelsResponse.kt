package com.android.tv.classics.jio.models

import com.google.gson.annotations.SerializedName

data class ChannelsResponse (
    @SerializedName("code"    ) var code    : Int?              = null,
    @SerializedName("message" ) var message : String?           = null,
    @SerializedName("result"  ) var result  : ArrayList<Result> = arrayListOf()
)

data class Result (

    @SerializedName("channel_id"                   ) var channelId                 : Int?              = null,
    @SerializedName("channel_order"                ) var channelOrder              : String?           = null,
    @SerializedName("channel_name"                 ) var channelName               : String?           = null,
    @SerializedName("channelCategoryId"            ) var channelCategoryId         : Int?              = null,
    @SerializedName("channelLanguageId"            ) var channelLanguageId         : Int?              = null,
    @SerializedName("isHD"                         ) var isHD                      : Boolean?          = null,
    @SerializedName("isCatchupAvailable"           ) var isCatchupAvailable        : Boolean?          = null,
    @SerializedName("isCam"                        ) var isCam                     : Int?              = null,
    @SerializedName("screenType"                   ) var screenType                : Int?              = null,
    @SerializedName("concurrentEnabled"            ) var concurrentEnabled         : Boolean?          = null,
    @SerializedName("broadcasterId"                ) var broadcasterId             : Int?              = null,
    @SerializedName("logoUrl"                      ) var logoUrl                   : String?           = null,
    @SerializedName("packageIds"                   ) var packageIds                : ArrayList<String> = arrayListOf(),
    @SerializedName("playbackRightIds"             ) var playbackRightIds          : String?           = null,
    @SerializedName("isFingerPrint"                ) var isFingerPrint             : Boolean?          = null,
    @SerializedName("isFingerPrintMobile"          ) var isFingerPrintMobile       : Boolean?          = null,
    @SerializedName("stbChannelNumber"             ) var stbChannelNumber          : Int?              = null,
    @SerializedName("isAdsEnabled"                 ) var isAdsEnabled              : Boolean?          = null,
    @SerializedName("isMidRollAdsEnabled"          ) var isMidRollAdsEnabled       : Boolean?          = null,
    @SerializedName("isMulticastStream"            ) var isMulticastStream         : Boolean?          = null,
    @SerializedName("ShowPDPExtra"                 ) var ShowPDPExtra              : Boolean?          = null,
    @SerializedName("PDPExtras"                    ) var PDPExtras                 : ArrayList<String> = arrayListOf(),
    @SerializedName("concurrencyCode"              ) var concurrencyCode           : Int?              = null,
    @SerializedName("aspectRatio"                  ) var aspectRatio               : String?           = null,
    @SerializedName("stbCatchup"                   ) var stbCatchup                : Boolean?          = null,
    @SerializedName("isMulticastStreamOberoi"      ) var isMulticastStreamOberoi   : Boolean?          = null,
    @SerializedName("channelPrice"                 ) var channelPrice              : String?           = null,
    @SerializedName("enableMidRollAds"             ) var enableMidRollAds          : Int?              = null,
    @SerializedName("scorecardEnabled"             ) var scorecardEnabled          : Boolean?          = null,
    @SerializedName("PlayAlongEnabled"             ) var PlayAlongEnabled          : Int?              = null,
    @SerializedName("midRollAdSpotId"              ) var midRollAdSpotId           : String?           = null,
    @SerializedName("nativeInfeedAdSpotId"         ) var nativeInfeedAdSpotId      : String?           = null,
    @SerializedName("preRollAdSpotId"              ) var preRollAdSpotId           : String?           = null,
    @SerializedName("midRollCatchupAdSpotId"       ) var midRollCatchupAdSpotId    : String?           = null,
    @SerializedName("enableVideoInterstitial"      ) var enableVideoInterstitial   : Boolean?          = null,
    @SerializedName("enablePlayAlong"              ) var enablePlayAlong           : Int?              = null,
    @SerializedName("is_premium"                   ) var isPremium                 : Boolean?          = null,
    @SerializedName("playAlongUrl"                 ) var playAlongUrl              : String?           = null,
    @SerializedName("playAlongIconUrl"             ) var playAlongIconUrl          : String?           = null,
    @SerializedName("enable_preroll_companion_ads" ) var enablePrerollCompanionAds : Boolean?          = null,
    @SerializedName("prerollCompanionAdSpotId"     ) var prerollCompanionAdSpotId  : String?           = null,
    @SerializedName("enable_midroll_companion_ads" ) var enableMidrollCompanionAds : Boolean?          = null,
    @SerializedName("midrollCompanionAdSpotId"     ) var midrollCompanionAdSpotId  : String?           = null,
    @SerializedName("plan_type"                    ) var planType                  : String?           = null,
    @SerializedName("business_type"                ) var businessType              : String?           = null

)