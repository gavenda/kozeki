package dev.gavenda.kozeki.data.tracking.anilist

/**
 * The scale an AniList account scores on. The app keeps every score as 0 to 100, which AniList
 * accepts and returns whatever the account shows, and converts only to show and to ask for one.
 */
enum class ScoreFormat(
    /** The largest value on the account's scale, in the units it is shown in. */
    val max: Int,
    /** How many steps one unit of the scale is divided into, so that 10.5 is 105 of 10. */
    val steps: Int = 1,
) {
    POINT_100(100),
    POINT_10_DECIMAL(10, steps = 10),
    POINT_10(10),
    POINT_5(5),
    POINT_3(3),
    ;

    /** How many positions there are above zero on the scale. */
    val positions: Int get() = max * steps

    /** Position on the scale, 0 being no score, of a score kept as 0 to 100. */
    fun toPosition(score: Int): Int = when (this) {
        POINT_100 -> score
        POINT_10_DECIMAL -> score
        POINT_10 -> (score / 10.0).toInt().coerceAtMost(10).let { if (score % 10 >= 5) (it + 1).coerceAtMost(10) else it }
        POINT_5 -> score / 20
        POINT_3 -> when {
            score <= 0 -> 0
            score < 50 -> 1
            score < 75 -> 2
            else -> 3
        }
    }

    /** The score, as 0 to 100, of a position on the scale. */
    fun toScore(position: Int): Int = when (this) {
        POINT_100, POINT_10_DECIMAL -> position
        POINT_10 -> position * 10
        POINT_5 -> position * 20
        POINT_3 -> when (position) {
            0 -> 0
            1 -> 35
            2 -> 60
            else -> 84
        }
    }

    companion object {
        fun parse(name: String?): ScoreFormat = entries.firstOrNull { it.name == name } ?: POINT_10_DECIMAL
    }
}
