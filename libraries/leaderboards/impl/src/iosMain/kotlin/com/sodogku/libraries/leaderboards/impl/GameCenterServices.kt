package com.sodogku.libraries.leaderboards.impl

import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.leaderboards.GameServices
import com.sodogku.libraries.leaderboards.GameServicesStatus
import com.sodogku.libraries.leaderboards.SubmitResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.GameKit.GKGameCenterViewControllerStateLeaderboards
import platform.GameKit.GKLeaderboard
import platform.GameKit.GKLeaderboardPlayerScopeGlobal
import platform.GameKit.GKLeaderboardTimeScopeAllTime
import platform.GameKit.GKLocalPlayer
import platform.GameKit.authenticateHandler
import platform.GameKit.create
import platform.GameKit.gameCenterDelegate
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume

/**
 * Game Center, reached through the GameKit platform bindings rather than a
 * Swift shim. GameKit is an ordinary Objective-C system framework, so there is
 * nothing here Kotlin/Native cannot call, and a Swift file would only be a
 * second place for the seam to drift from.
 *
 * ## The authentication handler is the whole problem
 *
 * `GKLocalPlayer.authenticateHandler` is not a callback, it is a subscription,
 * and three things about it are easy to get wrong.
 *
 * **It is set exactly once, ever.** Assigning it again restarts authentication;
 * assigning it per call site produces an app that re-authenticates whenever
 * anybody asks a question. [installed] is what makes [startAuthentication]
 * idempotent, and it is only ever touched on the main queue, which is also the
 * only queue GameKit calls the handler on.
 *
 * **It fires more than once.** The first call usually hands over a sign-in
 * screen; a later one says the player signed in; another says they signed out
 * again mid-session. So this holds no continuation to resume (resuming one
 * twice is a crash) and instead writes each answer into [state], which callers
 * observe. That is why [GameServices.startAuthentication] returns nothing.
 *
 * **The view controller it hands you is not a notification, it is a request.**
 * We keep it and present it only if the player asks to see a leaderboard. A
 * sign-in sheet that appears over the game at launch, for a feature the game
 * does not need, is the exact failure the fail-open rule is about.
 *
 * ## Everything else is a refusal, not an error
 *
 * Signed out, restricted by Screen Time, underage, offline, unavailable in the
 * region: all of them arrive here as an error or a false `isAuthenticated`, all
 * of them end as [GameServicesStatus.Unavailable] or [SubmitResult.Failed], and
 * none of them reach the player.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class GameCenterServices : GameServices {

    private val logger = KLog.withTag("GameCenter")

    private val state = MutableStateFlow(GameServicesStatus.Unknown)

    override val status: StateFlow<GameServicesStatus> = state.asStateFlow()

    /** Main queue only, like everything GameKit hands back. */
    private var installed = false

    /** Main queue only. The sign-in screen GameKit gave us, held until asked for. */
    private var signInViewController: UIViewController? = null

    /**
     * A field rather than a local, because the delegate is held weakly by the
     * view controller. A local would be collected between presenting the screen
     * and the player closing it, and the screen would then have no way to
     * dismiss itself.
     */
    private val dismissOnFinish = DismissingDelegate()

    override fun startAuthentication() {
        NSOperationQueue.mainQueue.addOperationWithBlock {
            if (installed) return@addOperationWithBlock
            installed = true
            GKLocalPlayer.local.authenticateHandler = { viewController, error ->
                onAuthenticationChanged(viewController, error)
            }
        }
    }

    override suspend fun submit(leaderboardId: String, value: Long): SubmitResult {
        val player = GKLocalPlayer.local
        if (!player.authenticated) return SubmitResult.NotAuthenticated

        return Catching {
            suspendCancellableCoroutine { continuation ->
                GKLeaderboard.submitScore(
                    score = value,
                    context = 0uL,
                    player = player,
                    leaderboardIDs = listOf(leaderboardId),
                ) { error ->
                    if (!continuation.isActive) return@submitScore
                    if (error == null) {
                        continuation.resume(SubmitResult.Submitted)
                    } else {
                        logger.w { "Game Center rejected $leaderboardId: ${error.localizedDescription}" }
                        continuation.resume(SubmitResult.Failed)
                    }
                }
            }
        }.logOnFailure { "Game Center submission threw for $leaderboardId" }
            .getOrDefault(SubmitResult.Failed)
    }

    override suspend fun presentDashboard(leaderboardId: String?) {
        withContext(Dispatchers.Main) {
            Catching { present(leaderboardId) }
                .logOnFailure { "Could not present Game Center" }
        }
    }

    // GKGameCenterViewController.create is one of the ObjC factory bridges
    // Kotlin/Native still marks as beta. The alternative is the deprecated
    // initWithState / initWithLeaderboardID pair, which is worse.
    @OptIn(BetaInteropApi::class)
    private fun present(leaderboardId: String?) {
        val host = topViewController() ?: return

        // The sign-in screen wins when we are holding one: there is nothing to
        // show a signed-out player on a leaderboard, and this is the moment they
        // asked for it.
        signInViewController?.let { signIn ->
            signInViewController = null
            host.presentViewController(signIn, animated = true, completion = null)
            return
        }

        if (!GKLocalPlayer.local.authenticated) return

        val controller = if (leaderboardId == null) {
            GKGameCenterViewController.create(GKGameCenterViewControllerStateLeaderboards)
        } else {
            GKGameCenterViewController.create(
                leaderboardID = leaderboardId,
                playerScope = GKLeaderboardPlayerScopeGlobal,
                timeScope = GKLeaderboardTimeScopeAllTime,
            )
        }
        controller.gameCenterDelegate = dismissOnFinish
        host.presentViewController(controller, animated = true, completion = null)
    }

    private fun onAuthenticationChanged(viewController: UIViewController?, error: NSError?) {
        when {
            viewController != null -> {
                signInViewController = viewController
                state.value = GameServicesStatus.SignInRequired
            }

            GKLocalPlayer.local.authenticated -> {
                signInViewController = null
                state.value = GameServicesStatus.Authenticated
            }

            else -> {
                signInViewController = null
                state.value = GameServicesStatus.Unavailable
                logger.i { "Game Center unavailable: ${error?.localizedDescription ?: "not signed in"}" }
            }
        }
    }

    private fun topViewController(): UIViewController? {
        val keyWindow = UIApplication.sharedApplication.connectedScenes
            .asSequence()
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>().asSequence() }
            .firstOrNull { it.isKeyWindow() }
            ?: return null

        var top: UIViewController? = keyWindow.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        return top
    }
}

private class DismissingDelegate : NSObject(), GKGameCenterControllerDelegateProtocol {
    override fun gameCenterViewControllerDidFinish(
        gameCenterViewController: GKGameCenterViewController,
    ) {
        gameCenterViewController.dismissViewControllerAnimated(flag = true, completion = null)
    }
}
