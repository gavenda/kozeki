package dev.gavenda.kozeki.data.metadata

import androidx.annotation.StringRes
import dev.gavenda.kozeki.R

/** A list a catalogue can show with nothing searched for. Which ones it has is up to the catalogue. */
enum class BrowseOrder(@StringRes val label: Int) {
    TRENDING_WEEK(R.string.browse_trending_week),
    TRENDING_MONTH(R.string.browse_trending_month),
    TRENDING_YEAR(R.string.browse_trending_year),
    POPULAR(R.string.browse_popular),
    TOP_RATED(R.string.browse_top_rated),
    NEW_RELEASES(R.string.browse_new_releases),

    /** The comics most lately given a chapter. */
    LATEST_UPDATES(R.string.browse_latest_updates),
    ;

    companion object {
        fun named(name: String?): BrowseOrder? = entries.firstOrNull { it.name == name }
    }
}
