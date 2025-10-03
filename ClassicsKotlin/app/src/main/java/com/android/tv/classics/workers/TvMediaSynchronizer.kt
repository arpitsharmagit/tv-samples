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
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/** Maps a JSONArray of strings */
 fun <T>JSONArray.mapString(transform: (String) -> T): List<T> =
        (0 until length()).map { transform(getString(it)) }

/** Maps a JSONArray of objects */
 fun <T>JSONArray.mapObject(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }

/** Worker that parses metadata from our assets folder and synchronizes the database */
class TvMediaSynchronizer(private val context: Context, params: WorkerParameters) :
        Worker(context, params) {

    /** Helper data class used to pass results around functions */
    private data class FeedParseResult(
            val metadata: List<TvMediaMetadata>,
            val collections: List<TvMediaCollection>)

    override fun doWork(): Result = try {
        synchronize(context)
        Result.success()
    } catch (exc: Exception) {
        Result.failure()
    }

    companion object {
        private val TAG = TvMediaSynchronizer::class.java.simpleName

        /** Fetches the metadata feed from our assets folder and parses its metadata */
        private suspend fun parseMediaFeed(context: Context): FeedParseResult {
            try {
                // Get channels data from API with error handling
                val data = JioAPI.getChannels()
                
                // Initializes an empty list to populate with metadata metadata
                val metadatas: MutableList<TvMediaMetadata> = mutableListOf()
    
                // Read genre data from assets file
                val genreData = try {
                    val genreStream = context.resources.assets.open("jio-genre-map.json")
                    JSONObject(String(genreStream.readBytes(), StandardCharsets.UTF_8))
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading genre data", e)
                    // Provide fallback data if file can't be read
                    JSONObject().apply { 
                        put("genre", JSONArray())
                    }
                }
    
                // Safely get genre array
                val genreArray = if (genreData.has("genre")) 
                    genreData.getJSONArray("genre") 
                else 
                    JSONArray()
                    
                // Traverses the feed and maps each genre
                val genres = genreArray.mapObject { obj ->
                    TvMediaCollection(
                        id = obj.optString("id", "0"),
                        title = obj.optString("title", "Unknown"),
                        description = obj.optString("description", ""),
                        artUri = obj.optString("image", "")?.let { if (it.isNotEmpty()) Uri.parse(it) else null },
                        orderBy = obj.optInt("order", 0))
                }

            try {
                // Safely get the 'result' array or empty array if it doesn't exist
                val resultArray = if (data.has("result")) data.getJSONArray("result") else JSONArray()
                
                // Traverses the feed and maps each collection
                val channels = resultArray.mapObject { obj ->
                    // Traverses the collection and map each content item metadata
                    // Use optString/optInt for safer JSON parsing
                    val logoUrl = obj.optString("logoUrl", "")
                    val contentUri = if (logoUrl.isNotEmpty()) 
                        Uri.parse(Constants.imageUrl + logoUrl) 
                    else 
                        Uri.EMPTY
                        
                    TvMediaMetadata(
                        collectionId = obj.optString("channelCategoryId", "0"),
                        id = obj.optString("channel_id", "0"),
                        title = obj.optString("channel_name", "Unknown Channel"),
                        lang = obj.optString("channelLanguageId", "1"),
                        contentUri = contentUri,
                        artUri = contentUri)
                }
    
                // Filter channels by language
                val myChannels = channels.filter { it.lang in listOf("1", "6", "3") }
    
                metadatas.addAll(myChannels)
                return FeedParseResult(metadatas, genres)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing channels data", e)
                // Return empty result if there's an error
                return FeedParseResult(emptyList(), genres)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in parseMediaFeed", e)
            // Return completely empty result in case of any exception
            return FeedParseResult(emptyList(), emptyList())
        }
        }

        /** Parses metadata from our assets folder and synchronizes the database */
        @Synchronized fun synchronize(context: Context) {
            Log.d(TAG, "Starting synchronization work")
            val database = TvMediaDatabase.getInstance(context)

            try {
                // Run in a blocking context since this is called from a Worker
                runBlocking {
                    try {
                        val feed = parseMediaFeed(context)
    
                        // Skip synchronization if we have no metadata or collections
                        if (feed.metadata.isEmpty() && feed.collections.isEmpty()) {
                            Log.w(TAG, "No metadata or collections found, skipping synchronization")
                            return@runBlocking
                        }
    
                        // Gets a list of the metadata IDs for comparisons
                        val metadataIdList = feed.metadata.map { it.id }
    
                        try {
                            // Deletes items in our database that have been deleted from the metadata feed
                            // NOTE: It's important to keep the things added to the TV launcher in sync
                            database.metadata().findAll()
                                .filter { !metadataIdList.contains(it.id) }
                                .forEach {
                                    try {
                                        database.metadata().delete(it)
                                        // Removes programs from TV launcher
                                        TvLauncherUtils.removeProgram(context, it)
                                        TvLauncherUtils.removeFromWatchNext(context, it)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error removing metadata: ${it.id}", e)
                                    }
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing metadata deletions", e)
                        }
                        
                        try {
                            database.collections().findAll()
                                .filter { !feed.collections.contains(it) }
                                .forEach {
                                    try {
                                        database.collections().delete(it)
                                        TvLauncherUtils.removeChannel(context, it)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error removing collection: ${it.id}", e)
                                    }
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing collection deletions", e)
                        }
    
                        // Insert new channels
                        try {
                            val dbChannels = database.metadata().findAll().map { it.id }
                            feed.metadata.filter { !dbChannels.contains(it.id) }
                                .forEach {
                                    try {
                                        database.metadata().insert(it)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error inserting metadata: ${it.id}", e)
                                    }
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error inserting new metadata", e)
                        }
    
                        // Insert new collections
                        try {
                            val dbCollections = database.collections().findAll().map { it.id }
                            feed.collections.filter { !dbCollections.contains(it.id) }
                                .forEach {
                                    try {
                                        database.collections().insert(it)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error inserting collection: ${it.id}", e)
                                    }
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error inserting new collections", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in synchronization process", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in synchronize method", e)
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
