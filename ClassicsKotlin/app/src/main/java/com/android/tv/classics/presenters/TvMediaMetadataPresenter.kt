package com.android.tv.classics.presenters

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.api.load
import coil.size.Scale
import com.android.tv.classics.R
import com.android.tv.classics.models.TvMediaMetadata
import com.android.tv.classics.utils.TvLauncherUtils

/** Default height in DP used for card presenters, larger than this results in rows overflowing */
const val DEFAULT_CARD_HEIGHT: Int = 150

/** [Presenter] used to display a metadata item as an image card */
class TvMediaMetadataPresenter(
    private val cardHeight: Int = DEFAULT_CARD_HEIGHT,
    private val onLongClick: ((TvMediaMetadata) -> Unit)? = null
) : Presenter() {

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        (viewHolder.view as? ImageCardView)?.setOnLongClickListener(null)
    }

    override fun onCreateViewHolder(parent: ViewGroup) =
            Presenter.ViewHolder(ImageCardView(parent.context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                isLongClickable = true
                setBackgroundColor(Color.WHITE)
            })

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val metadata = item as TvMediaMetadata
        val card = viewHolder.view as ImageCardView

        val cardWidth = TvLauncherUtils.parseAspectRatio(metadata.artAspectRatio).let {
            cardHeight * it.numerator / it.denominator
        }

        card.setMainImageDimensions(cardWidth, cardHeight)
        card.contentDescription = metadata.title

        if (metadata.artUri == null) {
            // Settings / utility tile — show label text and a per-item icon
            card.infoVisibility = View.VISIBLE
            card.titleText = metadata.title
            card.contentText = null

            card.mainImageView.load(R.mipmap.ic_launcher) {
                scale(Scale.FIT)
            }
        } else {
            // Channel tile — image fills the card, hide the info strip
            card.infoVisibility = View.GONE
            card.titleText = null
            card.mainImageView.load(metadata.artUri) {
                placeholder(R.drawable.ic_launcher_background)
                error(R.drawable.ic_launcher_background)
                scale(Scale.FILL)
            }
        }

        if (onLongClick != null) {
            card.setOnLongClickListener { onLongClick.invoke(metadata); true }
        }
    }
}
