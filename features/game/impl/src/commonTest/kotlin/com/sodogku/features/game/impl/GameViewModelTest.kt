package com.sodogku.features.game.impl

import com.sodogku.libraries.ads.AdGate
import com.sodogku.libraries.ads.AdPlacement
import com.sodogku.libraries.ads.RewardOutcome
import com.sodogku.libraries.billing.Entitlements
import com.sodogku.libraries.billing.PurchaseOutcome
import com.sodogku.libraries.billing.RestoreOutcome
import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.config.values.BoostersProSniffsPerAttempt
import com.sodogku.libraries.config.values.BoostersProTreatsPerAttempt
import com.sodogku.libraries.config.values.BoostersRefillTo
import com.sodogku.libraries.config.values.BoostersStartingSniffs
import com.sodogku.libraries.config.values.BoostersStartingTreats
import com.sodogku.libraries.config.values.BoostersTreatSchedule
import com.sodogku.libraries.config.values.TreatBand
import com.sodogku.libraries.config.values.asFallbackConfig
import com.sodogku.libraries.config.values.FeatureAchievements
import com.sodogku.libraries.config.values.FeatureBoosters
import com.sodogku.libraries.config.values.ProgressionSkipAfterFailedAttempts
import com.sodogku.libraries.config.values.ScoringBasePerPlacement
import com.sodogku.libraries.config.values.ScoringBoosterPenaltyRate
import com.sodogku.libraries.config.values.ScoringComboMax
import com.sodogku.libraries.config.values.ScoringComboStep
import com.sodogku.libraries.config.values.ScoringCompletionBase
import com.sodogku.libraries.config.values.ScoringDifficultyBonusRate
import com.sodogku.libraries.config.values.ScoringExcellentPraiseAt
import com.sodogku.libraries.config.values.ScoringGreatPraiseAt
import com.sodogku.libraries.config.values.ScoringLivesBonusRate
import com.sodogku.libraries.config.values.ScoringNicePraiseAt
import com.sodogku.libraries.config.values.ScoringPerfectPraiseAt
import com.sodogku.libraries.config.values.ScoringSpeedMaxMultiplier
import com.sodogku.libraries.config.values.ScoringSpeedWindowMs
import com.sodogku.libraries.config.values.ScoringThreePawFraction
import com.sodogku.libraries.config.values.ScoringTwoPawFraction
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.sodogku.libraries.levels.LevelDefinition
import com.sodogku.libraries.levels.LevelPacks
import com.sodogku.libraries.progress.LevelRecord
import com.sodogku.libraries.progress.LevelState
import com.sodogku.libraries.progress.ProgressRepository
import com.sodogku.libraries.progress.SkipRepository
import com.sodogku.libraries.progress.SkipResult
import com.sodogku.libraries.progress.daily.DailyOutcome
import com.sodogku.libraries.progress.daily.DailyRepository
import com.sodogku.libraries.progress.daily.DailyResult
import com.sodogku.libraries.progress.daily.DailyStatus
import com.sodogku.libraries.progress.daily.FreezeOffer
import com.sodogku.libraries.progress.daily.FreezeResult
import com.sodogku.libraries.progress.daily.RestoreOffer
import com.sodogku.libraries.progress.daily.RestoreResult
import com.sodogku.libraries.achievements.Achievement
import com.sodogku.libraries.achievements.AchievementId
import com.sodogku.libraries.achievements.AchievementState
import com.sodogku.libraries.achievements.AchievementsRepository
import com.sodogku.libraries.achievements.LevelResult
import com.sodogku.libraries.achievements.PlayMode
import com.sodogku.libraries.achievements.Stat
import com.sodogku.libraries.puzzle.autoMarkedCells
import com.sodogku.libraries.scoring.ScoringConfig
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import com.sodogku.libraries.sodogku.AppCache
import com.sodogku.libraries.sodogku.AppData
import com.sodogku.libraries.sodogku.ConsumableRefillTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import com.sodogku.libraries.progress.streak.StreakPrompt
import com.sodogku.libraries.progress.streak.StreakRepository
import com.sodogku.libraries.progress.streak.StreakSummary

class GameViewModelTest : CoroutineTest() {

    /**
     * The default test level. Deliberately past [StarterDogLevel]: the early
     * levels open with a dog already placed and its auto-marks spread, which
     * makes them a bad fixture for testing the loop itself.
     */
    private val level: LevelDefinition = assertNotNull(LevelPacks.campaign.byId(PlainLevel))

    @Test
    fun loadsTheRequestedLevelAndStartsPlaying() = runUnitTest {
        val vm = viewModel()

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(PlainLevel, vm.state.level?.id)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun aCorrectGuessPlacesADogAndScores() = runUnitTest {
        val vm = viewModel()

        vm.commit(cellFor(row = 0))

        assertEquals(1, vm.state.dogsPlaced)
        assertTrue(vm.state.score.total > 0)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun aCorrectGuessAutoMarksTheRowColumnRegionAndNeighbours() = runUnitTest {
        val vm = viewModel()
        val cell = cellFor(row = 0)

        vm.commit(cell)

        val board = level.board
        val row = board.rowOf(cell)
        val col = board.colOf(cell)
        val marks = vm.state.autoMarks
        assertTrue((0 until board.size).filter { it != col }.all { board.cellAt(row, it) in marks })
        assertTrue((0 until board.size).filter { it != row }.all { board.cellAt(it, col) in marks })
        assertTrue(board.neighborsOf(cell).all { it in marks })
    }

    // ---- R8: what the game knows vs what the player is shown. ---------------
    //
    // The setting is presentation only. Every test below asserts one half of
    // that sentence: the board stops drawing the cascade, and nothing that
    // reasons about the puzzle notices.

    @Test
    fun withAutoMarkOffNothingIsDrawnAndTheDeductionIsUntouched() = runUnitTest {
        val vm = viewModel(cache = puristCache())
        val cell = cellFor(row = 0)

        vm.commit(cell)

        // The exact set the difficulty engine rated this level against. Asserted
        // by equality rather than by "not empty", because the claim is that the
        // setting cannot move a baked difficulty — and the deduction the engine
        // walks is this function's answer.
        val ruledOut = level.board.autoMarkedCells(vm.state.placed)
        assertTrue(ruledOut.isNotEmpty(), "a placement that rules nothing out is not a fixture")
        assertEquals(ruledOut, vm.state.autoMarks, "the game stopped deducting, not just drawing")
        assertTrue(vm.state.visibleAutoMarks.isEmpty(), "squares were crossed off anyway")
    }

    @Test
    fun withAutoMarkOnTheDrawnSetIsTheDeduction() = runUnitTest {
        // The companion to the test above, and the reason it is not vacuous:
        // both sets are non-empty here, so `visibleAutoMarks` emptying there is
        // the setting and not an empty board.
        val vm = viewModel()

        vm.commit(cellFor(row = 0))

        assertTrue(vm.state.autoMarks.isNotEmpty())
        assertEquals(vm.state.autoMarks, vm.state.visibleAutoMarks)
    }

    @Test
    fun aBoardIsStillWinnableTheWayAPuristActuallyPlaysIt() = runUnitTest {
        // Played the way the setting asks for: place a dog, cross its
        // eliminations off by hand, carry on. `solve` alone would not exercise
        // this — the answer squares are never in the cascade, so a board can be
        // solved without ever touching one of these squares and the manual
        // marking path would go unchecked on the winning route.
        val vm = viewModel(cache = puristCache())
        vm.commit(cellFor(row = 0))
        val byHand = vm.state.autoMarks.filter { it !in vm.state.placedCells }.take(3)
        assertEquals(3, byHand.size, "the placement ruled out too little to mark")
        byHand.forEach { vm.note(it) }
        assertTrue(byHand.all { it in vm.state.manualMarks }, "the crosses were never made")

        (1 until level.size).forEach { row -> vm.commit(cellFor(row)) }

        assertEquals(GamePhase.Won, vm.state.phase)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining, "a clean solve cost a bone")
    }

    @Test
    fun theSettingSurvivesStartingTheBoardOver() = runUnitTest {
        // `startAttempt` builds a whole fresh `GameState`, so every display
        // setting has to be carried across by hand. The board that opens with
        // the ViewModel is covered by the settings flow landing a moment later;
        // a retry, the next level and a jump from the pane all happen long after
        // that flow has settled and `distinctUntilChanged` will not re-emit, so
        // a setting dropped here comes back on and nothing else notices.
        val vm = viewModel(cache = puristCache())

        vm.takeAction(GameAction.Retry)
        settle()
        vm.commit(cellFor(row = 0))

        assertTrue(vm.state.autoMarks.isNotEmpty(), "the fresh board deduced nothing")
        assertTrue(vm.state.visibleAutoMarks.isEmpty(), "starting over turned the crosses back on")
    }

    @Test
    fun aSniffGivesTheSameAdviceWhicheverWayTheSettingIsSet() = runUnitTest {
        // The failure this exists to catch is silent. If the hint reasoned from
        // what is *drawn* rather than from what is *true*, turning the setting
        // off would quietly buy worse advice — worse advice for asking for less
        // help, which is the one trade this feature must not make.
        val shown = viewModel()
        val hidden = viewModel(cache = puristCache())
        listOf(shown, hidden).forEach { vm ->
            vm.commit(cellFor(row = 0))
            vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
            vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        }

        assertTrue(shown.state.hintCells.isNotEmpty(), "the sniff showed nothing to compare")
        assertEquals(shown.state.hintCells, hidden.state.hintCells)
        assertEquals(shown.state.sniffs, hidden.state.sniffs, "one of them paid a different price")
    }

    @Test
    fun withAutoMarkOffATapOnARuledOutSquareWritesThePlayersOwnCross() = runUnitTest {
        // With the crosses drawn, a tap on one of these clears it. With them
        // hidden the square reads as empty, so a tap has to do what a tap on an
        // empty square does — otherwise a whole swathe of the board silently
        // refuses the only free gesture in the game.
        val vm = viewModel(cache = puristCache())
        vm.commit(cellFor(row = 0))
        val ruledOut = vm.state.autoMarks.first { it !in vm.state.placedCells }

        vm.note(ruledOut)

        assertTrue(ruledOut in vm.state.manualMarks, "the tap did nothing")
        assertTrue(ruledOut !in vm.state.clearedMarks, "it cleared a cross that was never drawn")
    }

    @Test
    fun flippingTheSettingMidBoardChangesWhatIsDrawnAndNothingElse() = runUnitTest {
        // Observed rather than read once when the board opens: the gear opens a
        // real screen, so a player flips this and comes straight back.
        val cache = InMemoryAppCache()
        val vm = viewModel(cache = cache)
        vm.commit(cellFor(row = 0))
        val deduced = vm.state.autoMarks
        assertTrue(deduced.isNotEmpty())
        assertEquals(deduced, vm.state.visibleAutoMarks, "nothing was crossed off to begin with")

        cache.set(cache.get().copy(autoMarkEnabled = false))
        settle()

        assertTrue(vm.state.visibleAutoMarks.isEmpty(), "the crosses stayed on screen")
        assertEquals(deduced, vm.state.autoMarks, "the deduction moved with the setting")

        cache.set(cache.get().copy(autoMarkEnabled = true))
        settle()

        assertEquals(deduced, vm.state.visibleAutoMarks, "the crosses did not come back")
    }

    @Test
    fun aDeliberatePlacementOnACrossedOffSquareStillCosts() = runUnitTest {
        // Reported from a device: "I tried to place a dog illegally and it
        // wouldn't let me fail, the double click did nothing." It did nothing,
        // silently, because `commit` returned early on any auto-marked square —
        // and a placed dog auto-marks its own row, column, region and
        // neighbours, which is exactly where an illegal placement lives.
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        val marked = vm.state.autoMarks.first { it !in vm.state.placedCells }
        val livesBefore = vm.state.livesRemaining

        vm.commit(marked)

        assertEquals(livesBefore - 1, vm.state.livesRemaining, "a deliberate illegal placement has to cost")
        assertTrue(marked in vm.state.wrongGuesses, "and leave the square red")
    }

    @Test
    fun aSingleTapOnACrossedOffSquareStillCostsNothing() = runUnitTest {
        // The companion. Only the *deliberate* second tap is allowed to spend a
        // bone; a stray single tap must stay free, or the board becomes a
        // minefield.
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        val marked = vm.state.autoMarks.first { it !in vm.state.placedCells }
        val livesBefore = vm.state.livesRemaining

        vm.note(marked)

        assertEquals(livesBefore, vm.state.livesRemaining)
        assertTrue(marked !in vm.state.wrongGuesses)
    }

    @Test
    fun aWrongGuessCostsALifeAndResetsTheCombo() = runUnitTest {
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        val earned = vm.state.score.total

        vm.commit(tappableWrongCell(vm))

        assertEquals(ScoringConfig.MAX_LIVES - 1, vm.state.livesRemaining)
        assertEquals(0, vm.state.score.combo)
        assertEquals(earned, vm.state.score.total, "a strike must not take back points")
    }

    @Test
    fun theStrikeNonceChangesSoASecondWrongGuessShakesToo() = runUnitTest {
        // Two different squares, because a square that already cost a bone is
        // now inert — see `aSecondCommitOnASquareThatAlreadyCostABoneCostsNothing`.
        // This used to re-commit the same one, which was the behaviour rather
        // than the intent: what the nonce is for is making a *consecutive*
        // strike re-fire the shake, and consecutive strikes land on different
        // squares.
        val vm = viewModel()

        vm.commit(wrongCellIn(row = 0))
        val first = vm.state.strikeNonce
        vm.commit(wrongCellIn(row = 1))

        assertTrue(vm.state.strikeNonce != first, "a second wrong guess has to re-fire the shake")
    }

    @Test
    fun threeStrikesEndTheAttempt() = runUnitTest {
        val vm = viewModel()

        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }

        assertEquals(GamePhase.Lost, vm.state.phase)
        assertEquals(0, vm.state.livesRemaining)
    }

    @Test
    fun aWrongGuessSpendsAPersistedBone() = runUnitTest {
        // The count on disk is the count, so a strike has to reach it. Without
        // this write nothing else in R15 holds: a relaunch, a second board or a
        // process death would all hand the bone straight back.
        val cache = InMemoryAppCache()
        val vm = viewModel(cache = cache)
        assertEquals(ConsumableRefillTo, cache.get().bones)

        vm.commit(wrongCellIn(row = 0))

        assertEquals(ConsumableRefillTo - 1, vm.state.livesRemaining)
        assertEquals(ConsumableRefillTo - 1, cache.get().bones)
    }

    @Test
    fun bonesDoNotComeBackByStartingALevel() = runUnitTest {
        // R15's headline. `startAttempt` set `livesRemaining` to `MAX_LIVES`
        // every time it ran, so the next level, a retry, a jump from the pane
        // and the daily each handed out a free set of three.
        val cache = InMemoryAppCache()
        val vm = viewModel(cache = cache)
        repeat(2) { vm.commit(wrongCellIn(row = it)) }
        val left = vm.state.livesRemaining
        assertEquals(ConsumableRefillTo - 2, left, "the fixture has to actually cost bones")

        // Backwards, because the frontier is wherever this board is and a jump
        // forward would be refused before `startAttempt` ever ran.
        vm.takeAction(GameAction.GoToLevel(PlainLevel - 1))

        assertEquals(PlainLevel - 1, vm.state.level?.id, "and the level has to actually change")
        assertEquals(left, vm.state.livesRemaining, "a fresh board is not a refill")
        assertEquals(left, cache.get().bones)
    }

    @Test
    fun aFreshBoardOpensWithWhateverIsOnDisk() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(bones = 1))

        val vm = viewModel(cache = cache)

        assertEquals(1, vm.state.livesRemaining)
    }

    @Test
    fun aBoardOpenedAtZeroMeetsTheOfferRatherThanTheNextWrongGuess() = runUnitTest {
        // Being at zero is a wall, and a wall the player only discovers by
        // losing a board to it is indistinguishable from a bug. The prompt it
        // opens is the same one the bone button shows, whose refill cannot fail
        // closed.
        val cache = InMemoryAppCache()
        cache.set(AppData(bones = 0))

        val vm = viewModel(cache = cache)

        assertEquals(GamePhase.Playing, vm.state.phase, "the board is still markable")
        assertEquals(Consumable.Bone, vm.state.boosterPrompt)
    }

    @Test
    fun aStrikeAtZeroDoesNotDriveTheCountNegative() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(bones = 0))
        val vm = viewModel(cache = cache)
        vm.takeAction(GameAction.DismissBoosterPrompt)

        vm.commit(wrongCellIn(row = 0))

        assertEquals(GamePhase.Lost, vm.state.phase)
        assertEquals(0, vm.state.livesRemaining)
        assertEquals(0, cache.get().bones, "a negative holding would refill up to itself")
    }

    @Test
    fun aBoardFollowsTheCountAnotherBoardSpent() = runUnitTest {
        // The daily opens on its own route, so the campaign board sits on the
        // backstack while it is played. Two live ViewModels, one economy: the
        // one underneath has to follow, or its next strike writes a stale count
        // back over what the daily spent.
        val cache = InMemoryAppCache()
        val campaign = viewModel(cache = cache)
        val onTop = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel), cache = cache)

        loseCurrent(onTop)

        assertEquals(0, onTop.state.livesRemaining)
        assertEquals(0, campaign.state.livesRemaining, "the board underneath has to follow")
    }

    @Test
    fun clearingALevelHandsTheScreenToAWaitingCeremony() = runUnitTest {
        val vm = viewModel(streak = SilentStreak(StreakPrompt.Intention))
        val events = eventsOf(vm)

        solveCurrent(vm)

        assertTrue(
            events.any { it is GameEvent.OpenStreakIntention },
            "the streak was owed a moment and never got it: $events",
        )
    }

    @Test
    fun aMilestoneCarriesTheRunItIsCelebrating() = runUnitTest {
        val vm = viewModel(streak = SilentStreak(StreakPrompt.Celebrate(streak = SEVEN_DAYS)))
        val events = eventsOf(vm)

        solveCurrent(vm)

        assertEquals(
            SEVEN_DAYS,
            events.filterIsInstance<GameEvent.OpenStreak>().single().streak,
            "the celebration was opened without the number it is about",
        )
    }

    @Test
    fun aQuietStreakNeverInterruptsAWin() = runUnitTest {
        // The overwhelmingly common case, and the one that would be most
        // annoying to get wrong: a full-screen page after every clear.
        val vm = viewModel(streak = SilentStreak(StreakPrompt.None))
        val events = eventsOf(vm)

        solveCurrent(vm)

        assertTrue(events.any { it == GameEvent.Won }, "the level was never won, so this proves nothing")
        assertTrue(
            events.none { it is GameEvent.OpenStreak || it is GameEvent.OpenStreakIntention },
            "a streak with nothing to say still took the screen: $events",
        )
    }

    @Test
    fun clearingTheDailyDoesNotOpenTheStreakOnTopOfItsOwnRecap() = runUnitTest {
        // The daily is what the streak is about, so celebrating it over the
        // daily's own recap would be two pages about one board.
        val vm = viewModel(
            isDaily = true,
            daily = FakeDaily(levelId = DailyLevel),
            streak = SilentStreak(StreakPrompt.Celebrate(streak = SEVEN_DAYS)),
        )
        val events = eventsOf(vm)

        solveCurrent(vm)

        assertTrue(events.any { it == GameEvent.Won }, "the daily was never won, so this proves nothing")
        assertTrue(
            events.none { it is GameEvent.OpenStreak },
            "the daily opened a streak celebration over its own recap: $events",
        )
    }

    @Test
    fun aSniffProposesCrossesRatherThanApplyingThem() = runUnitTest {
        // The charge buys a proposal. Applying it without asking would take the
        // decision away, and the player would have no way to see what changed.
        val vm = viewModel()

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        settle()

        assertTrue(vm.state.hintCells.isNotEmpty(), "the sniff proposed nothing, so this proves nothing")
        assertTrue(
            vm.state.hintCells.none { it in vm.state.manualMarks },
            "the sniff crossed squares off without being asked",
        )
    }

    @Test
    fun keepingTheProposalTurnsItIntoThePlayersOwnCrosses() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        settle()
        val proposed = vm.state.hintCells
        assertTrue(proposed.isNotEmpty(), "nothing was proposed")

        vm.takeAction(GameAction.ApplyHint)
        settle()

        assertTrue(vm.state.manualMarks.containsAll(proposed), "the accepted squares were not crossed off")
        assertTrue(vm.state.hintCells.isEmpty(), "the proposal stayed on screen after being accepted")
    }

    @Test
    fun leavingTheProposalMarksNothing() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        settle()
        val marksBefore = vm.state.manualMarks
        assertTrue(vm.state.hintCells.isNotEmpty(), "nothing was proposed")

        vm.takeAction(GameAction.DiscardHint)
        settle()

        assertEquals(marksBefore, vm.state.manualMarks, "declining the hint still crossed squares off")
        assertTrue(vm.state.hintCells.isEmpty())
    }

    @Test
    fun anAcceptedProposalSurvivesBeingForceQuit() = runUnitTest {
        // `applyHint` goes through `updateBoard` for this reason. Losing help
        // the player spent a sniff on and then explicitly accepted would be
        // worse than losing an unaccepted proposal.
        val cache = InMemoryAppCache()
        val first = viewModel(cache = cache)
        first.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        first.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        settle()
        val proposed = first.state.hintCells
        assertTrue(proposed.isNotEmpty(), "nothing was proposed")
        first.takeAction(GameAction.ApplyHint)
        settle()

        val resumed = viewModel(cache = cache)

        assertTrue(
            resumed.state.manualMarks.containsAll(proposed),
            "the crosses the player accepted were gone after a relaunch",
        )
    }

    @Test
    fun tappingADogThatIsAlreadyThereShakesInsteadOfDoingNothing() = runUnitTest {
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        val placedCell = vm.state.placedCells.first()
        val before = vm.state.shakeNonce

        vm.note(placedCell)

        assertTrue(vm.state.shakeNonce > before, "the tap was ignored silently")
        assertEquals(placedCell, vm.state.shakeCell)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining, "a refused tap cost a bone")
        assertTrue(placedCell !in vm.state.manualMarks, "a refused tap wrote a cross")
    }

    @Test
    fun aRefusedTapStopsExplainingTheLastRealMistake() = runUnitTest {
        // `brokenRule` reads `strikeCell`. Leaving it set through a refused tap
        // would keep a rule chip outlined next to a tap that broke no rule.
        val vm = viewModel()
        vm.commit(wrongCellIn(row = 0))
        assertTrue(vm.state.strikeCell != null, "the wrong guess was never diagnosed")
        vm.commit(cellFor(row = 1))
        val placedCell = vm.state.placedCells.first()

        vm.note(placedCell)

        assertEquals(null, vm.state.strikeCell, "a refused tap left a stale rule explanation up")
    }

    @Test
    fun aRealStrikeStillShakesAndStillDiagnoses() = runUnitTest {
        // The other half. Routing every refusal through `nudge` would be easy
        // to over-apply until a genuine wrong guess stopped explaining itself.
        val vm = viewModel()
        val before = vm.state.shakeNonce

        vm.commit(wrongCellIn(row = 0))

        assertTrue(vm.state.shakeNonce > before, "a wrong guess did not shake")
        assertEquals(wrongCellIn(row = 0), vm.state.strikeCell, "a wrong guess did not diagnose")
    }

    @Test
    fun aSecondCommitOnASquareThatAlreadyCostABoneCostsNothing() = runUnitTest {
        // The mirror of the bug that started this review. That one was `commit`
        // refusing too much; this is `commit` refusing too little, in the one
        // place the refusal was load-bearing.
        //
        // The first tap on a red square did nothing, which is exactly what makes
        // a player tap again, and the second tap reached `commit` and spent
        // another bone on a square they had already been told was wrong.
        val vm = viewModel()
        val wrong = wrongCellIn(row = 0)
        vm.commit(wrong)
        val afterFirst = vm.state.livesRemaining
        assertTrue(afterFirst < ConsumableRefillTo, "the first wrong guess did not cost a bone")

        vm.commit(wrong)

        assertEquals(afterFirst, vm.state.livesRemaining, "committing a red square again cost another bone")
        assertEquals(GamePhase.Playing, vm.state.phase)
    }

    @Test
    fun aRedSquareIsInertToASingleTapToo() = runUnitTest {
        // The other half. Guarding only the commit would leave the single tap
        // writing a manual cross over a square that already carries a red one.
        val vm = viewModel()
        val wrong = wrongCellIn(row = 0)
        vm.commit(wrong)
        // The commit is itself two taps, and the first of them already wrote a
        // manual cross, so the interesting assertion is that nothing *changes*
        // rather than that the set is empty.
        val before = vm.state.manualMarks

        vm.note(wrong)

        assertEquals(before, vm.state.manualMarks, "a tap on a red square changed the marks")
    }

    @Test
    fun onMarkedSaysWhetherTheSquareWasCrossedOffBeforeThePlayerStarted() = recordingEvents { events ->
        runUnitTest {
            // `on_marked` asks whether the crosses are reading as "ruled out".
            // It was read at the commit site, which runs on the *second* tap,
            // so it reported what the first tap had just done: true on a plain
            // empty square, false on a crossed-off one. Exactly inverted, and
            // pinned at nearly 100% either way.
            val vm = viewModel()

            vm.commit(wrongCellIn(row = 0))

            assertEquals(
                false,
                events.single("game.commit")["on_marked"],
                "a plain empty square was reported as already crossed off",
            )
        }
    }

    @Test
    fun onMarkedIsTrueWhenThePlayerCommitsOnASquareTheBoardCrossedOff() = recordingEvents { events ->
        runUnitTest {
            // The companion, and the one that makes the assertion above mean
            // something: a test that only ever saw `false` would pass against a
            // hardcoded `false`.
            val vm = viewModel()
            vm.commit(cellFor(row = 0))
            val autoMarked = vm.state.visibleAutoMarks.first { it !in vm.state.placedCells }

            vm.commit(autoMarked)

            assertEquals(
                true,
                events.attributesOf("game.commit").last()["on_marked"],
                "a square the board had crossed off was reported as clean",
            )
        }
    }

    @Test
    fun theFirstDailyVisitExplainsItselfAndTheSecondDoesNot() = runUnitTest {
        // The explainer answers the question players actually ask about the
        // daily: whether today's board costs them anything in the campaign. It
        // is worth showing once and actively annoying to show twice.
        val cache = InMemoryAppCache()

        val first = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel), cache = cache)
        assertTrue(first.state.showDailyIntro, "a player who has never seen the daily was told nothing")
        first.takeAction(GameAction.DailyIntroDismissed)
        assertFalse(first.state.showDailyIntro, "closing it left it open")

        val second = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel), cache = cache)
        assertFalse(second.state.showDailyIntro, "the explainer came back on the next visit")
    }

    @Test
    fun theCampaignNeverExplainsTheDaily() = runUnitTest {
        // The other half. A flag that opened the dialog on every board would
        // pass the test above on its first two assertions.
        val vm = viewModel(cache = InMemoryAppCache())

        assertFalse(vm.state.showDailyIntro, "a campaign level explained the daily")
    }

    @Test
    fun theExplainerSurvivesAFailedWrite() = runUnitTest {
        // Persisting is the only thing dismissal does, so a cache that refuses
        // must still close the dialog. Showing it forever because a write failed
        // would turn a storage hiccup into an unplayable daily.
        val vm = viewModel(
            isDaily = true,
            daily = FakeDaily(levelId = DailyLevel),
            cache = ThrowingAppCache(),
        )
        assertTrue(vm.state.showDailyIntro)

        vm.takeAction(GameAction.DailyIntroDismissed)

        assertFalse(vm.state.showDailyIntro, "a failed write left the player stuck behind the dialog")
    }

    @Test
    fun aDailyAndACampaignStrikeSpendTheSamePool() = runUnitTest {
        val cache = InMemoryAppCache()
        val campaign = viewModel(cache = cache)
        campaign.commit(wrongCellIn(row = 0))

        val today = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel), cache = cache)

        assertEquals(
            ConsumableRefillTo - 1,
            today.state.livesRemaining,
            "the daily used to open with three of its own",
        )
        val board = assertNotNull(today.state.level)
        val wrongCol = (0 until board.size).first { it != board.solution[0] }
        today.commit(board.board.cellAt(0, wrongCol))

        assertEquals(ConsumableRefillTo - 2, cache.get().bones)
    }

    @Test
    fun theCompletionBonusIsPricedOnThisAttemptRatherThanTheStash() = runUnitTest {
        // With one global count, `MAX_LIVES - livesRemaining` stops being "how
        // cleanly did this go". A player who refills mid-board would finish
        // reading as a clean sheet, worth a bigger completion bonus and a badge
        // they did not earn.
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)
        val level = assertNotNull(vm.state.level)
        vm.commit(tappableWrongCell(vm))
        vm.takeAction(GameAction.RefillBones)
        assertEquals(ConsumableRefillTo, vm.state.livesRemaining, "the refill has to land")

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }

        assertEquals(GamePhase.Won, vm.state.phase)
        assertEquals(1, badges.recorded.single().strikes, "the wrong guess still happened")
        assertEquals(1, vm.state.strikesThisAttempt)
        assertEquals(ConsumableRefillTo - 1, vm.state.bonesUnspent, "and the share card says so")
    }

    @Test
    fun aResumedAttemptRemembersTheStrikesItTook() = runUnitTest {
        // The snapshot used to carry `livesRemaining`, which now lives on disk
        // as a global. Without a per-attempt count in its place, a board
        // finished after a relaunch scores as a clean sheet.
        val cache = InMemoryAppCache()
        val first = viewModel(cache = cache)
        first.commit(wrongCellIn(row = 0))
        assertEquals(1, first.state.strikesThisAttempt)

        val resumed = viewModel(cache = cache)

        assertEquals(1, resumed.state.strikesThisAttempt)
        assertEquals(ConsumableRefillTo - 1, resumed.state.bonesUnspent)
    }

    @Test
    fun placingEveryDogWinsAndAwardsPaws() = runUnitTest {
        val vm = viewModel()

        solve(vm)

        assertEquals(GamePhase.Won, vm.state.phase)
        assertTrue(vm.state.paws >= 1, "finishing at all is worth a paw")
        assertTrue(vm.state.score.total > 0)
    }

    @Test
    fun tappingAnAutoMarkedCellDoesNothing() = runUnitTest {
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        val marked = vm.state.autoMarks.first()

        vm.takeAction(GameAction.CellTapped(marked))

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(1, vm.state.dogsPlaced)
    }

    @Test
    fun aSingleTapOnlyEverWritesTheNote() = runUnitTest {
        // The safety property of the whole interaction model: one tap can never
        // cost a life, however wrong the cell is.
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.note(cell)

        assertTrue(cell in vm.state.manualMarks)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(0, vm.state.dogsPlaced)
    }

    @Test
    fun tappingANotedCellAgainErasesIt() = runUnitTest {
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)
        vm.note(cell)

        vm.note(cell)

        assertTrue(cell !in vm.state.manualMarks)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun twoTapsOutsideTheWindowAreTwoNotesRatherThanACommit() = runUnitTest {
        // The failure this guards: a slow tap-tap on a wrong cell costing a life
        // the player never meant to spend.
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.takeAction(GameAction.CellTapped(cell))
        clock += LateGap
        vm.takeAction(GameAction.CellTapped(cell))

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertTrue(cell !in vm.state.manualMarks, "the second tap erased the note")
    }

    @Test
    fun tapsOnTwoDifferentCellsNeverCommit() = runUnitTest {
        val vm = viewModel()

        vm.takeAction(GameAction.CellTapped(wrongCellIn(row = 0)))
        vm.takeAction(GameAction.CellTapped(wrongCellIn(row = 1)))

        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertEquals(2, vm.state.manualMarks.size)
    }

    @Test
    fun aWrongGuessLeavesTheCellMarked() = runUnitTest {
        // The player just proved no dog goes there. Clearing it would make the
        // strike cost information as well as a life.
        val vm = viewModel()
        val cell = wrongCellIn(row = 0)

        vm.commit(cell)

        assertTrue(cell in vm.state.manualMarks)
    }

    @Test
    fun theFirstTapOfABoosterExplainsItRatherThanSpendingIt() = runUnitTest {
        // Spending a consumable cannot be undone, so an unfamiliar button has to
        // say what it costs before it costs anything.
        val vm = viewModel()
        val before = vm.state.sniffs

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        assertEquals(Consumable.Sniff, vm.state.boosterPrompt)
        assertEquals(before, vm.state.sniffs, "the explainer must not spend anything")
    }

    @Test
    fun aLaterTapSpendsWithoutExplaining() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        val after = vm.state.sniffs

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        assertEquals(null, vm.state.boosterPrompt)
        assertEquals(after - 1, vm.state.sniffs)
    }

    @Test
    fun anEmptyBoosterAlwaysOffersTheRefill() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        repeat(vm.state.sniffs) { vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff)) }
        assertEquals(0, vm.state.sniffs)

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        assertEquals(Consumable.Sniff, vm.state.boosterPrompt)
    }

    @Test
    fun aSniffRulesSquaresOutButNeverPlacesADog() = runUnitTest {
        // The whole point of the hint: it shows where a dog cannot go. One that
        // hands over the answer ends the puzzle.
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))

        assertEquals(0, vm.state.dogsPlaced)
        assertTrue(vm.state.hintCells.isNotEmpty())
        assertTrue(
            vm.state.hintCells.none { it in level.solution.cells().toSet() },
            "a hint must never rule out a square the answer occupies",
        )
    }

    @Test
    fun aSniffWithNothingToShowKeepsItsCharge() = runUnitTest {
        // Spending a booster for no visible effect is worse than refusing it.
        // On a nearly-solved board deduction has nothing left to rule out.
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
        val before = vm.state.sniffs

        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))

        assertEquals(before, vm.state.sniffs)
        assertTrue(vm.state.hintCells.isEmpty())
    }

    @Test
    fun aTreatPlacesACorrectDogForFree() = runUnitTest {
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Treat))
        val before = vm.state.treats

        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Treat))

        assertEquals(before - 1, vm.state.treats)
        assertEquals(1, vm.state.dogsPlaced)
        assertTrue(vm.state.placedCells.all { it in level.solution.cells().toSet() })
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining, "a treat risks no bone")
    }

    @Test
    fun aRefillToppedUpByAnAdNeverReducesAHolding() = runUnitTest {
        // Someone who earned five treats from level rewards and then watches an
        // ad must not be punished back down to three.
        val cache = InMemoryAppCache()
        cache.set(AppData(treats = 5))
        val vm = viewModel(cache = cache)
        assertEquals(5, vm.state.treats)

        vm.takeAction(GameAction.BoosterRefillRequested(Consumable.Treat))

        assertEquals(5, vm.state.treats)
    }

    @Test
    fun aRefillTopsAnEmptyBoosterBackToThree() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0))
        val vm = viewModel(cache = cache)

        vm.takeAction(GameAction.BoosterRefillRequested(Consumable.Sniff))

        assertEquals(ConsumableRefillTo, vm.state.sniffs)
        assertEquals(ConsumableRefillTo, cache.get().sniffs)
    }

    @Test
    fun dismissingTheRefillAdLeavesTheHoldingAlone() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0))
        val vm = viewModel(adGate = FixedAdGate(RewardOutcome.Dismissed), cache = cache)

        vm.takeAction(GameAction.BoosterRefillRequested(Consumable.Sniff))

        assertEquals(0, vm.state.sniffs)
        assertEquals(null, vm.state.boosterPrompt)
    }

    @Test
    fun theReviveRestoresEveryBoneAndKeepsTheBoard() = runUnitTest {
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        repeat(ScoringConfig.MAX_LIVES) {
            vm.commit(tappableWrongCell(vm))
        }
        assertEquals(GamePhase.Lost, vm.state.phase)

        vm.takeAction(GameAction.RefillBones)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(
            ConsumableRefillTo,
            vm.state.livesRemaining,
            "one bone would put the player straight back on this sheet",
        )
        assertEquals(1, vm.state.dogsPlaced, "the board the player earned must survive")
    }

    @Test
    fun aPlayerAtZeroWithNoNetworkIsNeverStuck() = runUnitTest {
        // The load-bearing rule of the whole ad layer, and since bones went
        // global it is the only thing standing between a player at zero and a
        // wall across the entire game. Only a deliberate dismissal withholds;
        // no fill, no route to the network and an SDK that threw all pay.
        listOf(
            RewardOutcome.NoFill,
            RewardOutcome.Offline,
            RewardOutcome.Failed("boom"),
        ).forEach { outcome ->
            val cache = InMemoryAppCache()
            cache.set(AppData(bones = 0))
            val vm = viewModel(adGate = FixedAdGate(outcome), cache = cache)
            assertEquals(0, vm.state.livesRemaining, "the fixture has to open at zero")

            vm.takeAction(GameAction.RefillBones)

            assertEquals(
                ConsumableRefillTo,
                vm.state.livesRemaining,
                "outcome $outcome left the player with nothing to spend",
            )
            assertEquals(
                ConsumableRefillTo,
                cache.get().bones,
                "and the refill has to reach disk, or the next board opens at zero again",
            )
        }
    }

    @Test
    fun dismissingTheAdWithholdsTheRevive() = runUnitTest {
        val vm = viewModel(adGate = FixedAdGate(RewardOutcome.Dismissed))
        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }

        vm.takeAction(GameAction.RefillBones)

        assertEquals(GamePhase.Lost, vm.state.phase)
        assertEquals(0, vm.state.livesRemaining)
    }

    @Test
    fun proSkipsTheAdEntirely() = runUnitTest {
        val gate = FixedAdGate(RewardOutcome.Dismissed)
        val vm = viewModel(adGate = gate, entitlements = ProEntitlements())
        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }

        vm.takeAction(GameAction.RefillBones)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(0, gate.rewardedShown, "Pro must not be asked to watch anything")
    }

    @Test
    fun retryClearsTheBoardAndDoesNotHandBackABone() = runUnitTest {
        // Half of R15 in one assertion. Start over used to run through
        // `startAttempt`, which reset the count to three, so the cheapest refill
        // in the game was the button that costs nothing.
        val vm = viewModel()
        vm.commit(cellFor(row = 0))
        repeat(ScoringConfig.MAX_LIVES) {
            vm.commit(tappableWrongCell(vm))
        }

        vm.takeAction(GameAction.Retry)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(0, vm.state.livesRemaining, "a retry is not a refill")
        assertEquals(0, vm.state.score.total)
        assertEquals(0, vm.state.dogsPlaced)
    }

    @Test
    fun earlyLevelsOpenWithOneDogAlreadyPlaced() = runUnitTest {
        // A teaching aid more than a leg-up: the free dog fires the auto-mark
        // cascade straight away, so the rules are visible before anyone has to
        // reason about them.
        val early = assertNotNull(LevelPacks.campaign.byId(StarterDogLevel))
        // Taught, explicitly. A default cache is a fresh install, and since R3 a
        // fresh install opening level 1 gets the rehearsal board instead — which
        // has a starter dog of its own and would pass this test for the wrong
        // board.
        val vm = viewModel(levelId = StarterDogLevel, cache = taughtCache())

        assertEquals(1, vm.state.dogsPlaced)
        assertTrue(vm.state.starterDogCell in early.solution.cells().toSet())
        assertEquals(0, vm.state.score.total, "a gift must not inflate an early best score")
        assertTrue(vm.state.autoMarks.isNotEmpty())
    }

    @Test
    fun laterLevelsOpenEmpty() = runUnitTest {
        val vm = viewModel(levelId = PlainLevel)

        assertEquals(0, vm.state.dogsPlaced)
        assertEquals(null, vm.state.starterDogCell)
    }

    @Test
    fun theLastBoneWarningFiresOnceOnTheEdgeIntoOneLife() = runUnitTest {
        // On the edge, not whenever one life happens to be showing. A warning
        // that reappears on every redraw stops being read.
        val vm = viewModel()
        assertEquals(null, vm.state.warning)

        vm.commit(tappableWrongCell(vm))
        assertEquals(null, vm.state.warning, "two lives left is not a warning")

        vm.commit(tappableWrongCell(vm))
        assertEquals(GameWarning.LastBone, vm.state.warning)

        vm.takeAction(GameAction.DismissWarning)
        assertEquals(null, vm.state.warning)
    }

    @Test
    fun refillingBonesFromAnAdRestoresEveryLife() = runUnitTest {
        val vm = viewModel()
        repeat(ScoringConfig.MAX_LIVES) { vm.commit(tappableWrongCell(vm)) }
        assertEquals(GamePhase.Lost, vm.state.phase)

        vm.takeAction(GameAction.RefillBones)

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(
            ScoringConfig.MAX_LIVES,
            vm.state.livesRemaining,
            "a single bone would put the player straight back here",
        )
    }

    @Test
    fun guessesDoNothingOnceTheAttemptIsOver() = runUnitTest {
        val vm = viewModel()
        solve(vm)

        vm.commit(wrongCellIn(row = 0))

        assertEquals(GamePhase.Won, vm.state.phase)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun openingALevelRecordsTheAttempt() = runUnitTest {
        // Counted when the level opens, not when it is cleared: an attempt the
        // player walked away from still happened.
        val progress = InMemoryProgress()

        viewModel(progress = progress)

        assertEquals(1, progress.record(PlainLevel).attempts)
    }

    @Test
    fun everyRestartCountsAsAnotherAttempt() = runUnitTest {
        val progress = InMemoryProgress()
        val vm = viewModel(progress = progress)

        vm.takeAction(GameAction.Retry)

        assertEquals(2, progress.record(PlainLevel).attempts)
    }

    @Test
    fun aWinRecordsTheScorePawsAndTime() = runUnitTest {
        val progress = InMemoryProgress()
        val vm = viewModel(progress = progress)

        solve(vm)

        val record = progress.record(PlainLevel)
        assertEquals(LevelState.Completed, record.state)
        assertEquals(vm.state.score.total, record.bestScore)
        assertEquals(vm.state.paws, record.bestPaws)
        assertEquals(vm.state.elapsedMs, record.bestTimeMs)
        assertTrue(record.bestTimeMs > 0, "a clear has to carry a duration to beat later")
    }

    @Test
    fun aWinOpensTheNextLevel() = runUnitTest {
        val vm = viewModel()

        solve(vm)

        assertEquals(PlainLevel + 1, vm.state.unlockedThrough)
    }

    @Test
    fun theDrawerUnlocksFromProgressAlone() = runUnitTest {
        // Progress is the only thing that says how far the player got. The
        // AppData stopgap this replaced is gone, and a second source would drift.
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 10, paws = 1, timeMs = 100)

        val vm = viewModel(levelId = StarterDogLevel, progress = progress)

        assertEquals(StarterDogLevel + 1, vm.state.unlockedThrough)
    }

    @Test
    fun unlockedThroughStopsAtTheEndOfThePack() = runUnitTest {
        // Clearing the last level unlocks `id + 1` in the repository, which is
        // one past everything that shipped.
        val lastLevel = LevelPacks.campaign.size
        val progress = InMemoryProgress()
        progress.onCompleted(lastLevel, score = 1, paws = 3, timeMs = 1)
        assertEquals(lastLevel + 1, progress.unlockedThrough(), "the fake has to reproduce the bug")

        val vm = viewModel(levelId = lastLevel, progress = progress)

        assertEquals(lastLevel, vm.state.unlockedThrough)
    }

    @Test
    fun openingTheDrawerLoadsEveryLevelRecord() = runUnitTest {
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 900, paws = 3, timeMs = 4_000)
        val vm = viewModel(progress = progress)
        assertTrue(vm.state.records.isEmpty(), "records cost a query, so nothing loads them early")

        vm.takeAction(GameAction.LevelsOpened)

        assertEquals(3, vm.state.records[StarterDogLevel]?.bestPaws)
        assertEquals(LevelState.Completed, vm.state.records[StarterDogLevel]?.state)
        assertEquals(null, vm.state.records[300], "an untouched level has no record at all")
    }

    @Test
    fun theDrawerOpensWithItsRecordsAndNotBefore() = runUnitTest {
        // The flag and the rows it draws move in one update. Held apart — the
        // flag in the screen's `remember`, the records here — the pane could show
        // a full list of locked levels for however long the query took.
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 900, paws = 3, timeMs = 4_000)
        val vm = viewModel(progress = progress)
        assertFalse(vm.state.drawerOpen)

        vm.takeAction(GameAction.LevelsOpened)

        assertTrue(vm.state.drawerOpen)
        assertTrue(vm.state.records.isNotEmpty(), "open with nothing to show is the bug")

        vm.takeAction(GameAction.LevelsClosed)
        assertFalse(vm.state.drawerOpen)
    }

    @Test
    fun aSettingChangedElsewhereReachesTheBoardWithoutARelaunch() = runUnitTest {
        // The gear opens a real settings screen, so a player flips a switch and
        // comes straight back. Reading AppData once at load meant the switch
        // moved and the board did not: measured on a device, zero pixels changed
        // until the next launch.
        val cache = InMemoryAppCache()
        val vm = viewModel(cache = cache)
        assertFalse(vm.state.colorblind)
        assertFalse(vm.state.reduceAnimations)

        cache.update { it.copy(colorblindMode = true, reduceAnimations = true, hapticsEnabled = false) }

        assertTrue(vm.state.colorblind, "the board has to pick the change up")
        assertTrue(vm.state.reduceAnimations)
        assertFalse(vm.state.haptics)
    }

    @Test
    fun theBoardDoesNotEchoItsOwnConsumableWrites() = runUnitTest {
        // The counts are deliberately not observed. This ViewModel is their
        // writer, and feeding its own writes back in would fight the spend it
        // just made — so a spend has to stick.
        val cache = InMemoryAppCache()
        val vm = viewModel(cache = cache)
        val before = vm.state.treats

        vm.takeAction(GameAction.BoosterTapped(Consumable.Treat))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Treat))

        assertEquals(before - 1, vm.state.treats)
        assertEquals(before - 1, cache.get().treats, "and reach disk")
    }

    @Test
    fun leavingALevelAndComingBackKeepsIt() = runUnitTest {
        // The half a process-death test misses: switching boards inside one
        // ViewModel never went through `load`, so the snapshot was written and
        // then never read. The player's board came back after a force-quit and
        // not after a trip through the level pane, which is the more common one.
        // PlainLevel, because `cellFor` is computed against the shared test
        // level — on any other board its cells are wrong and the commit becomes
        // a strike, which is how the first version of this test passed against
        // the bug it was written for.
        val progress = InMemoryProgress()
        progress.onCompleted(PlainLevel, score = 1, paws = 1, timeMs = 1)
        val vm = viewModel(levelId = PlainLevel, progress = progress)
        vm.commit(cellFor(row = 1))
        val placed = vm.state.placedCells
        assertEquals(1, placed.size, "one placement, and it has to be a placement not a strike")

        vm.takeAction(GameAction.GoToLevel(PlainLevel + 1))
        assertEquals(PlainLevel + 1, vm.state.level?.id, "the fixture has to actually move")
        assertTrue(vm.state.placedCells.isEmpty(), "and the new board starts empty")

        vm.takeAction(GameAction.GoToLevel(PlainLevel))

        assertEquals(placed, vm.state.placedCells, "the board has to be waiting where it was left")
    }

    @Test
    fun retryStillThrowsTheBoardAway() = runUnitTest {
        // The exception. Retry is the one path that means it, and handing the
        // board back would make the button appear to do nothing.
        val vm = viewModel()
        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }
        assertEquals(GamePhase.Lost, vm.state.phase)

        vm.takeAction(GameAction.Retry)

        assertTrue(vm.state.wrongGuesses.isEmpty(), "a retry starts clean")
        assertEquals(0, vm.state.strikesThisAttempt, "and the attempt is priced fresh")
        assertEquals(0, vm.state.livesRemaining, "but the bones it cost are gone for good")
    }

    @Test
    fun anAttemptSurvivesTheProcessBeingKilled() = runUnitTest {
        // The bug this exists for: booster spends were written to disk the moment
        // they happened and the board they paid for was not, so a force-quit
        // mid-puzzle kept the charge and lost the reasoning.
        val cache = InMemoryAppCache()
        val first = viewModel(cache = cache)
        val level = assertNotNull(first.state.level)
        first.commit(cellFor(row = 1))
        val marked = wrongCellIn(row = 3)
        first.note(marked)
        val placed = first.state.placedCells
        val score = first.state.score.total
        assertTrue(placed.isNotEmpty(), "the fixture needs a placement to lose")

        // A new ViewModel over the same cache is what a relaunch looks like.
        val resumed = viewModel(cache = cache)

        assertEquals(level.id, resumed.state.level?.id)
        assertEquals(placed, resumed.state.placedCells, "the placements have to come back")
        assertTrue(marked in resumed.state.manualMarks, "and the player's own crosses")
        assertEquals(score, resumed.state.score.total, "and the score they earned")
        assertEquals(GamePhase.Playing, resumed.state.phase)
    }

    @Test
    fun aResumedBoardKeepsTheBonesItAlreadySpent() = runUnitTest {
        val cache = InMemoryAppCache()
        val first = viewModel(cache = cache)
        first.commit(wrongCellIn(row = 0))
        val remaining = first.state.livesRemaining
        val red = first.state.wrongGuesses
        assertTrue(remaining < ScoringConfig.MAX_LIVES && red.isNotEmpty())

        val resumed = viewModel(cache = cache)

        assertEquals(remaining, resumed.state.livesRemaining, "a strike survives a relaunch")
        assertEquals(red, resumed.state.wrongGuesses, "and so does the red square it left")
    }

    @Test
    fun aFinishedAttemptIsNotOfferedBack() = runUnitTest {
        // Nothing to resume once the board is done, and offering it would put a
        // won board back on screen as if it were still in play.
        val cache = InMemoryAppCache()
        val first = viewModel(cache = cache)
        val level = assertNotNull(first.state.level)
        (0 until level.size).forEach { row -> first.commit(cellFor(row)) }
        assertEquals(GamePhase.Won, first.state.phase)
        assertEquals(null, cache.get().boardInProgress, "a finished board clears the slot")

        val resumed = viewModel(cache = cache)

        assertTrue(resumed.state.placedCells.size <= 1, "only the starter dog, if any")
    }

    @Test
    fun anUntouchedBoardIsNotWorthResuming() = runUnitTest {
        // A player who glanced at a level and left should not be offered it
        // forever. Restoring an untouched board is indistinguishable from
        // starting one anyway.
        val cache = InMemoryAppCache()
        viewModel(levelId = PlainLevel, cache = cache)

        assertEquals(null, cache.get().boardInProgress)
    }

    @Test
    fun aSnapshotOfADifferentLevelIsLeftAlone() = runUnitTest {
        // Opening level 2 must not restore level 1's board onto it, and must not
        // throw level 1's board away either — the player may well go back.
        val cache = taughtCache()
        val onLevelOne = viewModel(levelId = StarterDogLevel, cache = cache)
        onLevelOne.commit(cellFor(row = 1))
        val saved = assertNotNull(cache.get().boardInProgress)

        val other = viewModel(levelId = PlainLevel, cache = cache)

        assertTrue(other.state.manualMarks.isEmpty(), "a fresh board, not the other one")
        assertEquals(saved.levelId, cache.get().boardInProgress?.levelId, "the other board is kept")
    }

    @Test
    fun pickingTheLevelYouAreAlreadyOnKeepsTheBoard() = runUnitTest {
        // Reported from a device: open the pane mid-puzzle, tap the row you are
        // on, and every mark and placement is gone. Tapping your own row is a
        // way of closing the pane, not a request to start over.
        val vm = viewModel()
        val level = assertNotNull(vm.state.level)
        vm.commit(cellFor(row = 0))
        // `tappableWrongCellOn`, not `wrongCellIn`: the latter picks the first
        // column that is not the answer without checking whether the board has
        // already crossed that square off, so on a board where it has, `note`
        // clears the cross instead of drawing one and the fixture ends up with
        // nothing to lose. Re-curving the campaign moved level 200 onto exactly
        // such a board.
        vm.note(tappableWrongCellOn(vm))
        val placed = vm.state.placedCells
        val marks = vm.state.manualMarks
        assertTrue(placed.isNotEmpty() && marks.isNotEmpty(), "the fixture needs something to lose")

        vm.takeAction(GameAction.LevelsOpened)
        vm.takeAction(GameAction.GoToLevel(level.id))

        assertEquals(placed, vm.state.placedCells, "the placements have to survive")
        assertEquals(marks, vm.state.manualMarks, "and so do the player's own crosses")
        assertFalse(vm.state.drawerOpen, "but the pane still closes")
    }

    @Test
    fun pickingTheLevelYouAreOnAfterLosingDoesRestart() = runUnitTest {
        // The other half. Once the attempt is over there is nothing to protect,
        // and refusing to restart would strand the player on a dead board.
        val vm = viewModel()
        val level = assertNotNull(vm.state.level)
        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }
        assertEquals(GamePhase.Lost, vm.state.phase)

        vm.takeAction(GameAction.GoToLevel(level.id))

        assertEquals(GamePhase.Playing, vm.state.phase)
        assertEquals(0, vm.state.strikesThisAttempt)
        assertEquals(0, vm.state.livesRemaining, "restarting is not a way to buy bones")
    }

    @Test
    fun pickingALevelClosesTheDrawer() = runUnitTest {
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 1, paws = 1, timeMs = 1)
        val vm = viewModel(levelId = StarterDogLevel, progress = progress)
        vm.takeAction(GameAction.LevelsOpened)

        vm.takeAction(GameAction.GoToLevel(StarterDogLevel + 1))

        assertEquals(StarterDogLevel + 1, vm.state.level?.id)
        assertFalse(vm.state.drawerOpen, "the pane must not survive the level it launched")
    }

    @Test
    fun theStandingAdOfferRefillsBonesWithoutEverReducingThem() = runUnitTest {
        val vm = viewModel()
        repeat(2) { vm.commit(wrongCellIn(row = it)) }
        assertTrue(
            vm.state.livesRemaining < ScoringConfig.MAX_LIVES,
            "the fixture has to actually cost lives",
        )

        vm.takeAction(GameAction.RefillBones)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)

        // Already full: the ad may still play, but it must not take anything away.
        vm.takeAction(GameAction.RefillBones)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
    }

    @Test
    fun aClearIsHandedToTheAchievementLogWithTheFactsTheFoldNeeds() = runUnitTest {
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)
        val level = assertNotNull(vm.state.level)

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
        assertEquals(GamePhase.Won, vm.state.phase, "the fixture has to actually finish")

        val recorded = badges.recorded.single()
        assertEquals(level.id, recorded.levelId)
        assertEquals(PlayMode.Campaign, recorded.mode)
        assertEquals(level.size, recorded.size)
        assertTrue(recorded.completed)
        assertEquals(vm.state.attemptScore, recorded.score)
        assertEquals(vm.state.paws, recorded.paws)
        assertEquals(0, recorded.strikes, "a clean run has no strikes")
        assertEquals(level.size, recorded.bestCombo, "an unbroken run is the whole board")
        assertEquals(0, recorded.sniffsUsed)
        assertEquals(0, recorded.treatsUsed)
        assertTrue(recorded.isFirstClear, "nothing had cleared this level before")
        assertEquals(0, recorded.previousBestPaws)
        assertEquals(FixedHour, recorded.localHour)
    }

    @Test
    fun aReplayIsNotAFirstClear() = runUnitTest {
        // The reason this matters: "levels cleared" counts levels. If a replay
        // read as a first clear, one level replayed a hundred times would earn
        // the hundred-level badge.
        val badges = RecordingAchievements()
        val progress = InMemoryProgress()
        progress.onCompleted(PlainLevel, score = 5_000, paws = 2, timeMs = 9_000)
        val vm = viewModel(progress = progress, achievements = badges)
        val level = assertNotNull(vm.state.level)

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }

        val recorded = badges.recorded.single()
        assertFalse(recorded.isFirstClear)
        assertEquals(2, recorded.previousBestPaws, "the fold needs the bar this attempt had to beat")
    }

    @Test
    fun aFailedAttemptIsRecordedToo() = runUnitTest {
        // Losses are evidence about how someone plays. Recording only wins would
        // make the log a record of successes, which is a different thing.
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)

        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }
        assertEquals(GamePhase.Lost, vm.state.phase)

        val recorded = badges.recorded.single()
        assertFalse(recorded.completed)
        assertEquals(0, recorded.paws)
        assertEquals(ScoringConfig.MAX_LIVES, recorded.strikes)
        assertFalse(recorded.isFirstClear)
    }

    @Test
    fun spentBoostersAreCountedPerAttemptAndResetOnRetry() = runUnitTest {
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)
        val level = assertNotNull(vm.state.level)

        vm.takeAction(GameAction.BoosterTapped(Consumable.Treat))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Treat))
        assertEquals(ConsumableRefillTo - 1, vm.state.treats, "the fixture has to actually spend one")

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
        assertEquals(1, badges.recorded.single().treatsUsed)

        // A retry starts a new attempt, and "cleared without help" is a question
        // about this attempt, not about the session.
        vm.takeAction(GameAction.Retry)
        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
        assertEquals(0, badges.recorded.last().treatsUsed, "spends must not carry across attempts")
    }

    @Test
    fun aDailyResolvesAgainstTheDailyPackAndIgnoresTheRoutesId() = runUnitTest {
        // The two packs share a number line, so an id alone does not name a
        // board. The route's id is only a hint; the status is what decides,
        // because the board and the date it will be recorded against have to
        // come from one snapshot.
        val vm = viewModel(levelId = 1, isDaily = true, daily = FakeDaily(levelId = DailyLevel))

        val opened = assertNotNull(vm.state.level)
        assertEquals(DailyLevel, opened.id)
        assertEquals(LevelPacks.daily.byId(DailyLevel), opened)
        assertTrue(
            opened != LevelPacks.campaign.byId(DailyLevel),
            "daily $DailyLevel and campaign $DailyLevel are different boards",
        )
        assertTrue(vm.state.isDaily)
        assertEquals(GamePhase.Playing, vm.state.phase)
    }

    @Test
    fun aSpentDayCannotBePlayedAgain() = runUnitTest {
        // One attempt per day still holds — the board opens on its result rather
        // than in play, and nothing on it can reach the repository. What changed
        // is that it opens at all: refusing outright is what left a player
        // dumped into the campaign with no way back to the daily.
        val daily = FakeDaily(result = completedToday())

        val vm = viewModel(isDaily = true, daily = daily)

        assertEquals(GamePhase.Recap, vm.state.phase)
        assertTrue(daily.writes.isEmpty())

        vm.commit(cellFor(row = 0))
        vm.takeAction(GameAction.Retry)
        vm.takeAction(GameAction.RefillBones)

        assertEquals(GamePhase.Recap, vm.state.phase, "nothing on a spent day is a control")
        assertEquals(0, vm.state.dogsPlaced)
        assertTrue(daily.writes.isEmpty())
    }

    @Test
    fun aDailyClearIsWrittenToTheDailyAndNotToCampaignProgress() = runUnitTest {
        val daily = FakeDaily(levelId = DailyLevel)
        val progress = InMemoryProgress()
        val vm = viewModel(isDaily = true, daily = daily, progress = progress)

        solveCurrent(vm)

        assertEquals(GamePhase.Won, vm.state.phase)
        val written = daily.writes.single()
        assertEquals(DailyDate, written.date)
        assertEquals(DailyOutcome.Completed, written.outcome)
        assertEquals(vm.state.attemptScore, written.score)
        assertEquals(vm.state.paws, written.paws)

        assertTrue(
            progress.all().isEmpty(),
            "a daily may not touch campaign progress — not even an attempt row",
        )
        assertEquals(LevelRecord.FIRST_LEVEL_ID, progress.unlockedThrough())
        assertEquals(
            LevelRecord.FIRST_LEVEL_ID,
            vm.state.unlockedThrough,
            "daily $DailyLevel must not unlock campaign $DailyLevel",
        )
    }

    @Test
    fun aDailyOpeningDoesNotWidenTheCampaignFrontier() = runUnitTest {
        // The drawer unlocks against `unlockedThrough`, and the level on screen
        // is normally proof the player reached it. It is no such proof here.
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 1, paws = 1, timeMs = 1)
        val vm = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel), progress = progress)

        vm.takeAction(GameAction.LevelsOpened)

        assertEquals(StarterDogLevel + 1, vm.state.unlockedThrough)
    }

    @Test
    fun aDailyClearReachesTheAchievementLogWithTheStreakItProduced() = runUnitTest {
        // The streak *after* the write, which is the whole point: reading it
        // before recording the clear reports the number the player had
        // yesterday, and the log has no way to notice.
        val badges = RecordingAchievements()
        val vm = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel), achievements = badges)

        solveCurrent(vm)

        val recorded = badges.recorded.single()
        assertEquals(PlayMode.Daily, recorded.mode)
        assertEquals(DailyLevel, recorded.levelId)
        assertTrue(recorded.completed)
        assertEquals(OpeningStreak + 1, recorded.dailyStreakDays)
        assertEquals(OpeningStreak + 1, vm.state.dailyStreak, "the win sheet shows the same number")
    }

    @Test
    fun aCampaignClearIsStillCampaignAndCarriesNoStreak() = runUnitTest {
        val badges = RecordingAchievements()
        val vm = viewModel(achievements = badges)

        solve(vm)

        val recorded = badges.recorded.single()
        assertEquals(PlayMode.Campaign, recorded.mode)
        assertEquals(0, recorded.dailyStreakDays)
    }

    @Test
    fun leavingALostDailyDoesNotSpendTheDay() = runUnitTest {
        // R16, and the whole of it. Tapping Levels on a lost daily wrote
        // `onFailed`, which spends the day — so the player landed in the
        // campaign with today closed behind them and nothing said about it.
        val daily = FakeDaily(levelId = DailyLevel)
        val vm = viewModel(isDaily = true, daily = daily)

        loseCurrent(vm)
        assertEquals(GamePhase.Lost, vm.state.phase)
        assertTrue(daily.writes.isEmpty(), "the revive is still on the table")

        vm.takeAction(GameAction.Leave)

        assertTrue(daily.writes.isEmpty(), "walking away is not a forfeit")
        assertEquals(null, daily.status().result, "and the day is still open")
    }

    @Test
    fun aLostDailyIsSpentOnlyWhenThePlayerSaysSo() = runUnitTest {
        // Writing the loss the moment the bones run out would lock the day
        // against the clear an ad revive could still earn — `daily_result` takes
        // one row per date and never updates it. So the write waits, and the
        // only thing that fires it is the player choosing to.
        val daily = FakeDaily(levelId = DailyLevel)
        val vm = viewModel(isDaily = true, daily = daily)
        loseCurrent(vm)

        vm.takeAction(GameAction.ForfeitDailyRequested)
        assertTrue(vm.state.forfeitPrompt, "an irreversible write asks first")
        assertTrue(daily.writes.isEmpty(), "and asking is not doing")

        vm.takeAction(GameAction.ForfeitDailyConfirmed)

        val written = daily.writes.single()
        assertEquals(DailyOutcome.Failed, written.outcome)
        assertEquals(DailyDate, written.date)
    }

    @Test
    fun backingOutOfTheForfeitWritesNothing() = runUnitTest {
        val daily = FakeDaily(levelId = DailyLevel)
        val vm = viewModel(isDaily = true, daily = daily)
        loseCurrent(vm)

        vm.takeAction(GameAction.ForfeitDailyRequested)
        vm.takeAction(GameAction.DismissForfeitPrompt)

        assertFalse(vm.state.forfeitPrompt)
        assertTrue(daily.writes.isEmpty())
    }

    @Test
    fun aDailyCannotBeForfeitedFromABoardStillInPlay() = runUnitTest {
        // The confirmation is only ever reachable from the lose sheet, but the
        // action is public and a stale tap must not spend a day the player is
        // still winning.
        val daily = FakeDaily(levelId = DailyLevel)
        val vm = viewModel(isDaily = true, daily = daily)

        vm.takeAction(GameAction.ForfeitDailyConfirmed)

        assertTrue(daily.writes.isEmpty())
    }

    @Test
    fun aLostDailyComesBackWhereItWasLeft() = runUnitTest {
        // The other half of leaving without forfeiting: the day stays open, so
        // reopening it has to hand back the *position*. A blank board would be
        // the fresh run one-attempt-per-day exists to refuse.
        val cache = InMemoryAppCache()
        val daily = FakeDaily(levelId = DailyLevel)
        val first = viewModel(isDaily = true, daily = daily, cache = cache)
        val board = assertNotNull(first.state.level)
        first.commit(board.board.cellAt(0, board.solution[0]))
        loseCurrent(first)
        val red = first.state.wrongGuesses
        val placed = first.state.placedCells
        first.takeAction(GameAction.Leave)

        val reopened = viewModel(isDaily = true, daily = daily, cache = cache)

        assertEquals(GamePhase.Playing, reopened.state.phase)
        assertEquals(placed, reopened.state.placedCells, "the dogs have to come back")
        assertEquals(red, reopened.state.wrongGuesses, "and the squares that cost bones")
        assertEquals(0, reopened.state.livesRemaining, "but not the bones they cost")
    }

    @Test
    fun aRevivedDailyCanStillBeCleared() = runUnitTest {
        val daily = FakeDaily(levelId = DailyLevel)
        val vm = viewModel(isDaily = true, daily = daily)
        loseCurrent(vm)

        vm.takeAction(GameAction.RefillBones)
        assertEquals(GamePhase.Playing, vm.state.phase)
        solveCurrent(vm)

        assertEquals(DailyOutcome.Completed, daily.writes.single().outcome)
    }

    @Test
    fun aSpentDailyOpensOnItsResultRatherThanBouncingTheRoute() = runUnitTest {
        // The symptom the player actually reported: "I couldn't open back up the
        // daily board." `todaysBoard()` returned null once the day had a result,
        // so the route popped itself and dropped them into the campaign.
        val done = DailyResult(
            date = DailyDate,
            levelIndex = DailyLevel - 1,
            outcome = DailyOutcome.Completed,
            score = 4_200,
            paws = 3,
            timeMs = 90_000,
        )
        val vm = viewModel(
            isDaily = true,
            daily = FakeDaily(levelId = DailyLevel, result = done),
        )

        assertEquals(GamePhase.Recap, vm.state.phase)
        assertEquals(done, vm.state.dailyRecap)
        assertEquals(DailyLevel, vm.state.level?.id, "the day's own board, not a placeholder")
        assertEquals(3, vm.state.paws)
    }

    @Test
    fun aForfeitedDailyReopensOnTheForfeit() = runUnitTest {
        val cache = InMemoryAppCache()
        val daily = FakeDaily(levelId = DailyLevel)
        val first = viewModel(isDaily = true, daily = daily, cache = cache)
        loseCurrent(first)
        first.takeAction(GameAction.ForfeitDailyConfirmed)
        assertEquals(null, cache.get().boardInProgress, "a spent day holds no slot")

        val reopened = viewModel(isDaily = true, daily = daily, cache = cache)

        assertEquals(GamePhase.Recap, reopened.state.phase)
        assertEquals(DailyOutcome.Failed, reopened.state.dailyRecap?.outcome)
    }

    @Test
    fun theCardStillOffersASpentDay() = runUnitTest {
        // The drawer's half. `playDaily` refused once the day was over, so the
        // card went inert and there was no route back in at all.
        val vm = viewModel(
            daily = FakeDaily(levelId = DailyLevel, result = completedToday()),
        )
        val events = eventsOf(vm)
        vm.takeAction(GameAction.LevelsOpened)

        vm.takeAction(GameAction.PlayDaily)

        assertEquals(GameEvent.OpenDaily(DailyLevel), events.lastOrNull())
        assertFalse(vm.state.drawerOpen, "the pane still closes")
    }

    @Test
    fun aDailyThatIsSwitchedOffOpensNothing() = runUnitTest {
        val vm = viewModel(daily = FakeDaily(levelId = DailyLevel, enabled = false))
        val events = eventsOf(vm)
        vm.takeAction(GameAction.LevelsOpened)

        vm.takeAction(GameAction.PlayDaily)

        assertTrue(events.none { it is GameEvent.OpenDaily }, "the kill switch still kills")
        assertTrue(vm.state.drawerOpen, "and the pane stays put rather than closing on nothing")
    }

    @Test
    fun aDailyCannotBeRestarted() = runUnitTest {
        val vm = viewModel(isDaily = true, daily = FakeDaily(levelId = DailyLevel))
        loseCurrent(vm)

        vm.takeAction(GameAction.Retry)

        assertEquals(GamePhase.Lost, vm.state.phase, "a second run at today's board is a replay")
    }

    @Test
    fun aLostCampaignLevelWritesNothingToTheDaily() = runUnitTest {
        val daily = FakeDaily()
        val vm = viewModel(daily = daily)

        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }
        vm.takeAction(GameAction.Leave)

        assertTrue(daily.writes.isEmpty())
    }

    @Test
    fun theCardFollowsTheObservedStatus() = runUnitTest {
        // Observed rather than fetched, because the repository re-emits at local
        // midnight and a drawer left open has to pick the new board up.
        val vm = viewModel(daily = FakeDaily(levelId = DailyLevel))

        val status = assertNotNull(vm.state.daily)
        assertEquals(DailyDate, status.date)
        assertEquals(DailyLevel, status.levelId)
        assertEquals(OpeningStreak, status.streak)
        assertTrue(status.playable)
    }

    @Test
    fun playingTheDailyFromTheDrawerClosesIt() = runUnitTest {
        val vm = viewModel(daily = FakeDaily(levelId = DailyLevel))
        vm.takeAction(GameAction.LevelsOpened)

        vm.takeAction(GameAction.PlayDaily)

        assertFalse(vm.state.drawerOpen)
        assertEquals(PlainLevel, vm.state.level?.id, "the daily opens on its own route, not in place")
    }

    @Test
    fun everyFreezeOutcomeSaysSomething() = runUnitTest {
        // A freeze that resolves silently is indistinguishable from a crash, and
        // Declined — the player closing the ad early — is the branch most likely
        // to be read as one.
        val cases = listOf(
            FreezeResult.Applied(DailyDate, streak = 9) to FreezeMessage.Applied(9),
            FreezeResult.Declined to FreezeMessage.Declined,
            FreezeResult.NoneLeft to FreezeMessage.NoneLeft,
            FreezeResult.NothingToFreeze to FreezeMessage.NothingToFreeze,
        )
        cases.forEach { (result, expected) ->
            val daily = FakeDaily(freezeResult = result)
            val vm = viewModel(daily = daily)

            vm.takeAction(GameAction.UseFreeze)

            assertEquals(expected, vm.state.freezeMessage, "$result was not reported")
            assertEquals(1, daily.freezesRequested)

            vm.takeAction(GameAction.DismissFreezeMessage)
            assertEquals(null, vm.state.freezeMessage)
        }
    }

    @Test
    fun everyRestoreOutcomeSaysSomething() = runUnitTest {
        val cases = listOf(
            RestoreResult.Applied(days = 3, streak = 41) to FreezeMessage.Restored(3, 41),
            RestoreResult.Declined to FreezeMessage.Declined,
            RestoreResult.NoneLeft to FreezeMessage.RestoreNoneLeft,
            RestoreResult.OutOfReach to FreezeMessage.RestoreOutOfReach,
            RestoreResult.NothingToRestore to FreezeMessage.NothingToFreeze,
        )
        cases.forEach { (result, expected) ->
            val daily = FakeDaily(restoreResult = result)
            val vm = viewModel(daily = daily)

            vm.takeAction(GameAction.RestoreStreak)

            assertEquals(expected, vm.state.freezeMessage, "$result was not reported")
            assertEquals(1, daily.restoresRequested)
            assertEquals(0, daily.freezesRequested, "the restore does not quietly spend a freeze")

            vm.takeAction(GameAction.DismissFreezeMessage)
            assertEquals(null, vm.state.freezeMessage)
        }
    }

    @Test
    fun aFreezeThatBlowsUpIsStillAnswered() = runUnitTest {
        val vm = viewModel(daily = FakeDaily(freezeThrows = true))

        vm.takeAction(GameAction.UseFreeze)

        assertEquals(FreezeMessage.Unavailable, vm.state.freezeMessage)
    }

    @Test
    fun theTutorialTeachesOnItsOwnBoardAndNotOnLevelOne() = runUnitTest {
        // R3. The lesson used to run over campaign levels 1 to 3, so a player's
        // first three real boards were spent under a scrim and every highlight
        // landed wherever the generator happened to put it.
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache())

        assertEquals(TutorialBoard.LEVEL_ID, vm.state.level?.id)
        assertEquals(TutorialBoard.level.board, vm.state.level?.board)
        assertTrue(vm.state.isRehearsal)
        assertEquals(TutorialStep.StarterDog, vm.state.tutorial)
        assertEquals(GamePhase.Playing, vm.state.phase)
    }

    @Test
    fun theRehearsalTouchesNothingThatCounts() = runUnitTest {
        // "It is a rehearsal" is only true if none of it is written down. Each
        // of these was a real write before the demo board existed, because the
        // tutorial was running on a level the game was recording.
        val cache = untaughtCache()
        cache.set(cache.get().copy(hasCompletedTutorial = false, bones = ScoringConfig.MAX_LIVES))
        val progress = InMemoryProgress()
        val achievements = RecordingAchievements()
        val vm = viewModel(
            levelId = FirstGuidedLevel,
            cache = cache,
            progress = progress,
            achievements = achievements,
        )

        // Stopped on the last step, so the board under test is still the demo
        // one. Running to the end would hand over to level 1, and level 1
        // recording its own attempt is correct rather than a leak.
        vm.driveTo(TutorialStep.Graduation)

        assertTrue(vm.state.isRehearsal, "the lesson left the demo board early")
        assertEquals(0, progress.record(TutorialBoard.LEVEL_ID).attempts, "the demo board was recorded")
        assertEquals(0, progress.record(FirstGuidedLevel).attempts, "level 1 was recorded early")
        assertEquals(FirstGuidedLevel, progress.unlockedThrough(), "the rehearsal moved the frontier")
        assertTrue(achievements.recorded.isEmpty(), "the rehearsal reached the achievement log")
        assertEquals(ScoringConfig.MAX_LIVES, cache.get().bones, "the lesson spent a bone")
        assertEquals(null, cache.get().boardInProgress, "the demo board took the in-progress slot")
    }

    @Test
    fun aWrongGuessOnTheRehearsalBoardCostsNothing() = runUnitTest {
        // SPEC 10 asks the player to get one wrong on purpose. Charging for
        // following instructions is the failure; charging for the *second* one
        // used to be the rule, and a whole board that costs nothing cannot get
        // out of step with itself the way a one-shot flag could.
        val cache = untaughtCache()
        val vm = viewModel(levelId = FirstGuidedLevel, cache = cache)
        vm.driveTo(TutorialStep.TryAWrongOne)

        vm.commit(vm.state.tutorialCells.first())

        assertEquals(TutorialStep.WrongExplained, vm.state.tutorial)
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining, "the taught mistake cost a bone")
        assertEquals(0, vm.state.strikesThisAttempt)
    }

    @Test
    fun theBonesPillKeepsOpeningItsExplainer() = runUnitTest {
        // Sniff and Treat stop explaining once the player knows them, because a
        // later tap genuinely spends one. A bone is only ever spent by guessing
        // wrong, so there is nothing for its tap to fall through to: it fell
        // through to a branch that clears a prompt nobody opened, and the pill
        // went dead for the rest of the install while keeping its press
        // animation and its label.
        val vm = viewModel()

        vm.takeAction(GameAction.BoosterTapped(Consumable.Bone))
        assertEquals(Consumable.Bone, vm.state.boosterPrompt)
        // Confirming, not dismissing. `markExplained` runs in `spend`, so only
        // this path puts Bone in `explainedBoosters` — and being explained is
        // the precondition for the fall-through that broke the pill. A first
        // draft of this test dismissed instead, never marked it explained, and
        // passed against the bug.
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Bone))
        settle()
        assertEquals(null, vm.state.boosterPrompt, "the prompt did not close")
        assertTrue(Consumable.Bone in vm.state.explainedBoosters, "the bone was never marked explained")

        vm.takeAction(GameAction.BoosterTapped(Consumable.Bone))

        assertEquals(Consumable.Bone, vm.state.boosterPrompt, "the bones pill stopped responding")
    }

    @Test
    fun aKnownSniffIsSpentRatherThanExplainedAgain() = runUnitTest {
        // The other half: making every booster always prompt would put a dialog
        // in front of every hint forever.
        val vm = viewModel()
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        settle()
        val afterFirst = vm.state.sniffs

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        settle()

        assertEquals(null, vm.state.boosterPrompt, "a known sniff explained itself again")
        assertEquals(afterFirst - 1, vm.state.sniffs, "the second tap did not spend a sniff")
    }

    @Test
    fun aPlayerWithNoBonesLeftCanStillBeTaught() = recordingEvents { events ->
        runUnitTest {
            // The rehearsal is free, so `livesRemaining` is simply whatever the
            // player walked in holding. At zero, the strike path fell through to
            // `lose()` on the very step that instructs a wrong guess, writing a
            // level-0 result into the achievement log and a `game.level_failed`
            // for a board nobody chose to play.
            //
            // Reachable: Settings has a "Replay the tutorial" row, and a player
            // out of bones who declined the ad is exactly who goes looking at
            // Settings.
            val achievements = RecordingAchievements()
            val cache = InMemoryAppCache().apply {
                set(AppData(hasCompletedTutorial = false, bones = 0))
            }
            val vm = viewModel(levelId = FirstGuidedLevel, cache = cache, achievements = achievements)
            vm.driveTo(TutorialStep.TryAWrongOne)

            vm.commit(vm.state.tutorialCells.first())

            assertEquals(TutorialStep.WrongExplained, vm.state.tutorial, "the lesson did not continue")
            assertTrue(vm.state.phase != GamePhase.Lost, "the tutorial ended the attempt")
            assertTrue(achievements.recorded.isEmpty(), "the rehearsal reached the achievement log")
            assertTrue(
                events.attributesOf("game.level_failed").isEmpty(),
                "the rehearsal emitted a level failure: ${events.all.map { it.first }}",
            )
        }
    }

    @Test
    fun aRealBoardStillLosesWhenTheBonesRunOut() = runUnitTest {
        // The other half. Gating the loss on the wrong thing would make the
        // game unloseable, which the test above cannot see.
        val vm = viewModel()

        repeat(ScoringConfig.MAX_LIVES) { vm.commit(wrongCellIn(row = it)) }

        assertEquals(GamePhase.Lost, vm.state.phase)
    }

    @Test
    fun aSpentSniffSurvivesBeingForceQuit() = runUnitTest {
        // `persistCounts` writes the holding; only `updateBoard` writes the
        // attempt, and `sniffsUsed` lives on the attempt. Spending a sniff and
        // quitting before the next move restored a board that had taken no help,
        // banking a bigger score than was earned.
        val cache = InMemoryAppCache()
        val achievements = RecordingAchievements()
        val first = viewModel(cache = cache)
        first.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        first.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        settle()
        assertTrue(first.state.boostersUsed > 0, "the sniff was never spent, so the test proves nothing")

        val resumed = viewModel(cache = cache, achievements = achievements)
        solveCurrent(resumed)

        assertEquals(
            1,
            achievements.recorded.single().sniffsUsed,
            "the resumed attempt forgot the sniff it had already taken",
        )
    }

    @Test
    fun theRehearsalHoldsTheHeadlineScoreStill() = runUnitTest {
        // The lesson places dogs, and a placement scores. Letting that reach the
        // header would run the one number in the game up during the tutorial and
        // drop it back the moment level 1 opened.
        val progress = InMemoryProgress()
        progress.onCompleted(PlainLevel, score = 4_000, paws = 3, timeMs = 9_000)
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache(), progress = progress)
        val before = vm.state.lifetimeScore

        vm.driveTo(TutorialStep.AutoMark)

        assertTrue(vm.state.score.total > 0, "the board did not actually score anything")
        assertEquals(before, vm.state.lifetimeScore, "the rehearsal moved the lifetime score")
    }

    @Test
    fun finishingTheLessonLandsOnACleanLevelOne() = runUnitTest {
        val cache = untaughtCache()
        val vm = viewModel(levelId = FirstGuidedLevel, cache = cache)

        vm.runScript()

        assertEquals(FirstGuidedLevel, vm.state.level?.id, "the tutorial did not hand over")
        assertFalse(vm.state.isRehearsal)
        assertEquals(null, vm.state.tutorial)
        assertEquals(GamePhase.Playing, vm.state.phase)
        assertTrue(vm.state.wrongGuesses.isEmpty(), "the demo board's red square came along")
        assertEquals(1, vm.state.placed.placedCount, "level 1 opened with more than its starter dog")
        assertEquals(ScoringConfig.MAX_LIVES, vm.state.livesRemaining)
        assertTrue(cache.get().hasCompletedTutorial)
    }

    @Test
    fun aPlayerWhoAlreadyKnowsHowToPlayGetsNoCoachMarks() = runUnitTest {
        // "I know how to play" on the welcome screen writes the flag, so the
        // very first board they see has to be an ordinary one.
        val cache = InMemoryAppCache()
        cache.set(AppData(hasCompletedTutorial = true))
        val vm = viewModel(levelId = FirstGuidedLevel, cache = cache)

        assertEquals(FirstGuidedLevel, vm.state.level?.id)
        assertFalse(vm.state.isRehearsal)
        assertEquals(null, vm.state.tutorial)
        assertTrue(vm.state.tutorialCells.isEmpty())
    }

    @Test
    fun theGuidedRunNeverStartsOnADaily() = runUnitTest {
        // A daily board's id shares a number line with the campaign, so daily
        // level 1 would otherwise open on the rehearsal board and then hand the
        // player a *campaign* level when it finished.
        val vm = viewModel(
            levelId = FirstGuidedLevel,
            isDaily = true,
            cache = untaughtCache(),
            daily = FakeDaily(levelId = FirstGuidedLevel),
        )

        assertEquals(null, vm.state.tutorial)
        assertFalse(vm.state.isRehearsal)
    }

    @Test
    fun theGuidedRunNeverOpensOverALevelSomebodyJumpedTo() = runUnitTest {
        // The rehearsal hands back to the board the route asked for, so it only
        // makes sense in front of the first one. A Pro player who jumped to 200
        // with the flag unset gets level 200.
        val vm = viewModel(levelId = PlainLevel, cache = untaughtCache())

        assertEquals(PlainLevel, vm.state.level?.id)
        assertFalse(vm.state.isRehearsal)
        assertEquals(null, vm.state.tutorial)
    }

    @Test
    fun aStepOnlyMovesOnTheGestureItAskedFor() = runUnitTest {
        // The failure this guards: a step that advances on anything turns the
        // whole script into a slideshow that the first stray tap runs through.
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache())

        assertEquals(TutorialStep.StarterDog, vm.state.tutorial)
        vm.note(freeCell(vm))
        assertEquals(TutorialStep.StarterDog, vm.state.tutorial, "a mark is not a read")

        vm.driveTo(TutorialStep.MarkSquare)
        vm.takeAction(GameAction.TutorialAdvance)
        settle()
        assertEquals(TutorialStep.MarkSquare, vm.state.tutorial, "a tap does not do the doing")

        vm.note(vm.state.tutorialCells.first())
        assertEquals(TutorialStep.PlaceDog, vm.state.tutorial)

        vm.takeAction(GameAction.TutorialAdvance)
        settle()
        assertEquals(TutorialStep.PlaceDog, vm.state.tutorial)
        vm.commit(vm.state.tutorialCells.first())
        assertEquals(TutorialStep.Bones, vm.state.tutorial)
    }

    @Test
    fun aGatedStepWaitsForTheMarkToFinishDrawing() = runUnitTest {
        // R3's third ask. The coach mark used to advance in the same frame as
        // the tap, so the hole in the scrim jumped to the next square while the
        // cross the lesson had just asked for was two strokes into a 170ms
        // animation — the one thing the player was told to look at was the one
        // thing they never saw.
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache())
        vm.driveTo(TutorialStep.MarkSquare)
        val lit = vm.state.tutorialCells.first()

        vm.takeAction(GameAction.CellTapped(lit))
        clock += SettleGap
        // Everything the tap itself does has already landed...
        testDispatcher.scheduler.runCurrent()
        assertTrue(lit in vm.state.manualMarks, "the cross was not made")
        // ...and the lesson is still pointing at it while it draws.
        assertEquals(TutorialStep.MarkSquare, vm.state.tutorial)
        assertEquals(setOf(lit), vm.state.tutorialCells)

        testDispatcher.scheduler.advanceTimeBy(Tutorial.settleMillis(TutorialTrigger.Marked))
        testDispatcher.scheduler.runCurrent()
        assertEquals(TutorialStep.PlaceDog, vm.state.tutorial, "the lesson never moved on")
    }

    @Test
    fun everyGestureStepGivesItsMarkTimeToDraw() = runUnitTest {
        // The hold is per gesture, because the three animations are different
        // lengths and the board owns all three numbers.
        assertEquals(0L, Tutorial.settleMillis(TutorialTrigger.Tap), "a read has nothing to wait for")
        listOf(TutorialTrigger.Marked, TutorialTrigger.Placed, TutorialTrigger.Struck).forEach {
            assertTrue(Tutorial.settleMillis(it) > 0L, "$it advances before its animation runs")
        }
    }

    @Test
    fun theCurriculumTeachesEverythingSpecAsksFor() = runUnitTest {
        // Pins the curriculum itself. Without this, an implementation that
        // shipped one empty script passes every other test here.
        assertEquals(
            listOf(
                TutorialStep.StarterDog,
                TutorialStep.RuleRegion,
                TutorialStep.RuleLine,
                TutorialStep.RuleTouching,
                TutorialStep.MarkSquare,
                TutorialStep.PlaceDog,
                TutorialStep.Bones,
                TutorialStep.PlaceAndWatch,
                TutorialStep.AutoMark,
                TutorialStep.Sniff,
                TutorialStep.Treat,
                TutorialStep.TryAWrongOne,
                TutorialStep.WrongExplained,
                TutorialStep.Graduation,
            ),
            Tutorial.Script,
        )
        assertEquals(
            TutorialStep.entries.toSet(),
            Tutorial.Script.toSet(),
            "a step exists that the script never shows",
        )
    }

    @Test
    fun theRuleLessonsPointAtTheBoardAndNotAtAChip() = runUnitTest {
        // R3's second ask: "when you say only 1 dog per column maybe you
        // highlight a column, not just the tips at the top."
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache())
        val board = assertNotNull(vm.state.level).board
        val starter = assertNotNull(vm.state.starterDogCell)

        vm.driveTo(TutorialStep.RuleRegion)
        assertEquals(
            board.cellsInRegion(board.regionAt(starter)).toSet(),
            vm.state.tutorialCells,
            "the colour rule did not light the colour",
        )

        vm.driveTo(TutorialStep.RuleLine)
        val row = board.rowOf(starter)
        val col = board.colOf(starter)
        val cross = (0 until board.size)
            .flatMap { listOf(board.cellAt(row, it), board.cellAt(it, col)) }
            .toSet()
        assertEquals(cross, vm.state.tutorialCells, "the row-and-column rule did not light them")

        vm.driveTo(TutorialStep.RuleTouching)
        assertEquals(
            board.neighborsOf(starter).toSet(),
            vm.state.tutorialCells,
            "the adjacency rule did not light the ring",
        )
    }

    @Test
    fun noStepCanLeaveTheBoardUntappable() = runUnitTest {
        // The one that matters. A step the player cannot act on and cannot
        // dismiss is a dead end on the first board of the game, and nothing
        // else in the app would report it.
        //
        // Two ways out of any step, and every step has to have one: it either
        // dismisses on a tap anywhere, or it lights a square the player can
        // actually act on. A lit square that auto-mark has already swallowed is
        // not one — the game ignores taps on it.
        //
        // Extended for R3, because gating the doing steps made the second way
        // out load-bearing: it is no longer enough for a lit square to exist,
        // the gesture the step is waiting for has to *land* on it. So each one
        // is performed and the step is asserted to have actually moved. A step
        // that lights a square the asked-for gesture cannot satisfy — a
        // placement on a wrong square, a strike on the right one — would loop
        // here forever, which is precisely the dead end being ruled out.
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache())
        assertNotNull(vm.state.tutorial, "the tutorial opened with nothing to teach")
        var seen = 0
        while (vm.state.tutorial != null && vm.state.isRehearsal) {
            val step = assertNotNull(vm.state.tutorial)
            if (Tutorial.triggerFor(step) != TutorialTrigger.Tap) {
                val lit = vm.state.tutorialCells
                assertTrue(lit.isNotEmpty(), "$step asks for a gesture on nothing")
                lit.forEach { cell ->
                    assertTrue(cell !in vm.state.autoMarks, "$step lit a crossed-off square")
                    assertTrue(cell !in vm.state.placedCells, "$step lit an occupied square")
                }
            }
            assertEquals(GamePhase.Playing, vm.state.phase, "$step left the board out of play")
            vm.perform(step)
            assertTrue(vm.state.tutorial != step, "$step did not move on the gesture it asked for")
            seen++
            assertTrue(seen <= StepBudget, "the script never finished")
        }
        assertEquals(Tutorial.Script.size, seen, "a lesson was skipped")
        // And it let go of the board on the way out, rather than leaving the
        // player on a demo puzzle they can never finish.
        assertFalse(vm.state.isRehearsal)
        assertEquals(FirstGuidedLevel, vm.state.level?.id)
    }

    @Test
    fun skippingFromAnyStepLeavesAPlayableBoard() = runUnitTest {
        // A skip that only hid the card would leave the scrim's dead zone over
        // the board, which is the same dead end by another route. Since R3 it
        // also has to leave the *rehearsal*: dropping the player onto the demo
        // board would hand them a puzzle worth nothing that is not in any pack.
        Tutorial.Script.indices.forEach { stopAt ->
            val cache = untaughtCache()
            val vm = viewModel(levelId = FirstGuidedLevel, cache = cache)
            repeat(stopAt) { vm.perform(assertNotNull(vm.state.tutorial)) }
            assertNotNull(vm.state.tutorial, "nothing was showing to skip out of")

            vm.takeAction(GameAction.SkipTutorial)
            settle()

            assertEquals(null, vm.state.tutorial, "step $stopAt kept the card")
            assertTrue(vm.state.tutorialCells.isEmpty())
            assertEquals(GamePhase.Playing, vm.state.phase)
            assertFalse(vm.state.isRehearsal, "step $stopAt left them on the demo board")
            assertEquals(FirstGuidedLevel, vm.state.level?.id, "step $stopAt skipped to nowhere")
            assertTrue(cache.get().hasCompletedTutorial, "a skip is a completion")

            // And the board still answers, which is the thing the scrim
            // was blocking.
            val cell = freeCell(vm)
            vm.note(cell)
            assertTrue(cell in vm.state.manualMarks, "the board stopped taking taps")
        }
    }

    @Test
    fun theFlagSurvivesTheScreenItWasWrittenOn() = runUnitTest {
        val cache = untaughtCache()
        viewModel(levelId = FirstGuidedLevel, cache = cache)
            .takeAction(GameAction.SkipTutorial)
        settle()

        val next = viewModel(levelId = FirstGuidedLevel, cache = cache)

        assertEquals(null, next.state.tutorial, "the skip did not outlive the ViewModel")
        assertFalse(next.state.isRehearsal)
    }

    @Test
    fun startingLevelOneOverDoesNotReplayItsLessons() = runUnitTest {
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache())
        vm.runScript()

        vm.takeAction(GameAction.Retry)
        settle()

        assertEquals(null, vm.state.tutorial, "a retry is not a first look at the level")
        assertFalse(vm.state.isRehearsal)
    }

    @Test
    fun theAutoMarkLessonPointsAtTheSquaresThePlacementJustCrossedOff() = runUnitTest {
        // The whole content of the step. Pointing at every marked square would
        // include the starter dog's, which the player was shown five steps ago.
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache())
        vm.driveTo(TutorialStep.PlaceAndWatch)
        val before = vm.state.autoMarks

        vm.driveTo(TutorialStep.AutoMark)

        val lit = vm.state.tutorialCells
        assertTrue(lit.isNotEmpty())
        assertTrue(lit.all { it in vm.state.autoMarks }, "a lit square that is not crossed off")
        assertTrue(lit.none { it in before }, "these were already marked before the placement")
    }

    @Test
    fun theTutorialDoesNotTeachAutoMarkToAPlayerWhoTurnedItOff() = runUnitTest {
        // Replaying from Settings is the one way a player reaches the guided run
        // having already switched the crosses off, and it is the run that must
        // not teach a feature they do not have. The AutoMark card would have
        // shown regardless of the setting: its trigger is `Tap`, so the runner
        // does not skip it for having nothing to light — it would simply have
        // said "every square that dog rules out was crossed off for you" over a
        // board where none of them were.
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtPuristCache())
        val seen = mutableListOf<TutorialStep>()

        var guard = 0
        while (vm.state.tutorial != null && guard++ < StepBudget) {
            val step = assertNotNull(vm.state.tutorial)
            seen += step
            vm.perform(step)
        }

        assertTrue(seen.isNotEmpty(), "the guided run never started, so this asserts nothing")
        assertTrue(TutorialStep.AutoMark !in seen, "taught a feature the player switched off")
        assertTrue(TutorialStep.PlaceAndWatch !in seen, "asked them to watch a board do nothing")
        // The lessons that are still true have to survive the filter, or this
        // passes by teaching nothing at all.
        assertTrue(TutorialStep.MarkSquare in seen, "the cross gesture went with them")
        assertTrue(TutorialStep.PlaceDog in seen, "the placement gesture went with them")
        assertTrue(TutorialStep.Graduation in seen, "the run did not reach the end")
        assertFalse(vm.state.isRehearsal, "the player was left on the practice board")
        assertEquals(FirstGuidedLevel, vm.state.level?.id, "they were not handed level 1")
    }

    @Test
    fun aLessonPointsAtWhatTheBoardLooksLikeRatherThanAtWhatItKnows() = runUnitTest {
        // "Cross a square off" must never light a square that already shows a
        // cross — there would be nothing to do on it. With auto-mark off no
        // square shows one, so the lesson is free to pick a square the cascade
        // rules out, and that is the *better* square to pick: it is a deduction
        // the player now has to make for themselves.
        //
        // Which makes this the assertion that the lesson is handed the drawing
        // and not the deduction. Handed the deduction it would quietly skip the
        // most useful square on the board.
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtPuristCache())

        vm.driveTo(TutorialStep.MarkSquare)

        assertTrue(vm.state.autoMarks.isNotEmpty(), "the starter dog ruled nothing out")
        assertTrue(vm.state.visibleAutoMarks.isEmpty(), "something was crossed off after all")
        assertTrue(
            vm.state.tutorialCells.single() in vm.state.autoMarks,
            "the lesson avoided a square the player has not been shown is ruled out",
        )
    }

    @Test
    fun theTutorialStillTeachesAutoMarkToEveryoneElse() = runUnitTest {
        // The other half, so the filter above is a filter and not a deletion.
        val vm = viewModel(levelId = FirstGuidedLevel, cache = untaughtCache())
        val seen = mutableListOf<TutorialStep>()

        var guard = 0
        while (vm.state.tutorial != null && guard++ < StepBudget) {
            val step = assertNotNull(vm.state.tutorial)
            seen += step
            vm.perform(step)
        }

        assertTrue(TutorialStep.AutoMark in seen)
        assertTrue(TutorialStep.PlaceAndWatch in seen)
    }

    private val clock = TestTimeSource()

    /** A player who chose "Teach me how", which is the tutorial's entry condition. */
    private suspend fun untaughtCache(): InMemoryAppCache =
        InMemoryAppCache().apply { set(AppData(hasCompletedTutorial = false)) }

    /**
     * A player who has turned the crosses off, and been taught already.
     *
     * `hasCompletedTutorial` is not incidental at any call site that opens a
     * real board: a default cache is a fresh install, and a fresh install on
     * level 1 gets the rehearsal board in front of it.
     */
    private suspend fun puristCache(): InMemoryAppCache = InMemoryAppCache()
        .apply { set(AppData(autoMarkEnabled = false, hasCompletedTutorial = true)) }

    /** Untaught *and* a purist, which is what "replay the tutorial" produces. */
    private suspend fun untaughtPuristCache(): InMemoryAppCache = InMemoryAppCache()
        .apply { set(AppData(autoMarkEnabled = false, hasCompletedTutorial = false)) }

    /**
     * A player who has already been taught, so level 1 opens as level 1.
     *
     * Worth spelling out at any call site that opens the first level and then
     * asserts about the board: a default `InMemoryAppCache` is a fresh install,
     * and a fresh install on level 1 now gets the rehearsal board in front of it.
     */
    private suspend fun taughtCache(): InMemoryAppCache =
        InMemoryAppCache().apply { set(AppData(hasCompletedTutorial = true)) }

    /**
     * Runs out the virtual clock, because a gated step now *waits* before it
     * advances.
     *
     * `UnconfinedTestDispatcher` runs a handler eagerly until it suspends, and
     * `Tutorial.settleMillis` is a suspension — so without this a test reads the
     * state mid-hold and sees the step it just satisfied. That is the behaviour
     * under test rather than an inconvenience: `aGatedStepWaitsForTheMarkToFinishDrawing`
     * asserts on both sides of it deliberately.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun settle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** Does whatever the step is waiting for, and nothing else. */
    private fun GameViewModel.perform(step: TutorialStep) {
        when (Tutorial.triggerFor(step)) {
            TutorialTrigger.Tap -> takeAction(GameAction.TutorialAdvance)
            TutorialTrigger.Marked -> note(state.tutorialCells.first())
            TutorialTrigger.Placed, TutorialTrigger.Struck -> commit(state.tutorialCells.first())
        }
        settle()
    }

    private fun GameViewModel.runScript() {
        var guard = 0
        while (state.tutorial != null && guard++ < StepBudget) {
            perform(assertNotNull(state.tutorial))
        }
    }

    private fun GameViewModel.driveTo(step: TutorialStep) {
        var guard = 0
        while (state.tutorial != null && state.tutorial != step && guard++ < StepBudget) {
            perform(assertNotNull(state.tutorial))
        }
        assertEquals(step, state.tutorial, "never reached $step")
    }

    /** A square on the open board that a tap would actually write a note on. */
    private fun freeCell(vm: GameViewModel): Int {
        val open = assertNotNull(vm.state.level)
        return (0 until open.board.cellCount).first { cell ->
            cell !in vm.state.autoMarks &&
                cell !in vm.state.placedCells &&
                cell !in vm.state.manualMarks
        }
    }

    private fun tappableWrongCellOn(vm: GameViewModel): Int {
        val open = assertNotNull(vm.state.level)
        return (0 until open.board.cellCount).first { cell ->
            open.board.colOf(cell) != open.solution[open.board.rowOf(cell)] &&
                cell !in vm.state.autoMarks &&
                cell !in vm.state.placedCells
        }
    }

    // ---- Remote config, from the board's side of it. ------------------------
    //
    // `ConfiguredScoringTest` proves the fourteen keys reach a `ScoringConfig`.
    // These prove the `ScoringConfig` reaches the score, which is the half that
    // was missing: every coefficient resolved correctly for two months while
    // `GameViewModel` let the parameter default.

    @Test
    fun aPlacementIsWorthWhatConfigSaysItIsWorth() = runUnitTest {
        val vm = viewModel(config = configOf("scoring.basePerPlacement" to 1_000))
        // Past the speed window, so the multiplier is exactly 1.0 and the
        // expected score is arithmetic rather than a re-run of the formula.
        clock += PastSpeedWindow

        vm.commit(cellFor(row = 0))

        assertEquals(1_000 * level.size, vm.state.score.total)
    }

    @Test
    fun withNoConfigAPlacementIsWorthTheShippedRate() = runUnitTest {
        val vm = viewModel()
        clock += PastSpeedWindow

        vm.commit(cellFor(row = 0))

        assertEquals(ScoringConfig.Default.basePerPlacement * level.size, vm.state.score.total)
    }

    @Test
    fun anInvalidRemoteScoringSetScoresOnTheShippedOneRatherThanCrashing() = runUnitTest {
        // `ScoringConfig` throws on this. Reaching a `place()` through the real
        // ViewModel is the point: the exception would land on the tap that put
        // a dog on the board.
        val vm = viewModel(config = configOf("scoring.basePerPlacement" to -1_000))
        clock += PastSpeedWindow

        vm.commit(cellFor(row = 0))

        assertEquals(ScoringConfig.Default.basePerPlacement * level.size, vm.state.score.total)
    }

    @Test
    fun thePawRatingMovesWithItsConfiguredThresholds() = runUnitTest {
        // Both fractions at zero puts every finish over the three-paw line, and
        // the default set does not — so this fails against a `paws` call that
        // kept defaulting its config parameter.
        val generous = viewModel(
            config = configOf(
                "scoring.twoPawFraction" to 0.0,
                "scoring.threePawFraction" to 0.0,
            ),
        )
        // Slow, sloppy play: every placement outside the speed window.
        (0 until level.size).forEach { row ->
            clock += PastSpeedWindow
            generous.commit(cellFor(row))
        }
        assertEquals(3, generous.state.paws)

        val shipped = viewModel()
        (0 until level.size).forEach { row ->
            clock += PastSpeedWindow
            shipped.commit(cellFor(row))
        }
        assertTrue(
            shipped.state.paws < 3,
            "the fixture has to be a run the shipped thresholds would not rate three paws",
        )
    }

    @Test
    fun aFreshInstallStartsWithTheConfiguredBoosters() = runUnitTest {
        val vm = viewModel(
            config = configOf(
                "boosters.startingSniffs" to 7,
                "boosters.startingTreats" to 2,
            ),
        )

        assertEquals(7, vm.state.sniffs)
        assertEquals(2, vm.state.treats)
    }

    @Test
    fun withNoConfigAFreshInstallStartsWithThreeOfEach() = runUnitTest {
        val vm = viewModel()

        assertEquals(ConsumableRefillTo, vm.state.sniffs)
        assertEquals(ConsumableRefillTo, vm.state.treats)
    }

    @Test
    fun theStartingGrantIsOnlyForPlayersWhoHaveNeverHadAny() = runUnitTest {
        // Someone who spent down to one must not have the opening handful
        // re-granted every time the board opens.
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 1))

        val vm = viewModel(cache = cache, config = configOf("boosters.startingSniffs" to 7))

        assertEquals(1, vm.state.sniffs)
    }

    @Test
    fun aRefillTopsUpToTheConfiguredAmount() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0))
        val vm = viewModel(cache = cache, config = configOf("boosters.refillTo" to 6))

        vm.takeAction(GameAction.BoosterRefillRequested(Consumable.Sniff))

        assertEquals(6, vm.state.sniffs)
        assertEquals(6, cache.get().sniffs)
    }

    @Test
    fun switchingBoostersOffStopsTheEconomyAndLeavesBonesAlone() = runUnitTest {
        val vm = viewModel(config = configOf("features.boosters" to false))

        assertFalse(vm.state.boostersEnabled)

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        assertNull(vm.state.boosterPrompt, "a switched-off booster does not even explain itself")

        // Bones are the three-strike rule, which is a game rule and not a
        // feature — the header's explainer has to survive the switch.
        vm.takeAction(GameAction.BoosterTapped(Consumable.Bone))
        assertEquals(Consumable.Bone, vm.state.boosterPrompt)
    }

    @Test
    fun aSwitchedOffBoosterCannotBeRefilledByAnAdEither() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0))
        val vm = viewModel(cache = cache, config = configOf("features.boosters" to false))

        vm.takeAction(GameAction.BoosterRefillRequested(Consumable.Sniff))

        assertEquals(0, vm.state.sniffs)
    }

    @Test
    fun withNoConfigTheBoostersAreThere() = runUnitTest {
        val vm = viewModel()

        assertTrue(vm.state.boostersEnabled)

        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))

        assertEquals(Consumable.Sniff, vm.state.boosterPrompt)
    }

    @Test
    fun switchingAchievementsOffHidesTheBadgesAndKeepsTheLog() = runUnitTest {
        val badges = RecordingAchievements(unlocks = listOf(AnyAchievement))
        val vm = viewModel(
            achievements = badges,
            config = configOf("features.achievements" to false),
        )

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }

        assertEquals(emptyList(), vm.state.newBadges)
        assertEquals(
            1,
            badges.recorded.size,
            "the fact log is written either way, or a dark launch loses history",
        )
    }

    @Test
    fun withNoConfigABadgeStillLands() = runUnitTest {
        val badges = RecordingAchievements(unlocks = listOf(AnyAchievement))
        val vm = viewModel(achievements = badges)

        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }

        assertEquals(listOf(AnyAchievement), vm.state.newBadges)
    }

    @Test
    fun clearingALevelOnTheRewardCadencePaysATreat() = runUnitTest {
        val cache = InMemoryAppCache()
        val vm = viewModel(levelId = RewardLevel, cache = cache)
        val before = vm.state.treats

        solveCurrent(vm)

        assertEquals(before + 1, vm.state.treats)
        assertTrue(vm.state.treatAwarded, "the win sheet has to be able to say so")
        assertEquals(before + 1, cache.get().treats, "a reward that is not persisted is not a reward")
    }

    @Test
    fun aLevelOffTheCadencePaysNothing() = runUnitTest {
        val vm = viewModel(levelId = PlainRewardlessLevel)
        val before = vm.state.treats

        solveCurrent(vm)

        assertEquals(before, vm.state.treats)
        assertFalse(vm.state.treatAwarded)
    }

    @Test
    fun theCadenceIsTheConfiguredOneAndNotAHardcodedFive() = runUnitTest {
        // The half that stops a hardcoded cadence passing. Level 201 pays on a
        // schedule of every third and not on the shipped one; 200 is the other
        // way round.
        val config = configOf("boosters.treatSchedule" to everyN(THREE))
        val onThree = viewModel(levelId = PlainRewardlessLevel, config = config)
        val offThree = viewModel(levelId = RewardLevel, config = config)
        val beforeOn = onThree.state.treats
        val beforeOff = offThree.state.treats

        solveCurrent(onThree)
        solveCurrent(offThree)

        assertEquals(beforeOn + 1, onThree.state.treats, "201 is a multiple of 3")
        assertEquals(beforeOff, offThree.state.treats, "200 is not")
    }

    @Test
    fun aCadenceOfZeroPaysNothingRatherThanDividingByIt() = runUnitTest {
        val vm = viewModel(levelId = RewardLevel, config = configOf("boosters.treatSchedule" to everyN(0)))
        val before = vm.state.treats

        solveCurrent(vm)

        assertEquals(before, vm.state.treats)
    }

    @Test
    fun aReplayOfAClearedLevelPaysNothing() = runUnitTest {
        // Otherwise the shortest 4x4 in the pack is an ad-free treat printer.
        val progress = InMemoryProgress()
        progress.onCompleted(RewardLevel, score = 100, paws = 3, timeMs = 1_000)
        val vm = viewModel(levelId = RewardLevel, progress = progress)
        val before = vm.state.treats

        solveCurrent(vm)

        assertEquals(before, vm.state.treats)
        assertFalse(vm.state.treatAwarded)
    }

    @Test
    fun theRewardPushesAHoldingAboveTheRefillFloor() = runUnitTest {
        // SPEC 1.5: `boosters.refillTo` caps the refill, never the holding.
        val cache = InMemoryAppCache()
        cache.set(AppData(treats = ConsumableRefillTo))
        val vm = viewModel(levelId = RewardLevel, cache = cache)

        solveCurrent(vm)

        assertTrue(
            vm.state.treats > vm.state.refillTo,
            "a level reward has to be able to take a holding past the ad's ceiling",
        )
    }

    @Test
    fun theDailyPaysNoLevelReward() = runUnitTest {
        // Daily ids are positions in another pack; daily 200 is not campaign 200.
        val vm = viewModel(levelId = DailyLevel, isDaily = true)
        val before = vm.state.treats

        solveCurrent(vm)

        assertEquals(before, vm.state.treats)
    }

    @Test
    fun theLevelPaneIsToldTheCadenceItShouldAdvertise() = runUnitTest {
        val vm = viewModel(config = configOf("boosters.treatSchedule" to everyN(SEVEN)))

        assertEquals(listOf(TreatBand(fromLevel = 1, everyNLevels = SEVEN)), vm.state.treatBands)
    }

    @Test
    fun proOpensAnAttemptWithAFloorOfBoosters() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0, treats = 0))
        val vm = viewModel(entitlements = ProEntitlements(), cache = cache)

        assertEquals(DefaultProBoosters, vm.state.sniffs)
        assertEquals(DefaultProBoosters, vm.state.treats)
        assertEquals(DefaultProBoosters, cache.get().sniffs)
        assertEquals(DefaultProBoosters, cache.get().treats)
    }

    @Test
    fun theProFloorIsTheConfiguredOne() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0, treats = 0))
        val vm = viewModel(
            entitlements = ProEntitlements(),
            cache = cache,
            config = configOf(
                "boosters.proSniffsPerAttempt" to 1,
                "boosters.proTreatsPerAttempt" to 2,
            ),
        )

        assertEquals(1, vm.state.sniffs)
        assertEquals(2, vm.state.treats)
    }

    @Test
    fun theProFloorNeverTakesAStashAway() = runUnitTest {
        // The failure the "replace the holding" reading would ship: a paying
        // player opens their next board with three and six treats are gone.
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 9, treats = 9))
        val vm = viewModel(entitlements = ProEntitlements(), cache = cache)
        assertEquals(9, vm.state.treats)

        vm.takeAction(GameAction.Retry)

        assertEquals(9, vm.state.sniffs, "a restart is not a reset")
        assertEquals(9, vm.state.treats)
    }

    @Test
    fun aFreePlayerGetsNoPerAttemptTopUp() = runUnitTest {
        val cache = InMemoryAppCache()
        cache.set(AppData(sniffs = 0, treats = 0))
        val vm = viewModel(cache = cache)

        assertEquals(0, vm.state.sniffs)
        assertEquals(0, vm.state.treats)
    }

    @Test
    fun theSkipIsWithheldUntilEnoughAttemptsHaveFailed() = runUnitTest {
        val vm = viewModel()

        loseCurrent(vm)
        assertNull(vm.state.skip, "one loss is not stuck, it is one loss")

        vm.takeAction(GameAction.Retry)
        loseCurrent(vm)

        assertNotNull(vm.state.skip)
        assertEquals(DefaultSkipsPerDay, vm.state.skip?.remainingToday)
    }

    @Test
    fun theAttemptThresholdIsTheConfiguredOne() = runUnitTest {
        // Against the default of 2 this first loss offers nothing, so the
        // config value is what the assertion is actually about.
        val vm = viewModel(config = configOf("progression.skipAfterFailedAttempts" to 1))

        loseCurrent(vm)

        assertNotNull(vm.state.skip)
    }

    @Test
    fun theSkipIsNeverOfferedOnTheDaily() = runUnitTest {
        val vm = viewModel(
            levelId = DailyLevel,
            isDaily = true,
            config = configOf("progression.skipAfterFailedAttempts" to 1),
        )

        loseCurrent(vm)

        assertNull(vm.state.skip, "there is no next daily to skip to")
    }

    @Test
    fun takingTheSkipRecordsItAndOpensTheNextLevel() = runUnitTest {
        val skips = FakeSkips()
        val progress = InMemoryProgress()
        val vm = viewModel(
            skips = skips,
            progress = progress,
            config = configOf("progression.skipAfterFailedAttempts" to 1),
        )
        loseCurrent(vm)

        vm.takeAction(GameAction.SkipLevel)

        assertEquals(listOf(PlainLevel), skips.skipped)
        assertEquals(PlainLevel + 1, vm.state.level?.id)
        assertEquals(GamePhase.Playing, vm.state.phase)
        assertNull(vm.state.skip, "a fresh attempt has not earned an offer")
    }

    @Test
    fun aSpentAllowanceStillShowsTheOfferAndSaysItIsSpent() = runUnitTest {
        val vm = viewModel(
            skips = FakeSkips(remaining = 0),
            config = configOf("progression.skipAfterFailedAttempts" to 1),
        )

        loseCurrent(vm)

        val skip = assertNotNull(vm.state.skip)
        assertFalse(skip.available, "a player who has failed twice should be told the cap exists")
    }

    @Test
    fun closingTheSkipAdEarlyLeavesTheBoardWhereItWas() = runUnitTest {
        val vm = viewModel(
            skips = FakeSkips(declines = true),
            config = configOf("progression.skipAfterFailedAttempts" to 1),
        )
        loseCurrent(vm)

        vm.takeAction(GameAction.SkipLevel)

        assertEquals(PlainLevel, vm.state.level?.id)
        assertEquals(GamePhase.Lost, vm.state.phase)
    }

    @Test
    fun proSeesTheSkipAsFreeSoItIsNotBadgedWithAnAd() = runUnitTest {
        val vm = viewModel(
            entitlements = ProEntitlements(),
            config = configOf("progression.skipAfterFailedAttempts" to 1),
        )

        loseCurrent(vm)

        assertTrue(vm.state.skip?.free == true)
    }

    @Test
    fun theHeaderCarriesEveryBoardEverPaidFor() = runUnitTest {
        // The bug this whole item is about: the header showed the *attempt's*
        // score, so every campaign level opened at zero and the player
        // concluded scoring was broken.
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 5_000, paws = 2, timeMs = 9_000)
        val daily = FakeDaily(history = listOf(day(dayOfMonth = 1, score = 4_000)))

        val vm = viewModel(progress = progress, daily = daily)

        assertEquals(0, vm.state.score.total, "a fresh attempt has earned nothing yet")
        assertEquals(
            9_000,
            vm.state.lifetimeScore,
            "one score: a cleared level and a daily board both pay into it",
        )
    }

    @Test
    fun theHeaderClimbsWithEveryDogPlaced() = runUnitTest {
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 5_000, paws = 2, timeMs = 9_000)
        val vm = viewModel(progress = progress)

        vm.commit(cellFor(row = 0))

        assertTrue(vm.state.attemptScore > 0, "the fixture has to actually score")
        assertEquals(5_000 + vm.state.attemptScore, vm.state.lifetimeScore)
    }

    @Test
    fun replayingAClearedLevelCannotBankItTwice() = runUnitTest {
        // The failure worth guarding: adding the attempt to the banked total
        // would make replaying one easy level the fastest way to earn in the
        // game. The number may only move by however much this run beats the
        // old best on the same board.
        val vm = viewModel()
        solve(vm)
        val afterFirstClear = vm.state.lifetimeScore
        assertTrue(afterFirstClear > 0, "the first clear has to bank something")

        vm.takeAction(GameAction.Retry)
        assertEquals(
            afterFirstClear,
            vm.state.lifetimeScore,
            "a fresh attempt starts from what is already banked, not from it plus itself",
        )

        solve(vm)

        assertEquals(maxOf(afterFirstClear, vm.state.attemptScore), vm.state.lifetimeScore)
        assertTrue(
            vm.state.lifetimeScore < afterFirstClear + vm.state.attemptScore,
            "the same board was counted twice: ${vm.state.lifetimeScore}",
        )
    }

    @Test
    fun beatingAnOldScoreMovesTheTotalByTheDifferenceAndNoMore() = runUnitTest {
        // The other half of the same rule, and the one a "just ignore replays"
        // implementation would fail: a better run does have to move the number.
        val progress = InMemoryProgress()
        progress.onCompleted(PlainLevel, score = 1, paws = 1, timeMs = 9_000)
        val vm = viewModel(progress = progress)
        assertEquals(1, vm.state.lifetimeScore)

        solve(vm)

        assertEquals(vm.state.attemptScore, vm.state.lifetimeScore)
        assertEquals(vm.state.attemptScore, progress.record(PlainLevel).bestScore)
    }

    @Test
    fun aLostAttemptBanksNothing() = runUnitTest {
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 5_000, paws = 2, timeMs = 9_000)
        val vm = viewModel(progress = progress)
        vm.commit(cellFor(row = 0))
        assertTrue(vm.state.lifetimeScore > 5_000, "the attempt was climbing")

        repeat(ScoringConfig.MAX_LIVES) { vm.commit(tappableWrongCell(vm)) }

        assertEquals(GamePhase.Lost, vm.state.phase)
        assertEquals(
            5_000,
            vm.state.lifetimeScore,
            "an attempt that banked nothing may not leave points on the header",
        )
    }

    @Test
    fun aDailyClearPaysIntoTheSameTotalAsTheCampaign() = runUnitTest {
        val progress = InMemoryProgress()
        progress.onCompleted(StarterDogLevel, score = 5_000, paws = 2, timeMs = 9_000)
        val daily = FakeDaily(levelId = DailyLevel)
        val vm = viewModel(isDaily = true, daily = daily, progress = progress)

        solveCurrent(vm)

        val written = daily.writes.single()
        assertEquals(vm.state.attemptScore, written.score)
        assertEquals(5_000 + written.score, vm.state.lifetimeScore)
    }

    @Test
    fun aSniffCostsPointsButNeverPaws() = runUnitTest {
        // "How many hints they used" is half the user's ask. The control run is
        // the load-bearing part: asserting only that a sniffed run scores
        // *something* would pass with the cost switched off entirely.
        val control = viewModel()
        solve(control)
        val unaided = control.state.attemptScore

        val progress = InMemoryProgress()
        val vm = viewModel(progress = progress)
        vm.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        vm.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        assertEquals(1, vm.state.boostersUsed, "the fixture has to actually spend a sniff")
        solve(vm)

        assertEquals(unaided, vm.state.score.total, "the same run earned the same points")
        assertTrue(
            vm.state.attemptScore < unaided,
            "a hint has to cost something: banked ${vm.state.attemptScore} against $unaided",
        )
        assertEquals(
            vm.state.attemptScore,
            progress.record(PlainLevel).bestScore,
            "the cost has to reach the record, not just the sheet",
        )
        assertEquals(control.state.paws, vm.state.paws, "help must not put a paw out of reach")
    }

    @Test
    fun twoBoostersCostMoreThanOne() = runUnitTest {
        // A per-attempt flag rather than a count would pass every test above.
        val one = viewModel()
        one.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        one.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        solve(one)

        val two = viewModel()
        two.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        repeat(2) { two.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff)) }
        assertEquals(2, two.state.boostersUsed)
        solve(two)

        assertEquals(one.state.score.total, two.state.score.total, "both runs earned the same")
        assertTrue(two.state.attemptScore < one.state.attemptScore)
    }

    @Test
    fun theBoosterCostIsAConfigValueTheGameActuallyReads() = runUnitTest {
        // The off switch, driven end to end from a config map rather than from
        // `ConfiguredScoring` in isolation — the failure `ConfigValuesAreReadTest`
        // exists for is a key that resolves correctly and reaches no decision.
        val control = viewModel()
        solve(control)

        val free = viewModel(config = configOf("scoring.boosterPenaltyRate" to 0.0))
        free.takeAction(GameAction.BoosterTapped(Consumable.Sniff))
        free.takeAction(GameAction.BoosterConfirmed(Consumable.Sniff))
        solve(free)

        assertEquals(1, free.state.boostersUsed)
        assertEquals(
            control.state.attemptScore,
            free.state.attemptScore,
            "at a rate of zero a hint is free again",
        )
    }

    private fun day(dayOfMonth: Int, score: Int): DailyResult = DailyResult(
        date = LocalDate(2026, 9, dayOfMonth),
        levelIndex = dayOfMonth,
        outcome = DailyOutcome.Completed,
        score = score,
        paws = 3,
        timeMs = 90_000,
    )

    private fun viewModel(
        levelId: Int = PlainLevel,
        isDaily: Boolean = false,
        adGate: AdGate = FixedAdGate(RewardOutcome.Rewarded),
        entitlements: Entitlements = FreeEntitlementsFake(),
        cache: AppCache = InMemoryAppCache(),
        progress: ProgressRepository = InMemoryProgress(),
        skips: SkipRepository = FakeSkips(),
        daily: DailyRepository = FakeDaily(),
        achievements: AchievementsRepository = RecordingAchievements(),
        streak: StreakRepository = SilentStreak(),
        config: AppConfigMap = configOf(),
    ) = GameViewModel(
        levelId,
        isDaily,
        adGate,
        entitlements,
        clock,
        cache,
        progress,
        skips,
        daily,
        achievements,
        // Fixed rather than the system clock: `localHour` is an input to the
        // time-of-day badges, so a test that read the real clock would pass or
        // fail depending on when it ran.
        streak = streak,
        wallClock = FixedClock,
        deviceTimeZone = { TimeZone.UTC },
        scoringConfig = scoringFrom(config),
        startingSniffs = BoostersStartingSniffs(config),
        startingTreats = BoostersStartingTreats(config),
        refillTo = BoostersRefillTo(config),
        treatSchedule = BoostersTreatSchedule(config),
        proSniffsPerAttempt = BoostersProSniffsPerAttempt(config),
        proTreatsPerAttempt = BoostersProTreatsPerAttempt(config),
        skipAfterFailedAttempts = ProgressionSkipAfterFailedAttempts(config),
        achievementsEnabled = FeatureAchievements(config),
        boostersEnabled = FeatureBoosters(config),
    )

    /**
     * Every [GameEvent] the ViewModel emits from here on, in order.
     *
     * Collected into `backgroundScope` rather than asserted one at a time: the
     * event channel is unbounded, so anything sent before the collector starts
     * still arrives, and a list reads better than a receive per assertion.
     */
    private fun TestScope.eventsOf(vm: GameViewModel): List<GameEvent> {
        val seen = mutableListOf<GameEvent>()
        vm.eventFlow.onEach { seen += it }.launchIn(backgroundScope)
        return seen
    }

    private fun scoringFrom(config: AppConfigMap) = ConfiguredScoring(
        ScoringBasePerPlacement(config),
        ScoringCompletionBase(config),
        ScoringComboStep(config),
        ScoringComboMax(config),
        ScoringSpeedWindowMs(config),
        ScoringSpeedMaxMultiplier(config),
        ScoringLivesBonusRate(config),
        ScoringDifficultyBonusRate(config),
        ScoringBoosterPenaltyRate(config),
        ScoringTwoPawFraction(config),
        ScoringThreePawFraction(config),
        ScoringNicePraiseAt(config),
        ScoringGreatPraiseAt(config),
        ScoringExcellentPraiseAt(config),
        ScoringPerfectPraiseAt(config),
    )

    /**
     * Commits a guess: two taps inside the double-tap window. A single tap only
     * ever writes the player's own cross.
     */
    private fun GameViewModel.commit(cell: Int) {
        takeAction(GameAction.CellTapped(cell))
        takeAction(GameAction.CellTapped(cell))
        clock += SettleGap
        settle()
    }

    /** A tap far enough after the last one that it cannot read as a commit. */
    private fun GameViewModel.note(cell: Int) {
        takeAction(GameAction.CellTapped(cell))
        clock += SettleGap
        settle()
    }

    private fun solve(vm: GameViewModel) {
        (0 until level.size).forEach { row -> vm.commit(cellFor(row)) }
    }

    /** Solves whatever board the ViewModel actually opened, pack and all. */
    private fun solveCurrent(vm: GameViewModel) {
        val open = assertNotNull(vm.state.level, "nothing to solve — the board never opened")
        (0 until open.size).forEach { row ->
            vm.commit(open.board.cellAt(row, open.solution[row]))
        }
    }

    /** Spends every bone on the open board. */
    private fun loseCurrent(vm: GameViewModel) {
        val open = assertNotNull(vm.state.level)
        repeat(ScoringConfig.MAX_LIVES) { strike ->
            val row = strike % open.size
            val col = (0 until open.size).first { it != open.solution[row] }
            vm.commit(open.board.cellAt(row, col))
        }
    }

    private fun completedToday(): DailyResult = DailyResult(
        date = DailyDate,
        levelIndex = DailyLevel - 1,
        outcome = DailyOutcome.Completed,
        score = 4_200,
        paws = 3,
        timeMs = 90_000,
    )

    private fun cellFor(row: Int): Int = level.board.cellAt(row, level.solution[row])

    private fun wrongCellIn(row: Int): Int {
        val col = (0 until level.size).first { it != level.solution[row] }
        return level.board.cellAt(row, col)
    }

    /**
     * A wrong cell the player could actually tap right now.
     *
     * Not the same as "any wrong cell": auto-mark rules cells out as dogs land,
     * and a tap on a marked cell is correctly ignored. Picking blind made two
     * tests assert on strikes that the game had every right to swallow.
     */
    private fun tappableWrongCell(vm: GameViewModel): Int =
        (0 until level.board.cellCount).first { cell ->
            val row = level.board.rowOf(cell)
            level.board.colOf(cell) != level.solution[row] &&
                cell !in vm.state.autoMarks &&
                cell !in vm.state.manualMarks &&
                cell !in vm.state.placedCells
        }

    private companion object {
        const val FixedHour = 12

        /** Arbitrary, and deliberately not "today" — nothing here reads a clock. */
        val DailyDate = LocalDate(2026, 9, 7)

        /**
         * A daily id that also exists in the campaign, so a test asserting the
         * right pack is asserting something. Daily 200 is a 6x6, campaign 200 an
         * 8x8.
         */
        const val DailyLevel = 200

        /** Days already in the bag when a daily test starts. */
        const val OpeningStreak = 4

        /** Arbitrary but fixed: 2026-01-01T12:00:00Z, so `localHour` is 12 in UTC. */
        val FixedClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-01-01T12:00:00Z")
        }

        /** Past the starter-dog band, so the board opens empty. */
        const val PlainLevel = 200

        /** Inside the starter-dog band. */
        const val StarterDogLevel = 1

        /** The level the rehearsal opens in front of, and hands back to. */
        const val FirstGuidedLevel = 1

        /**
         * A ceiling on how many steps a script may take, so a lesson that
         * refuses to advance fails the test instead of hanging it.
         */
        const val StepBudget = 20

        /** Long enough that the next tap starts a fresh gesture. */
        val SettleGap = 500.milliseconds

        /** Just past the commit window, so a second tap is a second note. */
        val LateGap = 400.milliseconds

        /**
         * Longer than `scoring.speedWindowMs`, so the speed multiplier is
         * exactly 1.0 and a placement's score is arithmetic a test can state
         * rather than a second copy of the formula.
         */
        val PastSpeedWindow = 20.seconds

        /** Any badge will do; these tests care about whether one is shown. */
        val AnyAchievement = Achievement(AchievementId.FirstSteps, Stat.LevelsCleared, target = 1)

        /** Mirrors `ProgressionSkipsPerDay.default`. */
        const val DefaultSkipsPerDay = 3

        /** Mirrors `BoostersProSniffsPerAttempt` / `BoostersProTreatsPerAttempt`. */
        const val DefaultProBoosters = 3

        /**
         * A campaign level the shipped `boosters.treatSchedule` pays on, and
         * past the starter-dog band so the board opens empty. Level 200 is
         * both: it sits in the every-twenty-fifth band and 200 divides by 25.
         */
        const val RewardLevel = 200

        /** In the same band but not a multiple of it, so a reward there is a bug. */
        const val PlainRewardlessLevel = 201

        const val THREE = 3
        const val SEVEN = 7

        /** A milestone the streak celebrates. */
        const val SEVEN_DAYS = 7

        /** A whole-campaign schedule at one rate, as the config map holds it. */
        fun everyN(rate: Int): List<Map<String, Int>> =
            listOf(TreatBand(fromLevel = 1, everyNLevels = rate)).asFallbackConfig()
    }

    /**
     * In-memory [DailyRepository] that keeps the three rules the game leans on.
     *
     * A date with a result is never overwritten (the primary key does that in
     * Room), `playable` follows from the result exactly as [DailyStatus] defines
     * it, and **a clear moves the streak**. The last one matters: a ViewModel
     * that read the streak before writing the clear would still look right
     * against a fake whose streak never changed.
     */
    private class FakeDaily(
        date: LocalDate = DailyDate,
        levelId: Int = DailyLevel,
        streak: Int = OpeningStreak,
        enabled: Boolean = true,
        result: DailyResult? = null,
        /** Days already in the bag, for anything that folds over the history. */
        history: List<DailyResult> = emptyList(),
        freezeOffer: FreezeOffer? = null,
        restoreOffer: RestoreOffer? = null,
        private val freezeResult: FreezeResult = FreezeResult.NothingToFreeze,
        private val restoreResult: RestoreResult = RestoreResult.NothingToRestore,
        private val freezeThrows: Boolean = false,
    ) : DailyRepository {

        val writes = history.toMutableList()
        var freezesRequested = 0
            private set
        var restoresRequested = 0
            private set

        private val state = MutableStateFlow(
            DailyStatus(
                date = date,
                packIndex = levelId - 1,
                levelId = levelId,
                result = result,
                streak = streak,
                freezeOffer = freezeOffer,
                restoreOffer = restoreOffer,
                resetsIn = 6.hours,
                enabled = enabled,
            ),
        )

        override fun observe(): Flow<DailyStatus> = state

        override suspend fun status(): DailyStatus = state.value

        override suspend fun history(): List<DailyResult> = writes.toList()

        override suspend fun onCompleted(date: LocalDate, score: Int, paws: Int, timeMs: Long) {
            write(date, DailyOutcome.Completed, score, paws, timeMs)
        }

        override suspend fun onFailed(date: LocalDate, timeMs: Long) {
            write(date, DailyOutcome.Failed, score = 0, paws = 0, timeMs = timeMs)
        }

        override suspend fun useFreeze(): FreezeResult {
            freezesRequested++
            if (freezeThrows) error("no ad service")
            return freezeResult
        }

        override suspend fun restoreStreak(): RestoreResult {
            restoresRequested++
            if (freezeThrows) error("no ad service")
            return restoreResult
        }

        override suspend fun reset() {
            writes.clear()
        }

        private fun write(
            date: LocalDate,
            outcome: DailyOutcome,
            score: Int,
            paws: Int,
            timeMs: Long,
        ) {
            if (writes.any { it.date == date }) return
            val snapshot = state.value
            val result = DailyResult(date, snapshot.packIndex, outcome, score, paws, timeMs)
            writes += result
            if (date != snapshot.date) return
            state.value = snapshot.copy(
                result = result,
                streak = if (outcome == DailyOutcome.Completed) {
                    snapshot.streak + 1
                } else {
                    snapshot.streak
                },
            )
        }
    }

    /** In-memory [AppCache], so a settings toggle can be asserted without disk. */
    /**
     * A streak that never wants the screen.
     *
     * The default for every test here, because a ceremony firing mid-assertion
     * would make an unrelated test about the win path fail for a reason that has
     * nothing to do with it. The tests that care about the ceremony hand in one
     * that answers.
     */
    private class SilentStreak(
        private val prompt: StreakPrompt = StreakPrompt.None,
    ) : StreakRepository {
        val shown = mutableListOf<StreakPrompt>()
        override fun observe(): Flow<StreakSummary> = flowOf(empty)
        override suspend fun summary(): StreakSummary = empty
        override suspend fun pendingPrompt(): StreakPrompt = prompt
        override suspend fun onPromptShown(prompt: StreakPrompt) { shown += prompt }
        override suspend fun reset() = Unit

        private val empty = StreakSummary(
            current = 0,
            longest = 0,
            today = LocalDate(2026, 1, 1),
            days = emptyList(),
            enabled = true,
        )
    }

    private class InMemoryAppCache : AppCache {
        private val state = MutableStateFlow(AppData())
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData) { state.value = value }
        override suspend fun clear() { state.value = AppData() }
    }

    /**
     * A cache whose writes fail.
     *
     * Reads still work, so a board opens normally and only the persisting half
     * breaks. That is the shape of a real storage failure, and it is the one
     * that turns "we remembered that for you" into "you are stuck".
     */
    private class ThrowingAppCache : AppCache {
        private val state = MutableStateFlow(AppData())
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData): Unit = error("disk is full")
        override suspend fun clear(): Unit = error("disk is full")
    }

    /**
     * In-memory [ProgressRepository].
     *
     * Keeps the two rules the game leans on — a metric only ever improves, and
     * clearing a level opens the next — so a test asserting on either is
     * asserting the contract the Room implementation also has to meet. It keeps
     * the *unclamped* `unlockedThrough`, one past the end of the pack after the
     * last level, because clamping that is the game's job.
     */
    /** Keeps every attempt handed to it, so a test can assert on what was recorded. */
    private class RecordingAchievements(
        /** What every recorded attempt unlocks. Empty is the normal answer. */
        private val unlocks: List<Achievement> = emptyList(),
    ) : AchievementsRepository {
        val recorded = mutableListOf<LevelResult>()
        private var state = AchievementState.Empty

        override fun observe(): Flow<AchievementState> = flowOf(state)

        override suspend fun state(): AchievementState = state

        override suspend fun record(result: LevelResult): List<Achievement> {
            recorded += result
            return unlocks
        }

        override suspend fun reset() {
            recorded.clear()
            state = AchievementState.Empty
        }
    }

    private class InMemoryProgress : ProgressRepository {
        private val records = mutableMapOf<Int, LevelRecord>()

        override fun observe(levelId: Int): Flow<LevelRecord> =
            flowOf(records[levelId] ?: LevelRecord.unplayed(levelId))

        override suspend fun record(levelId: Int): LevelRecord =
            records[levelId] ?: LevelRecord.unplayed(levelId)

        override suspend fun all(): List<LevelRecord> = records.values.sortedBy { it.levelId }

        override suspend fun unlockedThrough(): Int = records.values
            .filter { it.state != LevelState.Locked }
            .maxOfOrNull { it.levelId }
            ?: LevelRecord.FIRST_LEVEL_ID

        override suspend fun onAttemptStarted(levelId: Int) {
            val current = record(levelId)
            records[levelId] = current.copy(
                state = current.state.orUnlocked(),
                attempts = current.attempts + 1,
            )
        }

        override suspend fun onCompleted(levelId: Int, score: Int, paws: Int, timeMs: Long) {
            val current = record(levelId)
            records[levelId] = current.copy(
                state = LevelState.Completed,
                bestScore = maxOf(current.bestScore, score),
                bestPaws = maxOf(current.bestPaws, paws),
                bestTimeMs = if (current.bestTimeMs == 0L) {
                    timeMs
                } else {
                    minOf(current.bestTimeMs, timeMs)
                },
            )
            unlock(levelId + 1)
        }

        override suspend fun onSkipped(levelId: Int) {
            val current = record(levelId)
            if (current.state != LevelState.Completed) {
                records[levelId] = current.copy(state = LevelState.Skipped)
            }
            unlock(levelId + 1)
        }

        override suspend fun reset() {
            records.clear()
        }

        private suspend fun unlock(levelId: Int) {
            records[levelId] = record(levelId).let { it.copy(state = it.state.orUnlocked()) }
        }

        private fun LevelState.orUnlocked(): LevelState =
            if (this == LevelState.Locked) LevelState.Unlocked else this
    }

    /**
     * In-memory [SkipRepository] that keeps the one rule the game leans on: the
     * allowance goes *down* when a skip is taken. A fake whose count never moved
     * would let a ViewModel that offered an infinite skip look correct.
     */
    private class FakeSkips(
        private var remaining: Int = DefaultSkipsPerDay,
        private val declines: Boolean = false,
    ) : SkipRepository {
        val skipped = mutableListOf<Int>()

        override suspend fun remainingToday(): Int = remaining

        override suspend fun skip(levelId: Int): SkipResult {
            if (remaining <= 0) return SkipResult.NoneLeft
            if (declines) return SkipResult.Declined
            remaining--
            skipped += levelId
            return SkipResult.Skipped(remaining)
        }
    }

    private class FixedAdGate(private val outcome: RewardOutcome) : AdGate {
        var rewardedShown = 0
            private set

        override suspend fun showRewarded(placement: AdPlacement): RewardOutcome {
            rewardedShown++
            return outcome
        }

        override fun preload(placement: AdPlacement) = Unit
    }

    private class FreeEntitlementsFake : Entitlements {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
        override suspend fun purchasePro(trigger: String?) = PurchaseOutcome.Unavailable
        override suspend fun restore() = RestoreOutcome.NothingToRestore
    }

    private class ProEntitlements : Entitlements {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(true)
        override suspend fun purchasePro(trigger: String?) = PurchaseOutcome.AlreadyOwned
        override suspend fun restore() = RestoreOutcome.Restored
    }
}
