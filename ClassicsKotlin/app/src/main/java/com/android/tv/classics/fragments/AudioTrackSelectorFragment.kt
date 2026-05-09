package com.android.tv.classics.fragments

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.android.tv.classics.R

/**
 * A Leanback GuidedStep dialog for selecting an audio track.
 * Launched from [NowPlayingFragment] when DPAD_UP is pressed during playback.
 */
class AudioTrackSelectorFragment : GuidedStepSupportFragment() {

    private var onSelected: ((Int) -> Unit)? = null

    companion object {
        private const val KEY_LABELS = "labels"

        fun newInstance(
            labels: List<String>,
            onSelected: (Int) -> Unit
        ): AudioTrackSelectorFragment {
            return AudioTrackSelectorFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(KEY_LABELS, ArrayList(labels))
                }
                this.onSelected = onSelected
            }
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Select Audio Track",
            "Choose the audio language or track for this channel.",
            "",
            requireContext().getDrawable(R.mipmap.ic_launcher)
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val labels = arguments?.getStringArrayList(KEY_LABELS) ?: return
        labels.forEachIndexed { index, label ->
            actions.add(
                GuidedAction.Builder(context)
                    .id(index.toLong())
                    .title(label)
                    .build()
            )
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        onSelected?.invoke(action.id.toInt())
        parentFragmentManager.popBackStack()
    }
}
