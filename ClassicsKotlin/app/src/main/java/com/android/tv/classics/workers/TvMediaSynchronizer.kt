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

package com.android.tv.classics.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.tv.classics.jio.Constants
import com.android.tv.classics.jio.JioAPI
import com.android.tv.classics.models.*
import com.android.tv.classics.utils.TvLauncherUtils
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.interceptors.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/** Maps a JSONArray of strings */
private fun <T>JSONArray.mapString(transform: (String) -> T): List<T> =
        (0 until length()).map { transform(getString(it)) }

/** Maps a JSONArray of objects */
private fun <T>JSONArray.mapObject(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }

/** Worker that parses metadata from our assets folder and synchronizes the database */
class TvMediaSynchronizer(private val context: Context, params: WorkerParameters) :
        Worker(context, params) {

    /** Helper data class used to pass results around functions */
    private data class FeedParseResult(
            val metadata: List<TvMediaMetadata>,
            val collections: List<TvMediaCollection>)

    override fun doWork(): Result = try {
        AndroidNetworking.initialize(context)
        AndroidNetworking.enableLogging() // simply enable logging
        AndroidNetworking.enableLogging(HttpLoggingInterceptor.Level.BODY) // enabling logging with level
        synchronize(context)
        Result.success()
    } catch (exc: Exception) {
        Result.failure()
    }

    companion object {
        private val TAG = TvMediaSynchronizer::class.java.simpleName

        /** Fetches the metadata feed from our assets folder and parses its metadata */
        private fun parseMediaFeed(context: Context): FeedParseResult {
            // Reads JSON input into a JSONArray
            // We are using a local file, in your app you most likely will be using a remote URL
            val data = JioAPI.GetChannels()
//            val stream = context.resources.assets.open("jio-feed.json")
//            val data = JSONObject(
//                String(stream.readBytes(), StandardCharsets.UTF_8)
//            )

            // Initializes an empty list to populate with metadata metadata
            val metadatas: MutableList<TvMediaMetadata> = mutableListOf()

            // Reads JSON input into a JSONArray
            // We are using a local file, in your app you most likely will be using a remote URL
            var genreStream = context.resources.assets.open("jio-genre-map.json")
            var genreData = JSONObject(
                String(genreStream.readBytes(), StandardCharsets.UTF_8)
            )

            // Traverses the feed and maps each genre
            val genre = genreData.getJSONArray("genre")
            val genres = genre.mapObject { obj ->
                val collection = TvMediaCollection(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    artUri = obj.getString("image")?.let { Uri.parse(it) },
                    orderBy = obj.getInt("order"))
                collection
            }

            // Traverses the feed and maps each collection
            val channels = data.getJSONArray("result").mapObject { obj ->
                // Traverses the collection and map each content item metadata
                TvMediaMetadata(
                    collectionId = obj.getString("channelCategoryId"),
                    id = obj.getString("channel_id"),
                    title = obj.getString("channel_name"),
                    lang = obj.getString("channelLanguageId"),
                    contentUri = (Constants.imageUrl + obj.getString("logoUrl"))?.let { Uri.parse(it) }!!,
                    artUri = (Constants.imageUrl + obj.getString("logoUrl"))?.let { Uri.parse(it) })
            }

            val myChannels = channels.filter { it.lang in listOf("1", "6", "3") }

            metadatas.addAll(myChannels)
            return FeedParseResult(metadatas, genres)
        }

        /** Parses metadata from our assets folder and synchronizes the database */
        @Synchronized fun synchronize(context: Context) {
            Log.d(TAG, "Starting synchronization work")
            val database = TvMediaDatabase.getInstance(context)

            val feed = parseMediaFeed(context)

            // Gets a list of the metadata IDs for comparisons
            val metadataIdList = feed.metadata.map { it.id }


            // Deletes items in our database that have been deleted from the metadata feed
            // NOTE: It's important to keep the things added to the TV launcher in sync
            database.metadata().findAll()
                    .filter { !metadataIdList.contains(it.id) }
                    .forEach {
                        database.metadata().delete(it)
                        // Removes programs no longer present from TV launcher
                        TvLauncherUtils.removeProgram(context, it)
                        // Removes programs no longer present from Watch Next row
                        TvLauncherUtils.removeFromWatchNext(context, it)
                    }
            database.collections().findAll()
                    .filter { !feed.collections.contains(it) }
                    .forEach {
                        database.collections().delete(it)
                        // Removes channels from TV launcher
                        TvLauncherUtils.removeChannel(context, it)
                    }

            var dbChannels = database.metadata().findAll().map { it.id }
            feed.metadata.filter { !dbChannels.contains(it.id) }
                .forEach {
                    database.metadata().insert(it)
                }

            var dbCollections = database.collections().findAll().map { it.id }
            feed.collections.filter { !dbCollections.contains(it.id) }
                .forEach {
                    database.collections().insert(it)
                }

            // Upon insert, we will replace all metadata already added so we can update titles,
            // images, descriptions, etc. Note that we overloaded the `equals` function in our data
            // class to avoid replacing metadata which has an updated state such as playback
            // position.
//            database.metadata().insert(*feed.metadata.toTypedArray())
//            database.collections().insert(*feed.collections.toTypedArray())

            // Inserts the first collection as the "default" channel
//            val defaultChannelTitle = context.getString(R.string.app_name)
//            val defaultChannelArtUri = TvLauncherUtils.resourceUri(
//                    context.resources, R.drawable.ic_jasmine_logo)
//            val defaultChannelCollection = feed.collections.first().copy(
//                    title = defaultChannelTitle, artUri = defaultChannelArtUri)
//            val defaultChannelUri = TvLauncherUtils.upsertChannel(
//                    context, defaultChannelCollection,
//                    database.metadata().findByCollection(defaultChannelCollection.id))
//
////             Inserts the rest of the collections as channels that user can add to home screen
//            feed.collections.forEach {
//                TvLauncherUtils.upsertChannel(
//                        context, it, database.metadata().findByCollection(it.id))
//            }
        }


    }
}
