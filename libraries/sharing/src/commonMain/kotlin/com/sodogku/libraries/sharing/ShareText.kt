package com.sodogku.libraries.sharing

/**
 * Builds the Wordle-style share.
 *
 * ```
 * Sodogku Daily · Sep 8
 * ⏱ 1:42   🏆 14,820   🐾🐾🐾   🦴🦴🦴
 * 🔥 12 day streak
 *
 * 🟥🟧🟧🟨🟨🟩🟩
 * 🟥🟥🟧🟨🟩🟩🟦
 * …
 *
 * sodogku.app
 * ```
 *
 * The grid is the *region layout*, never the placements. That is the one thing
 * the format has to get right: a share of the daily is worth having because
 * everyone played the same board, and it stops being worth having the moment it
 * hands the reader the answer. [ShareResult] carries no solution at all, so the
 * grid renders the same for two players who solved the same board and the same
 * for a player who has not solved it yet.
 */
object ShareText {

    fun format(result: ShareResult, labels: ShareLabels): String = buildString {
        appendLine(labels.title)
        appendLine(statsLine(result))
        labels.streak?.let { appendLine(it) }
        appendLine()
        append(grid(result))
        labels.footer?.let {
            appendLine()
            appendLine()
            append(it)
        }
    }

    /**
     * The board as coloured squares, one line per row.
     *
     * A cell's square depends only on its region id, so every cell of a region
     * looks identical — including the one the dog was on. Anything that varied
     * per cell would be a leak, and the emoji vocabulary is deliberately too
     * small to say anything else.
     */
    fun grid(result: ShareResult): String = (0 until result.size).joinToString("\n") { row ->
        (0 until result.size).joinToString("") { col ->
            squareFor(result.regions[row * result.size + col])
        }
    }

    private fun statsLine(result: ShareResult): String = listOf(
        "$CLOCK ${duration(result.timeMs)}",
        "$TROPHY ${grouped(result.score)}",
        PAW.repeat(result.paws.coerceIn(0, MAX_PAWS)),
        BONE.repeat(result.bonesRemaining.coerceIn(0, MAX_BONE_GLYPHS)),
    ).filter { it.isNotEmpty() }.joinToString(GROUP_GAP)

    /**
     * `m:ss`, or `h:mm:ss` for the rare run that crosses an hour.
     *
     * Public because the win sheet and the level pane show the same time this
     * line does, and a second copy of the arithmetic is how a shared run comes
     * to disagree with the sheet it was shared from.
     */
    fun duration(millis: Long): String {
        val total = (millis.coerceAtLeast(0)) / MILLIS_PER_SECOND
        val seconds = total % SECONDS_PER_MINUTE
        val minutes = (total / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
        val hours = total / SECONDS_PER_HOUR
        return if (hours > 0) {
            "$hours:${minutes.padded()}:${seconds.padded()}"
        } else {
            "$minutes:${seconds.padded()}"
        }
    }

    private fun Long.padded(): String = toString().padStart(2, '0')

    /**
     * Thousands-grouped with a comma.
     *
     * Not locale-aware, because common Kotlin has no number formatter and the
     * alternative — threading a separator through the API for a string nobody
     * parses — buys less than it costs. A caller that needs a localised score
     * has the number and can say so if it ever comes up.
     */
    private fun grouped(value: Int): String {
        val digits = value.toString()
        val sign = if (digits.startsWith("-")) "-" else ""
        val body = digits.removePrefix("-")
        return sign + body.reversed().chunked(GROUP_SIZE).joinToString(",").reversed()
    }

    /**
     * Unicode has exactly nine coloured-or-neutral square emoji and the 10x10
     * band needs ten regions, so the last one is the framed square. It is the
     * closest pair in the set — on a ten-region board a share reads slightly
     * worse than a screenshot, which is the trade for the format working at all.
     *
     * Wraps rather than throwing, for the same reason `RegionPalette` does: a
     * board with more regions than glyphs is a content bug, and a repeated
     * square is a much better failure than a crash on the win sheet.
     */
    private fun squareFor(region: Int): String = SQUARES[region.mod(SQUARES.size)]

    private val SQUARES = listOf("🟥", "🟧", "🟨", "🟩", "🟦", "🟪", "🟫", "⬜", "⬛", "🔲")

    private const val CLOCK = "⏱"
    private const val TROPHY = "🏆"
    private const val PAW = "🐾"
    private const val BONE = "🦴"

    /** Three spaces, wide enough to read as a gap between emoji on one line. */
    private const val GROUP_GAP = "   "

    private const val MAX_PAWS = 3

    /**
     * Bone holdings are uncapped, but a line of fifteen bones is a wall rather
     * than a brag, so the glyph run stops here.
     */
    private const val MAX_BONE_GLYPHS = 5

    private const val GROUP_SIZE = 3
    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
