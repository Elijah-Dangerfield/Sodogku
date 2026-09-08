package com.sodogku.libraries.sharing

/**
 * What a finished run looks like from the outside: the board's shape, and how
 * the player did on it.
 *
 * There is no solution here and no placement history, and that is the whole
 * design. "A share must not leak the answer" is enforced by this type rather
 * than by care at the call site — the formatter cannot print what it was never
 * given. [regions] is the partition every player of this board sees before they
 * make a single move, so publishing it tells a reader which puzzle you played
 * and nothing whatsoever about where the dogs went.
 */
data class ShareResult(
    /** Grid dimension, 4 to 10. */
    val size: Int,

    /** Region id per cell, row-major, exactly as `Board.regions` holds it. */
    val regions: List<Int>,

    val timeMs: Long,
    val score: Int,

    /** 0 to 3. */
    val paws: Int,

    /**
     * Bones the player finished with. Holdings can exceed the three an attempt
     * starts with (level rewards grant extra), so this is not capped at three.
     */
    val bonesRemaining: Int,
) {
    init {
        require(size >= MIN_SIZE) { "A board is at least ${MIN_SIZE}x$MIN_SIZE, was $size" }
        require(regions.size == size * size) {
            "Expected ${size * size} region entries for a ${size}x$size board, got ${regions.size}"
        }
    }

    companion object {
        /** `N = 2` and `N = 3` have no legal placement, so 4x4 is the floor. */
        const val MIN_SIZE: Int = 4
    }
}

/**
 * The words in a share. Every one of them is resolved by the UI from
 * `:libraries:resources` and handed down, because this module has no business
 * holding English and no way to format a date or pluralise a streak for a
 * locale it cannot see.
 *
 * The formatter owns the layout, the emoji and the numbers; the caller owns the
 * language.
 */
data class ShareLabels(
    /** e.g. "Sodogku Daily · Sep 8" or "Sodogku · Level 137". */
    val title: String,

    /** e.g. "🔥 12 day streak". Omitted entirely when null. */
    val streak: String? = null,

    /** The footer line, normally the app's domain. Omitted when null. */
    val footer: String? = null,
)
