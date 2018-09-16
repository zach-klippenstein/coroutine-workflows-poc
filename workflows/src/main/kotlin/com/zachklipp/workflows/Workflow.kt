package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.mapNotNull

typealias EventHandler<E> = (E) -> Boolean

data class WorkflowState<out State : Any, in Event : Any>(
  val state: State,
  val eventHandler: EventHandler<Event>
)

interface Workflow<out State : Any, in Event : Any, out Result : Any> {
  val state: ReceiveChannel<WorkflowState<State, Event>>
  val result: Deferred<Result?>

  fun abandon()
}

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
  val workflowJob = Job(parent = coroutineScope.coroutineContext[Job])
  // CoroutineScope.plus is broken.
  with(CoroutineScope(coroutineScope.coroutineContext + workflowJob)) {
    val bcast = broadcast<Reaction<S, R>> {
      coroutineContext[Job]!!.invokeOnCompletion { println("bcast completed: $it") }
      reactLoop(reactor, initialState, channel, eventChannel)
    }

    // Open the subscription immediately so it'll see the finish reaction even if it's the initial
    // reaction. If this were in the async block for result, we'd need to pass start = UNDISPATCHED
    val finishReaction = bcast.openSubscription()
        .mapNotNull { it as? FinishWith<R> }

    return object : Workflow<S, E, R> {
      override val state: ReceiveChannel<WorkflowState<S, E>> = bcast.openSubscription()
          .mapNotNull { (it as? EnterState)?.state }
          .map { state -> WorkflowState(state, eventChannel::offer) }

      override val result: Deferred<R?> = async {
        coroutineContext[Job]!!.invokeOnCompletion { println("async completed: $it") }
        finishReaction.firstOrNull()
            ?.result
      }

      override fun abandon() {
//        workflowJob.cancel()
        workflowJob.cancel(CancellationException("Workflow abandoned."))
//        bcast.cancel()
//        result.cancel()
      }
    }
  }
}

private suspend fun <S : Any, E : Any, R : Any> reactLoop(
  reactor: Reactor<S, E, R>,
  initialState: Reaction<S, R>,
  outputChannel: SendChannel<Reaction<S, R>>,
  eventChannel: ReceiveChannel<E>
) {
  var currentReaction = initialState
  do {
    outputChannel.send(currentReaction)
    (currentReaction as? EnterState)?.let { nextState ->
      currentReaction = reactor.onReact(nextState.state, eventChannel)
    }
  } while (currentReaction !is FinishWith)
}
