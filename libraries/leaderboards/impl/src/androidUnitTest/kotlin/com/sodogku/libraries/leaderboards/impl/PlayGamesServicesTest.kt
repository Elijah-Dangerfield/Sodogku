package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.LogEntry
import com.sodogku.libraries.core.logging.LogId
import com.sodogku.libraries.core.logging.LogLevel
import com.sodogku.libraries.core.logging.LogTree
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.flowroutines.testing.CoroutineTest
import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.Leaderboard
import com.sodogku.libraries.leaderboards.SubmitResult
import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.sodogku.AppEventBus
import com.sodogku.libraries.sodogku.AppEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Android binding's policy, which is where the two Play-specific traps live.
 *
 * **A player with no Games profile is not an error.** Play Games answers `false`
 * for them and it answers `false` a lot — a Google account is not a Games
 * profile, and the SDK's automatic sign-in comes back negative for anyone who
 * has never made one. So `aPlayerWithNoGamesProfileIsOfferedTheEntryPointRather
 * ThanAnError` asserts two things at once: the status that lets the Settings row
 * be drawn, and that nothing was logged at error while getting there. The second
 * half is the one that would otherwise ship a crash report per launch for a
 * state the app is built to shrug off.
 *
 * **A score has to go to the Play id.** The two id spaces look alike enough in a
 * diff that submitting `appleId` from here is a plausible edit, and Play would
 * answer the same way it answers a board that does not exist: silently. So the
 * submission tests assert on the exact string that reached the platform.
 *
 * NOT covered here: `GmsPlayGamesApi`, which is the Activity and `Task` plumbing
 * and needs a device — see its own KDoc for why the split exists. Nor the
 * holding and improvement rules, which are common to both platforms and belong
 * to `RealLeaderboardsTest`.
 */
class PlayGamesServicesTest : CoroutineTest() {

    private val foregrounds = MutableSharedFlow<AppEvent>(replay = 1)
    private val play = FakePlayGamesApi()
    private val errors = ErrorCapture()

    @AfterTest
    fun uprootTheTree() {
        KLog.uproot(errors)
    }

    /**
     * Started, because production always is: `RealLeaderboards` is an
     * `AutoInit` and calls `startAuthentication` from its own constructor. What
     * a test controls is the foreground, not the start.
     */
    private fun services() = PlayGamesServices(
        play = play,
        appScope = AppCoroutineScope(dispatchers),
        appEventsProvider = { AppEvents(NoBus, foregrounds.asSharedFlow()) },
    ).also { it.startAuthentication() }

    private suspend fun foreground(isColdBoot: Boolean = true) {
        foregrounds.emit(AppEvent.OnForeground(isColdBoot = isColdBoot))
    }

    // ------------------------------------------------------------------
    // Resolving, and what is offered while it is unresolved
    // ------------------------------------------------------------------

    @Test
    fun nothingIsOfferedUntilPlayGamesHasBeenAsked() = runUnitTest {
        // Play Games needs a foreground Activity and RealLeaderboards is an
        // AutoInit, so `startAuthentication` runs in Application.onCreate with
        // no Activity anywhere. Everything between there and the first
        // foreground is Unknown, and Unknown must not draw a row that would
        // open onto nothing.
        val services = services()
        val leaderboards = RealLeaderboards(services, AppCoroutineScope(dispatchers))

        assertEquals(GameServicesStatus.Unknown, services.status.value)
        assertFalse(leaderboards.isOfferable.value)
        assertEquals(0, play.signInChecks, "Play Games must not be asked without an Activity")
    }

    @Test
    fun aPlayerWithNoGamesProfileIsOfferedTheEntryPointRatherThanAnError() = runUnitTest {
        KLog.plant(errors)
        play.signedIn = false
        val services = services()
        val leaderboards = RealLeaderboards(services, AppCoroutineScope(dispatchers))

        foreground()

        assertEquals(GameServicesStatus.SignInRequired, services.status.value)
        assertTrue(leaderboards.isOfferable.value, "there is a way in, so offer it")
        assertEquals(emptyList(), errors.messages, "a missing Games profile is not a fault")
    }

    @Test
    fun aSignedInPlayerIsAuthenticated() = runUnitTest {
        play.signedIn = true

        val services = services()
        foreground()

        assertEquals(GameServicesStatus.Authenticated, services.status.value)
    }

    @Test
    fun aDeviceWithoutPlayGamesOffersNothing() = runUnitTest {
        // No Play services, no foreground Activity, a wedged SDK: the api layer
        // collapses all of them to "would not answer", and all of them have to
        // end with the game not noticing.
        KLog.plant(errors)
        play.signedIn = null
        val services = services()
        val leaderboards = RealLeaderboards(services, AppCoroutineScope(dispatchers))

        foreground()

        assertEquals(GameServicesStatus.Unavailable, services.status.value)
        assertFalse(leaderboards.isOfferable.value)
        assertEquals(emptyList(), errors.messages)
    }

    @Test
    fun signingInInAnotherAppAndComingBackIsNoticed() = runUnitTest {
        // The reason the status hangs off the foreground edge rather than one
        // question at boot. GameKit re-fires its handler; Play Games has no
        // equivalent, and a player who taps through to make a profile comes
        // back through exactly this door.
        play.signedIn = false
        val services = services()
        foreground()
        assertEquals(GameServicesStatus.SignInRequired, services.status.value)

        play.signedIn = true
        foreground(isColdBoot = false)

        assertEquals(GameServicesStatus.Authenticated, services.status.value)
    }

    // ------------------------------------------------------------------
    // Submitting
    // ------------------------------------------------------------------

    @Test
    fun aScoreGoesToThePlayIdAndNeverTheGameCenterOne() = runUnitTest {
        play.signedIn = true
        play.playIdOf(Leaderboard.LifetimeScore, MINTED_LIFETIME_ID)
        val services = services()
        foreground()

        val result = services.submit(Leaderboard.LifetimeScore, 4_200L)

        assertEquals(SubmitResult.Submitted, result)
        assertEquals(listOf(MINTED_LIFETIME_ID to 4_200L), play.submissions)
    }

    @Test
    fun aBoardWithNoPlayIdIsNotSubmittedToAtAll() = runUnitTest {
        // WeeklyScore permanently, and the other two until somebody pastes an
        // id out of the Play Console. Sending a blank id is an SDK error, and an
        // SDK error is one more way for this to be loud about something the
        // player cannot act on.
        play.signedIn = true
        val services = services()
        foreground()

        val result = services.submit(Leaderboard.WeeklyScore, 900L)

        assertEquals(SubmitResult.Failed, result)
        assertTrue(play.submissions.isEmpty())
    }

    @Test
    fun nothingIsSubmittedBeforeSignInHasSucceeded() = runUnitTest {
        play.signedIn = false
        play.playIdOf(Leaderboard.LifetimeScore, MINTED_LIFETIME_ID)
        val services = services()
        foreground()

        val result = services.submit(Leaderboard.LifetimeScore, 4_200L)

        assertEquals(SubmitResult.NotAuthenticated, result)
        assertTrue(play.submissions.isEmpty())
    }

    @Test
    fun aRefusedSubmissionIsReportedAsFailedSoTheValueIsHeld() = runUnitTest {
        // The value is worth more than the attempt: RealLeaderboards keeps a
        // Failed one and retries it on the next flush, and swallowing it here
        // as Submitted would lose the player's score with no trace anywhere.
        play.signedIn = true
        play.playIdOf(Leaderboard.LifetimeScore, MINTED_LIFETIME_ID)
        play.accepts = false
        val services = services()
        foreground()

        assertEquals(SubmitResult.Failed, services.submit(Leaderboard.LifetimeScore, 4_200L))
    }

    // ------------------------------------------------------------------
    // The window Play does not have
    // ------------------------------------------------------------------

    @Test
    fun noBoardHasAWindowOnAndroidAndTheLedgerIsNeverRead() = runUnitTest {
        // Play Games has no recurring board and no occurrence to have a start.
        // Answering null rather than a plausible Monday is what stops a week's
        // points being filed against a window that does not exist — and it is
        // also what keeps the score ledger unread on a platform that cannot use
        // the answer.
        play.signedIn = true
        val services = services()
        foreground()
        Leaderboard.entries.forEach { board ->
            assertNull(services.currentWindowStart(board), "${board.name} cannot have a window")
        }

        var ledgerRead = false
        RealLeaderboards(services, AppCoroutineScope(dispatchers))
            .submitWindowed(Leaderboard.WeeklyScore) {
                ledgerRead = true
                900L
            }

        assertFalse(ledgerRead, "Android must not price a board it cannot submit")
        assertTrue(play.submissions.isEmpty())
    }

    // ------------------------------------------------------------------
    // The dashboard, which is the only thing allowed on screen
    // ------------------------------------------------------------------

    @Test
    fun theSignInSheetIsOnlyRaisedWhenThePlayerAsksForTheDashboard() = runUnitTest {
        play.signedIn = false
        val services = services()
        foreground()
        assertEquals(0, play.signInPrompts, "a launch may not put a sheet on screen")

        play.signInPromptSucceeds = true
        services.presentDashboard(null)

        assertEquals(1, play.signInPrompts)
        assertEquals(GameServicesStatus.Authenticated, services.status.value)
        assertEquals(listOf<String?>(null), play.opened)
    }

    @Test
    fun aDeclinedSignInSheetOpensNothingAndCostsNothing() = runUnitTest {
        KLog.plant(errors)
        play.signedIn = false
        play.signInPromptSucceeds = false
        val services = services()
        foreground()

        services.presentDashboard(null)

        assertTrue(play.opened.isEmpty(), "nothing to show a player who declined")
        assertEquals(GameServicesStatus.SignInRequired, services.status.value)
        assertEquals(emptyList(), errors.messages, "declining is a choice, not a fault")
    }

    @Test
    fun askingForABoardWithNoPlayIdOpensTheWholeList() = runUnitTest {
        // Rather than nothing. The dashboard is the answer to "show me the
        // leaderboards", and Play's own list is a better answer than a tap that
        // does nothing.
        play.signedIn = true
        val services = services()
        foreground()

        services.presentDashboard(Leaderboard.WeeklyScore)

        assertEquals(listOf<String?>(null), play.opened)
    }

    @Test
    fun askingForABoardWithAPlayIdOpensThatBoard() = runUnitTest {
        play.signedIn = true
        play.playIdOf(Leaderboard.LongestStreak, MINTED_STREAK_ID)
        val services = services()
        foreground()

        services.presentDashboard(Leaderboard.LongestStreak)

        assertEquals(listOf<String?>(MINTED_STREAK_ID), play.opened)
    }

    private companion object {
        /**
         * Shaped like the opaque ids the Play Console mints, and deliberately
         * nothing like a Game Center id: the point of the submission tests is
         * that the wrong one is visibly wrong.
         */
        const val MINTED_LIFETIME_ID = "CgkI1s6Wl-0dEAIQAQ"
        const val MINTED_STREAK_ID = "CgkI1s6Wl-0dEAIQAg"
    }
}

/**
 * [PlayGamesApi] with the Activity and the `Task` taken out, which is all that
 * was ever standing between these rules and a test.
 *
 * The board ids are a map rather than the real `Leaderboard.playId` because
 * those are empty until somebody has been in the Play Console, and a test suite
 * that only ever sees empty cannot tell a right id from a wrong one.
 */
private class FakePlayGamesApi : PlayGamesApi {

    /** `null` is Play declining to answer, which is not the same as `false`. */
    var signedIn: Boolean? = null

    var signInPromptSucceeds: Boolean? = null

    var accepts: Boolean = true

    var signInChecks = 0
        private set

    var signInPrompts = 0
        private set

    val submissions = mutableListOf<Pair<String, Long>>()

    val opened = mutableListOf<String?>()

    private val minted = mutableMapOf<Leaderboard, String>()

    /** Stands in for a trip to the Play Console. */
    fun playIdOf(board: Leaderboard, id: String) {
        minted[board] = id
    }

    override fun leaderboardId(board: Leaderboard): String? = minted[board]

    override suspend fun isSignedIn(): Boolean? {
        signInChecks++
        return signedIn
    }

    override suspend fun promptSignIn(): Boolean? {
        signInPrompts++
        return signInPromptSucceeds
    }

    override suspend fun submitScore(leaderboardId: String, value: Long): Boolean {
        submissions += leaderboardId to value
        return accepts
    }

    override suspend fun showLeaderboard(leaderboardId: String?): Boolean {
        opened += leaderboardId
        return true
    }
}

/** Captures anything logged at error, which is the level this must never use. */
private class ErrorCapture : LogTree() {

    private val captured = mutableListOf<String>()

    val messages: List<String> get() = captured.toList()

    override fun isLoggable(level: LogLevel, tag: String?): Boolean =
        level.priority >= LogLevel.Error.priority

    override fun log(entry: LogEntry): LogId? {
        if (entry.level.priority >= LogLevel.Error.priority) {
            captured += entry.message ?: entry.throwable?.message.orEmpty()
        }
        return null
    }
}

/** [AppEvents] wraps a bus it never reaches for when it is handed a flow. */
private object NoBus : AppEventBus {
    override fun dispatch(event: AppEvent) = Unit
    override fun eventStream(): Flow<AppEvent> = MutableSharedFlow()
    override fun liveEventStream(): Flow<AppEvent> = MutableSharedFlow()
}
