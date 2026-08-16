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
import timber.log.Timber
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
                    Timber.e( "Error reading genre data", e)
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
                Timber.e( "Error processing channels data", e)
                // Return empty result if there's an error
                return FeedParseResult(emptyList(), genres)
            }
        } catch (e: Exception) {
            Timber.e( "Fatal error in parseMediaFeed", e)
            // Return completely empty result in case of any exception
            return FeedParseResult(emptyList(), emptyList())
        }
        }

        /** Parses metadata from our assets folder and synchronizes the database */
        @Synchronized fun synchronize(context: Context) {
            Timber.d("Starting synchronization work")
            val database = TvMediaDatabase.getInstance(context)

            try {
                // Run in a blocking context since this is called from a Worker
                runBlocking {
                    try {
                        val feed = parseMediaFeed(context)
    
                        // Skip synchronization if we have no metadata or collections
                        if (feed.metadata.isEmpty() && feed.collections.isEmpty()) {
                            Timber.w( "No metadata or collections found, skipping synchronization")
                            return@runBlocking
                        }
    
                        // Gets a set of incoming metadata IDs for O(1) lookups
                        val metadataIdSet = feed.metadata.map { it.id }.toSet()

                        // Load all existing DB data once — reused for both delete and insert checks
                        val existingMetadata = database.metadata().findAll()
                        val existingMetadataIds = existingMetadata.map { it.id }.toSet()

                        try {
                            // Batch delete channels that were removed from the feed
                            val toDelete = existingMetadata.filter { it.id !in metadataIdSet }
                            if (toDelete.isNotEmpty()) {
                                database.metadata().deleteAll(toDelete)
                                toDelete.forEach {
                                    try {
                                        TvLauncherUtils.removeProgram(context, it)
                                        TvLauncherUtils.removeFromWatchNext(context, it)
                                    } catch (e: Exception) {
                                        Timber.e("Error removing from launcher: ${it.id}", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e("Error processing metadata deletions", e)
                        }

                        try {
                            // Batch delete collections removed from feed
                            val feedCollectionIds = feed.collections.map { it.id }.toSet()
                            val existingCollectionIds = database.collections().findAllIds().toSet()
                            val toDeleteCollections = existingCollectionIds
                                .filter { it !in feedCollectionIds }
                                .mapNotNull { database.collections().findById(it) }
                            toDeleteCollections.forEach {
                                try {
                                    database.collections().delete(it)
                                    TvLauncherUtils.removeChannel(context, it)
                                } catch (e: Exception) {
                                    Timber.e("Error removing collection: ${it.id}", e)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e("Error processing collection deletions", e)
                        }

                        try {
                            // Batch insert new channels (preserves existing user state like favorite/hidden)
                            val toInsert = feed.metadata.filter { it.id !in existingMetadataIds }
                            if (toInsert.isNotEmpty()) {
                                database.metadata().insert(*toInsert.toTypedArray())
                            }
                        } catch (e: Exception) {
                            Timber.e("Error inserting new metadata", e)
                        }

                        try {
                            // Batch insert new collections
                            val existingCollectionIds = database.collections().findAllIds().toSet()
                            val toInsertCollections = feed.collections.filter { it.id !in existingCollectionIds }
                            if (toInsertCollections.isNotEmpty()) {
                                database.collections().insert(*toInsertCollections.toTypedArray())
                            }
                        } catch (e: Exception) {
                            Timber.e("Error inserting new collections", e)
                        }
                    } catch (e: Exception) {
                        Timber.e( "Error in synchronization process", e)
                    }
                }
            } catch (e: Exception) {
                Timber.e( "Fatal error in synchronize method", e)
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

        /**
         * Re-fetches channels and replaces the local DB copies for both metadata and collections.
         * Returns true when refresh data is available and persisted successfully.
         */
        @Synchronized fun refreshAndReplace(context: Context): Boolean {
            Timber.d("Starting manual refresh and replace")
            val database = TvMediaDatabase.getInstance(context)

            return try {
                runBlocking {
                    val feed = parseMediaFeed(context)
                    if (feed.metadata.isEmpty() && feed.collections.isEmpty()) {
                        Timber.w("Refresh aborted: no metadata or collections fetched")
                        return@runBlocking false
                    }

                    database.metadata().truncate()
                    database.collections().truncate()

                    if (feed.metadata.isNotEmpty()) {
                        database.metadata().insert(*feed.metadata.toTypedArray())
                    }
                    if (feed.collections.isNotEmpty()) {
                        database.collections().insert(*feed.collections.toTypedArray())
                    }
                    true
                }
            } catch (e: Exception) {
                Timber.e("Error while refreshing channels", e)
                false
            }
        }


    }
}
