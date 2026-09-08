package com.sodogku.libraries.ui.components.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.dog.AnimatedDog
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.drawStarburst
import com.sodogku.libraries.ui.system.color.BoardMark
import com.sodogku.libraries.ui.system.color.RegionPalette
import com.sodogku.libraries.ui.system.color.drawBoardMark
import com.sodogku.libraries.ui.system.color.drawRegionGlyph
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.clip
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.sin

/** What is currently in a cell. */
enum class BoardCellState {
    /** Nothing yet. */
    Empty,

    /** Ruled out, by auto-mark or by the player's own note. */
    Marked,

    /**
     * Ruled out the expensive way: the player guessed here and it cost a bone.
     * Kept visually distinct because it is different information — a square
     * someone *paid* for, not one they reasoned out.
     */
    Wrong,

    /**
     * Crossed off on the board's suggestion, not yet accepted by the player.
     *
     * A sniff used to spotlight the squares it ruled out and then let the
     * highlight fade, which left the player holding the information in their
     * head and the board looking exactly as it did before. Painting the crosses
     * and asking for one tap to keep them turns the hint into something that
     * lands on the board, and keeps it a decision rather than a fait accompli.
     *
     * Drawn as the same cross at reduced strength, because it *is* the same
     * cross, one step away.
     */
    Proposed,

    /** A dog. */
    Occupied,
}

/**
 * One square of the board.
 *
 * A 10x10 puts a hundred of these on screen, so it stays cheap: the region
 * glyph and the cross are drawn rather than composed, the fill is a
 * `drawBehind`, and the dog is one downscaled image.
 *
 * Every animation lives here rather than at the call site, so no screen can
 * forget to animate a cell and every cell in the app behaves identically:
 *
 * - **Entrance** — [entranceDelayMillis] staggers the drop-in. The grid feeds it
 *   a diagonal offset so the board lands as a wave instead of appearing at once.
 * - **Mark** — the cross draws stroke by stroke. A note being *made* reads
 *   differently from a fact that was always true.
 * - **Placement** — the dog overshoots and settles.
 * - **Consequence** — [placementRole] gives the landing square a warm starburst
 *   and lights the row and column the dog just resolved. Without it a placement
 *   is a dog appearing; with it, it is a deduction landing on two lines.
 * - **Strike** — [strikeNonce] shakes the cell and flashes a red cross. A nonce
 *   rather than a boolean, so the same cell can be got wrong twice running.
 *
 * ### What it says
 *
 * Because none of the above is a composable, none of it is in the semantics
 * tree either, and a screen reader met a hundred anonymous boxes. Everything a
 * player needs is a parameter here already, so the cell states its own meaning:
 * where it is, which region it belongs to, and what is on it. See
 * [BoardCellLabels] for the wording and [onPlace] for the half that content
 * descriptions alone would have got wrong.
 */
@Composable
fun BoardCell(
    region: Int,
    state: BoardCellState,
    /** Zero-based, for the spoken position. */
    row: Int,
    /** Zero-based, for the spoken position. */
    column: Int,
    modifier: Modifier = Modifier,
    size: Dp = DefaultCellSize,
    colorblind: Boolean = false,
    strikeNonce: Int = 0,
    /** Bumped once per placement, board-wide. See [PlacementPulse]. */
    placementNonce: Int = 0,
    /** What this square owes the placement [placementNonce] refers to. */
    placementRole: PlacementRole = PlacementRole.None,
    entranceDelayMillis: Int = 0,
    /**
     * Staggers the placed dog's idle loop and picks which loop it gets, so a
     * board of them is not in lockstep. Any stable per-cell number works.
     */
    animationOffset: Int = 0,
    /** False swaps the living dog for a still, for the battery setting. */
    animated: Boolean = true,
    enabled: Boolean = true,
    onTap: () -> Unit = {},
    /**
     * Commit a guess here, for a player who cannot make the tap that does it.
     *
     * The sighted gesture is a second tap inside 320ms, recognised upstream. A
     * screen reader eats the double tap and delivers one activation, so a board
     * with content descriptions and nothing else is a board that can be marked
     * and unmarked and never played. This is the placement as an *action* the
     * reader can offer by name instead of by timing.
     *
     * Null where a placement is not on offer — a square the board has already
     * ruled out, where committing would do nothing and announcing it would be a
     * lie.
     */
    onPlace: (() -> Unit)? = null,
    labels: BoardCellLabels? = LocalBoardCellLabels.current,
) {
    val style = RegionPalette[region]
    // Resolved here only when nobody hoisted them, which means a preview or a
    // lone cell. Fourteen `stringResource` call sites per cell is exactly the
    // cost `LocalBoardCellLabels` exists to keep off a hundred-cell board, and
    // `BoardSurface` provides it for every real one.
    val spoken = labels ?: rememberBoardCellLabels()
    val spokenProperties = rememberBoardCellSemantics(
        labels = spoken,
        row = row,
        column = column,
        region = region,
        state = state,
        colorblind = colorblind,
        enabled = enabled,
        onTap = onTap,
        onPlace = onPlace,
    )

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (entranceDelayMillis > 0) delay(entranceDelayMillis.toLong())
        entrance.animateTo(1f, Motion.Pop)
    }

    val pop = remember { Animatable(if (state == BoardCellState.Occupied) 1f else 0f) }
    val mark = remember {
        Animatable(if (state == BoardCellState.Empty || state == BoardCellState.Occupied) 0f else 1f)
    }
    LaunchedEffect(state) {
        when (state) {
            BoardCellState.Occupied -> {
                mark.snapTo(0f)
                pop.animateTo(Motion.PopOvershoot, Motion.Pop)
                pop.animateTo(1f, Motion.Tap)
            }
            BoardCellState.Marked, BoardCellState.Wrong, BoardCellState.Proposed -> {
                pop.snapTo(0f)
                mark.animateTo(1f, tween(Motion.MarkDrawMillis))
            }
            BoardCellState.Empty -> {
                pop.animateTo(0f, Motion.Tap)
                mark.animateTo(0f, Motion.fade())
            }
        }
    }

    val shake = remember { Animatable(0f) }
    val nonce by rememberUpdatedState(strikeNonce)
    LaunchedEffect(nonce) {
        // Reset first, and unconditionally. `GameScreen` drives every cell that
        // is not the current strike cell to nonce 0, so when a second wrong tap
        // lands elsewhere within the shake this effect is cancelled mid-flight
        // and re-entered with nonce 0. Returning before the reset left the
        // translation frozen at whatever the interrupted curve had reached —
        // `sin(shake * 18) * 7 * (1 - shake)`, so up to about six pixels — and
        // the cell simply stayed there, off its own grid line.
        shake.snapTo(0f)
        if (nonce == 0) return@LaunchedEffect
        shake.animateTo(1f, tween(Motion.ShakeMillis))
    }

    val dogVisible by remember { derivedStateOf { pop.value > 0f } }

    val pulse = placementPulseProgress(placementNonce, placementRole, animated)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val enter = entrance.value
                scaleX = enter
                scaleY = enter
                alpha = enter
                translationY = (1f - enter) * -EntranceDropPx
                translationX = sin(shake.value * ShakeCycles) * ShakeAmplitudePx * (1f - shake.value)
                // The lift on a resolved line. Small on purpose: nineteen
                // squares swelling at once is the board convulsing, and the
                // glow is what is meant to be read — this only says the two
                // lines are one thing.
                val lift = 1f + pulse.value * LinePulseScale
                scaleX *= lift
                scaleY *= lift
            }
            .clip(Radii.Cell)
            .drawBehind {
                drawRect(style.fill)

                // Order matters and is the whole reason these are draws rather
                // than stacked composables: the glow washes over the fill, the
                // burst sits on top of the glow, and the player's own cross is
                // last so nothing the board is celebrating can obscure the note
                // they made.
                val elapsed = 1f - pulse.value
                if (pulse.value > 0f) {
                    drawRect(LineGlow.copy(alpha = LineGlow.alpha * pulse.value))
                    if (placementRole == PlacementRole.Origin) {
                        drawStarburst(BurstInk, elapsed)
                    }
                }

                if (colorblind) {
                    drawRegionGlyph(
                        style.glyph,
                        style.ink.copy(alpha = style.ink.alpha * GlyphAlpha),
                        GlyphFraction,
                    )
                }

                // A near-white dog on a pastel square is 1.44:1 at worst, which
                // is not enough on its own. The shadow is what puts an edge back
                // under it, so it is drawn for every placed dog rather than only
                // while the placement animation runs.
                // `derivedStateOf`, not `pop.value > 0f` directly. Reading an
        // `Animatable` in composition subscribes this scope to every frame of
        // the pop, recomposing the whole content subtree instead of just
        // re-drawing the layer that reads it — the landmine AGENTS.md documents.
        //
        // Gating on `state == Occupied` would also fix the recomposition and
        // would be wrong: `pop` animates *down* when a dog is removed, and the
        // presence check is what keeps it on screen long enough to shrink away.
        // The derived boolean flips twice per placement rather than once per
        // frame, and the fade-out survives.
        if (dogVisible) {
                    val radius = this.size.minDimension * DogShadowFraction
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(DogShadow, Color.Transparent),
                            center = center.copy(y = center.y + radius * DogShadowDrop),
                            radius = radius,
                        ),
                        radius = radius,
                        center = center.copy(y = center.y + radius * DogShadowDrop),
                        alpha = pop.value.coerceIn(0f, 1f),
                    )
                }

                if (mark.value > 0f) {
                    val ink = when (state) {
                        BoardCellState.Wrong -> StrikeInk
                        // Same cross, drawn faint. A different colour would read
                        // as a third kind of mark rather than as a weaker one.
                        BoardCellState.Proposed -> MarkInk.copy(alpha = ProposedMarkAlpha)
                        else -> MarkInk
                    }
                    drawBoardMark(ink, BoardMark.Fraction, mark.value)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // `onDoubleTap` is deliberately not registered here. Compose
                // withholds `onTap` until the double-tap timeout expires once it
                // is, which would put ~300ms of lag on the gesture players use
                // most. The second tap is recognised upstream instead, so the
                // cross appears instantly and converts if another tap follows.
                detectTapGestures(onTap = { onTap() })
            }
            .semantics(properties = spokenProperties)
    ) {
        // `derivedStateOf`, not `pop.value > 0f` directly. Reading an
        // `Animatable` in composition subscribes this scope to every frame of
        // the pop, recomposing the whole content subtree instead of just
        // re-drawing the layer that reads it — the landmine AGENTS.md documents.
        //
        // Gating on `state == Occupied` would also fix the recomposition and
        // would be wrong: `pop` animates *down* when a dog is removed, and the
        // presence check is what keeps it on screen long enough to shrink away.
        // The derived boolean flips twice per placement rather than once per
        // frame, and the fade-out survives.
        if (dogVisible) {
            // A placed dog is alive: it looks around and blinks. Driven from a
            // shared sprite sheet, so ten of them on a board cost one bitmap.
            val dogModifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = pop.value.coerceIn(0f, 1f)
            }
            if (animated) {
                AnimatedDog(
                    size = size * DogFraction,
                    frameOffset = animationOffset,
                    variant = animationOffset,
                    modifier = dogModifier,
                )
            } else {
                Dog(pose = DogPose.Still, size = size * DogFraction, modifier = dogModifier)
            }
        }
    }
}

/**
 * How far through its placement pulse this square is, 1 down to 0.
 *
 * The `Animatable` only exists for the squares a placement actually touched —
 * about 19 of a 10x10's hundred, and none at all when the player is only
 * marking. A hundred idle animations that spend their lives at zero is the
 * cheap-looking version of this that is not actually cheap.
 */
@Composable
private fun placementPulseProgress(
    nonce: Int,
    role: PlacementRole,
    animated: Boolean,
): State<Float> {
    if (role == PlacementRole.None || !animated) return NoPulse
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(nonce) {
        pulse.snapTo(1f)
        pulse.animateTo(0f, tween(Motion.PlacementPulseMillis))
    }
    return pulse.asState()
}

private val NoPulse: State<Float> = mutableFloatStateOf(0f)

/** Fits a 10x10 board on the narrowest phone we support with room for padding. */
val DefaultCellSize: Dp = 34.dp

/** A square that cost a bone stays red, for the rest of the attempt. */
private val StrikeInk = Color(0xE6D32F2F)

/**
 * The player's own cross. White on every fill, rather than the region's derived
 * ink on each: a mark that has to be spotted across a hundred squares wants one
 * answer, and white is the only colour that is louder than every pastel in the
 * palette at once.
 */
private val MarkInk = Color(0xFFFFFFFF)

/** The wash that runs down a resolved row and column. Warm, so it reads as praise. */
private val LineGlow = Color(0x8CFFE39B)

/** The burst behind a landed dog. */
private val BurstInk = Color(0xCCFFD25E)

/** Puts an edge under a near-white dog on a pastel square. */
private val DogShadow = Color(0x33241207)
private const val DogShadowFraction = 0.44f
private const val DogShadowDrop = 0.16f

/**
 * The region glyph is a watermark, not a badge. Loud enough to tell two fills
 * apart at a glance, quiet enough that a board of them does not compete with
 * the crosses and dogs the player is actually reading.
 *
 * The alpha went up with the pastel retune, from 0.45 to 0.60. Softer fills
 * narrowed the lightness ladder the palette used to lean on, so the glyph is
 * carrying more of colourblind mode than it was; measured against the ten
 * fills, this takes the composited watermark from 1.48–1.67:1 to 1.82–2.02:1.
 * It is still a watermark — but it is now the *most* legible it has been, on a
 * palette where it matters more.
 */
private const val GlyphFraction = 0.42f
private const val GlyphAlpha = 0.60f

/**
 * How faint a proposed cross is against a committed one.
 *
 * Low enough to read as provisional at a glance across a 10x10, high enough to
 * survive the region fill underneath it. The white cross on a pastel is already
 * a low-contrast pairing, so there is less headroom here than the number
 * suggests.
 */
private const val ProposedMarkAlpha = 0.45f
private const val DogFraction = 0.82f
private const val ShakeCycles = 18f
private const val ShakeAmplitudePx = 7f
private const val EntranceDropPx = 26f
private const val LinePulseScale = 0.05f

@Preview
@Composable
private fun BoardCellStatesPreview() {
    PreviewContent {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            BoardCell(region = 0, state = BoardCellState.Empty, row = 0, column = 0, size = 48.dp)
            BoardCell(region = 3, state = BoardCellState.Marked, row = 0, column = 1, size = 48.dp)
            BoardCell(region = 6, state = BoardCellState.Occupied, row = 0, column = 2, size = 48.dp)
            BoardCell(region = 2, state = BoardCellState.Empty, row = 0, column = 3, size = 48.dp, colorblind = true)
            BoardCell(region = 8, state = BoardCellState.Marked, row = 0, column = 4, size = 48.dp, colorblind = true)
        }
    }
}
