package com.sodogku.libraries.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sodogku.libraries.ui.Elevation
import com.sodogku.libraries.ui.PreviewContent
import com.sodogku.libraries.ui.elevation
import com.sodogku.system.AppTheme
import com.sodogku.system.Dimension
import com.sodogku.system.Radii
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The card the grid rests on: white, rounded, with a gutter of its own around
 * the cells.
 *
 * Bare cells sitting straight on the page read as a table — a thing you fill
 * in. The same cells inside a card read as a board, a thing you play on, and
 * the white gutter it puts between the cells is what makes a pastel grid look
 * like sweets rather than a heat map.
 *
 * It lives beside [BoardCell] rather than in the screen for the same reason
 * every board animation does: there is exactly one board in this app and there
 * should be exactly one place that decides what it looks like.
 */
@Composable
fun BoardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .elevation(Elevation.Card, Radii.Board.shape, color = AppTheme.colors.borderSecondary)
            .background(AppTheme.colors.surfacePrimary.color, Radii.Board.shape)
            .padding(BoardInset),
        content = { content() },
    )
}

/**
 * The white margin between the outermost cells and the card's edge. Matched to
 * a little more than the gutter between cells, so the outer ring of squares
 * looks placed rather than cropped.
 */
private val BoardInset = Dimension.D400

/**
 * The gutter between two squares. Lives here rather than in the screen laying
 * out the grid because it is half of what [BoardSurface] is for: the card is
 * only white *showing through* if there is something for it to show through.
 */
val BoardCellGap = Dimension.D200

@Preview
@Composable
private fun BoardSurfacePreview() {
    PreviewContent {
        BoardSurface(modifier = Modifier.padding(Dimension.D500)) {
            BoardCell(region = 3, state = BoardCellState.Marked, size = Dimension.D1300)
        }
    }
}
