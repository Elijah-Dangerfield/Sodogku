package com.sodogku.libraries.flowroutines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sodogku.libraries.core.logging.KLog
import com.sodogku.libraries.core.Catching
import com.sodogku.libraries.core.ConcurrentHashMap
import com.sodogku.libraries.core.logOnFailure
import com.sodogku.libraries.core.throwIfDebug
import kotlinx.coroutines.FlowPreview
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class IllegalViewModelStateException(
    override val message: String?,
    override val cause: Throwable? = null
): Exception()


/**
 * A view model that revolves around State (S), Events (E) and Actions (A)
 * Encourages a unidirectional flow of data from actions to state.
 *
 * S - State - The state of the view. Should be an immutable class that represents the
 * current state of the view. Should represent view state, NOT view element state
 * See https://developer.android.com/topic/architecture/ui-layer/stateholders#elements-ui
 *
 * E - Event - Events are used to trigger one time events that should not be stored in the state.
 * Examples: Navigation, Showing a toast, etc...
 * Storing one time events in the state requires acknowledgment of the state from the view and can
 * lead to complications and bugs if not careful. Turns out to be easier to just roll with events in
 * a channel.
 *

 * A - Action - Actions are the only way to update state. They represent work to be done either user
 * triggered or from the view model itself.
 * Tips:
 * - If you need data to load on init, have the view model take an action in the init block.
 *
 * This viewmodel backs the state into the saved state handle if the state is savable.
 * See [SavedStateHandle.ACCEPTABLE_CLASSES]
 */
abstract class SEAViewModel<S : Any, E : Any, A : Any>(
    private val initialStateArg: S? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    /**
     * Only ever overridden by tests, so event expiry can be exercised without a
     * five second sleep. `runTest`'s virtual clock cannot help here: the marks
     * come from a monotonic source, which does not advance with the scheduler.
     */
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {

    private val actions = Channel<A>(Channel.UNLIMITED)
    private val events = Channel<TimedEvent<E>>(Channel.UNLIMITED)
    private val actionDebouncer = ConcurrentHashMap<String, Channel<suspend (S) -> S>>()
    private val _initialState: S by lazy { initialState() }

    // Lazy so that we do not let initialState() from the child get called before the child is initialized
    private val mutableStateFlow: MutableStateFlow<S> by lazy {
        MutableStateFlow(
            Catching {
                savedStateHandle.get<S>(STATE_KEY)
            }.getOrNull() ?: _initialState
        )
    }

    /**
     * The flow exposing the state of the view model
     * Lazy so that Mutable State flow doesnt get created before it needs to be
     */
    val stateFlow: StateFlow<S> by lazy {
        mutableStateFlow.mapNotNull {
            Catching { mapEachState(it) }
                .logOnFailure()
                .throwIfDebug()
                .getOrNull()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _initialState,
        )
    }

    /**
     * The flow exposing events from the view model.
     *
     * **Events expire.** An event is a side effect that was worth doing when it
     * was sent -- navigate, play a haptic, show a toast -- and none of those are
     * worth doing several minutes later. The channel underneath is UNLIMITED and
     * the collector is lifecycle-gated, so anything sent while the collector is
     * stopped banks up instead of being lost, and arrives in a burst when it
     * restarts.
     *
     * That is not theoretical. On 2026-09-09 a lifecycle stall left the game
     * screen taking taps with nothing collecting; twelve `OpenAchievements`
     * events accumulated over eight minutes, then replayed 1.5ms apart when
     * collection resumed, and the twelve navigations crashed NavController with
     * "Attempted to pop Destination ... which is not the top of the back stack".
     *
     * `ShakeDetector` already carries this lesson in its own doc -- "a shake is
     * only meaningful in the moment it happens" -- and it is the same mistake
     * one layer up, in the base class every screen uses.
     *
     * The shelf life only ever bites when nothing is collecting. While a
     * collector is attached, delivery is immediate and every event is
     * microseconds old, so this cannot drop an event from a slow view model. It
     * drops events from a stopped screen, which is the intent.
     */
    val eventFlow: Flow<E> = events.receiveAsFlow().mapNotNull { timed ->
        val age = timed.sentAt.elapsedNow()
        if (age > EventShelfLife) {
            KLog.w(
                "Dropping stale event ${timed.event::class.simpleName} " +
                    "after ${age.inWholeMilliseconds}ms with nothing collecting"
            )
            null
        } else {
            timed.event
        }
    }

    /**
     * The current state value
     */
    val state: S get() = stateFlow.value

    init {
        viewModelScope.launch {
            for (action in actions) {
                handleAction(action = action)
            }
        }
    }

    /**
     * Submits an action to be handled by the view model.
     */
    fun takeAction(action: A) {
        actions.trySend(action)
    }

    /**
     * Updates the state of the view model.
     * This is the only way to update the state.
     *
     * Callers must have an action to update the state, this helps maintain UDF.
     */
    suspend fun A.updateState(f: suspend (S) -> S) {
        Catching {
            mutableStateFlow.update {
                val mappedValue = Catching { mapEachState(it) }.logOnFailure().throwIfDebug().getOrNull()
                f(mappedValue ?: it)
            }
        }
            .logOnFailure("Could not up state for: ${state::class.simpleName ?: "missing name"}")
            .throwIfDebug()
    }

    /**
     * Updates state in a debounced manner.
     *
     * The update will not happen until the debounce time has passed without another call to
     * `debounceUpdateState` from the same action type. Every update from the same action
     * type will reset the debounce timer.
     *
     * This can be useful if you have a bit of state that is updated frequently and there is
     * work to be done on each update. This can help prevent unnecessary work from being done.
     *
     * Examples:
     * - Search field state updates that trigger a network call.
     * - Typing in a form field that triggers validation. (prevent error spam if the user is still typing)
     */
    @OptIn(FlowPreview::class)
    suspend fun A.updateStateDebounced(duration: Duration = 1.seconds, f: suspend (S) -> S) {
        val actionIdentifier = this::class.simpleName ?: "Action missing"
        val debouncedChannel = actionDebouncer[actionIdentifier]

        if (debouncedChannel == null) {
            val channel = Channel<suspend (S) -> S>(Channel.UNLIMITED)

            actionDebouncer[actionIdentifier] = channel

            channel.receiveAsFlow()
                .debounce(duration.inWholeMilliseconds)
                .collectIn(viewModelScope) {
                    updateState(it)
                }

            channel.trySend(f)
        } else {
            debouncedChannel.trySend(f)
        }
    }

    /**
     * Sets the initial state to be emitted by the `states` flow.
     *
     * We use a function to force for lazy initialization of the state, allowing the view model to
     * define the initial state in a more flexible way. Including using SavedStateHandle Args.
     */
    protected open fun initialState(): S {
        return initialStateArg
            ?: throw IllegalStateException("Initial state must be passed in or overridden in the initialState function.")
    }

    /**
     * Events in this code base are synonymous with side effects. They are used to trigger
     * one time events that should not be stored in the state.
     *
     * Examples:
     * - Navigation
     * - Showing a toast
     * - etc...
     */
    fun sendEvent(event: E) {
        KLog.i("Sending event ${event::class.simpleName}")
        events.trySend(TimedEvent(event, timeSource.markNow()))
    }

    /**
     * Adds a mapper to the state flow. This is useful for mapping the entire state to a different
     * state on each update.
     *
     * The mapping is called safely, if an error is thrown it will be silently caught, logged and
     * will not update the state.
     *
     * Child view models can also always expose their own state stream that maps over the parents
     * if that's preferred.
     *
     * ex:
     * ```
     * val stateStream: StateFlow<State> = this.states.map {
     *    it.copy(something = somethingElse)
     *    }.stateIn(viewModelScope, ...)
     * ```
     */
    protected open suspend fun mapEachState(
        state: S
    ): S {
        return state
    }

    /**
     * Function that ensures all children handle their actions
     *
     * This helps to ensure that the flow is unidirectional.
     *
     * View -> Action -> State -> View
     *
     * @param action the action to be handled
     */
    protected abstract suspend fun handleAction(action: A)

    /**
     * Best effort. Most states are not savable and that is fine.
     *
     * `SavedStateHandle` takes primitives and Parcelables
     * ([SavedStateHandle.ACCEPTABLE_CLASSES]); a plain Kotlin data class is
     * neither, so this throws on nearly every screen in the app, on every
     * teardown, by design.
     *
     * It used to go through `logOnFailure`, which logs at Error, and `SentryLogTree`
     * turns Error into an event. So the single most routine thing this class
     * does was filing Sentry issues -- "Can't put value with type
     * OnboardingState into saved state" -- for behaviour the class documents as
     * expected. Debug keeps it in the local log where it belongs.
     *
     * If a screen genuinely needs its state to survive process death, the fix is
     * to make that state savable, not to raise this back to a warning.
     */
    override fun onCleared() {
        Catching {
            savedStateHandle[STATE_KEY] = state
        }.onFailure {
            KLog.d { "${state::class.simpleName} is not savable, so it will not survive process death" }
        }
    }

    companion object {
        private const val STATE_KEY = "state"

        /**
         * How long an undelivered event stays worth delivering.
         *
         * Generous on purpose. This only has to be longer than the gap between a
         * view model sending an event in `init` and the screen composing its
         * collector, which is milliseconds, and shorter than a stall anybody
         * would notice. It is not a tuning knob for how long a screen may be
         * backgrounded: a screen that comes back after five seconds should not
         * be replaying what it meant to do before it left.
         */
        private val EventShelfLife = 5.seconds
    }
}

/**
 * An event and when it was sent, so [SEAViewModel.eventFlow] can tell a fresh
 * event from one that has been sitting in the channel.
 *
 * A [TimeMark] rather than a wall-clock stamp: this measures an interval, and
 * wall clocks jump backwards.
 */
private data class TimedEvent<E : Any>(val event: E, val sentAt: TimeMark)

