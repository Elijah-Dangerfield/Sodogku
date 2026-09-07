package com.sodogku.libraries.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.components.board.BoardCell
import com.sodogku.libraries.ui.components.board.BoardCellState
import com.sodogku.libraries.ui.components.dog.Dog
import com.sodogku.libraries.ui.components.dog.DogPose
import com.sodogku.libraries.ui.components.game.RewardBadge
import com.sodogku.libraries.ui.components.game.RewardButton
import com.sodogku.libraries.ui.components.text.Text
import com.sodogku.libraries.ui.system.color.RegionPalette
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * # Game components
 *
 * The Sodogku-specific half of the design system. Everything here exists because
 * the board or a game screen needs it, and everything here is the *only* way a
 * feature should get at it — no screen reaches for a region colour or a dog
 * drawable directly.
 */

/**
 * The ten region colours, each with the glyph that identifies it when hue
 * cannot. Read this page in a colourblind simulator: if two rows are hard to
 * tell apart by fill alone that is expected and fine, but if two *glyphs* are
 * hard to tell apart, the palette has a real problem.
 */
@Composable
fun RegionPaletteCatalog(modifier: Modifier = Modifier) {
    CatalogPage(
        title = "Region palette",
        description = "Indexed to Board.regions. Fills are saturated mid-tones so the cream " +
            "dog reads on every one, and lightness varies as much as hue so the set does not " +
            "collapse for red-green colour vision deficiency.",
        modifier = modifier,
    ) {
        RegionPalette.styles.forEachIndexed { index, _ ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
                modifier = Modifier.padding(vertical = Dimension.D200),
            ) {
                BoardCell(region = index, state = BoardCellState.Empty)
                BoardCell(region = index, state = BoardCellState.Empty, colorblind = true)
                BoardCell(region = index, state = BoardCellState.Marked)
                BoardCell(region = index, state = BoardCellState.Occupied)
                Text(
                    text = "Region $index",
                    typography = AppTheme.typography.Body.B400,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}

/**
 * Cell states, side by side. `Occupied` pops in on state change and shakes on a
 * new `strikeNonce`; both are driven inside the component so no screen can
 * forget to animate one.
 */
@Composable
fun BoardCellCatalog(modifier: Modifier = Modifier) {
    CatalogPage(
        title = "Board cell",
        description = "Empty, marked, occupied. Tap places, long-press marks. A hundred of " +
            "these are on screen at 10x10, so the glyph is drawn rather than composed and the " +
            "fill is a drawBehind rather than a stack of boxes.",
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
            BoardCell(region = 0, state = BoardCellState.Empty)
            BoardCell(region = 0, state = BoardCellState.Marked)
            BoardCell(region = 0, state = BoardCellState.Occupied)
        }
    }
}

/**
 * Every dog pose. Board poses ship at 192px and hero poses at 512px — putting a
 * hero pose in a grid cell is roughly 28MB of decoded bitmap for one puzzle,
 * which is why `DogPose` carries the weight rather than the call site choosing.
 */
@Composable
fun DogCatalog(modifier: Modifier = Modifier) {
    CatalogPage(
        title = "Dog",
        description = "Dog(pose = DogPose.X). Never reference a dog drawable directly.",
        modifier = modifier,
    ) {
        DogPose.entries.chunked(CHUNK).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
                row.forEach { pose ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Dog(pose = pose, size = Dimension.D1900)
                        Text(
                            text = pose.name,
                            typography = AppTheme.typography.Caption.C300,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The two ad-facing controls, which share the secondary accent so a player only
 * has to learn one colour. Check this page whenever the accent moves: an offer
 * that stops being visually distinct from an ordinary CTA is the failure mode,
 * and it looks fine in isolation.
 */
@Composable
fun RewardCatalog(modifier: Modifier = Modifier) {
    CatalogPage(
        title = "Reward controls",
        description = "RewardButton is a standing offer and pulses in bursts with a rest " +
            "between; RewardBadge marks a button the player already wanted as one that plays " +
            "an ad first. Both go quiet under LocalInspectionMode so previews and screenshot " +
            "tests can reach idle.",
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
        ) {
            RewardButton(label = RewardSample)
            RewardButton(label = RewardSample, enabled = false)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            Text(text = BadgedControlSample, typography = AppTheme.typography.Body.B500)
            RewardBadge()
        }
    }
}

@Composable
private fun CatalogPage(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Dimension.D700),
        verticalArrangement = Arrangement.spacedBy(Dimension.D400),
    ) {
        Text(text = title, typography = AppTheme.typography.Heading.H600)
        Text(
            text = description,
            typography = AppTheme.typography.Body.B400,
            color = AppTheme.colors.textSecondary,
        )
        content()
    }
}

private const val CHUNK = 3

/** Catalog copy is developer-facing spec, so it stays a constant rather than a string resource. */
private const val RewardSample = "Free bones"
private const val BadgedControlSample = "Next level"

@Preview
@Composable
private fun RegionPaletteCatalogPreview() = PreviewContent { RegionPaletteCatalog() }

@Preview
@Composable
private fun BoardCellCatalogPreview() = PreviewContent { BoardCellCatalog() }

@Preview
@Composable
private fun DogCatalogPreview() = PreviewContent { DogCatalog() }

@Preview
@Composable
private fun RewardCatalogPreview() = PreviewContent { RewardCatalog() }
