package com.sodogku.libraries.navigation.impl

import com.sodogku.libraries.navigation.DeepLinkBridge
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * A queue rather than a replaying stream, and both halves of that are load
 * bearing.
 *
 * **It buffers.** A home-screen quick action on a cold start reaches UIKit while
 * the scene is still being built, long before Compose has composed anything, so
 * there is nobody collecting yet. The buffer holds the URL until one arrives.
 *
 * **It delivers once.** This used to be a `MutableSharedFlow(replay = 1)`, which
 * hands its last value to every new subscriber. The collector is restarted
 * whenever the App composable leaves and re-enters composition, and each restart
 * re-opened the last deep link — a shortcut tapped an hour ago yanking the
 * player off their board. A channel is consumed, so a second collector gets
 * nothing.
 *
 * Four deep, oldest dropped. Nothing sends these in bursts; the depth exists so
 * a link that arrives during boot cannot be lost, not so a backlog can build.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DeepLinkBridgeImpl : DeepLinkBridge {

    private val queue = Channel<String>(
        capacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val urls: Flow<String> = queue.receiveAsFlow()

    override fun emit(url: String) {
        queue.trySend(url)
    }
}
