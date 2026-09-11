package com.sodogku.libraries.telemetry.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a screen visit reports about its frames, and when it reports nothing.
 *
 * Every failure here is a dashboard that reads plausibly and is wrong, which is
 * the worst kind: a panel with a number on it is trusted. One janky frame out
 * of three is thirty-three percent and means only that the screen was barely on
 * show, so short visits are dropped. A dropped visit still has to clear its
 * counters, or the next screen inherits frames it never rendered.
 *
 * The worst-frame field is the one that earns its keep for stall work. A screen
 * dropping one frame in two hundred rounds to one percent and looks healthy; if
 * that frame took nine hundred milliseconds there is a freeze in it, and this
 * is the only field that says so. Its companion is that a long frame which was
 * still on time never sets it, since duration is recorded for every frame and
 * a slow first composition inside a generous budget is not jank.
 *
 * A perfectly smooth screen reports too. Zero is a result, and a tally that
 * spoke up only about trouble could not tell a fixed screen from an unvisited
 * one.
 *
 * ### Not here
 *
 * Where the frame callbacks come from is platform code. That the emitted
 * attribute names match what the dashboards query is
 * `DashboardQueryContractTest`.
 */
class JankTallyTest {

    @Test
    fun aShortVisitReportsNothing() {
        // The reason the threshold exists: one janky frame out of three is 33%,
        // and means only that the screen was barely on show. Letting that reach
        // a dashboard makes every panel built on the metric untrustworthy.
        val tally = JankTally(minFramesToReport = 120)
        repeat(3) { tally.record(isJank = true, frameDurationNanos = 20_000_000) }

        assertNull(tally.takeSummary())
    }

    @Test
    fun aDroppedVisitDoesNotLeakIntoTheNextOne() {
        // A visit below the threshold still has to clear its counters, or the
        // next screen inherits frames it never rendered and gets blamed for
        // jank that happened somewhere else.
        val tally = JankTally(minFramesToReport = 10)
        repeat(5) { tally.record(isJank = true, frameDurationNanos = 50_000_000) }
        assertNull(tally.takeSummary())

        repeat(10) { tally.record(isJank = false, frameDurationNanos = 8_000_000) }

        val summary = tally.takeSummary()
        assertEquals(10, summary?.frames)
        assertEquals(0, summary?.jankyFrames, "the dropped visit's jank must not carry over")
    }

    @Test
    fun countsAndPercentageDescribeTheVisit() {
        val tally = JankTally(minFramesToReport = 10)
        repeat(90) { tally.record(isJank = false, frameDurationNanos = 8_000_000) }
        repeat(10) { tally.record(isJank = true, frameDurationNanos = 40_000_000) }

        val summary = tally.takeSummary()
        assertEquals(100, summary?.frames)
        assertEquals(10, summary?.jankyFrames)
        assertEquals(10, summary?.jankPercent)
    }

    @Test
    fun theWorstFrameSurvivesAnOtherwiseHealthyScreen() {
        // The assertion that matters for ANR work. A screen that drops one
        // frame in two hundred looks fine as a percentage; if that frame took
        // 900ms, the screen has a stall in it and this is the only field that
        // says so.
        val tally = JankTally(minFramesToReport = 10)
        repeat(199) { tally.record(isJank = false, frameDurationNanos = 8_000_000) }
        tally.record(isJank = true, frameDurationNanos = 900_000_000)

        val summary = tally.takeSummary()
        assertEquals(1, summary?.jankPercent, "200 frames with one janky rounds to 1%")
        assertEquals(900, summary?.worstFrameMs)
    }

    @Test
    fun aSmoothFrameNeverSetsTheWorstFrame() {
        // `frameDurationUiNanos` is reported for every frame, janky or not. A
        // long-but-on-time frame (a slow first composition inside a long
        // vsync budget) must not be reported as the worst *janky* frame.
        val tally = JankTally(minFramesToReport = 1)
        tally.record(isJank = false, frameDurationNanos = 500_000_000)
        tally.record(isJank = true, frameDurationNanos = 20_000_000)

        assertEquals(20, tally.takeSummary()?.worstFrameMs)
    }

    @Test
    fun aPerfectlySmoothScreenStillReports() {
        // Zero jank is a result, not an absence of one — it is what proves a
        // fix held. Reporting only troubled screens would make the dashboard
        // unable to tell "smooth" from "nobody went there".
        val tally = JankTally(minFramesToReport = 10)
        repeat(120) { tally.record(isJank = false, frameDurationNanos = 8_000_000) }

        val summary = tally.takeSummary()
        assertEquals(120, summary?.frames)
        assertEquals(0, summary?.jankyFrames)
        assertEquals(0, summary?.jankPercent)
        assertEquals(0, summary?.worstFrameMs)
    }
}
