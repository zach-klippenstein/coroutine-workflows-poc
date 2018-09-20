package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
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
  reactor: Reactor<S, E, R>
) = reactor(EnterState(initialState), context, reactor)

/**
 * Creates a [Workflow] from this [Reactor].
 *
 * @receiver The coroutine scope used to host the coroutine that runs the reactor loop.
 * [reactor] is invoked from this scope + [context].
 * @param initialReaction The initial state to pass to [Reactor] or the result if the
 * workflow should be started as finished.
 * @param context Any additional [CoroutineContext] elements to add to the context from the scope.
 * [reactor] is invoked from the calling scope + this context.
 */
fun <S : Any, E : Any, R : Any> CoroutineScope.reactor(
  initialReaction: Reaction<S, R>,
  context: CoroutineContext = EmptyCoroutineContext,
  reactor: Reactor<S, E, R>
): Workflow<S, E, R> {

  // Use the Unconfined dispatcher for the workflow _machinery_, to reduce the dispatch overhead,
  // but we'll jump back to the passed-in dispatcher to run `reactor`. The fact that we're
  // overriding the context for the reactor logic is an implementation detail, so it shouldn't
  // leak into the reactor.
  return workflow(context + Dispatchers.Unconfined) {
    // Snapshot the current scope's context with the additional context so we call react from the
    // right context.
    // We also need to use the workflow job as the parent, otherwise cancelling the workflow
    // Job won't actually cancel if the reactor method is suspended (since it will be running
    // with the workflow's parent job, not the workflow job).
    val reactContext = this@reactor.coroutineContext + context + coroutineContext[Job]!!

    var currentReaction = initialReaction
    while (currentReaction is EnterState) {
      val state = currentReaction.state
      send(state)

      currentReaction = withContext(reactContext) {
        reactor(state, this@workflow)
      }
    }
    return@workflow (currentReaction as FinishWith).result
  }
}
