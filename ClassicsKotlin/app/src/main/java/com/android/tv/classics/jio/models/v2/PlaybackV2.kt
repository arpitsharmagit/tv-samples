package com.android.tv.classics.jio.models.v2

import com.google.gson.annotations.SerializedName

/** Top-level response for POST /playback/v2/{channelId} */
data class PlaybackResponseV2(
    @SerializedName("code")         val code: Int = 0,
    @SerializedName("playbackCode") val playbackCode: Int = 0,
    @SerializedName("message")      val message: String = "",
    @SerializedName("data")         val data: PlaybackDataV2? = null
)

data class PlaybackDataV2(
    @SerializedName("contentId")   val contentId: String = "",
    @SerializedName("videoId")     val videoId: Long = 0L,
    @SerializedName("name")        val name: String = "",
    @SerializedName("m3u8")        val m3u8: StreamUrlsV2 = StreamUrlsV2(),
    @SerializedName("mpd")         val mpd: StreamUrlsV2 = StreamUrlsV2(),
    @SerializedName("keyURL")      val keyURL: String = "",
    @SerializedName("enableLR")    val enableLR: Boolean = false
)

data class StreamUrlsV2(
    @SerializedName("low")    val low: String = "",
    @SerializedName("medium") val medium: String = "",
    @SerializedName("high")   val high: String = "",
    @SerializedName("auto")   val auto: String = ""
) {
    /** Returns the best available URL: auto → high → medium → low */
    val best: String get() = auto.ifEmpty { high.ifEmpty { medium.ifEmpty { low } } }
    val isEmpty: Boolean get() = auto.isEmpty() && high.isEmpty() && medium.isEmpty() && low.isEmpty()
}
