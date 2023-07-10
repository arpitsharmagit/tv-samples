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

package com.android.tv.classics.fragments

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.PlaybackSupportFragment
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackGlue
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import coil.Coil
import coil.api.get
import com.android.tv.classics.LiveTvApplication
import com.android.tv.classics.MainActivity
import com.android.tv.classics.R
import com.android.tv.classics.jio.Constants
import com.android.tv.classics.jio.JioAPI
import com.android.tv.classics.jio.store.HttpStore
import com.android.tv.classics.jio.store.PrefStore
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.models.TvMediaMetadata
import com.android.tv.classics.presenters.TvMediaMetadataPresenter
import com.android.tv.classics.utils.TvLauncherUtils
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import com.google.gson.Gson
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy


/** A fragment representing the current metadata item being played */
class NowPlayingFragment : VideoSupportFragment() {

    private lateinit var metadata:TvMediaMetadata
    private var currentPlayIndex = -1
    private lateinit var channelsMetadataList: HashMap<String,TvMediaMetadata>

    /** AndroidX navigation arguments */
    private val args: NowPlayingFragmentArgs by navArgs()

    private lateinit var player: SimpleExoPlayer
    private lateinit var database: TvMediaDatabase

    /** Allows interaction with transport controls, volume keys, media buttons  */
    private lateinit var mediaSession: MediaSessionCompat

    /** Glue layer between the player and our UI */
    private lateinit var playerGlue: MediaPlayerGlue

    /**
     * Connects a [MediaSessionCompat] to a [Player] so transport controls are handled automatically
     */
    private lateinit var mediaSessionConnector: MediaSessionConnector

//    override fun onVideoSizeChanged(width: Int, height: Int) { }

    /** Custom implementation of [PlaybackTransportControlGlue] */
    private inner class MediaPlayerGlue(context: Context, adapter: LeanbackPlayerAdapter) :
            PlaybackTransportControlGlue<LeanbackPlayerAdapter>(context, adapter) {

        private val actionRewind = PlaybackControlsRow.RewindAction(context)
        private val actionFastForward = PlaybackControlsRow.FastForwardAction(context)
        private val actionClosedCaptions = PlaybackControlsRow.ClosedCaptioningAction(context)

        fun skipForward(millis: Long = SKIP_PLAYBACK_MILLIS) =
                // Ensures we don't advance past the content duration (if set)
                player.seekTo(if (player.contentDuration > 0) {
                    min(player.contentDuration, player.currentPosition + millis)
                } else {
                    player.currentPosition + millis
                })

        fun skipBackward(millis: Long = SKIP_PLAYBACK_MILLIS) =
                // Ensures we don't go below zero position
                player.seekTo(max(0, player.currentPosition - millis))

        fun nextChannel() {
            val nextChannelMetadata = channelsMetadataList[++currentPlayIndex]
            if(nextChannelMetadata != null){
                setMetadata(nextChannelMetadata)
            }
        }
        fun previousChannel() {
            val prevChannelMetadata = channelsMetadataList[--currentPlayIndex]
            if (prevChannelMetadata != null) {
                setMetadata(prevChannelMetadata)
            }
        }

        override fun onUpdateProgress() {}

        override fun onCreateRowPresenter(): PlaybackRowPresenter {
            return super.onCreateRowPresenter().apply {
                val rp = (this as? PlaybackTransportRowPresenter)
                rp?.progressColor = Color.TRANSPARENT
                rp?.secondaryProgressColor = Color.TRANSPARENT
            }
        }

        override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
//            super.onCreatePrimaryActions(adapter)
            // Append rewind and fast forward actions to our player, keeping the play/pause actions
            // created by default by the glue
//            adapter.add(actionRewind)
//            adapter.add(actionFastForward)
//            adapter.add(actionClosedCaptions)
        }

        override fun onActionClicked(action: Action) = when (action) {
//            actionRewind -> skipBackward()
//            actionFastForward -> skipForward()
            else -> super.onActionClicked(action)
        }

        /** Custom function used to update the metadata displayed for currently playing media */
        fun setMetadata(metadata: TvMediaMetadata) {
            // Displays basic metadata in the player
//            title = metadata.title
//            subtitle = metadata.title

            lifecycleScope.launch(Dispatchers.IO) {
                // set playback row art
//                metadata.artUri?.let { art = Coil.get(it) }

                // uses Dispatchers.Main context
                val authHeaders = LiveTvApplication.getAuthHeaders()
                // blocking I/O operation
                val response = JioAPI.GetPlaybackUrl(metadata.id,authHeaders)
                Log.i(TAG,response.getString("result"))
                metadata.contentUri = Uri.parse(response.getString("result"))

                val hashMap = HashMap<String, String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    hashMap[Constants.UNIQUE_ID] = authHeaders.getOrDefault("uniqueId","")
                    hashMap[Constants.SSO_TOKEN] = authHeaders.getOrDefault("ssotoken","")
                    hashMap[Constants.SUBSCRIBER_ID] = authHeaders.getOrDefault("crmid","")
                    hashMap[Constants.DEVICE_ID] = authHeaders.getOrDefault("deviceId","")
                    hashMap[Constants.OS] = "android"
                    hashMap[Constants.USER_ID] = authHeaders.getOrDefault("userId","")
                    hashMap[Constants.OS_VERSION] = "10"
                    hashMap[Constants.VERSION_CODE] = "285"
                    hashMap[Constants.CRM_ID] = authHeaders.getOrDefault("crmid","")
                    hashMap[Constants.SRNO] = metadata.id
                    hashMap[Constants.CHANNEL_ID] = metadata.id
                    hashMap[Constants.DEVICE_TYPE] = "phone"
                    hashMap[Constants.USER_GROUP] = authHeaders.getOrDefault("usergroup","")
                    hashMap[Constants.ACCESS_TOKEN] = authHeaders.getOrDefault("authToken","")
                }

                val playbackCookie = JioAPI.GetHeaderCookie(response.getString("result"),authHeaders);
                hashMap["Cookie"] = playbackCookie

                withContext(Dispatchers.Main){
                    // Prepares metadata playback
                    val mediaSource = prepareMediaSource(metadata.contentUri, hashMap)
                    player.prepare(mediaSource, false, true)
                    increasePlayCount()
                }
            }
        }
    }

    private fun prepareMediaSource(playbackUri: Uri, playbackHeaders: Map<String, String>): MediaSource {
        var buildCronetDataSourceFactory: HttpDataSource.Factory? =
            HttpStore.buildHttpDataSourceFactory(defaultBandwidthMeter)
        val buildHttpDataSourceFactory = HttpStore.buildHttpDataSourceFactory(defaultBandwidthMeter)

        if (buildCronetDataSourceFactory == null) {
            buildHttpDataSourceFactory.defaultRequestProperties.set(playbackHeaders)
        } else {
            buildCronetDataSourceFactory.defaultRequestProperties.set(playbackHeaders)
        }
        if (buildCronetDataSourceFactory == null) {
            buildCronetDataSourceFactory = buildHttpDataSourceFactory
        }

        val dataSourceFactory: ResolvingDataSource.Factory = ResolvingDataSource.Factory(
            buildCronetDataSourceFactory,  // Provide just-in-time request headers.
            { dataSpec: DataSpec ->
                dataSpec.withRequestHeaders(playbackHeaders)
            })

        return HlsMediaSource.Factory(dataSourceFactory as DataSource.Factory).createMediaSource(playbackUri)
    }

    /** Updates last know playback position */
    private val updateMetadataTask: Runnable = object : Runnable {
        override fun run() {

            // Make sure that the view has not been destroyed
            view ?: return

            // The player duration is more reliable, since metadata.playbackDurationMillis has the
            //  "official" duration as per Google / IMDb which may not match the actual media
            val contentDuration = player.duration
            val contentPosition = player.currentPosition

            // Updates metadata state
            metadata = args.metadata

            // Refersh Token
            LiveTvApplication.getAuthHeaders()?.let { headers ->
                JioAPI.RefreshToken(headers)
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            headers.put("authToken", response.getString("authToken"))
                            LiveTvApplication.setAuthHeaders(headers)
                            Toast.makeText(activity, "Token Refreshed ", Toast.LENGTH_SHORT)
                                .show()
                        }

                        override fun onError(error: ANError) {
                            Toast.makeText(
                                activity,
                                "Unable to Refresh Token",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    })

                // Get New GetPlaybackUrl
                lifecycleScope.launch(Dispatchers.IO) {
                    val response =
                        JioAPI.GetPlaybackUrl(metadata.id, LiveTvApplication.getAuthHeaders())
                    metadata.apply { contentUri = Uri.parse(response.getString("result")) }
                }

                // Marks as complete if 95% or more of video is complete
//                if (player.playbackState == SimpleExoPlayer.STATE_ENDED ||
//                    (contentDuration > 0 && contentPosition > contentDuration * 0.95)
//                ) {
//                    val programUri = TvLauncherUtils.removeFromWatchNext(requireContext(), metadata)
//                    if (programUri != null) lifecycleScope.launch(Dispatchers.IO) {
//                        database.metadata().update(metadata.apply { watchNext = false })
//                    }
//
//                    // If playback is not done, update the state in watch next row with latest time
//                } else {
//                    val programUri = TvLauncherUtils.upsertWatchNext(requireContext(), metadata)
//                    lifecycleScope.launch(Dispatchers.IO) {
//                        database.metadata().update(
//                            metadata.apply { if (programUri != null) watchNext = true })
//                    }
//                }

                // Schedules the next metadata update in METADATA_UPDATE_INTERVAL_MILLIS milliseconds
                Log.d(TAG, "Media metadata updated successfully")
                view?.postDelayed(this, METADATA_UPDATE_INTERVAL_MILLIS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backgroundType = PlaybackSupportFragment.BG_NONE
        channelsMetadataList = hashMapOf()
        database = TvMediaDatabase.getInstance(requireContext())
        metadata = args.metadata

        lifecycleScope.launch(Dispatchers.IO) {
            populateChannelList(metadata.collectionId)
        }

        // Adds this program to the continue watching row, in case the user leaves before finishing
        addWatchNext()

        // Initializes the video player
        player = ExoPlayerFactory.newSimpleInstance(requireContext())
        player.addListener(PlayerEventListener())
        mediaSession = MediaSessionCompat(requireContext(), getString(R.string.app_name))
        mediaSessionConnector = MediaSessionConnector(mediaSession)

        // Links our video player with this Leanback video playback fragment
        val playerAdapter = LeanbackPlayerAdapter(
                requireContext(), player, PLAYER_UPDATE_INTERVAL_MILLIS)

        // Enables pass-through of transport controls to our player instance
        playerGlue = MediaPlayerGlue(requireContext(), playerAdapter).apply {
            host = VideoSupportFragmentGlueHost(this@NowPlayingFragment)

            // Adds playback state listeners
//            addPlayerCallback(object : PlaybackGlue.PlayerCallback() {
//
//                override fun onPreparedStateChanged(glue: PlaybackGlue?) {
//                    super.onPreparedStateChanged(glue)
//                    if (glue?.isPrepared == true) {
//                        // When playback is ready, skip to last known position
//                        val startingPosition = metadata.playbackPositionMillis ?: 0
//                        Log.d(TAG, "Setting starting playback position to $startingPosition")
//                        seekTo(startingPosition)
//                    }
//                }
//
//                override fun onPlayCompleted(glue: PlaybackGlue?) {
//                    super.onPlayCompleted(glue)
//
//                    // Don't forget to remove irrelevant content from the continue watching row
//                    TvLauncherUtils.removeFromWatchNext(requireContext(), args.metadata)
//
//                    // When playback is finished, go back to the previous screen
//                    val navController = Navigation.findNavController(
//                            requireActivity(), R.id.fragment_container)
//                    navController.currentDestination?.id?.let {
//                        navController.popBackStack(it, true)
//                    }
//                }
//            })

            // Begins playback automatically
            playWhenPrepared()

            // Displays the current item's metadata
            setMetadata(metadata)

            title = ""
            subtitle = ""
        }

        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(
            playerGlue.controlsRow.javaClass, playerGlue.playbackRowPresenter
        )
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        // Setup the fragment adapter with our player glue presenter
        val arrayObjectAdapter = ArrayObjectAdapter(presenterSelector).apply {
            add(playerGlue.controlsRow)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val collection = database.collections().findById(metadata.collectionId)
            val header = HeaderItem(0, collection?.title)

            // Create corresponding row adapter for the album's songs
            val listRowAdapter = ArrayObjectAdapter(TvMediaMetadataPresenter()).apply {
                // Add all the collection's metadata to the row's adapter
                if (collection != null) {
                    setItems(database.metadata().findByCollection(collection.id), null)
                }
            }
            setOnItemViewClickedListener { _, item, _, row ->
                if (item is TvMediaMetadata) {
                    playerGlue.setMetadata(item)
                }
            }
            // Add a list row for the <header, row adapter> pair
            var row = ListRow(header, listRowAdapter)
            // Add all new rows at once using our diff callback for a smooth animation
            arrayObjectAdapter.add(row)
        }

        adapter = arrayObjectAdapter
        // Adds key listeners
        playerGlue.host.setOnKeyInterceptListener { view, keyCode, event ->

            // Early exit: if the controls overlay is visible, don't intercept any keys
            if (playerGlue.host.isControlsOverlayVisible) return@setOnKeyInterceptListener false

            // TODO(owahltinez): This workaround is necessary for navigation library to work with
            //  Leanback's [PlaybackSupportFragment]
            if (!playerGlue.host.isControlsOverlayVisible &&
                    keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "Intercepting BACK key for fragment navigation")
                if (player != null){
                    player.stop();
                }
                val navController = Navigation.findNavController(
                        requireActivity(), R.id.fragment_container)
                navController.currentDestination?.id?.let { navController.popBackStack(it, true) }
                return@setOnKeyInterceptListener true
            }

            // Skips ahead when user presses DPAD_RIGHT
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                // playerGlue.skipForward()
                preventControlsOverlay(playerGlue)
                return@setOnKeyInterceptListener true
            }

            // Rewinds when user presses DPAD_LEFT
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                // playerGlue.skipBackward()
                preventControlsOverlay(playerGlue)
                return@setOnKeyInterceptListener true
            }

            false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    /** Workaround used to prevent controls overlay from showing and taking focus */
    private fun preventControlsOverlay(playerGlue: MediaPlayerGlue) = view?.postDelayed({
        playerGlue.host.showControlsOverlay(false)
        playerGlue.host.hideControlsOverlay(false)
    }, 10)

    private fun populateChannelList(collectionId: String){
        val collection = database.collections().findById(collectionId)
        if (collection != null) {
            database.metadata().findByCollection(collection.id).forEach {
                    tvMediaMetadata -> channelsMetadataList[tvMediaMetadata.id] =
                tvMediaMetadata
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(Color.BLACK)
//        view.findViewById<View>(R.id.playback_controls_dock)?.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()

        mediaSessionConnector.setPlayer(player)
        mediaSession.isActive = true

        // Kick off metadata update task which runs periodically in the main thread
        //view?.postDelayed(updateMetadataTask, METADATA_UPDATE_INTERVAL_MILLIS)
    }

    /**
     * Deactivates and removes callbacks from [MediaSessionCompat] since the [Player] instance is
     * destroyed in onStop and required metadata could be missing.
     */
    override fun onPause() {
        super.onPause()

        playerGlue.pause()
        mediaSession.isActive = false
        mediaSessionConnector.setPlayer(null)

//        view?.post {
//            // Launch metadata update task one more time as the fragment becomes paused to ensure
//            //  that we have the most up-to-date information
//            updateMetadataTask.run()
//
//            // Cancel all future metadata update tasks
//            view?.removeCallbacks(updateMetadataTask)
//        }
    }

    /** Do all final cleanup in onDestroy */
    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
    }

    private fun increasePlayCount(){
        lifecycleScope.launch(Dispatchers.IO) {
            database.metadata().update(
                metadata.apply { metadata.playCount++ })
        }
    }

    private fun decreasePlayCount(){
        lifecycleScope.launch(Dispatchers.IO) {
            database.metadata().update(
                metadata.apply { metadata.playCount-- })
        }
    }

    private fun addWatchNext(){
        val programUri = TvLauncherUtils.upsertWatchNext(requireContext(), metadata)
        lifecycleScope.launch(Dispatchers.IO) {
            database.metadata().update(
                metadata.apply { if (programUri != null) watchNext = true })
        }
    }

    private fun removeWatchNext(){
        val programUri = TvLauncherUtils.removeFromWatchNext(requireContext(), metadata)
        if (programUri != null) lifecycleScope.launch(Dispatchers.IO) {
            database.metadata().update(metadata.apply { watchNext = false })
        }
    }

    private inner class PlayerEventListener : Player.EventListener {
        override fun onPlayerError(error: ExoPlaybackException) {
            removeWatchNext()
            decreasePlayCount()
        }
    }

    companion object {
        private val TAG = NowPlayingFragment::class.java.simpleName

        private val defaultBandwidthMeter = DefaultBandwidthMeter()

        /** How often the player refreshes its views in milliseconds */
        private const val PLAYER_UPDATE_INTERVAL_MILLIS: Int = 100

        /** Time between metadata updates in milliseconds */
        private val METADATA_UPDATE_INTERVAL_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)

        /** Default time used when skipping playback in milliseconds */
        private val SKIP_PLAYBACK_MILLIS: Long = TimeUnit.SECONDS.toMillis(10)
    }
}