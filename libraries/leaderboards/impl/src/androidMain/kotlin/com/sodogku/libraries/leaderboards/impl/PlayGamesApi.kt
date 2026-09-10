package com.sodogku.libraries.leaderboards.impl

import android.app.Activity
import android.content.Intent
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.tasks.Task
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.flowroutines.DispatcherProvider
import com.sodogku.libraries.leaderboards.Leaderboard
import com.sodogku.libraries.sodogku.ActivityProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume

/**
 * The four things the app asks Play Games for, with the Activity plumbing and
 * the `Task` callbacks already dealt with.
 *
 * This exists as its own interface, under a seam that is already a seam,
 * because every Play Games entry point needs a foreground `Activity` and
 * answers through a `Task`. Neither of those can be reached from a host-JVM
 * test, so without this split the whole of `PlayGamesServices` would be in the
 * same position as `GameCenterServices` — compiled and never run until it is on
 * a phone. The rules that matter here are policy, not plumbing: a player with no
 * Games profile is a normal state rather than an error, and a score must go to
 * the Play id rather than the Game Center one. Both are worth a test, so the
 * plumbing moved down here and the policy stayed up there.
 *
 * `null` is "Play would not answer": no Play services, no foreground Activity,
 * a failed `Task`, a cancelled one. Everything above reads it as
 * [com.sodogku.libraries.leaderboards.GameServicesStatus.Unavailable] and stops.
 * A `false` is different and much more common — it means Play answered, and the
 * answer was that nobody is signed in.
 */
interface PlayGamesApi {

    /**
     * The id Play Games files [board] under, or `null` when it does not have
     * one.
     *
     * The one place the two id spaces are bridged, and the reason it is here
     * rather than read off the enum at the call site: `appleId` and `playId` sit
     * next to each other on [Leaderboard], reading the wrong one compiles, and
     * Play answers a Game Center id exactly the way it answers a board that does
     * not exist. Silently.
     *
     * `null` covers two cases that end the same way. `WeeklyScore` will never
     * have a Play id, because Play has no recurring board to give it. The other
     * two do not have one *yet*: the console mints the id when the board is
     * created, and until somebody pastes it into [Leaderboard] there is nothing
     * to submit to.
     */
    fun leaderboardId(board: Leaderboard): String?

    /**
     * Whether Play Games already considers this player signed in, without
     * showing anything.
     *
     * The v2 SDK attempts sign-in on its own at process start, so this is
     * usually reading a decision that has already been made. `false` is the
     * ordinary answer for a player who has never opted into a Games profile.
     */
    suspend fun isSignedIn(): Boolean?

    /**
     * Asks Play Games to sign the player in, which puts Google's own sheet on
     * screen. Only ever called because the player tapped something.
     */
    suspend fun promptSignIn(): Boolean?

    /** Sends one score. True when Play accepted it. */
    suspend fun submitScore(leaderboardId: String, value: Long): Boolean

    /**
     * Opens Play Games' own leaderboard screen, on [leaderboardId] when one is
     * given and on the list of all of them when it is not. True when something
     * was actually shown.
     */
    suspend fun showLeaderboard(leaderboardId: String?): Boolean
}

/**
 * [PlayGamesApi] over `play-services-games-v2`.
 *
 * Two details of that SDK shape this file.
 *
 * **Everything needs the foreground Activity**, which the app hands over
 * through [ActivityProvider]. There is no Context-only variant, so a call that
 * arrives while the app is backgrounded cannot be made at all and returns the
 * "would not answer" value rather than waiting for one.
 *
 * **Scores go through `submitScoreImmediate`, not `submitScore`.** The plain
 * `submitScore` returns `void`: it hands the value to Play's own outbox and
 * never says what happened to it. `RealLeaderboards` holds a value until the
 * platform confirms it, so a call that cannot fail is a value it would drop on
 * the floor and call sent. The immediate variant answers through a `Task` and
 * costs one round trip, which is what a truthful answer is worth here.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class GmsPlayGamesApi(
    private val activityProvider: ActivityProvider,
    private val dispatchers: DispatcherProvider,
) : PlayGamesApi {

    private val logger = KLog.withTag("PlayGames")

    override fun leaderboardId(board: Leaderboard): String? = board.playId.ifBlank { null }

    override suspend fun isSignedIn(): Boolean? = when {
        // A build whose boards have not been created in the Play Console has
        // nothing to sign in for and nowhere to send a score, and asking Play
        // about a package it has never heard of is a round trip whose only
        // possible answer is the one below. This is what keeps the feature
        // silent on Android until item 16 of OWNER-TODO is done.
        Leaderboard.entries.none { leaderboardId(it) != null } -> null

        else -> onMain { activity ->
            // The SDK ships an init ContentProvider that has already done this
            // at process start. Calling it anyway keeps the documented entry
            // point in our own code path rather than resting on a manifest
            // entry inside somebody else's AAR, and a second call is a no-op.
            PlayGamesSdk.initialize(activity)

            PlayGames.getGamesSignInClient(activity)
                .isAuthenticated()
                .awaitOrNull()
                ?.isAuthenticated
        }
    }

    override suspend fun promptSignIn(): Boolean? = onMain { activity ->
        PlayGames.getGamesSignInClient(activity)
            .signIn()
            .awaitOrNull()
            ?.isAuthenticated
    }

    override suspend fun submitScore(leaderboardId: String, value: Long): Boolean =
        onMain { activity ->
            PlayGames.getLeaderboardsClient(activity)
                .submitScoreImmediate(leaderboardId, value)
                .awaitOrNull() != null
        } ?: false

    override suspend fun showLeaderboard(leaderboardId: String?): Boolean =
        onMain { activity ->
            val client = PlayGames.getLeaderboardsClient(activity)
            val intent: Intent = when (leaderboardId) {
                null -> client.getAllLeaderboardsIntent()
                else -> client.getLeaderboardIntent(leaderboardId)
            }.awaitOrNull() ?: return@onMain false

            activity.startActivity(intent)
            true
        } ?: false

    /**
     * Every call below runs on the main thread with a live Activity or does not
     * run. `null` out of here means there was no Activity or the SDK threw, and
     * both are the same "Play would not answer" the interface documents.
     */
    private suspend fun <T> onMain(block: suspend (Activity) -> T?): T? =
        withContext(dispatchers.main) {
            val activity = activityProvider.currentActivity() ?: return@withContext null
            Catching { block(activity) }
                .onFailure { logger.i(it) { "Play Games threw and the leaderboard stays closed" } }
                .getOrNull()
        }

    /**
     * A `Task` as a suspend call, where every unhappy ending is `null`.
     *
     * Deliberately not `logOnFailure`, which logs at error. The most common
     * failure here is a player who has never made a Games profile, and a report
     * per launch for a state the app is designed to shrug off would bury the
     * failures that do mean something.
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            addOnFailureListener { error ->
                if (continuation.isActive) {
                    logger.i { "Play Games declined: ${error.message}" }
                    continuation.resume(null)
                }
            }
            addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
        }
}
