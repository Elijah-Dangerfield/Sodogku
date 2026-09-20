package com.sodogku.libraries.ui.components.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sodogku.libraries.ui.Border
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.NonLazyVerticalGrid
import com.sodogku.libraries.ui.components.ProgressBar
import com.sodogku.libraries.ui.components.Surface
import com.sodogku.libraries.ui.components.icon.Icon
import com.sodogku.libraries.ui.components.icon.IconSize
import com.sodogku.libraries.ui.components.icon.Icons
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.BadgeSetStyle
import com.sodogku.libraries.ui.system.color.ColorResource
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Motion
import com.sodogku.system.Radii
import com.sodogku.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** How far along a badge is: the bar's value, and the "22 / 25" the caller has already worded. */
@Immutable
data class BadgeProgress(val fraction: Float, val label: String)

/** What one tile in the grid is showing about its badge. */
@Immutable
sealed interface BadgeTileState {
    data object Earned : BadgeTileState

    /**
     * [upNext] is the rung the player is on: the one locked badge on its shelf
     * that keeps its set's colour and a glyph in full colour. The rest of the
     * shelf goes grey, so a shelf reads as one thing to do next and a row of
     * things after it, rather than as nine equally distant asks.
     */
    data class Locked(val progress: BadgeProgress, val upNext: Boolean) : BadgeTileState

    /** Hidden and not yet earned: a face and a name that give nothing away, and no bar. */
    data object Mystery : BadgeTileState
}

/**
 * One tile of the badge grid, as words and a state. The copy is the caller's,
 * so a mystery badge's `???` and a locked badge's fraction arrive already
 * translated; the tile decides only what to draw around them.
 *
 * [spoken] is the whole tile as one sentence, for the same reason a stat chip
 * has one: read out label by label, a tile is three fragments.
 */
@Immutable
data class BadgeTileSpec(
    val face: String,
    val name: String,
    val state: BadgeTileState,
    val set: BadgeSetStyle,
    val spoken: String,
)

/**
 * A shelf of badges, three across when three fit.
 *
 * Three across is the handoff, and it holds at the default text size on the
 * narrowest phone this app supports. It does not hold at the largest system
 * text size: a third of that phone, less the tile's own padding, is not wide
 * enough to set "Regular as Clockwork" in two lines, and a name cut to
 * "Regular as…" on the one screen that exists to name badges is worse than a
 * wider tile. So the grid measures every name at the width three columns
 * would give it and drops to two when any of them needs a third line. The
 * measurement is the same typography and the same width the tile draws with,
 * so what fits here fits there, and the drop carries on to one column when two
 * are still short; `BadgeTileGridFitsTest` holds that at font scale two.
 *
 * Measured rather than switched on the font scale, because the threshold is a
 * property of the longest name in the catalog and the width of the phone, and
 * a number typed here would be right for one catalog on one phone.
 */
@Composable
fun BadgeTileGrid(
    tiles: List<BadgeTileSpec>,
    /** The word on the pill under an earned badge. Copy, so it is the caller's. */
    earnedLabel: String,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = badgeGridColumns(names = tiles.map { it.name }, width = maxWidth)
        NonLazyVerticalGrid(
            columns = columns,
            data = tiles,
            verticalSpacing = GridGap,
            horizontalSpacing = GridGap,
        ) { index, tile ->
            BadgeTile(
                spec = tile,
                earnedLabel = earnedLabel,
                onClick = { onSelect(index) },
                // Every tile in a row takes the row's height, so an earned tile
                // beside a locked one does not sit a bar shorter than it.
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

/**
 * How many tiles across [width] can carry every one of [names] in
 * [MaxNameLines] lines.
 *
 * A `TextMeasurer` rather than a probe composable: this is a layout decision
 * and it is made before anything is placed, so the answer is a number and not
 * a recomposition. The density is a key on purpose. It carries the font scale,
 * and a change to the player's text size is the one event that moves this.
 */
@Composable
private fun badgeGridColumns(names: List<String>, width: Dp): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = BadgeNameTypography.style
    return remember(names, width, density, style) {
        with(density) {
            val gap = GridGap.roundToPx()
            val padding = (TileSidePadding * 2).roundToPx()
            val widthPx = width.roundToPx()
            (PreferredColumns downTo MinColumns).first { columns ->
                val nameWidth = (widthPx - gap * (columns - 1)) / columns - padding
                columns == MinColumns || names.all { name ->
                    val laid = measurer.measure(
                        text = AnnotatedString(name),
                        style = style,
                        constraints = Constraints(maxWidth = nameWidth.coerceAtLeast(0)),
                    )
                    laid.lineCount <= MaxNameLines
                }
            }
        }
    }
}

/**
 * One card in the grid.
 *
 * Earned and locked share a surface and differ in the three places that carry
 * meaning: the stroke inside the edge, the disc behind the glyph, and the line
 * under the name. The stroke is drawn inside the card's bounds, which is what
 * the design's inset box-shadow is, and it is the surface's own border rather
 * than a second layer: Compose draws a border fully inside the node, so a 3dp
 * amber ring here is 3dp of amber with the card's corner, not a centred stroke
 * losing half its width to the clip.
 *
 * A locked badge that is not the next rung is drawn on the track colour with
 * its glyph desaturated and dimmed. Desaturated as well as dimmed, because a
 * dimmed emoji in full colour still looks earned from across the room; grey
 * is the state everybody already reads as "not yet".
 */
@Composable
fun BadgeTile(
    spec: BadgeTileSpec,
    earnedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val earned = spec.state == BadgeTileState.Earned
    val dimmed = when (val state = spec.state) {
        BadgeTileState.Earned -> false
        is BadgeTileState.Locked -> !state.upNext
        BadgeTileState.Mystery -> true
    }

    Surface(
        color = AppTheme.colors.surfacePrimary,
        contentColor = AppTheme.colors.onSurfacePrimary,
        modifier = modifier.fillMaxWidth(),
        radius = Radii.BadgeTile,
        border = if (earned) earnedRing() else softInset(),
        onClick = onClick,
        bounceScale = Motion.PressScale,
        contentDescription = spec.spoken,
        contentPadding = PaddingValues(
            start = TileSidePadding,
            top = Dimension.D700,
            end = TileSidePadding,
            bottom = Dimension.D600,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeDisc(
                face = spec.face,
                background = when (val state = spec.state) {
                    BadgeTileState.Earned -> AppTheme.colors.badgeEarnedDisc
                    is BadgeTileState.Locked -> if (state.upNext) spec.set.disc else AppTheme.colors.track
                    BadgeTileState.Mystery -> AppTheme.colors.track
                },
                size = TileDiscSize,
                typography = AppTheme.typography.Display.D900,
                dimmed = dimmed,
            )
            Spacer(Modifier.height(Dimension.D400))
            Text(
                text = spec.name,
                typography = BadgeNameTypography,
                color = AppTheme.colors.text,
                textAlign = TextAlign.Center,
                maxLines = MaxNameLines,
            )
            when (val state = spec.state) {
                BadgeTileState.Earned -> {
                    Spacer(Modifier.height(Dimension.D300))
                    EarnedPill(label = earnedLabel)
                }
                is BadgeTileState.Locked -> {
                    Spacer(Modifier.height(Dimension.D400))
                    ProgressBar(
                        progress = state.progress.fraction,
                        fill = spec.set.fill,
                        modifier = Modifier.padding(horizontal = Dimension.D100),
                    )
                    Spacer(Modifier.height(Dimension.D200))
                    Text(
                        text = state.progress.label,
                        typography = AppTheme.typography.Body.B500,
                        color = AppTheme.colors.textMuted,
                        maxLines = 1,
                    )
                }
                BadgeTileState.Mystery -> Unit
            }
        }
    }
}

/**
 * One of the three rows pinned above the grid: the badge, its set, its name,
 * and either how much is left or that it just landed.
 *
 * [progress] null is the celebrated form, for a badge earned since the last
 * look: the earned ring, the disc in the earned amber, the kicker in the
 * amber ink, and the same trophy the unlock toast puts at the trailing edge
 * of every pill. The glyph on the left varies, so nothing else on the row says
 * "you earned something" rather than "here is a thing".
 */
@Immutable
data class BadgeSpotlightSpec(
    val face: String,
    val name: String,
    /** The line over the name: the set's name, or the earned label when celebrated. */
    val kicker: String,
    val set: BadgeSetStyle,
    val progress: BadgeProgress?,
    val spoken: String,
)

@Composable
fun BadgeSpotlightCard(
    spec: BadgeSpotlightSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val celebrated = spec.progress == null

    Surface(
        color = AppTheme.colors.surfacePrimary,
        contentColor = AppTheme.colors.onSurfacePrimary,
        modifier = modifier.fillMaxWidth(),
        radius = Radii.BadgeSpotlight,
        border = if (celebrated) earnedRing() else softInset(),
        onClick = onClick,
        bounceScale = Motion.PressScale,
        contentDescription = spec.spoken,
        contentPadding = PaddingValues(horizontal = Dimension.D700, vertical = Dimension.D600),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D600),
        ) {
            BadgeDisc(
                face = spec.face,
                background = if (celebrated) AppTheme.colors.badgeEarnedDisc else spec.set.disc,
                size = SpotlightDiscSize,
                typography = AppTheme.typography.Display.D1000,
                dimmed = false,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.kicker,
                    typography = SetLabelTypography,
                    color = if (celebrated) AppTheme.colors.accentBrandInkSmall else spec.set.ink,
                    allCaps = true,
                    maxLines = 1,
                )
                Text(
                    text = spec.name,
                    typography = AppTheme.typography.Heading.H800,
                    color = AppTheme.colors.text,
                )
                if (spec.progress != null) {
                    Spacer(Modifier.height(Dimension.D300))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
                    ) {
                        ProgressBar(
                            progress = spec.progress.fraction,
                            fill = spec.set.fill,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = spec.progress.label,
                            typography = AppTheme.typography.Heading.H600,
                            color = AppTheme.colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (celebrated) {
                Icon(
                    icon = Icons.Trophy.decorative,
                    size = IconSize.Small,
                    color = AppTheme.colors.accentBrand,
                )
            }
        }
    }
}

/**
 * The round of colour behind a badge's face.
 *
 * The grey is a colour filter in the layer, not a second set of glyphs. It and
 * the alpha are read in `graphicsLayer` so a tile that flips from locked to
 * earned redraws rather than re-lays-out.
 */
@Composable
private fun BadgeDisc(
    face: String,
    background: ColorResource,
    size: Dp,
    typography: TypographyResource,
    dimmed: Boolean,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(background.color, CircleShape),
    ) {
        Text(
            text = face,
            typography = typography,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer {
                alpha = if (dimmed) DimmedGlyphAlpha else 1f
                colorFilter = if (dimmed) Grayscale else null
            },
        )
    }
}

@Composable
private fun EarnedPill(label: String) {
    Box(
        modifier = Modifier
            .background(AppTheme.colors.accentBrand.color, Radii.Round.shape)
            .padding(horizontal = Dimension.D400, vertical = Dimension.D50),
    ) {
        Text(
            text = label,
            typography = AppTheme.typography.Caption.C400.Bold.tracked(PillTracking),
            color = AppTheme.colors.onAccentBrand,
            allCaps = true,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun earnedRing() = Border(AppTheme.colors.accentBrand, EarnedRingWidth)

@Composable
private fun softInset() = Border(AppTheme.colors.cardInset, Dimension.D50)

/**
 * The name on a tile, named so `BadgeTileGridFitsTest` and the grid's own
 * column count measure the size the tile draws with rather than a copy of it.
 */
internal val BadgeNameTypography: TypographyResource
    @Composable get() = AppTheme.typography.Body.B500

/** The set's name over a spotlight row: `12px/700`, spaced 0.8. */
private val SetLabelTypography: TypographyResource
    @Composable get() = AppTheme.typography.Body.B500.Bold.tracked(SetLabelTracking)

private val SetLabelTracking = 0.8.sp
private val PillTracking = 0.6.sp

internal const val MaxNameLines = 2
private const val PreferredColumns = 3
/** One across is where the drop stops: a tile the width of the page holds any name in the catalog. */
private const val MinColumns = 1

internal val GridGap = Dimension.D500
internal val TileSidePadding = Dimension.D400

/** The earned ring is 3 where the soft inset is 2: off the dimension scale by one, on purpose. */
private val EarnedRingWidth = 3.dp

/** The handoff's 52 and 54 discs: the 48 rung plus 4 and plus 6. */
private val TileDiscSize = Dimension.D1300 + Dimension.D100
private val SpotlightDiscSize = Dimension.D1300 + Dimension.D200

/** `opacity: 0.55` on a locked glyph. Dimmed, not ghosted: the shape is half the invitation. */
private const val DimmedGlyphAlpha = 0.55f

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Preview
@Composable
private fun BadgeCardsPreview() {
    PreviewContent(contentPadding = PaddingValues(Dimension.D700)) {
        val campaign = AppTheme.colors.badgeSets[com.sodogku.libraries.achievements.AchievementGroup.Campaign]
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
            BadgeSpotlightCard(
                spec = BadgeSpotlightSpec(
                    face = "🌳",
                    name = "Off the Leash",
                    kicker = "The campaign",
                    set = campaign,
                    progress = BadgeProgress(0.88f, "22/25"),
                    spoken = "Off the Leash. Locked, 22 of 25.",
                ),
                onClick = {},
            )
            BadgeSpotlightCard(
                spec = BadgeSpotlightSpec(
                    face = "🐾",
                    name = "First Steps",
                    kicker = "Earned",
                    set = campaign,
                    progress = null,
                    spoken = "First Steps. Just earned.",
                ),
                onClick = {},
            )
            BadgeTileGrid(
                tiles = listOf(
                    BadgeTileSpec("🐾", "First Steps", BadgeTileState.Earned, campaign, "First Steps. Earned."),
                    BadgeTileSpec(
                        "🌳",
                        "Off the Leash",
                        BadgeTileState.Locked(BadgeProgress(0.88f, "22 / 25"), upNext = true),
                        campaign,
                        "Off the Leash. Locked, 22 of 25.",
                    ),
                    BadgeTileSpec(
                        "🎓",
                        "Well Trained",
                        BadgeTileState.Locked(BadgeProgress(0.44f, "22 / 50"), upNext = false),
                        campaign,
                        "Well Trained. Locked, 22 of 50.",
                    ),
                    BadgeTileSpec("❓", "???", BadgeTileState.Mystery, campaign, "Hidden badge. Not found yet."),
                ),
                earnedLabel = "Earned",
                onSelect = {},
            )
        }
    }
}
