package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import com.zachklipp.workflows.Reactor.Companion.invoke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

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
 * Use the [factory function][invoke] on this companion object to create a `Reactor` from a lambda
 * or a reference.
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
   */
  suspend fun onReact(
    state: State,
    events: ReceiveChannel<Event>
  ): Reaction<State, Result>

  companion object {
    /**
     * Creates a [Reactor] instance from a function or reference.
     */
    inline operator fun <S : Any, E : Any, R : Any> invoke(
      crossinline react: suspend (S, ReceiveChannel<E>) -> Reaction<S, R>
    ): Reactor<S, E, R> = object : Reactor<S, E, R> {
      override suspend fun onReact(
        state: S,
        events: ReceiveChannel<E>
      ): Reaction<S, R> = react(state, events)
    }
  }
}

/**
 * Convenience for calling [toWorkflow] with [EnterState].
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <S : Any, E : Any, R : Any> Reactor<S, E, R>.toWorkflow(
  coroutineScope: CoroutineScope,
  initialState: S
) = toWorkflow(coroutineScope, EnterState(initialState))

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
fun <S : Any, E : Any, R : Any> Reactor<S, E, R>.toWorkflow(
  coroutineScope: CoroutineScope,
  initialReaction: Reaction<S, R>
): Workflow<S, E, R> {
  val reactor = this
  val eventChannel = Channel<E>()
  val stateChannel = Channel<WorkflowState<S, E>>()

  return object : Workflow<S, E, R> {
    override val state: ReceiveChannel<WorkflowState<S, E>> = stateChannel

    // This coroutine contains the main reactor loop.
    override val result: Deferred<R> = coroutineScope.async(
        context = CoroutineName("workflow reactor loop") +
            Dispatchers.Unconfined,
        // Cancelling the result also cancels the channel (and vice versa).
        // If the reactor finishes normally (i.e. by returning FinishWith) the channel will be
        // closed normally first, so this will be a no-op.
        onCompletion = { cause ->
          stateChannel.cancel(cause)
          eventChannel.cancel(cause)
        }
    ) {
      var currentReaction = initialReaction

      while (currentReaction is EnterState) {
        val state = currentReaction.state
        stateChannel.send(WorkflowState(state, eventChannel::offer))

        /**
         * At one point, I wrote this:
         *
         *    Make sure we're using the exact context we were given, since we've overridden the
         *    dispatcher. E.g. if the caller passes in a main thread dispatcher, onReact should be
         *    called with that.
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
        currentReaction = reactor.onReact(state, eventChannel)
      }

      // Once we break out of the loop, it means the reactor has returned FinishWith and we're done,
      // so we need to close the output channel.
      // If anything inside the loop throws an error, the channel will get cancelled and propagate
      // the error because it's registered as the onCompletion handler on this coroutine.
      stateChannel.close()
      eventChannel.close()
      return@async (currentReaction as FinishWith).result
    }
        .also { result ->
          // Cancelling the channel also cancels the result (and vice versa).
          stateChannel.invokeOnClose { cause ->
            if (cause is CancellationException) result.cancel(cause)
          }
          eventChannel.invokeOnClose { cause ->
            if (cause is CancellationException) result.cancel(cause)
          }
        }

    override fun abandon() {
      result.cancel(CancellationException("Workflow abandoned."))
    }
  }
}
