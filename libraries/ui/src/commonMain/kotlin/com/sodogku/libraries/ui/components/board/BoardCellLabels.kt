package com.sodogku.libraries.ui.components.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.stateDescription
import com.sodogku.libraries.ui.system.color.RegionPalette
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import sodogku.libraries.resources.generated.resources.Res
import sodogku.libraries.resources.generated.resources.board_action_clear
import sodogku.libraries.resources.generated.resources.board_action_mark
import sodogku.libraries.resources.generated.resources.board_action_place
import sodogku.libraries.resources.generated.resources.board_cell_dog
import sodogku.libraries.resources.generated.resources.board_cell_format
import sodogku.libraries.resources.generated.resources.board_cell_empty
import sodogku.libraries.resources.generated.resources.board_cell_marked
import sodogku.libraries.resources.generated.resources.board_cell_proposed
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
     * glyphs on, the glyph is the region's identity —
     * `features.md#accessibility` says so — and a player who turned that mode
     * on is the one player for whom "periwinkle" is the least useful word
     * available.
     */
    val glyphs: List<String>,
    val empty: String,
    val marked: String,
    val proposed: String,
    val wrong: String,
    val dog: String,

    /** Placeholders: row, column, region. */
    val cellFormat: String,
    val markAction: String,
    val clearAction: String,
    val placeAction: String,
) {

    init {
        // The two string sets, the fills and the glyphs are four lists that
        // have to be the same length and are maintained in four different
        // places: `strings.xml`, `strings.xml` again, `RegionPalette.styles`
        // and `RegionGlyph`. Nothing joined them up, so adding an eleventh
        // colour without an eleventh `board_region_*` would have left region 10
        // announced by region 0's name, silently, in the one mode where the
        // announcement is all the player has.
        require(regions.size == RegionPalette.size) {
            "The board has ${RegionPalette.size} regions and ${regions.size} names for them"
        }
        require(glyphs.size == RegionPalette.size) {
            "The board has ${RegionPalette.size} regions and ${glyphs.size} glyph names for them"
        }
    }

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
     *
     * [region] must be a region that has a name. This used to wrap with `mod`,
     * matching what `RegionPalette.get` did, and both have stopped: a region
     * announced by another region's name is a screen-reader player told two
     * different colours are the same one, which is not a smaller failure than a
     * crash. It is the same failure, reported by nobody. See
     * [RegionPalette.get].
     */
    fun describe(row: Int, column: Int, region: Int, colorblind: Boolean): String {
        val names = if (colorblind) glyphs else regions
        require(region in names.indices) {
            "No name for region $region: the board speaks ${names.size} of them"
        }
        return cellFormat.fill(row + 1, column + 1, names[region])
    }

    /** What is on the square. */
    fun stateOf(state: BoardCellState): String = when (state) {
        BoardCellState.Empty -> empty
        BoardCellState.Marked -> marked
        BoardCellState.Wrong -> wrong
        // A reader gets told it is provisional. The sighted cue is a fainter
        // cross, which is exactly the kind of difference a state description
        // has to carry in words instead.
        BoardCellState.Proposed -> proposed
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
    // One left-to-right pass, not one `replace` per argument. Replacing in turn
    // re-reads what the previous argument wrote, so a value that happens to look
    // like a placeholder gets expanded a second time and lands the wrong word in
    // the sentence. None of this app's arguments contain a `%` today; the ones
    // that do would be somebody's translated region name tomorrow.
    val out = StringBuilder(length)
    var index = 0
    while (index < length) {
        val token = placeholderAt(index)
        if (token == null) {
            out.append(this[index])
            index++
        } else {
            args.getOrNull(token.argument - 1)?.let { out.append(it.toString()) }
            index = token.end
        }
    }
    return out.toString()
}

private class Placeholder(val argument: Int, val end: Int)

/** The `%<n>$<s|d>` starting at [start], or null if one does not. */
private fun String.placeholderAt(start: Int): Placeholder? {
    if (this[start] != '%') return null
    var cursor = start + 1
    var argument = 0
    while (cursor < length && this[cursor].isDigit()) {
        argument = argument * 10 + this[cursor].digitToInt()
        cursor++
    }
    if (cursor == start + 1) return null
    if (cursor >= length || this[cursor] != '$') return null
    cursor++
    if (cursor >= length || (this[cursor] != 's' && this[cursor] != 'd')) return null
    return Placeholder(argument, cursor + 1)
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

/**
 * One `board_region_*` per region, in region order.
 *
 * A list of resources rather than ten `stringResource` calls written out inside
 * the composable, so the *number* of them is a value a plain unit test can
 * count. It was ten calls, `RegionPalette` was ten colours, and nothing joined
 * the two: an eleventh colour with no eleventh string would have compiled, and
 * region 10 would have been announced as region 0 to the only player who cannot
 * see that it is not.
 */
internal val RegionNameResources: List<StringResource> = listOf(
    Res.string.board_region_0,
    Res.string.board_region_1,
    Res.string.board_region_2,
    Res.string.board_region_3,
    Res.string.board_region_4,
    Res.string.board_region_5,
    Res.string.board_region_6,
    Res.string.board_region_7,
    Res.string.board_region_8,
    Res.string.board_region_9,
)

/** One `board_glyph_*` per region, in the same order and under the same rule. */
internal val GlyphNameResources: List<StringResource> = listOf(
    Res.string.board_glyph_0,
    Res.string.board_glyph_1,
    Res.string.board_glyph_2,
    Res.string.board_glyph_3,
    Res.string.board_glyph_4,
    Res.string.board_glyph_5,
    Res.string.board_glyph_6,
    Res.string.board_glyph_7,
    Res.string.board_glyph_8,
    Res.string.board_glyph_9,
)

/** Resolves the board's spoken vocabulary. [BoardSurface] does this for you. */
@Composable
fun rememberBoardCellLabels(): BoardCellLabels {
    val regions = RegionNameResources.map { stringResource(it) }
    val glyphs = GlyphNameResources.map { stringResource(it) }
    val empty = stringResource(Res.string.board_cell_empty)
    val marked = stringResource(Res.string.board_cell_marked)
    val proposed = stringResource(Res.string.board_cell_proposed)
    val wrong = stringResource(Res.string.board_cell_wrong)
    val dog = stringResource(Res.string.board_cell_dog)
    val cellFormat = stringResource(Res.string.board_cell_format)
    val markAction = stringResource(Res.string.board_action_mark)
    val clearAction = stringResource(Res.string.board_action_clear)
    val placeAction = stringResource(Res.string.board_action_place)
    return remember(
        regions, glyphs, empty, marked, proposed, wrong, dog,
        cellFormat, markAction, clearAction, placeAction,
    ) {
        BoardCellLabels(
            regions = regions,
            glyphs = glyphs,
            empty = empty,
            marked = marked,
            proposed = proposed,
            wrong = wrong,
            dog = dog,
            cellFormat = cellFormat,
            markAction = markAction,
            clearAction = clearAction,
            placeAction = placeAction,
        )
    }
}

/**
 * The cell's `semantics` block, held still between the changes that matter.
 *
 * `Modifier.semantics { }` takes a plain lambda, and a plain lambda that closes
 * over anything is a new object on every composition. Compose compares those
 * objects to decide whether the node changed, so an inline block invalidates all
 * hundred cells' semantics on every board recomposition — forty taps across a
 * 10x10 measured **8.2ms median / 10.6ms p90 before the semantics landed and
 * 11.2ms / 20.4ms after**, with frames over the 16.7ms budget going from 3 in
 * 204 to 40 in 217. The tree is rebuilt whether or not a screen reader is
 * listening, because the platform's own content-capture path walks it too.
 *
 * Remembered against what the block actually says, a cell whose state did not
 * change hands back the same object and invalidates nothing. The two callbacks
 * are read through a plain holder rather than keyed on, because they are fresh
 * lambdas every composition and would defeat the whole thing; `onPlace`
 * contributes only whether it is null, which is what the block branches on.
 */
@Composable
internal fun rememberBoardCellSemantics(
    labels: BoardCellLabels,
    row: Int,
    column: Int,
    region: Int,
    state: BoardCellState,
    colorblind: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onPlace: (() -> Unit)?,
): SemanticsPropertyReceiver.() -> Unit {
    // A plain holder, not `rememberUpdatedState`. Both callbacks are fresh
    // lambdas on every composition, so two snapshot states per cell is two
    // hundred snapshot *writes* per board recomposition for values nothing
    // needs to recompose on. The fields are written during composition and read
    // on the same thread when the tree is queried.
    val callbacks = remember { BoardCellCallbacks() }
    callbacks.tap = onTap
    callbacks.place = onPlace
    val placeable = onPlace != null
    return remember(labels, row, column, region, state, colorblind, enabled, placeable) {
        {
            // Built here rather than above, so a board nobody is listening to
            // never assembles a string: the platform only runs this block when
            // something is reading the tree.
            contentDescription = labels.describe(row, column, region, colorblind)
            stateDescription = labels.stateOf(state)
            if (!enabled) {
                disabled()
            } else {
                onClick(
                    label = if (state == BoardCellState.Empty) labels.markAction else labels.clearAction,
                ) {
                    callbacks.tap()
                    true
                }
                if (placeable) {
                    // Offered twice, because the two platforms put the same
                    // capability in different places. `onLongClick` is a
                    // *primary* gesture on Android — double-tap and hold, which
                    // TalkBack announces with the label as a hint — and the
                    // custom action is the one VoiceOver puts on its rotor and
                    // TalkBack in its actions menu. Neither is a fallback; a
                    // player who knows one never needs the other.
                    //
                    // Both are accessibility actions and neither adds a gesture
                    // detector: the sighted double tap is still the pointer path
                    // in `BoardCell`, untouched and unlagged.
                    onLongClick(label = labels.placeAction) {
                        callbacks.place?.invoke()
                        true
                    }
                    customActions = listOf(
                        CustomAccessibilityAction(labels.placeAction) {
                            callbacks.place?.invoke()
                            true
                        },
                    )
                }
            }
        }
    }
}

/** The two callbacks a cell's semantics fire, kept behind one stable reference. */
private class BoardCellCallbacks {
    var tap: () -> Unit = {}
    var place: (() -> Unit)? = null
}
