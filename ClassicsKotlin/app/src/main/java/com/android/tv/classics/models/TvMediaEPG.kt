/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.classics.models

import android.app.SearchManager
import android.database.Cursor
import android.media.tv.TvContentRating
import android.net.Uri
import android.os.Parcelable
import android.provider.BaseColumns
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.tvprovider.media.tv.BasePreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.android.parcel.Parcelize
import java.util.Locale

/**
 * Data class representing a piece of content metadata (title, content URI, state-related fields
 * [playback position], etc.)
 */
@Entity
@Parcelize
data class TvMediaEPG(
    var srno                : Long?              = null,
    var showId              : String?           = null,
    var showtime            : String?           = null,
    var showname            : String?           = null,
    var description         : String?           = null,
    var director            : String?           = null,
    var starCast            : String?           = null,
    var duration            : Int?              = null,
    var endtime             : String?           = null,
    var showCategory        : String?           = null,
    var showGenre           : ArrayList<String> = arrayListOf(),
    var episodeNum          : Int?              = null,
    var willRepeat          : Boolean?          = null,
    var episodeDesc         : String?           = null,
    var isCatchupAvailable  : Boolean?          = null,
    var twitterHandle       : String?           = null,
    var keywords            : ArrayList<String> = arrayListOf(),
    var premiumText         : String?           = null,
    var isCam               : Int?              = null,
    var pcr                 : String?           = null,
    var channelId           : Int?              = null,
    var startTime           : Int?              = null,
    var showLanguageId      : Int?              = null,
    var showCategoryId      : Int?              = null,
    var renderImage         : Boolean?          = null,
    var episodeThumbnail    : String?           = null,
    var episodePoster       : String?           = null,
    var isLiveAvailable     : Boolean?          = null,
    var canRecord           : Boolean?          = null,
    var canRecordStb        : Boolean?          = null,
    var stbCatchupAvailable : Boolean?          = null,
    var isDownloadable      : Boolean?          = null,
    var startEpoch          : Long?             = null,
    var endEpoch            : Long?             = null,
    var channelName         : String?           = null,
    var isPremium           : Boolean?          = null,
    var planType            : String?           = null,
    var businessType        : String?           = null,
    var isPastEpisode       : Boolean?          = null

) : Parcelable {

    /** Compares only fields not related to the state */
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    /** We must override [hashCode] if we override the [equals] function */
    override fun hashCode(): Int {
        return super.hashCode()
    }

    companion object {

    }
}


