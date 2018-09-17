package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused")
sealed class Reaction<out State : Any, out Result : Any> {
  data class EnterState<out State : Any>(val state: State) : Reaction<State, Nothing>()
  data class FinishWith<out Result : Any>(val result: Result) : Reaction<Nothing, Result>()

  companion object {
    /** Shorthand for reactors without a result. */
    val Finish = FinishWith(Unit)
  }
}

/**
 * Given the current [state][State], and a channel of [events][Event], returns a command value
 * that indicates either the next state for the state machine or the final result.
 *
 * ## Dispatching
 *
 * This method is invoked with the context used to create the [Workflow], but with
 * [Dispatchers.Unconfined] as the dispatcher.
 *
 * Initially I thought it made more sense for this method to be called from the dispatcher from
 * the original [CoroutineScope] used to create the [Workflow].
 *
 * However, in the unit tests that send events, when that happens, the continuation that
 * performs the send seems to be scheduled before this coroutine that calls onReact and
 * eventually suspends putting the channel in a waiting-to-read state.
 *
 * Leaving this invocation in the Unconfined context means the implementor needs to be
 * explicit about their dispatcher if they care, but they should rarely care because:
 *  1. `Reactor` should be a pure function
 *  2. If the UI cares about being on a specific thread, it will control that when receiving
 *     from the state channel.
 *
 * Ultimately, it's simpler to not make any guarantees about which dispatcher is used to invoke
 * this method, since it shouldn't matter in most cases. And if it _is_ significant, the
 * implementor should be explicit anyway.
 *
 * @param State The type that contains all the internal state for the state machine.
 * Usually a sealed class.
 * @param Event The type that represents all possible events the state machine takes as input.
 * Usually a sealed class or enum.
 * @param Result The type that represents all the possible terminal states of the state machine.
 */
typealias Reactor<State, Event, Result> =
    suspend (state: State, events: ReceiveChannel<Event>) -> Reaction<State, Result>

/**
 * Create a running [Workflow] initially in [initialState] that is defined by [reactor].
 *
 * @param reactor See [Reactor] for documentation.
 */
fun <S : Any, E : Any, R : Any> CoroutineScope.reactor(
  initialState: S,
  context: CoroutineContext = EmptyCoroutineContext,
  reactor: suspend (S, ReceiveChannel<E>) -> Reaction<S, R>
) = reactor(EnterState(initialState), context, reactor)

/**
 * Creates a [Workflow] from this [Reactor].
 *
 * @receiver The coroutine scope used to host the coroutine that runs the reactor loop.
 * The loop will always use the [Dispatchers.Unconfined] dispatcher for itself, however
 * [Reactor] is invoked from this scope directly, including any dispatcher it is configured
 * with.
 * @param initialReaction The initial state to pass to [Reactor] or the result if the
 * workflow should be started as finished.
 */
fun <S : Any, E : Any, R : Any> CoroutineScope.reactor(
  initialReaction: Reaction<S, R>,
  context: CoroutineContext = EmptyCoroutineContext,
  reactor: Reactor<S, E, R>
): Workflow<S, E, R> = workflow(context + Dispatchers.Unconfined) {
  var currentReaction = initialReaction
  while (currentReaction is EnterState) {
    val state = currentReaction.state
    send(state)

    /**
     * Initially I thought it made more sense for `onReact` to be called from the dispatcher from
     * the original [CoroutineScope].
     *
     * However, in the unit tests that send events, when that happens, the continuation that
     * performs the send seems to be scheduled before this coroutine that calls onReact and
     * eventually suspends putting the channel in a waiting-to-read state.
     *
     * Leaving this invocation in the Unconfined context means the implementor needs to be
     * explicit about their dispatcher if they care, but they should rarely care because:
     *  1. onReact should be a pure function
     *  2. If the UI cares about being on a specific thread, it will control that when receiving
     *     from the state channel.
     */
    currentReaction = reactor(state, this)
  }
  return@workflow (currentReaction as FinishWith).result
}
