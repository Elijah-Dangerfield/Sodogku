package com.sodogku.libraries.ui.components.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.board_action_clear
import sodogku.libraries.resources.generated.resources.board_action_mark
import sodogku.libraries.resources.generated.resources.board_action_place
import sodogku.libraries.resources.generated.resources.board_cell_dog
import sodogku.libraries.resources.generated.resources.board_cell_format
import sodogku.libraries.resources.generated.resources.board_cell_empty
import sodogku.libraries.resources.generated.resources.board_cell_marked
import sodogku.libraries.resources.generated.resources.board_cell_wrong
import sodogku.libraries.resources.generated.resources.board_glyph_0
import sodogku.libraries.resources.generated.resources.board_glyph_1
import sodogku.libraries.resources.generated.resources.board_glyph_2
import sodogku.libraries.resources.generated.resources.board_glyph_3
import sodogku.libraries.resources.generated.resources.board_glyph_4
import sodogku.libraries.resources.generated.resources.board_glyph_5
import sodogku.libraries.resources.generated.resources.board_glyph_6
import sodogku.libraries.resources.generated.resources.board_glyph_7
import sodogku.libraries.resources.generated.resources.board_glyph_8
import sodogku.libraries.resources.generated.resources.board_glyph_9
import sodogku.libraries.resources.generated.resources.board_region_0
import sodogku.libraries.resources.generated.resources.board_region_1
import sodogku.libraries.resources.generated.resources.board_region_2
import sodogku.libraries.resources.generated.resources.board_region_3
import sodogku.libraries.resources.generated.resources.board_region_4
import sodogku.libraries.resources.generated.resources.board_region_5
import sodogku.libraries.resources.generated.resources.board_region_6
import sodogku.libraries.resources.generated.resources.board_region_7
import sodogku.libraries.resources.generated.resources.board_region_8
import sodogku.libraries.resources.generated.resources.board_region_9

/**
 * Everything a [BoardCell] has to *say*, resolved once for the whole board.
 *
 * The cell draws its fill, its glyph and its cross rather than composing them,
 * which is what keeps a hundred of them affordable and is also why none of them
 * can be inferred from the tree. So the meaning is stated here instead.
 *
 * **Resolved once, not per cell.** `stringResource` is a composable that reads
 * through a resource cache and keeps state per call site; a hundred cells asking
 * for fourteen strings each is fourteen hundred of those on every board
 * recomposition. [BoardSurface] resolves the set once and hands it down
 * [LocalBoardCellLabels], so the per-cell cost is a field read.
 *
 * The strings themselves are never touched unless something is actually
 * listening: [describe] runs inside the cell's `semantics` block, which the
 * platform only invokes when an accessibility service reads the tree. With
 * TalkBack and VoiceOver off, this type is a pointer nobody dereferences.
 */
@Immutable
data class BoardCellLabels(
    /** One name per region, indexed to match `RegionPalette`. */
    val regions: List<String>,

    /**
     * One name per region *glyph*, used in place of [regions] when colourblind
     * mode is on.
     *
     * A label should name the thing that is actually on the square. With the
     * glyphs on, the glyph is the region's identity — SPEC 16 says so — and a
     * player who turned that mode on is the one player for whom "periwinkle" is
     * the least useful word available.
     */
    val glyphs: List<String>,
    val empty: String,
    val marked: String,
    val wrong: String,
    val dog: String,

    /** Placeholders: row, column, region. */
    val cellFormat: String,
    val markAction: String,
    val clearAction: String,
    val placeAction: String,
) {

    /**
     * Which square this is: where it sits, and which region it belongs to.
     *
     * The two halves of a cell's label are split because a screen reader treats
     * them differently. This one is the square's *identity* and never changes,
     * so it is the content description; [stateOf] is what is on the square right
     * now, and goes in the state description — which is the half TalkBack and
     * VoiceOver re-announce on their own when it changes under the reading
     * cursor. Fold them into one string and a player who crosses a square off
     * hears nothing back.
     *
     * [row] and [column] arrive zero-based, because that is what the board
     * counts in, and come out one-based, because that is what a person counts
     * in. This is the only place in the app the two conventions meet.
     */
    fun describe(row: Int, column: Int, region: Int, colorblind: Boolean): String {
        val names = if (colorblind) glyphs else regions
        return cellFormat.fill(row + 1, column + 1, names[region.mod(names.size)])
    }

    /** What is on the square. */
    fun stateOf(state: BoardCellState): String = when (state) {
        BoardCellState.Empty -> empty
        BoardCellState.Marked -> marked
        BoardCellState.Wrong -> wrong
        BoardCellState.Occupied -> dog
    }
}

/**
 * Substitutes `%1$s` / `%1$d` placeholders, positionally.
 *
 * `String.format` is JVM-only, and `stringResource(res, args)` — Compose
 * Resources' own formatter — is `@Composable`, which is the one thing this
 * cannot be: the description is built inside a `semantics` block so that a
 * board nobody is listening to pays nothing for it.
 *
 * Positional on purpose. A translation is free to put the column before the
 * row, or to drop an argument, and does not have to keep this app's word order
 * to keep its numbers.
 */
internal fun String.fill(vararg args: Any): String {
    var out = this
    args.forEachIndexed { index, arg ->
        val value = arg.toString()
        out = out.replace("%${index + 1}\$s", value).replace("%${index + 1}\$d", value)
    }
    return out
}

/**
 * The board's spoken vocabulary, or null where nobody has provided one.
 *
 * Null rather than an English fallback: a wrong-language label is harder to
 * notice than a missing one, and [BoardCell] fills the gap by resolving the
 * strings itself. That path costs what this local exists to avoid, so it is for
 * previews and one-off cells — a real board goes through [BoardSurface], which
 * provides this.
 */
val LocalBoardCellLabels = staticCompositionLocalOf<BoardCellLabels?> { null }

/** Resolves the board's spoken vocabulary. [BoardSurface] does this for you. */
@Composable
fun rememberBoardCellLabels(): BoardCellLabels {
    val regions = listOf(
        stringResource(Res.string.board_region_0),
        stringResource(Res.string.board_region_1),
        stringResource(Res.string.board_region_2),
        stringResource(Res.string.board_region_3),
        stringResource(Res.string.board_region_4),
        stringResource(Res.string.board_region_5),
        stringResource(Res.string.board_region_6),
        stringResource(Res.string.board_region_7),
        stringResource(Res.string.board_region_8),
        stringResource(Res.string.board_region_9),
    )
    val glyphs = listOf(
        stringResource(Res.string.board_glyph_0),
        stringResource(Res.string.board_glyph_1),
        stringResource(Res.string.board_glyph_2),
        stringResource(Res.string.board_glyph_3),
        stringResource(Res.string.board_glyph_4),
        stringResource(Res.string.board_glyph_5),
        stringResource(Res.string.board_glyph_6),
        stringResource(Res.string.board_glyph_7),
        stringResource(Res.string.board_glyph_8),
        stringResource(Res.string.board_glyph_9),
    )
    val empty = stringResource(Res.string.board_cell_empty)
    val marked = stringResource(Res.string.board_cell_marked)
    val wrong = stringResource(Res.string.board_cell_wrong)
    val dog = stringResource(Res.string.board_cell_dog)
    val cellFormat = stringResource(Res.string.board_cell_format)
    val markAction = stringResource(Res.string.board_action_mark)
    val clearAction = stringResource(Res.string.board_action_clear)
    val placeAction = stringResource(Res.string.board_action_place)
    return remember(regions, glyphs, empty, marked, wrong, dog, cellFormat, markAction) {
        BoardCellLabels(
            regions = regions,
            glyphs = glyphs,
            empty = empty,
            marked = marked,
            wrong = wrong,
            dog = dog,
            cellFormat = cellFormat,
            markAction = markAction,
            clearAction = clearAction,
            placeAction = placeAction,
        )
    }
}
