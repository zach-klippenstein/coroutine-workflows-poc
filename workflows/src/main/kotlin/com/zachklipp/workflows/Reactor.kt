package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumes

@Suppress("unused")
sealed class Reaction<out State : Any, out Result : Any> {
  data class EnterState<out State : Any>(val state: State) : Reaction<State, Nothing>()
  data class FinishWith<out Result : Any>(val result: Result) : Reaction<Nothing, Result>()
}

interface Reactor<State : Any, in Event : Any, out Result : Any> {
  suspend fun onReact(
    state: State,
    events: ReceiveChannel<Event>
  ): Reaction<State, Result>
}

fun <S : Any, E : Any, R : Any> Reactor<S, E, R>.toWorkflow(
  coroutineScope: CoroutineScope,
  initialState: Reaction<S, R>
): Workflow<S, E, R> {
  val reactor = this
  val eventChannel = Channel<E>()
  val state = Channel<WorkflowState<S, E>>()

  return object : Workflow<S, E, R> {
    override val state: ReceiveChannel<WorkflowState<S, E>> = state
    override val result: Deferred<R?> = coroutineScope.async(
        context = CoroutineName("workflow react loop") +
            Dispatchers.Unconfined,
        // Cancelling the result also cancels the channel (and vice versa).
        onCompletion = state.consumes()
    ) {
      var currentReaction = initialState
      while (currentReaction is EnterState) {
        state.send(WorkflowState(currentReaction.state, eventChannel::offer))
        currentReaction = reactor.onReact(currentReaction.state, eventChannel)
      }
      state.close()
      return@async (currentReaction as FinishWith).result
    }
        // Cancelling the channel also cancels the result (and vice versa).
        .apply { state.invokeOnClose { if (it is CancellationException) cancel(it) } }

    override fun abandon() {
      result.cancel(CancellationException("Workflow abandoned."))
    }
  }
}
