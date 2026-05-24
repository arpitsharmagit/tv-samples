package com.android.tv.classics.jio.models.v2

import com.google.gson.annotations.SerializedName

/** Top-level response for GET /metadata/v2/livechannels/epg */
data class EpgResponseV2(
    @SerializedName("code")    val code: Int = 0,
    @SerializedName("message") val message: String = "",
    /** Map of channelId → list of shows */
    @SerializedName("data")    val data: Map<String, List<EpgShowV2>> = emptyMap()
)

/** A single EPG entry for a v2 channel. */
data class EpgShowV2(
    @SerializedName("programId")   val programId: String = "",
    @SerializedName("title")       val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("startEpoch")  val startEpoch: Long = 0L,
    @SerializedName("endEpoch")    val endEpoch: Long = 0L,
    @SerializedName("thumbnail")   val thumbnail: String = "",
    @SerializedName("programDate") val programDate: String = "",
    @SerializedName("showId")      val showId: String = ""
)
