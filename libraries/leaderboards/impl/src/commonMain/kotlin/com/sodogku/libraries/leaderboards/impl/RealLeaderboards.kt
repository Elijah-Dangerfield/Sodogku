package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.core.AutoInit
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.logging.logEvent
import com.sodogku.libraries.flowroutines.AppCoroutineScope
import com.sodogku.libraries.leaderboards.GameServices
import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.Leaderboard
import com.sodogku.libraries.leaderboards.Leaderboards
import com.sodogku.libraries.leaderboards.NoLeaderboards
import com.sodogku.libraries.leaderboards.SubmitResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The real [Leaderboards]: when a number is worth sending, and what happens to
 * one that could not be sent yet.
 *
 * ## The case this exists for
 *
 * Authentication is asynchronous and slow, and finishing a board is fast. A
 * player who launches the app and clears a level in eight seconds has produced
 * a score before Game Center has decided who they are. Submitting it then does
 * nothing, and without somewhere to put it the score is lost until the *next*
 * board, which on the first session is often never. So an unsendable value is
 * held, one slot per board, newest wins, and flushed the moment authentication
 * lands. That single behaviour is most of what this class is.
 *
 * The held values are in memory only. A process death loses them, which is
 * correct rather than a shortcut: every value here is a running total that the
 * next completed board will recompute and resend, so persisting them would be
 * caching something already on disk in a more fragile form.
 *
 * ## The other half: not sending
 *
 * [submitted] remembers the best value the platform has accepted this process,
 * and anything no better is dropped before it reaches the network. A lifetime
 * score is submitted after every board and only changes on a personal best, so
 * without this the app would spend a network call per level to tell Game Center
 * a number it already has.
 *
 * ## Failure
 *
 * Every exit is silent. A refusal, an error, an exception out of the platform
 * seam, a submission for a board id that does not exist in App Store Connect:
 * all of them leave the game exactly as it was. The only trace is a log line
 * and, on success, an app event.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = Leaderboards::class,
    replaces = [NoLeaderboards::class],
)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class RealLeaderboards(
    private val services: GameServices,
    private val appScope: AppCoroutineScope,
) : Leaderboards, AutoInit {

    private val logger = KLog.withTag("Leaderboards")

    /** Guards [pending] and [submitted], which are read-modify-write from several coroutines. */
    private val lock = Mutex()

    /** The best value per board that has not been accepted yet. At most one entry per board. */
    private val pending = mutableMapOf<Leaderboard, Long>()

    /** The best value per board the platform has accepted this process. */
    private val submitted = mutableMapOf<Leaderboard, Long>()

    override val isOfferable: StateFlow<Boolean> = services.status
        .map { it == GameServicesStatus.Authenticated || it == GameServicesStatus.SignInRequired }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    init {
        // Apple asks for authentication as early as possible, and this is a
        // singleton nothing touches until the player finishes a board, so
        // without the AutoInit marker above it would first run at the exact
        // moment a score needs sending.
        services.startAuthentication()

        appScope.launch {
            services.status.collect { status ->
                if (status == GameServicesStatus.Authenticated) flush()
            }
        }
    }

    override fun submit(board: Leaderboard, value: Long) {
        appScope.launch { record(board, value) }
    }

    override fun openDashboard(board: Leaderboard?) {
        appScope.launch {
            Catching { services.presentDashboard(board?.id) }
                .logOnFailure { "Could not present the leaderboard dashboard" }
        }
    }

    private suspend fun record(board: Leaderboard, value: Long) {
        // Note there is no separate guard for zero or a negative, which is what
        // a fresh install reports for both boards. Nothing has been accepted
        // yet, so [submitted] reads as 0 and the improvement test below already
        // drops them. A second guard would be a second thing to keep in step.
        val worthSending = lock.withLock {
            if (value <= submitted.bestFor(board)) {
                false
            } else {
                pending[board] = maxOf(pending.bestFor(board), value)
                true
            }
        }
        if (worthSending) flush()
    }

    private suspend fun flush() {
        if (services.status.value != GameServicesStatus.Authenticated) return

        // The whole flush holds the lock, including the network call inside it.
        // Nothing on a screen is waiting on this, and the alternative is two
        // concurrent flushes sending the same value twice.
        lock.withLock {
            pending.toMap().forEach { (board, value) -> send(board, value) }
        }
    }

    private suspend fun send(board: Leaderboard, value: Long) {
        val result = Catching { services.submit(board.id, value) }
            .logOnFailure { "Leaderboard submit threw for ${board.id}" }
            .getOrDefault(SubmitResult.Failed)

        if (result != SubmitResult.Submitted) {
            logger.d { "Leaderboard ${board.name} not submitted ($result); holding $value" }
            return
        }

        // Both writes can be plain rather than a max, because flush holds the
        // lock across the whole network call: nothing can have raised either
        // map since this value was read out of it.
        submitted[board] = value
        pending.remove(board)
        logger.logEvent(
            "leaderboard.submitted",
            "board" to board.name,
            "value" to value,
        )
    }

    private fun Map<Leaderboard, Long>.bestFor(board: Leaderboard): Long = this[board] ?: 0L
}
