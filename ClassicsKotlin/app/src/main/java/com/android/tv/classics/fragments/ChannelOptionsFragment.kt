package com.android.tv.classics.fragments

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.android.tv.classics.models.TvMediaMetadata

/**
 * Hold-OK popup for channel options: Favourite / Hide / Cancel.
 * Results are delivered via the static [onResult] callback set before adding to the back stack.
 */
class ChannelOptionsFragment : GuidedStepSupportFragment() {

    companion object {
        private const val KEY_METADATA = "metadata"

        private const val ACTION_FAVORITE = 1L
        private const val ACTION_HIDE     = 2L
        private const val ACTION_CANCEL   = 3L

        /** Set this before calling [newInstance]. Cleared after invocation. */
        var onResult: ((action: String) -> Unit)? = null

        fun newInstance(metadata: TvMediaMetadata, callback: (String) -> Unit): ChannelOptionsFragment {
            onResult = callback
            return ChannelOptionsFragment().apply {
                arguments = Bundle().apply { putParcelable(KEY_METADATA, metadata) }
            }
        }
    }

    private val metadata: TvMediaMetadata by lazy {
        requireArguments().getParcelable<TvMediaMetadata>(KEY_METADATA)!!
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            metadata.title,
            "Choose an action for this channel",
            "Channel Options",
            null
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val favLabel = if (metadata.favorite) "Remove from Favourites" else "Add to Favourites"
        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_FAVORITE).title(favLabel).build()
        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_HIDE).title("Hide Channel").description("Remove from all categories").build()
        actions += GuidedAction.Builder(requireContext())
            .id(ACTION_CANCEL).title("Cancel").build()
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val cb = onResult
        onResult = null
        when (action.id) {
            ACTION_FAVORITE -> cb?.invoke("favorite")
            ACTION_HIDE     -> cb?.invoke("hide")
        }
        parentFragmentManager.popBackStack()
    }
}
