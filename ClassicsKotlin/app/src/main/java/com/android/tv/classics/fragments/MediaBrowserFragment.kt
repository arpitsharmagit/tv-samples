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

import android.animation.ArgbEvaluator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.navigation.Navigation
import com.android.tv.classics.R
import androidx.lifecycle.lifecycleScope
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.models.TvMediaMetadata
import com.android.tv.classics.workers.TvMediaSynchronizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.leanback.widget.DiffCallback
import androidx.palette.graphics.Palette
import coil.Coil
import coil.api.get
import coil.api.getAny
import coil.bitmappool.BitmapPool
import coil.transform.Transformation
import com.android.tv.classics.activities.LogViewerActivity
import com.android.tv.classics.BuildConfig
import com.android.tv.classics.LiveTvApplication
import com.android.tv.classics.fragments.LeanbackUpdateDialogFragment
import com.android.tv.classics.presenters.TvMediaMetadataPresenter
import com.android.tv.classics.utils.AppUpdateManager
import com.android.tv.classics.utils.TvLauncherUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * A fragment that lets user browse our collection of metadata.
 */
class MediaBrowserFragment : BrowseSupportFragment() {

    private lateinit var database: TvMediaDatabase

    /** Tint applied to the background, default to dark grey */
    private var currentTintColor: Int = ColorUtils.setAlphaComponent(
            Color.parseColor("#000000"), BACKGROUND_TINT_ALPHA)

    /** Animation task used to update the background tint */
    private var backgroundAnimation: Runnable? = null

    /** Background for our fragment, selected randomly at runtime */
    private lateinit var backgroundDrawable: Deferred<Drawable>

    /** Job used to debounce background tint updates on d-pad navigation */
    private var selectionJob: Job? = null

    /** Job used to synchronize our media database */
    private lateinit var synchronizeJob: Job

    /** List row used exclusively to display "credits" and no media content */
    private lateinit var creditsRow: ListRow

    /** Used to efficiently add items to our array adapter for display */
    private val listRowDiffCallback = object : DiffCallback<ListRow>() {
        override fun areContentsTheSame(
                oldItem: ListRow, newItem: ListRow) = oldItem.hashCode() == newItem.hashCode()
        override fun areItemsTheSame(
                oldItem: ListRow, newItem: ListRow): Boolean =
                oldItem.headerItem == newItem.headerItem &&
                        oldItem.adapter.size() == newItem.adapter.size() &&
                        (0 until oldItem.adapter.size()).all { idx ->
                            oldItem.adapter.get(idx) == newItem.adapter.get(idx)  // was oldItem == oldItem (bug)
                        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        badgeDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_jasmine_logo)
        title = getString(R.string.app_name)
        headersState = BrowseSupportFragment.HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = true

        // Initialize database connection
        database = TvMediaDatabase.getInstance(requireContext())

        // Setup this browser fragment's adapter
        adapter = ArrayObjectAdapter(ListRowPresenter())

        // Each time an item is selected, debounce then change the background
        setOnItemViewSelectedListener { _, item, _, row ->
            if (item == null) return@setOnItemViewSelectedListener
            val metadata = item as TvMediaMetadata

            // Cancel pending palette job so rapid d-pad scrolling doesn't queue up downloads
            selectionJob?.cancel()
            selectionJob = lifecycleScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(300)
                metadata.artUri?.let { artUri ->
                    Coil.get(artUri) { allowHardware(false) }.toBitmap()
                }?.let { artBitmap ->
                    Palette.Builder(artBitmap).generate {
                        val dominantColor =
                                it?.getDominantColor(currentTintColor) ?: currentTintColor
                        val backgroundColor =
                                ColorUtils.setAlphaComponent(dominantColor, BACKGROUND_TINT_ALPHA)
                        Timber.d("Using dominant color for background tint: $backgroundColor")
                        updateBackgroundTint(backgroundColor)
                    }
                }
            }
        }

        // When user clicks on an item, navigate to the appropriate screen
        setOnItemViewClickedListener { _, item, _, _ ->
            val metadata = item as TvMediaMetadata
            when (metadata.id) {
                "log_viewer"      -> startActivity(Intent(requireContext(), LogViewerActivity::class.java))
                "check_updates"   -> checkForUpdatesManually()
                "refresh_channels" -> refreshChannelsManually()
                "hidden_channels" -> showHiddenChannels()
                else -> Navigation.findNavController(
                        requireActivity(), R.id.fragment_container).navigate(
                        MediaBrowserFragmentDirections.actionToNowPlaying(metadata))
            }
        }

        // Instantiate the credits row, which will be added to the adapter inside [populateAdapter]
        creditsRow = ListRow(HeaderItem(getString(R.string.credits)), object : ObjectAdapter() {
            override fun size(): Int = 0
            override fun get(position: Int): Any? = null
        })

        // Keep track of the synchronization work so we can join it later
        synchronizeJob = lifecycleScope.launch(Dispatchers.IO) {

            // Show cached DB data immediately — no network wait
            populateAdapter(adapter as ArrayObjectAdapter)

            // Sync with server in background
            TvMediaSynchronizer.synchronize(requireContext())

            // Refresh adapter with any new/removed channels from the sync
            populateAdapter(adapter as ArrayObjectAdapter)
        }

        // Pick a random background for our fragment
        backgroundDrawable = lifecycleScope.async(Dispatchers.IO) {
//            val backgroundList = listOf("tealbg","smoothredbg","skybluebg","seagreanbg",
//                "orangebg","jetblackbg","diffbluebg","bg5","bg4","bg3")
//            val drawableId = resources.getIdentifier(backgroundList.shuffled().first(), "drawable", requireContext().packageName)
//            val uri = context?.let { TvLauncherUtils.resourceUri(it.resources, drawableId) }
//            Coil.getAny(uri ?: R.drawable.bg5)
            Coil.getAny(R.drawable.bg5)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Because we will be handling the database, we must do it from a different context
        // But we also need the view to be completely inflated to know its size, which is why we
        // use `view.post` here.
        view.post { lifecycleScope.launch(Dispatchers.IO) {

            // With the view inflated, we know the final size of our background
            val sizedBackground = Coil.get(backgroundDrawable.await()) {
                transformations(object : Transformation {
                    override fun key(): String = "CenterCropTransform"
                    override suspend fun transform(pool: BitmapPool, input: Bitmap): Bitmap {
                        return ThumbnailUtils.extractThumbnail(input, view.width, view.height)
                    }
                })
            }

            // Set background and restore focus — don't block on network sync
            withContext(Dispatchers.Main) {
                view.background = sizedBackground
                view.backgroundTintMode = PorterDuff.Mode.OVERLAY
                view.backgroundTintList = ColorStateList.valueOf(currentTintColor)

                try {
                    com.android.tv.classics.utils.FocusManager.restoreFocusState(
                        this@MediaBrowserFragment,
                        R.id.media_browser_fragment,
                        view
                    )
                } catch (e: Exception) {
                    Timber.e("Error restoring focus", e)
                }
            }
        } }
    }

    /** Convenience function used to update the background tint to match the selected item */
    private fun updateBackgroundTint(targetColor: Int) = view?.let { view ->

        // We update the background tint using a smooth animation
        ValueAnimator.ofObject(ArgbEvaluator(), currentTintColor, targetColor).apply {
            duration = BACKGROUND_ANIMATION_MILLIS

            // As the animation progresses, we update the background tint
            addUpdateListener {
                view.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int)
            }

            // Remote previously scheduled animation, if any
            backgroundAnimation?.let { view.removeCallbacks(it) }

            // Schedule a new animation, to let the user settle on an item
            backgroundAnimation = Runnable {
                start()
                currentTintColor = targetColor
                // Use the same time as animation to prevent overlap, since we only stop scheduled
                // animations but we don't stop ongoing animations
            }.also { view.postDelayed(it, BACKGROUND_ANIMATION_MILLIS) }
        }
    }

    /**
     * Convenience function used to populate the main screen's adapter with all media collections.
     * DB queries run on the caller's IO context; adapter writes are dispatched to the main thread.
     */
    private suspend fun populateAdapter(adapter: ArrayObjectAdapter) {
        // All DB reads happen on the caller's IO dispatcher
        val collections = database.collections().findAll()
        val favorites = database.metadata().findFavorites()
        // Single query for all visible (non-hidden) metadata, then group in memory
        val metadataByCollection = database.metadata().findAllNonHidden()
            .groupBy { it.collectionId }

        val collectionRows = mutableListOf<ListRow>()

        // ★ Favourites row — only shown when at least one channel is favourited
        if (favorites.isNotEmpty()) {
            val favHeader = HeaderItem(0L, "★ Favourites")
            val favAdapter = ArrayObjectAdapter(TvMediaMetadataPresenter(onLongClick = { showChannelOptions(it) })).apply {
                setItems(favorites, null)
            }
            collectionRows.add(ListRow(favHeader, favAdapter))
        }

        collections.forEachIndexed { idx, collection ->
            val header = HeaderItem((idx + 1).toLong(), collection.title)
            val listRowAdapter = ArrayObjectAdapter(TvMediaMetadataPresenter(onLongClick = { showChannelOptions(it) })).apply {
                setItems(metadataByCollection[collection.id] ?: emptyList<TvMediaMetadata>(), null)
            }
            collectionRows.add(ListRow(header, listRowAdapter))
        }

        // Settings row
        val settingsHeader = HeaderItem((collections.size + 1).toLong(), "Settings")
        val settingsAdapter = ArrayObjectAdapter(TvMediaMetadataPresenter()).apply {
            add(TvMediaMetadata(
                id = "hidden_channels",
                title = "Manage Hidden Channels",
                lang = "6",
                collectionId = "8",
                contentUri = Uri.EMPTY,
                artUri = null
            ))
            add(TvMediaMetadata(
                id = "log_viewer",
                title = "View Application Logs",
                lang = "6",
                collectionId = "8",
                contentUri = Uri.EMPTY,
                artUri = null
            ))
            add(TvMediaMetadata(
                id = "check_updates",
                title = "Check for Updates",
                lang = "6",
                collectionId = "8",
                contentUri = Uri.EMPTY,
                artUri = null
            ))
            add(TvMediaMetadata(
                id = "refresh_channels",
                title = "Refresh Channels",
                lang = "6",
                collectionId = "8",
                contentUri = Uri.EMPTY,
                artUri = null
            ))
        }
        collectionRows.add(ListRow(settingsHeader, settingsAdapter))

        // Resolve scroll target on IO before switching threads
        val channelId = arguments?.let {
            MediaBrowserFragmentArgs.fromBundle(it)
        }?.channelId

        // Switch to main thread, defer to next UI cycle via view.post so any in-progress
        // layout pass completes before we touch the adapter.
        withContext(Dispatchers.Main) {
            val rowCount = adapter.size()
            // Use null diff callback (notifyChanged full rebind) instead of DiffCallback —
            // Leanback's GridLayoutManager crashes with IndexOutOfBoundsException during
            // position-based remove/insert animations when the row count changes.
            view?.post {
                adapter.setItems(collectionRows, null)

                val scrollPosition = channelId?.let { id ->
                    collections.indexOfFirst { it.id == id }.coerceAtLeast(0)
                } ?: 0

                if (scrollPosition != selectedPosition || rowCount != adapter.size()) {
                    view?.postDelayed({
                        setSelectedPosition(scrollPosition, true)
                        Timber.d("Requesting scrolling to $scrollPosition")
                    }, BACKGROUND_ANIMATION_MILLIS)
                }
            }
        }
    }

    /** Shows the hold-OK options popup for [metadata]. Must be called from the main thread. */
    private fun showChannelOptions(metadata: TvMediaMetadata) {
        ChannelOptionsFragment.newInstance(metadata) { action ->
            lifecycleScope.launch(Dispatchers.IO) {
                when (action) {
                    "favorite" -> {
                        metadata.favorite = !metadata.favorite
                        database.metadata().update(metadata)
                    }
                    "hide" -> {
                        metadata.hidden = true
                        metadata.favorite = false
                        database.metadata().update(metadata)
                    }
                }
                populateAdapter(adapter as ArrayObjectAdapter)
            }
        }.also { fragment ->
            GuidedStepSupportFragment.add(requireActivity().supportFragmentManager, fragment)
        }
    }

    /** Opens the hidden-channels manager. */
    private fun showHiddenChannels() {
        HiddenChannelsFragment.onChannelsChanged = {
            lifecycleScope.launch(Dispatchers.IO) {
                populateAdapter(adapter as ArrayObjectAdapter)
            }
        }
        GuidedStepSupportFragment.add(requireActivity().supportFragmentManager, HiddenChannelsFragment())
    }

    /** Triggered when user manually selects "Check for Updates". */
    private fun checkForUpdatesManually() {
        LiveTvApplication.showToast("Checking for updates…")
        lifecycleScope.launch {
            val updateInfo = withContext(Dispatchers.IO) {
                AppUpdateManager(requireContext()).checkForUpdates()
            }
            if (!isAdded) return@launch
            if (updateInfo.isUpdateAvailable) {
                LeanbackUpdateDialogFragment.show(requireActivity(), updateInfo)
            } else {
                LiveTvApplication.showToast("You're on the latest version (${BuildConfig.VERSION_NAME})")
            }
        }
    }

    /** Triggered when user manually selects "Refresh Channels". */
    private fun refreshChannelsManually() {
        LiveTvApplication.showToast("Refreshing channels…")
        lifecycleScope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                TvMediaSynchronizer.refreshAndReplace(requireContext())
            }
            if (!isAdded) return@launch

            if (refreshed) {
                withContext(Dispatchers.IO) {
                    populateAdapter(adapter as ArrayObjectAdapter)
                }
                LiveTvApplication.showToast("Channels refreshed successfully")
            } else {
                LiveTvApplication.showToast("Failed to refresh channels")
            }
        }
    }

    override fun onDestroyView() {
        selectionJob?.cancel()
        selectionJob = null
        // Cancel animation and remove any pending callbacks
        view?.let { view ->
            backgroundAnimation?.let { view.removeCallbacks(it) }
            backgroundAnimation = null
        }
        super.onDestroyView()
    }
    
    override fun onStop() {
        super.onStop()
        
        // Save focus state when fragment is stopped (before leaving to another fragment)
        try {
            com.android.tv.classics.utils.FocusManager.saveFocusState(
                this,
                R.id.media_browser_fragment
            )
        } catch (e: Exception) {
            Timber.e( "Error saving focus", e)
        }
    }
    
    override fun onDestroy() {
        // Cancel any ongoing coroutine jobs
        if (::synchronizeJob.isInitialized && synchronizeJob.isActive) {
            synchronizeJob.cancel()
        }
        super.onDestroy()
    }
    
    companion object {
        private val TAG = MediaBrowserFragment::class.java.simpleName

        /** Animation time in milliseconds for background changes */
        private val BACKGROUND_ANIMATION_MILLIS: Long = 300L

        /** Alpha component (0-255) of the background color tint */
        private const val BACKGROUND_TINT_ALPHA: Int = 150
    }
}

