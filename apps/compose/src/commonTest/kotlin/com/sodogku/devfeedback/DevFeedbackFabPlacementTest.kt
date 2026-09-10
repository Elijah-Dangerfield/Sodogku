package com.sodogku.devfeedback

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The button's position is stored as a fraction of its travel, so every one of
 * these is about the conversion at one end or the other.
 *
 * Worth testing rather than eyeballing because the failure mode is a button
 * parked off the edge of the screen, and the toggle that would hide it again is
 * behind a menu the tester reaches by other means. Getting it wrong is
 * recoverable only by reinstalling.
 */
class DevFeedbackFabPlacementTest {

    @Test
    fun aDragMovesTheButtonByItsShareOfTheTravel() {
        val travel = IntSize(width = 400, height = 800)

        val moved = FabPlacement(x = 0.5f, y = 0.5f).movedBy(Offset(x = -100f, y = 200f), travel)

        assertEquals(0.25f, moved.x)
        assertEquals(0.75f, moved.y)
    }

    @Test
    fun aDragPastTheEdgeStopsAtTheEdge() {
        val travel = IntSize(width = 400, height = 800)

        val offTheRight = FabPlacement(x = 0.9f, y = 0.5f).movedBy(Offset(x = 5_000f, y = 0f), travel)
        val offTheTop = FabPlacement(x = 0.5f, y = 0.1f).movedBy(Offset(x = 0f, y = -5_000f), travel)

        assertEquals(1f, offTheRight.x, "dragged past the right edge, it has to stay reachable")
        assertEquals(0f, offTheTop.y, "same on the way up, where the status bar would swallow it")
    }

    @Test
    fun aWindowWithNoRoomToMoveDoesNotProduceANonsensePlacement() {
        // Division by a zero travel is the arithmetic that puts a NaN on disk,
        // and a NaN fraction places the button nowhere at all.
        val moved = FabPlacement(x = 0.4f, y = 0.6f).movedBy(Offset(x = 30f, y = 30f), IntSize.Zero)

        assertEquals(0f, moved.x)
        assertEquals(0f, moved.y)
    }

    @Test
    fun aFractionBecomesAPixelOffsetWithinTheTravel() {
        val offset = FabPlacement(x = 1f, y = 0.5f).offsetIn(IntSize(width = 300, height = 900))

        assertEquals(IntOffset(x = 300, y = 450), offset)
    }

    @Test
    fun theTravelIsTheContainerLessTheButton() {
        val travel = travelWithin(container = IntSize(width = 1080, height = 1920), buttonPx = 144)

        assertEquals(IntSize(width = 936, height = 1776), travel)
    }

    @Test
    fun aWindowSmallerThanTheButtonHasNoTravelRatherThanNegativeTravel() {
        // Negative travel would flip the fractions and place the button outside
        // the very container it is being clamped to.
        val travel = travelWithin(container = IntSize(width = 100, height = 80), buttonPx = 144)

        assertEquals(IntSize.Zero, travel)
    }

    @Test
    fun aStoredPositionOffTheEdgeIsBroughtBackOnScreen() {
        val stored = DevFeedbackFabState(x = 4.2f, y = -3f)

        assertEquals(FabPlacement(x = 1f, y = 0f), stored.placement)
    }

    @Test
    fun aStoredPositionThatIsNotANumberFallsBackToTheDefault() {
        val stored = DevFeedbackFabState(x = Float.NaN, y = Float.POSITIVE_INFINITY)

        assertEquals(DefaultFabPlacement, stored.placement)
    }

    @Test
    fun aFreshInstallStartsSomewhereItCanBeTapped() {
        val fresh = DevFeedbackFabState()

        assertTrue(fresh.placement.x in 0f..1f)
        assertTrue(fresh.placement.y in 0f..1f)
        assertTrue(!fresh.hidden, "the button is the only way in, so it starts shown")
    }

    @Test
    fun movingTheButtonKeepsWhetherItIsShown() {
        val hidden = DevFeedbackFabState(hidden = true)

        val moved = hidden.withPlacement(FabPlacement(x = 0.2f, y = 0.3f))

        assertTrue(moved.hidden)
        assertEquals(FabPlacement(x = 0.2f, y = 0.3f), moved.placement)
    }
}
