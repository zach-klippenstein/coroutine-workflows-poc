package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentScope
import kotlinx.coroutines.plus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused")
sealed class Reaction<out State : Any, out Result : Any> {
  data class EnterState<out State : Any>(val state: State) : Reaction<State, Nothing>()
  data class FinishWith<out Result : Any>(val result: Result) : Reaction<Nothing, Result>()
}

/** Shorthand for reactors without a result. */
val Finish = FinishWith(Unit)

/**
 * Contains a single function that defines a state machine.
 *
 * See [onReact] for more information.
 *
 * @param State The type that contains all the internal state for the state machine.
 * Usually a sealed class.
 * @param Event The type that represents all possible events the state machine takes as input.
 * Usually a sealed class or enum.
 * @param Result The type that represents all the possible terminal states of the state machine.
 */
interface Reactor<State : Any, in Event : Any, out Result : Any> {
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
   *  1. onReact should be a pure function
   *  2. If the UI cares about being on a specific thread, it will control that when receiving
   *     from the state channel.
   *
   * Ultimately, it's simpler to not make any guarantees about which dispatcher is used to invoke
   * this method, since it shouldn't matter in most cases. And if it _is_ significant, the
   * implementor should be explicit anyway.
   */
  suspend fun onReact(
    state: State,
    events: ReceiveChannel<Event>
  ): Reaction<State, Result>
}

/**
 * Create a running [Workflow] initially in [initialState] that is defined by [react].
 *
 * @param react See [Reactor.onReact] for documentation.
 */
inline fun <S : Any, E : Any, R : Any> CoroutineScope.generateWorkflow(
  initialState: S,
  crossinline react: suspend (S, ReceiveChannel<E>) -> Reaction<S, R>
) = generateWorkflow(EnterState(initialState), react)

/**
 * Creates a [Workflow] defined by [react].
 *
 * @param react See [Reactor.onReact] for documentation.
 */
inline fun <S : Any, E : Any, R : Any> CoroutineScope.generateWorkflow(
  initialReaction: Reaction<S, R>,
  crossinline react: suspend (S, ReceiveChannel<E>) -> Reaction<S, R>
): Workflow<S, E, R> = object : Reactor<S, E, R> {
  override suspend fun onReact(
    state: S,
    events: ReceiveChannel<E>
  ): Reaction<S, R> = react(state, events)
}.asWorkflow(this, initialReaction)

/**
 * Creates a workflow from a [Reactor] using the current scope.
 */
suspend inline fun <S : Any, E : Any, R : Any> Reactor<S, E, R>.asWorkflow(
  initialState: S,
  coroutineContext: CoroutineContext = EmptyCoroutineContext
): Workflow<S, E, R> = asWorkflow(EnterState(initialState), coroutineContext)

/**
 * Creates a workflow from a [Reactor] using the current scope.
 */
suspend inline fun <S : Any, E : Any, R : Any> Reactor<S, E, R>.asWorkflow(
  initialReaction: Reaction<S, R>,
  coroutineContext: CoroutineContext = EmptyCoroutineContext
): Workflow<S, E, R> = currentScope { asWorkflow(this + coroutineContext, initialReaction) }

/**
 * Convenience for calling [asWorkflow] with [EnterState].
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <S : Any, E : Any, R : Any> Reactor<S, E, R>.asWorkflow(
  coroutineScope: CoroutineScope,
  initialState: S
) = asWorkflow(coroutineScope, EnterState(initialState))

/**
 * Creates a [Workflow] from this [Reactor].
 *
 * @param coroutineScope The coroutine scope used to host the coroutine that runs the reactor loop.
 * The loop will always use the [Dispatchers.Unconfined] dispatcher for itself, however
 * [Reactor.onReact] is invoked from this scope directly, including any dispatcher it is configured
 * with.
 * @param initialReaction The initial state to pass to [Reactor.onReact] or the result if the
 * workflow should be started as finished.
 */
fun <S : Any, E : Any, R : Any> Reactor<S, E, R>.asWorkflow(
  coroutineScope: CoroutineScope,
  initialReaction: Reaction<S, R>
): Workflow<S, E, R> = coroutineScope.workflow(Dispatchers.Unconfined) {
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
    currentReaction = onReact(state, this)
  }
  return@workflow (currentReaction as FinishWith).result
}
