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
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.PlaybackSupportFragment
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackGlue
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.android.tv.classics.LiveTvApplication
import com.android.tv.classics.R
import com.android.tv.classics.jio.Constants
import com.android.tv.classics.jio.JioAPI
import com.android.tv.classics.jio.store.HttpStore
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.models.TvMediaEPG
import com.android.tv.classics.models.TvMediaMetadata
import com.android.tv.classics.presenters.TvMediaMetadataPresenter
import com.android.tv.classics.utils.TvLauncherUtils
import com.android.tv.classics.workers.mapObject
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.util.EventLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.min


/** A fragment representing the current metadata item being played */
class NowPlayingFragment : VideoSupportFragment() {

    private lateinit var metadata:TvMediaMetadata
    private lateinit var shows: List<TvMediaEPG>
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

    override fun onVideoSizeChanged(width: Int, height: Int) { }

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
            val index: String = (++currentPlayIndex).toString()
            val nextChannelMetadata = channelsMetadataList[index]
            if(nextChannelMetadata != null){
                setMetadata(nextChannelMetadata)
            }
        }
        fun previousChannel() {
            val index: String = (--currentPlayIndex).toString()
            val prevChannelMetadata = channelsMetadataList[index]
            if (prevChannelMetadata != null) {
                setMetadata(prevChannelMetadata)
            }
        }

//        override fun onUpdateProgress() {}

//        override fun onCreateRowPresenter(): PlaybackRowPresenter {
//            return super.onCreateRowPresenter().apply {
//                val rp = (this as? PlaybackTransportRowPresenter)
//                rp?.progressColor = Color.TRANSPARENT
//                rp?.secondaryProgressColor = Color.TRANSPARENT
//            }
//        }

        override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
            super.onCreatePrimaryActions(adapter)
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
        fun setMetadata(newmetadata: TvMediaMetadata) {
            metadata = newmetadata
            // Displays basic metadata in the player
            lifecycleScope.launch(Dispatchers.IO) {
                // set playback row art
//                metadata.artUri?.let { art = Coil.get(it) }
                try {
                    TvLauncherUtils.refreshToken()
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing token", e)
                }
//                shows = emptyList()
                val epgResponse = JioAPI.getEPG(metadata.id)
                shows = epgResponse.getJSONArray("epg").mapObject { obj ->
                    // Traverses the collection and map each content item metadata
                    TvMediaEPG(
                        srno = obj.getLong("srno"),
                        showtime = obj.getString("showtime"),
                        showname = obj.getString("showname"),
                        description = obj.getString("description"),
                        duration = obj.getInt("duration"),
                        endtime = obj.getString("endtime"),
                        startEpoch = obj.getLong("startEpoch"),
                        endEpoch = obj.getLong("endEpoch"),
                        isPastEpisode = obj.getBoolean("isPastEpisode"),
                        isCatchupAvailable = obj.getBoolean("isCatchupAvailable"),
                    )
                }
                var currentShow = findCurrentShow()
                startPlayingCurrentShow(currentShow)
            }
        }
    }

    private suspend fun startPlayingCurrentShow(show: TvMediaEPG?){
        try {
            val authHeaders = LiveTvApplication.getAuthHeaders()

            val simpleDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
            simpleDateFormat.timeZone = TimeZone.getTimeZone("GMT")
            val beginTime = simpleDateFormat.format(show?.startEpoch)
            val endTime = simpleDateFormat.format(show?.endEpoch)
            val programId = show?.srno.toString()
            val srNo = programId.take(6)
            val body = mapOf(
                "channel_id" to metadata.id,
                "stream_type" to "Seek",
                "srno" to srNo,
                "programId" to programId,
                "begin" to beginTime,
                "end" to endTime
            )
            // blocking I/O operation
            val response = JioAPI.getPlaybackUrl(body, authHeaders)
            metadata.contentUri = Uri.parse(response.getString("result"))

            val hashMap = HashMap<String, String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                hashMap[Constants.UNIQUE_ID] = authHeaders.getOrDefault("uniqueId", "")
                hashMap[Constants.SSO_TOKEN] = authHeaders.getOrDefault("ssotoken", "")
                hashMap[Constants.SUBSCRIBER_ID] = authHeaders.getOrDefault("crmid", "")
                hashMap[Constants.DEVICE_ID] = authHeaders.getOrDefault("deviceId", "")
                hashMap[Constants.OS] = Constants.VALUES.OS
                hashMap[Constants.USER_ID] = authHeaders.getOrDefault("userId", "")
                hashMap[Constants.OS_VERSION] = Constants.VALUES.OS_VERSION
                hashMap[Constants.VERSION_CODE] = Constants.VALUES.VERSION_CODE
                hashMap[Constants.CRM_ID] = authHeaders.getOrDefault("crmid", "")
                hashMap[Constants.SRNO] = metadata.id
                hashMap[Constants.CHANNEL_ID] = metadata.id
                hashMap[Constants.DEVICE_TYPE] = "phone"
                hashMap[Constants.USER_GROUP] = authHeaders.getOrDefault("usergroup", "")
                hashMap[Constants.ACCESS_TOKEN] = authHeaders.getOrDefault("authToken", "")
            }

            val playbackCookie = JioAPI.getHeaderCookie(response.getString("result"), authHeaders)
            hashMap["Cookie"] = playbackCookie

            withContext(Dispatchers.Main) {
                increasePlayCount()
                // Prepares metadata playback
                val mediaSource = prepareMediaSource(metadata.contentUri, hashMap)
                player.prepare(mediaSource, false, true)

                val subTitleFormat = SimpleDateFormat("EEE dd MMM HH:mm a", Locale.US)
                subTitleFormat.timeZone = TimeZone.getDefault()

                playerGlue.title = show?.showname
                playerGlue.subtitle =
                    subTitleFormat.format(show?.startEpoch) + " | " + show?.duration.toString() + " mins"
            }
        }
        catch(e: Exception){
            Log.e(TAG,"error occurred while playing",e);
        }
    }

    private fun findCurrentShow(): TvMediaEPG? {
        var currentShow = shows.filter {
            Instant.now().isAfter(it.startEpoch?.let { it1 -> Instant.ofEpochMilli(it1) })  &&
                    Instant.now().isBefore(it.endEpoch?.let { it1 -> Instant.ofEpochMilli(it1) })
        }.firstOrNull()
        return currentShow
    }

    private fun findNextShow(): TvMediaEPG? {
        var currentShow = shows.filter {
            Instant.now().isAfter(it.endEpoch?.let { it1 -> Instant.ofEpochMilli(it1) })
        }.firstOrNull()
        return currentShow
    }

    private fun prepareMediaSource(playbackUri: Uri, playbackHeaders: Map<String, String>): HlsMediaSource {
        // Use DefaultHttpDataSource.Factory as the base
        val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            defaultRequestProperties.set(playbackHeaders)
        }

        // Attempt to build Cronet factory (if available)
        val buildCronetDataSourceFactory: HttpDataSource.Factory? =
            HttpStore.buildHttpDataSourceFactory(defaultBandwidthMeter)

        // Choose the appropriate factory (Cronet or default)
        val httpDataSourceFactory: HttpDataSource.Factory = buildCronetDataSourceFactory ?: defaultHttpDataSourceFactory

        // Create the ResolvingDataSource.Factory
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            httpDataSourceFactory,
            { dataSpec: DataSpec ->
                dataSpec.withRequestHeaders(playbackHeaders)
            }
        )

        // Create the HlsMediaSource
        return HlsMediaSource.Factory(resolvingDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(playbackUri))
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
                JioAPI.refreshToken(headers)
                    .getAsJSONObject(object : JSONObjectRequestListener {
                        override fun onResponse(response: JSONObject) {
                            val updatedHeaders = headers.toMutableMap()
                            updatedHeaders["authToken"] = response.getString("authToken")
                            LiveTvApplication.setAuthHeaders(updatedHeaders)
                        }

                        override fun onError(error: ANError) {
                            LiveTvApplication.showToast("Unable to Refresh Token")
                        }
                    })

                // Get New getPlaybackUrl
//                lifecycleScope.launch(Dispatchers.IO) {
//                    val response =
//                        JioAPI.getPlaybackUrl(metadata.id, LiveTvApplication.getAuthHeaders())
//                    metadata.apply { contentUri = Uri.parse(response.getString("result")) }
//                }

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
//        addWatchNext()

        // Initializes the video player
        player = ExoPlayerFactory.newSimpleInstance(requireContext())
        player.addListener(PlayerEventListener())
//        player.addAnalyticsListener(EventLogger(null))
        mediaSession = MediaSessionCompat(requireContext(), getString(R.string.app_name))
        mediaSessionConnector = MediaSessionConnector(mediaSession)

        // Links our video player with this Leanback video playback fragment
        val playerAdapter = LeanbackPlayerAdapter(
                requireContext(), player, PLAYER_UPDATE_INTERVAL_MILLIS)

        // Enables pass-through of transport controls to our player instance
        playerGlue = MediaPlayerGlue(requireContext(), playerAdapter).apply {
            host = VideoSupportFragmentGlueHost(this@NowPlayingFragment)

            // Adds playback state listeners
            addPlayerCallback(object : PlaybackGlue.PlayerCallback() {

                override fun onPlayStateChanged(glue: PlaybackGlue?) {
                    super.onPlayStateChanged(glue)

                    if (glue?.isPlaying == true && player.contentDuration > 0) {
                        // When playback is ready, skip to last known position
                        var currentShow = findCurrentShow()
//                        Log.d(TAG,"DURATION ======" + player.duration.toString() +" "+ player.contentDuration+" "+player.contentPosition)
//                        Log.d(TAG,"Range ===="+ player.contentPosition +" "+(player.contentDuration - (currentShow?.endEpoch!! - Calendar.getInstance().time.time)))
                        val remainingShowTimeMS = (currentShow?.endEpoch!! - Calendar.getInstance().time.time)
                        val diffOfPlaybackPosition = (player.contentDuration - remainingShowTimeMS) - player.contentPosition

                        if(diffOfPlaybackPosition> 10000 && arrayOf("154","155","162","289","291","471","474","476","483","514","524","525","872","1393","1396").contains(metadata.id)){
                            var seekPostion =  player.contentDuration - remainingShowTimeMS
                            Log.d(TAG,"SEEK POSITION ======" + seekPostion.toString()+ " contentDuration === "+ player.contentDuration)
                            seekTo(seekPostion)
                        }
                    }
                }
                override fun onPreparedStateChanged(glue: PlaybackGlue?) {
                    super.onPreparedStateChanged(glue)
                    if (glue?.isPrepared == true) {
                        // When playback is ready, skip to last known position
//                        val startingPosition = metadata.playbackPositionMillis ?: 0
//                        Log.d(TAG, "Setting starting playback position to $startingPosition")
//                        seekTo(0)
                    }
                }

                override fun onPlayCompleted(glue: PlaybackGlue?) {
                    super.onPlayCompleted(glue)
//                    var nextShow = findNextShow()
//                    if(nextShow?.isPastEpisode == true) {
//                        // Don't forget to remove irrelevant content from the continue watching row
//                        lifecycleScope.launch(Dispatchers.IO) {
//                            startPlayingCurrentShow(nextShow)
//                        }
//                    }
                }
            })

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
                    player.release();
                }
                val navController = Navigation.findNavController(
                        requireActivity(), R.id.fragment_container)
                navController.currentDestination?.id?.let { navController.popBackStack(it, true) }
                // Explicitly cleanup resources
                mediaSession.isActive = false
                mediaSessionConnector.setPlayer(null)
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
//        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
//            if (playWhenReady && playbackState == Player.STATE_READY) {
//                // media actually playing
//                Log.i(TAG,"started Playing");
//                Log.i(TAG,player.contentDuration.toString())
////                player.seekTo(C.TIME_UNSET);
//            }
//        }
        override fun onPlayerError(error: ExoPlaybackException) {

            Log.e(TAG,"PlayError: ChannelNo: ${metadata.id} Url: ${metadata.contentUri}",error);
//            removeWatchNext()
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