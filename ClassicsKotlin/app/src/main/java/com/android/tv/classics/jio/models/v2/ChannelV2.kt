package com.android.tv.classics.jio.models.v2

import com.google.gson.annotations.SerializedName

/** Top-level response for GET /metadata/v2/livechannels */
data class ChannelsResponseV2(
    @SerializedName("code")    val code: Int = 0,
    @SerializedName("message") val message: String = "",
    @SerializedName("data")    val data: Map<String, ChannelV2> = emptyMap()
)

/** A single live channel from the v2 channels map. */
data class ChannelV2(
    @SerializedName("contentId")      val contentId: String = "",
    @SerializedName("name")           val name: String = "",
    @SerializedName("subtitle")       val subtitle: String = "",
    @SerializedName("still")          val still: String = "",
    @SerializedName("thumbnail")      val thumbnail: String = "",
    @SerializedName("stillFallback")  val stillFallback: String = "",
    @SerializedName("description")    val description: String = "",
    @SerializedName("language")       val language: String = "",
    @SerializedName("genres")         val genres: List<String> = emptyList(),
    @SerializedName("quality")        val quality: String = "",
    @SerializedName("channelNumber")  val channelNumber: Int = 0,
    @SerializedName("order")          val order: Int = 0,
    @SerializedName("isPremium")      val isPremium: Boolean = false,
    @SerializedName("restriction")    val restriction: Int = 0,
    @SerializedName("onAir")          val onAir: Boolean = false,
    @SerializedName("pause")          val pause: Boolean = false,
    @SerializedName("provider")       val provider: String = "",
    @SerializedName("vendor")         val vendor: String = "",
    @SerializedName("currentProgram") val currentProgram: CurrentProgramV2? = null
)

/** Inline current-program info embedded in each channel from the channels list. */
data class CurrentProgramV2(
    @SerializedName("title")     val title: String = "",
    @SerializedName("thumbnail") val thumbnail: String = "",
    @SerializedName("startEpoch") val startEpoch: Long = 0L,
    @SerializedName("endEpoch")   val endEpoch: Long = 0L,
    @SerializedName("updatedAt")  val updatedAt: String = ""
)
