package com.android.tv.classics.fragments

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.android.tv.classics.models.TvMediaDatabase
import com.android.tv.classics.models.TvMediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen that lists all hidden channels. Selecting one unhides it.
 * After making changes, [onChannelsChanged] is called so the caller can refresh its adapter.
 */
class HiddenChannelsFragment : GuidedStepSupportFragment() {

    companion object {
        /** Called after one or more channels are unhidden. */
        var onChannelsChanged: (() -> Unit)? = null
    }

    private lateinit var database: TvMediaDatabase
    private val hiddenChannels = mutableListOf<TvMediaMetadata>()
    private var dirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = TvMediaDatabase.getInstance(requireContext())
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            "Hidden Channels",
            "Select a channel to unhide it. It will reappear in its category.",
            "Settings",
            null
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        // Load hidden channels on IO, then populate on main
        CoroutineScope(Dispatchers.IO).launch {
            val channels = database.metadata().findHidden()
            withContext(Dispatchers.Main) {
                hiddenChannels.clear()
                hiddenChannels.addAll(channels)
                if (channels.isEmpty()) {
                    actions += GuidedAction.Builder(requireContext())
                        .id(-1L).title("No hidden channels").infoOnly(true).build()
                } else {
                    channels.forEachIndexed { idx, ch ->
                        actions += GuidedAction.Builder(requireContext())
                            .id(idx.toLong()).title(ch.title)
                            .description("Tap to unhide").build()
                    }
                }
                // Back / Done button at the bottom
                actions += GuidedAction.Builder(requireContext())
                    .id(Long.MAX_VALUE).title("Done").build()
                onCreateViewInternal(actions)
            }
        }
    }

    private fun onCreateViewInternal(actions: MutableList<GuidedAction>) {
        // GuidedStepSupportFragment rebuilds from the list – notify adapter
        try {
            val field = GuidedStepSupportFragment::class.java
                .getDeclaredField("mActionsStylist")
            field.isAccessible = true
        } catch (_: Exception) { }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id == Long.MAX_VALUE || action.id == -1L) {
            // Done / info-only — pop back
            if (dirty) onChannelsChanged?.invoke()
            parentFragmentManager.popBackStack()
            return
        }
        val ch = hiddenChannels.getOrNull(action.id.toInt()) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            ch.hidden = false
            database.metadata().update(ch)
            dirty = true
            withContext(Dispatchers.Main) {
                // Remove from local list and update action to show it's done
                actions.find { it.id == action.id }?.let { act ->
                    act.description = "✓ Unhidden"
                    notifyActionChanged(findActionPositionById(action.id))
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (dirty) onChannelsChanged?.invoke()
    }
}
