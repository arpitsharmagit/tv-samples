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

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.leanback.app.GuidedStepSupportFragment
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
import com.android.tv.classics.jio.ConstantsV2
import com.android.tv.classics.jio.JioAPIv2
import com.android.tv.classics.jio.store.HttpStore
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.models.TvMediaEPG
import com.android.tv.classics.models.TvMediaMetadata
import com.android.tv.classics.presenters.TvMediaMetadataPresenter
import com.android.tv.classics.utils.TvLauncherUtils
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.FrameworkMediaDrm
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.EventLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min


/** A fragment representing the current metadata item being played */
class NowPlayingFragment : VideoSupportFragment() {

    private lateinit var metadata:TvMediaMetadata
    private lateinit var shows: List<TvMediaEPG>
    private var channelsMetadataList: List<TvMediaMetadata> = emptyList()

    /** Tracks whether the Leanback controls overlay (channel row) is currently visible */
    private var overlayVisible = false

    /** Current stream protocol — set when playback starts, used in quality action label */
    private var currentStreamType = "—"

    override fun showControlsOverlay(runAnimation: Boolean) {
        super.showControlsOverlay(runAnimation)
        overlayVisible = true
    }

    override fun hideControlsOverlay(runAnimation: Boolean) {
        super.hideControlsOverlay(runAnimation)
        overlayVisible = false
    }


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

    override fun onVideoSizeChanged(width: Int, height: Int) {
        // Refresh quality label when adaptive stream settles on a new resolution
        view?.post { if (::playerGlue.isInitialized) playerGlue.refreshTrackInfo() }
    }

    /** Custom implementation of [PlaybackTransportControlGlue] */
    private inner class MediaPlayerGlue(context: Context, adapter: LeanbackPlayerAdapter) :
            PlaybackTransportControlGlue<LeanbackPlayerAdapter>(context, adapter) {

        private val actionAudioTrack = Action(ACTION_AUDIO_ID, "Audio", "Loading…")
        private val actionQuality    = Action(ACTION_QUALITY_ID, "Quality", "Loading…")

        fun skipForward(millis: Long = SKIP_PLAYBACK_MILLIS) =
                player.seekTo(if (player.contentDuration > 0) {
                    min(player.contentDuration, player.currentPosition + millis)
                } else {
                    player.currentPosition + millis
                })

        fun skipBackward(millis: Long = SKIP_PLAYBACK_MILLIS) =
                player.seekTo(max(0, player.currentPosition - millis))

        fun nextChannel() {
            val idx = channelsMetadataList.indexOfFirst { it.id == metadata.id }
            val next = channelsMetadataList.getOrNull(idx + 1) ?: return
            showChannelToast("▶ ${next.title}")
            setMetadata(next)
        }

        fun previousChannel() {
            val idx = channelsMetadataList.indexOfFirst { it.id == metadata.id }
            if (idx <= 0) return
            val prev = channelsMetadataList[idx - 1]
            showChannelToast("◀ ${prev.title}")
            setMetadata(prev)
        }

        override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
            // No actions — live TV: no play/pause, no skip buttons
        }

        override fun onCreateSecondaryActions(adapter: ArrayObjectAdapter) {
            adapter.add(actionAudioTrack)
            adapter.add(actionQuality)
        }

        override fun onUpdateProgress() {
            // Let Leanback update the seek bar — for live streams ExoPlayer provides
            // a valid currentPosition within the live window, so the bar moves naturally.
            super.onUpdateProgress()
        }

        override fun onActionClicked(action: Action) = when (action.id) {
            ACTION_AUDIO_ID   -> showAudioTrackSelector()
            ACTION_QUALITY_ID -> showQualitySelector()
            else -> super.onActionClicked(action)
        }

        /** Reads current track info from ExoPlayer and updates the secondary action labels */
        fun refreshTrackInfo() {
            val selector = player.trackSelector as? com.google.android.exoplayer2.trackselection.DefaultTrackSelector ?: return
            val info = selector.currentMappedTrackInfo ?: return

            // -- Audio tracks --
            var audioCount = 0
            var firstLang: String? = null
            for (i in 0 until info.rendererCount) {
                if (info.getRendererType(i) != C.TRACK_TYPE_AUDIO) continue
                val groups = info.getTrackGroups(i)
                for (j in 0 until groups.length) {
                    val group = groups.get(j)
                    for (k in 0 until group.length) {
                        audioCount++
                        if (firstLang == null) {
                            firstLang = group.getFormat(k).language?.uppercase()
                        }
                    }
                }
            }
            val audioLabel = when {
                audioCount > 1 -> "Audio ($audioCount)"
                audioCount == 1 -> "Audio (${firstLang ?: "1"})"
                else -> "Audio"
            }
            // label1 = action title (always visible), label2 = subtitle (may or may not render)
            actionAudioTrack.setLabel1(audioLabel)
            actionAudioTrack.setLabel2(if (audioCount > 1) "Tap to switch" else "")

            // -- Video quality (current playing format) --
            val vFmt = player.videoFormat
            val qualLabel = if (vFmt != null && vFmt.height > 0) "${vFmt.height}p" else "Auto"
            actionQuality.setLabel1("$currentStreamType $qualLabel")
            actionQuality.setLabel2("Tap to switch")

            // Force a full row rebuild — most reliable way to get Leanback to re-render actions
            host?.notifyPlaybackRowChanged()
        }

        /** Custom function used to update the metadata displayed for currently playing media */
        fun setMetadata(newmetadata: TvMediaMetadata) {
            metadata = newmetadata
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                // set playback row art
//                metadata.artUri?.let { art = Coil.get(it) }
                // Token refresh disabled — authToken is valid for 10 days.
                // TODO: implement v2 refresh endpoint when identified.
                
                try {
                    val session = LiveTvApplication.getAuthHeaders()
                    // v2: batch EPG — fetch today (offset 0) for this channel
                    val epgResp = JioAPIv2.getEPGBatch(
                        channelIds = listOf(metadata.id),
                        offsets    = listOf(0, 1),
                        session    = session
                    )

                    val showsForChannel = epgResp.data[metadata.id] ?: emptyList()

                    shows = showsForChannel.map { show ->
                        TvMediaEPG(
                            srno            = show.programId.take(6).toLongOrNull() ?: 0L,
                            showId          = show.showId,
                            showname        = show.title,
                            description     = show.description,
                            startEpoch      = show.startEpoch,
                            endEpoch        = show.endEpoch,
                            episodeThumbnail = show.thumbnail,
                            duration        = ((show.endEpoch - show.startEpoch) / 60000).toInt().coerceAtLeast(1),
                            isPastEpisode   = show.endEpoch < System.currentTimeMillis(),
                            isCatchupAvailable = show.endEpoch < System.currentTimeMillis()
                        )
                    }

                    if (shows.isNotEmpty()) {
                        val currentShow = findCurrentShow()
                        startPlayingCurrentShow(currentShow)
                    } else {
                        Timber.e("No EPG data available for channel ${metadata.id}")
                        withContext(Dispatchers.Main) {
                            hideLoading()
                            LiveTvApplication.showToast("No program information available")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing EPG data")
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        LiveTvApplication.showToast("Error loading program information")
                    }
                }
            }
        }
    }

    private suspend fun startPlayingCurrentShow(show: TvMediaEPG?) {
        try {
            val session = LiveTvApplication.getAuthHeaders()

            // v2 playback: POST JSON device capabilities to /playback/v2/{channelId}
            val playbackResp = JioAPIv2.getPlaybackUrl(metadata.id, session)
            val playbackData = playbackResp.data

            if (playbackData == null) {
                Timber.e("v2 playback: null data for channel ${metadata.id}")
                withContext(Dispatchers.Main) { hideLoading() }
                return
            }

            // Prefer DASH over HLS when available
            val dashUrl: String? = playbackData.mpd.best.takeIf { it.isNotEmpty() }
            val hlsUrl:  String? = playbackData.m3u8.best.takeIf { it.isNotEmpty() }
            val keyURL:  String? = playbackData.keyURL.takeIf { it.isNotEmpty() }
            val isDash   = dashUrl != null
            val requiresDrm = isDash && keyURL != null

            val chosenUrl = dashUrl ?: hlsUrl ?: run {
                Timber.e("v2 playback: no stream URL for channel ${metadata.id}")
                withContext(Dispatchers.Main) { hideLoading() }
                return
            }

            metadata.contentUri = Uri.parse(chosenUrl)
            Timber.d("v2 Playback: ${if (isDash) "DASH" else "HLS"}  isDRM=$requiresDrm  channel=${metadata.id}")

            // v2 stream headers — tokens embedded in URL, minimal headers needed
            fun str(key: String) = when (val v = session[key]) {
                is String -> v; null -> ""; else -> v.toString()
            }
            val hashMap = hashMapOf(
                ConstantsV2.X_ACCESS_TOKEN   to str(ConstantsV2.KEY_AUTH_TOKEN),
                ConstantsV2.DEVICEID         to str(ConstantsV2.KEY_DEVICE_ID),
                ConstantsV2.UNIQUEID         to str(ConstantsV2.KEY_UNIQUE_ID),
                ConstantsV2.SUBID            to str(ConstantsV2.KEY_SUBSCRIBER_ID),
                ConstantsV2.X_APPNAME        to ConstantsV2.Values.APP_NAME,
                ConstantsV2.X_API_SIGNATURES to ConstantsV2.Values.API_SIGNATURES,
                ConstantsV2.X_FEATURE_CODE   to ConstantsV2.Values.FEATURE_CODE
            )

            // v2 DRM license request headers
            val drmLicenseHeaders: Map<String, String> = if (requiresDrm) {
                hashMapOf(
                    ConstantsV2.X_ACCESS_TOKEN   to str(ConstantsV2.KEY_AUTH_TOKEN),
                    ConstantsV2.UNIQUEID         to str(ConstantsV2.KEY_UNIQUE_ID),
                    ConstantsV2.SUBID            to str(ConstantsV2.KEY_SUBSCRIBER_ID),
                    ConstantsV2.DEVICEID         to str(ConstantsV2.KEY_DEVICE_ID),
                    ConstantsV2.X_APPNAME        to ConstantsV2.Values.APP_NAME,
                    ConstantsV2.X_API_SIGNATURES to ConstantsV2.Values.API_SIGNATURES,
                    ConstantsV2.X_FEATURE_CODE   to ConstantsV2.Values.FEATURE_CODE
                )
            } else emptyMap()

            withContext(Dispatchers.Main) {
                increasePlayCount()
                hidePlaybackError()
                hideLoading()
                currentStreamType = if (isDash) "DASH" else "HLS"
                val mediaSource = prepareMediaSource(
                    metadata.contentUri, hashMap, isDash, keyURL, drmLicenseHeaders
                )
                player.stop()
                player.prepare(mediaSource, true, true)

                val subTitleFormat = SimpleDateFormat("EEE dd MMM HH:mm a", Locale.US)
                subTitleFormat.timeZone = TimeZone.getDefault()

                playerGlue.title    = show?.showname
                playerGlue.subtitle = show?.let {
                    subTitleFormat.format(it.startEpoch) + " | ${it.duration} mins"
                } ?: ""
            }
        } catch (e: Exception) {
            Timber.e(e, "error occurred while playing")
            withContext(Dispatchers.Main) { hideLoading() }
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

    private fun prepareMediaSource(
        playbackUri: Uri,
        playbackHeaders: Map<String, String>,
        isDash: Boolean = false,
        drmLicenseUrl: String? = null,
        drmLicenseHeaders: Map<String, String> = emptyMap()
    ): MediaSource {
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

        return if (isDash) {
            val dashFactory = DashMediaSource.Factory(resolvingDataSourceFactory)

            // Configure Widevine DRM when a license URL is present
            if (!drmLicenseUrl.isNullOrEmpty()) {
                Timber.d("Configuring Widevine DRM — license: $drmLicenseUrl")
                val drmCallback = HttpMediaDrmCallback(
                    drmLicenseUrl,
                    DefaultHttpDataSourceFactory("okhttp/4.0.1")
                )
                drmLicenseHeaders.forEach { (k, v) -> drmCallback.setKeyRequestProperty(k, v) }
                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)
                dashFactory.setDrmSessionManager(drmSessionManager)
            }

            dashFactory.createMediaSource(MediaItem.fromUri(playbackUri))
        } else {
            // HLS (M3U8) stream — fallback when no DASH URL is available
            HlsMediaSource.Factory(resolvingDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(playbackUri))
        }
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

            // Token refresh disabled — authToken valid for 10 days.
            // TODO: implement v2 refresh endpoint when identified.
            // lifecycleScope.launch(Dispatchers.IO) { TvLauncherUtils.refreshToken() }

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
                Timber.d("Media metadata updated successfully")
                view?.postDelayed(this, METADATA_UPDATE_INTERVAL_MILLIS)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backgroundType = PlaybackSupportFragment.BG_NONE
        database = TvMediaDatabase.getInstance(requireContext())
        metadata = args.metadata

        lifecycleScope.launch(Dispatchers.IO) {
            populateChannelList(metadata.collectionId)
        }

        // Adds this program to the continue watching row, in case the user leaves before finishing
//        addWatchNext()

        // Initializes the video player.
        // Enable decoder fallback so ExoPlayer automatically retries with the next available
        // decoder (software or alternate hardware) when the primary one (e.g., OMX.MTK.VIDEO.DECODER.AVC
        // on OnePlus/MediaTek TVs) fails to initialise.
        val renderersFactory = DefaultRenderersFactory(requireContext())
            .setEnableDecoderFallback(true)
        player = SimpleExoPlayer.Builder(requireContext(), renderersFactory).build()
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
                        val currentShow = findCurrentShow()
                        val endEpoch = currentShow?.endEpoch ?: return
                        val remainingShowTimeMS = endEpoch - Calendar.getInstance().time.time
                        val diffOfPlaybackPosition = (player.contentDuration - remainingShowTimeMS) - player.contentPosition

                        if(diffOfPlaybackPosition> 10000 && arrayOf("154","155","162","289","291","471","474","476","483","514","524","525","872","1393","1396").contains(metadata.id)){
                            val seekPostion = player.contentDuration - remainingShowTimeMS
                            Timber.d("SEEK POSITION ======" + seekPostion.toString()+ " contentDuration === "+ player.contentDuration)
                            seekTo(seekPostion)
                        }
                    }
                }
                override fun onPreparedStateChanged(glue: PlaybackGlue?) {
                    super.onPreparedStateChanged(glue)
                    if (glue?.isPrepared == true) {
                        // When playback is ready, skip to last known position
//                        val startingPosition = metadata.playbackPositionMillis ?: 0
//                        Timber.d("Setting starting playback position to $startingPosition")
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
                    hideControlsOverlay(true)
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

            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyInterceptListener false

            // If overlay is visible, BACK should dismiss it rather than leave the screen
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // If a track selector dialog (audio/quality) is open, close it first
                if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                    requireActivity().supportFragmentManager.popBackStack()
                    return@setOnKeyInterceptListener true
                }
                if (overlayVisible) {
                    hideControlsOverlay(true)
                    return@setOnKeyInterceptListener true
                }
                Timber.d("Intercepting BACK key for fragment navigation")
                try {
                    player.stop()
                    player.release()
                    mediaSession.isActive = false
                    mediaSessionConnector.setPlayer(null)
                    view?.removeCallbacks(updateMetadataTask)
                    val navController = Navigation.findNavController(
                            requireActivity(), R.id.fragment_container)
                    navController.currentDestination?.id?.let { navController.popBackStack(it, true) }
                } catch (e: Exception) {
                    Timber.e("Error during back navigation", e)
                }
                return@setOnKeyInterceptListener true
            }

            when (keyCode) {
                // Only switch channels / open audio selector when overlay is NOT visible.
                // When overlay is visible, pass all keys through to Leanback for normal row navigation.
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!overlayVisible) { playerGlue.nextChannel(); hideControlsOverlay(false); true } else false
                KeyEvent.KEYCODE_DPAD_LEFT  -> if (!overlayVisible) { playerGlue.previousChannel(); hideControlsOverlay(false); true } else false
                KeyEvent.KEYCODE_DPAD_UP    -> if (!overlayVisible) { showAudioTrackSelector(); true } else false
                else -> false
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    // Overlay TextView for channel switch feedback — persists for 3 seconds
    private var channelInfoView: TextView? = null
    private val channelInfoHandler = Handler(Looper.getMainLooper())
    private val hideChannelInfoRunnable = Runnable {
        channelInfoView?.let { tv ->
            ObjectAnimator.ofFloat(tv, "alpha", 1f, 0f).apply {
                duration = 400
                start()
            }
        }
    }

    // Persistent error overlay shown when playback fails
    private var errorOverlayView: TextView? = null

    /** Shows a full-screen error banner that stays until next successful playback */
    private fun showPlaybackError(message: String) {
        val rootView = view as? ViewGroup ?: return
        if (errorOverlayView == null) {
            errorOverlayView = TextView(requireContext()).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setShadowLayer(4f, 0f, 2f, Color.BLACK)
                setPadding(60, 40, 60, 40)
                setBackgroundColor(Color.parseColor("#CC880000"))
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER }
            }
            rootView.addView(errorOverlayView)
        }
        errorOverlayView!!.text = message
        errorOverlayView!!.visibility = View.VISIBLE
    }

    private fun hidePlaybackError() {
        errorOverlayView?.visibility = View.GONE
    }

    // Indeterminate loading spinner shown while stream URL is being fetched
    private var loadingOverlay: FrameLayout? = null

    private fun showLoading() {
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            val rootView = view as? ViewGroup ?: return@runOnUiThread
            if (loadingOverlay == null) {
                val spinner = ProgressBar(requireContext()).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
                    layoutParams = FrameLayout.LayoutParams(120, 120).apply {
                        gravity = Gravity.CENTER
                    }
                }
                loadingOverlay = FrameLayout(requireContext()).apply {
                    setBackgroundColor(Color.parseColor("#99000000"))
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    addView(spinner)
                }
                rootView.addView(loadingOverlay)
            }
            // Ensure error overlay is behind loading overlay
            errorOverlayView?.let { rootView.bringChildToFront(it) }
            loadingOverlay!!.bringToFront()
            loadingOverlay!!.visibility = View.VISIBLE
        }
    }

    private fun hideLoading() {
        activity?.runOnUiThread {
            loadingOverlay?.visibility = View.GONE
        }
    }

    /** Shows a persistent on-screen banner with the channel name for 3 seconds */
    private fun showChannelToast(message: String) {
        val rootView = view as? ViewGroup ?: return

        // Create overlay on first use
        if (channelInfoView == null) {
            channelInfoView = TextView(requireContext()).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(null, Typeface.BOLD)
                setShadowLayer(4f, 0f, 2f, Color.BLACK)
                setPadding(40, 20, 40, 20)
                setBackgroundColor(Color.parseColor("#AA000000"))
                gravity = Gravity.CENTER
                alpha = 0f

                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = 80
                }
                layoutParams = lp
            }
            rootView.addView(channelInfoView)
        }

        channelInfoView!!.text = message
        channelInfoHandler.removeCallbacks(hideChannelInfoRunnable)

        // Fade in
        channelInfoView!!.alpha = 0f
        ObjectAnimator.ofFloat(channelInfoView!!, "alpha", 0f, 1f).apply {
            duration = 200
            start()
        }

        // Auto-hide after 3 seconds
        channelInfoHandler.postDelayed(hideChannelInfoRunnable, 3000)
    }

    /**
     * Shows an audio track selection dialog.
     * Lists all audio tracks in the current stream; user picks one via D-pad.
     * Triggered by DPAD_UP.
     */
    private fun showAudioTrackSelector() {
        val trackSelector = player.trackSelector as? com.google.android.exoplayer2.trackselection.DefaultTrackSelector ?: run {
            LiveTvApplication.showToast("Audio selection not available")
            return
        }
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: run {
            LiveTvApplication.showToast("Stream not ready yet")
            return
        }

        // Collect all audio tracks across all renderers
        data class AudioTrack(val rendererIndex: Int, val groupIndex: Int, val trackIndex: Int, val label: String)
        val audioTracks = mutableListOf<AudioTrack>()

        for (rendererIdx in 0 until mappedTrackInfo.rendererCount) {
            if (player.getRendererType(rendererIdx) != com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO) continue
            val groups = mappedTrackInfo.getTrackGroups(rendererIdx)
            for (groupIdx in 0 until groups.length) {
                val group = groups.get(groupIdx)
                for (trackIdx in 0 until group.length) {
                    val format = group.getFormat(trackIdx)
                    val lang = format.language ?: "und"
                    val label = buildString {
                        append(java.util.Locale(lang).displayLanguage.ifEmpty { lang.uppercase() })
                        if (!format.label.isNullOrEmpty()) append(" — ${format.label}")
                        format.channelCount.takeIf { it > 0 }?.let { append(" (${it}ch)") }
                        format.bitrate.takeIf { it > 0 }?.let { append(" ${it / 1000}kbps") }
                    }
                    audioTracks.add(AudioTrack(rendererIdx, groupIdx, trackIdx, label))
                }
            }
        }

        if (audioTracks.isEmpty()) {
            LiveTvApplication.showToast("No alternate audio tracks")
            return
        }
        if (audioTracks.size == 1) {
            LiveTvApplication.showToast("Only one audio track: ${audioTracks[0].label}")
            return
        }

        // Show Leanback-friendly GuidedStep dialog with audio options
        val fragment = AudioTrackSelectorFragment.newInstance(
            audioTracks.map { it.label },
            onSelected = { selectedIndex ->
                val chosen = audioTracks[selectedIndex]
                val groups = mappedTrackInfo.getTrackGroups(chosen.rendererIndex)
                val override = com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride(
                    chosen.groupIndex, chosen.trackIndex)
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setRendererDisabled(chosen.rendererIndex, false)
                        .setSelectionOverride(chosen.rendererIndex, groups, override)
                )
                LiveTvApplication.showToast("Audio: ${chosen.label}")
            }
        )
        GuidedStepSupportFragment.add(requireActivity().supportFragmentManager, fragment)
    }

    private fun showQualitySelector() {
        val trackSelector = player.trackSelector as? com.google.android.exoplayer2.trackselection.DefaultTrackSelector ?: run {
            LiveTvApplication.showToast("Quality selection not available")
            return
        }
        val mappedInfo = trackSelector.currentMappedTrackInfo ?: run {
            LiveTvApplication.showToast("Stream not ready yet")
            return
        }

        data class VideoTrack(val rendererIdx: Int, val groupIdx: Int, val trackIdx: Int, val label: String)
        val tracks = mutableListOf<VideoTrack>()
        tracks.add(VideoTrack(-1, -1, -1, "Auto (Recommended)"))

        for (i in 0 until mappedInfo.rendererCount) {
            if (player.getRendererType(i) != C.TRACK_TYPE_VIDEO) continue
            val groups = mappedInfo.getTrackGroups(i)
            for (j in 0 until groups.length) {
                val group = groups.get(j)
                for (k in 0 until group.length) {
                    val fmt = group.getFormat(k)
                    val label = buildString {
                        if (fmt.height > 0) append("${fmt.height}p")
                        else append("Track ${tracks.size}")
                        if (fmt.bitrate > 0) append("  ${fmt.bitrate / 1000} kbps")
                        if (!fmt.codecs.isNullOrEmpty()) append("  (${fmt.codecs!!.substringBefore(".")})")
                    }
                    tracks.add(VideoTrack(i, j, k, label))
                }
            }
            break // only first video renderer
        }

        if (tracks.size <= 1) {
            LiveTvApplication.showToast("No alternate quality tracks available")
            return
        }

        // Mark currently selected quality
        val currentHeight = player.videoFormat?.height ?: -1
        val labels = tracks.map { t ->
            if (t.rendererIdx < 0 && currentHeight <= 0) "✓ ${t.label}"
            else if (t.rendererIdx >= 0) {
                val fmt = mappedInfo.getTrackGroups(t.rendererIdx).get(t.groupIdx).getFormat(t.trackIdx)
                if (fmt.height == currentHeight) "✓ ${t.label}" else t.label
            } else t.label
        }

        val fragment = AudioTrackSelectorFragment.newInstance(labels) { selectedIdx ->
            val sel = tracks[selectedIdx]
            if (sel.rendererIdx < 0) {
                // Auto — clear all video overrides
                trackSelector.setParameters(trackSelector.buildUponParameters().clearSelectionOverrides())
            } else {
                val groups = mappedInfo.getTrackGroups(sel.rendererIdx)
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setSelectionOverride(sel.rendererIdx, groups,
                            com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride(sel.groupIdx, sel.trackIdx))
                )
            }
            view?.postDelayed({ playerGlue.refreshTrackInfo() }, 800)
        }
        GuidedStepSupportFragment.add(requireActivity().supportFragmentManager, fragment)
    }

    private fun populateChannelList(collectionId: String) {
        val collection = database.collections().findById(collectionId)
        if (collection != null) {
            channelsMetadataList = database.metadata().findByCollection(collection.id)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(Color.BLACK)
        // Hide controls overlay on start; D-pad DOWN will reveal it naturally via Leanback
        hideControlsOverlay(false)

        // Restore focus state if available
        try {
            view.post {
                com.android.tv.classics.utils.FocusManager.restoreFocusState(
                    this, 
                    R.id.now_playing_fragment, 
                    view
                )
            }
        } catch (e: Exception) {
            Timber.e( "Error restoring focus", e)
        }
    }

    override fun onResume() {
        super.onResume()

        mediaSessionConnector.setPlayer(player)
        mediaSession.isActive = true

        // Live stream URLs carry short-lived tokens (~2 min); re-fetch stream on every resume
        if (::shows.isInitialized && shows.isNotEmpty()) {
            showLoading()
            lifecycleScope.launch(Dispatchers.IO) {
                startPlayingCurrentShow(findCurrentShow())
            }
        }
    }

    /**
     * Deactivates and removes callbacks from [MediaSessionCompat] since the [Player] instance is
     * destroyed in onStop and required metadata could be missing.
     */
    override fun onPause() {
        super.onPause()

        try {
            // Save last playing channel so MainActivity can auto-resume after process death
            LiveTvApplication.getPrefStore().saveData("lastChannelId", metadata.id)

            // Pause the player
            playerGlue.pause()
            
            // Deactivate the media session
            mediaSession.isActive = false
            mediaSessionConnector.setPlayer(null)
            
            // Cancel any pending callbacks
            view?.removeCallbacks(updateMetadataTask)
        } catch (e: Exception) {
            Timber.e( "Error in onPause", e)
        }
    }
    
    override fun onStop() {
        super.onStop()
        
        // Save focus state when stopping
        try {
            com.android.tv.classics.utils.FocusManager.saveFocusState(
                this,
                R.id.now_playing_fragment
            )
        } catch (e: Exception) {
            Timber.e( "Error saving focus", e)
        }
    }

    /** Do all final cleanup in onDestroy */
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            // Release the player
            player.release()
            
            // Release the media session
            mediaSessionConnector.setPlayer(null)
            mediaSession.release()
            
            // Cancel any pending callbacks
            view?.removeCallbacks(updateMetadataTask)
        } catch (e: Exception) {
            Timber.e( "Error cleaning up resources", e)
        }
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
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            // Refresh audio/quality action labels once the stream is fully ready
            if (playbackState == Player.STATE_READY) {
                activity?.runOnUiThread {
                    if (::playerGlue.isInitialized) playerGlue.refreshTrackInfo()
                }
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            Timber.e("PlayError: ChannelNo: ${metadata.id} Url: ${metadata.contentUri}", error)
            decreasePlayCount()

            val reason = when (error.type) {
                ExoPlaybackException.TYPE_SOURCE ->
                    "Stream unavailable — source error\n${error.sourceException?.message?.take(80) ?: ""}"
                ExoPlaybackException.TYPE_RENDERER -> {
                    val msg = error.rendererException?.message ?: ""
                    Timber.w("Renderer error (decoder fallback may have been attempted): $msg")
                    "Playback error — renderer failure\n${msg.take(80)}"
                }
                ExoPlaybackException.TYPE_UNEXPECTED ->
                    "Unexpected error\n${error.unexpectedException?.message?.take(80) ?: ""}"
                else -> "Playback failed"
            }

            val channelName = metadata.title ?: metadata.id
            val msg = "⚠  ${channelName}\n\n${reason}\n\nPress ◀ ▶ to switch channel"

            view?.post { hideLoading(); showPlaybackError(msg) }
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

        private const val ACTION_AUDIO_ID   = 1001L
        private const val ACTION_QUALITY_ID = 1002L
    }
}