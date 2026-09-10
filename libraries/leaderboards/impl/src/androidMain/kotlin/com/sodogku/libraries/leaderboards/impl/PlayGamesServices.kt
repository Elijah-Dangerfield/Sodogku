package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.leaderboards.GameServices
import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.Leaderboard
import com.sodogku.libraries.leaderboards.SubmitResult
import com.sodogku.libraries.sodogku.AppEvent
import com.sodogku.libraries.sodogku.AppEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Play Games Services, the Android half of [GameServices].
 *
 * ## A player with no Games profile is the ordinary case
 *
 * Game Center is part of signing into an iPhone. A Play Games profile is not
 * part of signing into an Android phone: plenty of people have a Google account
 * and have never opted into one, and the v2 SDK's automatic sign-in simply
 * comes back false for them. That is a state, not a fault, so it is
 * [GameServicesStatus.SignInRequired] and not [GameServicesStatus.Unavailable],
 * it never logs above info, and nothing is put on screen because of it. The
 * player sees one extra row in Settings, and only finds out Play Games exists if
 * they tap it.
 *
 * The whole of this file is written so that no branch through it can cost the
 * player anything. A wedged SDK, an offline device, a phone with no Play
 * services at all: each of them lands on `Unavailable`, the Settings row is not
 * drawn, and every score carries on being banked locally exactly as before.
 *
 * ## Why the status is re-asked on every foreground
 *
 * GameKit hands over an authentication handler and keeps calling it, so iOS
 * hears about a sign-out the moment it happens. Play Games has no such callback
 * — `isAuthenticated()` is a question, asked once, answered once. The player who
 * taps through to the Play Games app, makes a profile and comes back is the case
 * that matters, and the only signal we get for it is that the app came
 * foreground. So [startAuthentication] subscribes to the foreground edge rather
 * than asking once at boot.
 *
 * That also settles a smaller problem. `RealLeaderboards` is an `AutoInit`, so
 * it calls [startAuthentication] from `Application.onCreate`, where there is no
 * Activity yet and every Play Games entry point needs one. Waiting for the
 * foreground event means waiting for exactly the thing that guarantees an
 * Activity exists.
 *
 * ## What Android does not have
 *
 * [currentWindowStart] is `null` for every board, always. Play Games has no
 * recurring board: it derives daily, weekly and all-time views of one board from
 * the time each score was submitted, so there is no occurrence and no start
 * date to report. `RealLeaderboards` reads a missing window as "do not submit
 * that board", which is the right outcome here rather than a degraded one — the
 * weekly standing an Android player wants is already a tab on the lifetime
 * board. Inventing a Monday and submitting a week's points against it would put
 * a number on a board that means something else.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PlayGamesServices(
    private val play: PlayGamesApi,
    private val appScope: AppCoroutineScope,
    appEventsProvider: () -> AppEvents,
) : GameServices {

    private val logger = KLog.withTag("PlayGames")

    private val appEvents by lazy(appEventsProvider)

    private val state = MutableStateFlow(GameServicesStatus.Unknown)

    override val status: StateFlow<GameServicesStatus> = state.asStateFlow()

    private val watching = AtomicBoolean(false)

    override fun startAuthentication() {
        if (!watching.compareAndSet(false, true)) return

        appScope.launch {
            // The replaying stream rather than live(): this is started from an
            // AutoInit during Application.onCreate, and on a cold boot the
            // foreground event can land either side of that. Replay makes the
            // race not a race. Re-running the check on a foreground we have
            // already handled costs one local call into Play services.
            appEvents.filterIsInstance<AppEvent.OnForeground>().collect { resolve() }
        }
    }

    override suspend fun submit(board: Leaderboard, value: Long): SubmitResult {
        if (state.value != GameServicesStatus.Authenticated) return SubmitResult.NotAuthenticated

        val leaderboardId = play.leaderboardId(board) ?: run {
            logger.d { "${board.name} has no Play Games board, so nothing was sent" }
            return SubmitResult.Failed
        }

        return if (play.submitScore(leaderboardId, value)) {
            SubmitResult.Submitted
        } else {
            SubmitResult.Failed
        }
    }

    /** See the class docs: Play Games has no window to report, for any board. */
    override suspend fun currentWindowStart(board: Leaderboard): Long? = null

    override suspend fun presentDashboard(board: Leaderboard?) {
        // The one call that can put something on screen, and the only place the
        // sign-in sheet is ever raised. A player who has no Games profile gets
        // offered one here, because here is where they asked.
        if (state.value == GameServicesStatus.SignInRequired) {
            val signedIn = play.promptSignIn()
            state.value = signedIn.asStatus()
            if (signedIn != true) return
        }

        if (state.value != GameServicesStatus.Authenticated) return

        // A board with no Play id falls back to the whole list rather than to
        // nothing: the tap meant "show me the leaderboards", and Play's own list
        // is a better answer than a control that does not respond.
        play.showLeaderboard(board?.let(play::leaderboardId))
    }

    private suspend fun resolve() {
        state.value = play.isSignedIn().asStatus()
    }

    /**
     * The one mapping in this file, and the reason it is a function is that
     * getting it wrong is invisible. `false` is a player Play Games answered
     * about, so there is a way in and the entry point is worth drawing; `null`
     * is Play Games declining to answer at all, so there is not.
     */
    private fun Boolean?.asStatus(): GameServicesStatus = when (this) {
        true -> GameServicesStatus.Authenticated
        false -> GameServicesStatus.SignInRequired
        null -> GameServicesStatus.Unavailable
    }
}
