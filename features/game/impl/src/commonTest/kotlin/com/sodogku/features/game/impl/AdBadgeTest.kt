package com.sodogku.features.game.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which controls under the board wear an "Ad" badge.
 *
 * The badge is a promise about what a tap will do, so every case here is written
 * against what [GameViewModel] actually does with that tap rather than against
 * how the row looks. The expensive failure is the Pro one: `RealAdGate` grants a
 * Pro player the reward without showing anything, so a badge on their screen is
 * an advert for the thing they paid to remove.
 *
 * **Not covered here:** the badge's appearance, position and colour. That is one
 * `RewardBadge` in one corner of `BoardControl` with no branch in it, and the
 * emulator screenshots are the only honest check of it.
 */
class AdBadgeTest {

    private val playing = GameState(
        phase = GamePhase.Playing,
        sniffs = 2,
        treats = 2,
        livesRemaining = 3,
        refillTo = 3,
    )

    @Test
    fun anEmptyBoosterPromisesAnAd() {
        assertTrue(playing.copy(sniffs = 0).tapPlaysAd(Consumable.Sniff))
        assertTrue(playing.copy(treats = 0).tapPlaysAd(Consumable.Treat))
    }

    /**
     * A booster the player is holding is spent by tapping it. The prompt that
     * offers the ad only leads with the ad when there is nothing to use.
     */
    @Test
    fun aBoosterInHandDoesNot() {
        assertFalse(playing.tapPlaysAd(Consumable.Sniff))
        assertFalse(playing.tapPlaysAd(Consumable.Treat))
    }

    /**
     * The two boosters read their own holdings. Both counts live on the same
     * state object one field apart, and a swap is invisible until a player with
     * one of each taps the wrong button.
     */
    @Test
    fun eachBoosterReadsItsOwnCount() {
        val onlyTreats = playing.copy(sniffs = 0, treats = 5)

        assertTrue(onlyTreats.tapPlaysAd(Consumable.Sniff))
        assertFalse(onlyTreats.tapPlaysAd(Consumable.Treat))
        assertEquals(0, onlyTreats.held(Consumable.Sniff))
        assertEquals(5, onlyTreats.held(Consumable.Treat))
        assertEquals(3, onlyTreats.held(Consumable.Bone))
    }

    /** The bones button *is* the ad. There is no version of that tap that is not. */
    @Test
    fun theBonesRefillIsAlwaysAnAd() {
        assertTrue(playing.copy(livesRemaining = 1).tapPlaysAd(Consumable.Bone))
        assertTrue(playing.copy(livesRemaining = 0).tapPlaysAd(Consumable.Bone))
    }

    /**
     * Nothing to give, nothing to promise. The comparison is against
     * `boosters.refillTo` rather than the shipped three, so raising the key must
     * put the offer back rather than leave it greyed out with bones to hand out.
     */
    @Test
    fun fullBonesPromiseNothing() {
        assertFalse(playing.copy(livesRemaining = 3, refillTo = 3).tapPlaysAd(Consumable.Bone))
        assertTrue(playing.copy(livesRemaining = 3, refillTo = 5).tapPlaysAd(Consumable.Bone))
    }

    @Test
    fun aSpentDailyOffersNoRefill() {
        val recap = playing.copy(phase = GamePhase.Recap, livesRemaining = 0)

        assertFalse(recap.bonesRefillable)
        assertFalse(recap.tapPlaysAd(Consumable.Bone))
    }

    /**
     * **Pro is the first thing asked, and the answer is always no.** Checked
     * against every control in every state that would otherwise badge, because
     * this is the one wrong answer here that costs money rather than clarity.
     */
    @Test
    fun proNeverSeesAnAdBadge() {
        val pro = playing.copy(isPro = true, sniffs = 0, treats = 0, livesRemaining = 0)

        Consumable.entries.forEach { consumable ->
            assertFalse(pro.tapPlaysAd(consumable), "Pro was promised an ad for $consumable")
        }
    }

    @Test
    fun proIsStillProWhenEverythingElseSaysYes() {
        val everyoneElse = playing.copy(sniffs = 0, treats = 0, livesRemaining = 0)

        Consumable.entries.forEach { consumable ->
            assertTrue(everyoneElse.tapPlaysAd(consumable), "$consumable should badge for a free player")
            assertFalse(everyoneElse.copy(isPro = true).tapPlaysAd(consumable))
        }
    }

    /**
     * `ads.enabled` off is the same shape as Pro: the gate grants the reward for
     * free, so the tap is a grant and not an ad, and the button must not say
     * otherwise.
     */
    @Test
    fun adsSwitchedOffInConfigTakeEveryBadgeWithThem() {
        val noAds = playing.copy(adsEnabled = false, sniffs = 0, treats = 0, livesRemaining = 0)

        Consumable.entries.forEach { consumable ->
            assertFalse(noAds.tapPlaysAd(consumable), "$consumable badged with ads switched off")
        }
    }

    /**
     * `features.boosters` off hides the two booster controls entirely, and
     * `boosterTapped` refuses them — but it exempts bones, because three strikes
     * is a game rule rather than part of the economy. The refill button is still
     * on screen and still an ad.
     */
    @Test
    fun switchingTheEconomyOffLeavesTheBonesRefillAlone() {
        val noEconomy = playing.copy(boostersEnabled = false, sniffs = 0, treats = 0, livesRemaining = 1)

        assertFalse(noEconomy.tapPlaysAd(Consumable.Sniff))
        assertFalse(noEconomy.tapPlaysAd(Consumable.Treat))
        assertTrue(noEconomy.tapPlaysAd(Consumable.Bone))
    }

    /**
     * The booster controls are disabled off the playing phase, and a disabled
     * button promises nothing. Bones are the exception again: the lose sheet's
     * whole recovery is that refill, so it stays live on a board already lost.
     */
    @Test
    fun aBoardThatIsOverStopsPromisingBoosterAds() {
        val lost = playing.copy(phase = GamePhase.Lost, sniffs = 0, treats = 0, livesRemaining = 0)

        assertFalse(lost.tapPlaysAd(Consumable.Sniff))
        assertFalse(lost.tapPlaysAd(Consumable.Treat))
        assertTrue(lost.tapPlaysAd(Consumable.Bone))
    }

    /**
     * A board that has not been built yet carries the shipped defaults, and the
     * honest default for `ads.enabled` is the one the config ships — the badge
     * must not go missing on the first frame and appear a moment later.
     */
    @Test
    fun aFreshStateAssumesAdsAreLive() {
        assertTrue(GameState().adsEnabled)
    }
}
