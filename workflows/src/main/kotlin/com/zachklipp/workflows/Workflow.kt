package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.mapNotNull
import kotlinx.coroutines.launch

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

val <S : Any, E : Any, R : Any> Workflow<S, E, R>.outputs
  get() = Pair(state, result)

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
  lateinit var workflowJob: Job
  lateinit var state: ReceiveChannel<WorkflowState<S, E>>
  lateinit var result: Deferred<R?>

  // This coroutine gives us a job that contains all the worker coroutines as children,
  // so we can cancel everything at once.
  workflowJob = coroutineScope.launch(
      CoroutineName("workflow scope") +
          Dispatchers.Unconfined
  ) {
    val bcast = broadcast<Reaction<S, R>>(
//        start = CoroutineStart.LAZY,
        context = CoroutineName("reactor loop"),
        onCompletion = { println("finisehd with $it") }
    ) {
      reactLoop(reactor, initialState, channel, eventChannel)
    }

    state = bcast.openSubscription()
        .mapNotNull { (it as? EnterState)?.state }
        .map { state -> WorkflowState(state, eventChannel::offer) }

    result = async(CoroutineName("reactor result")) {
      val channel = bcast.openSubscription()
      val r = channel
          .mapNotNull { it as? FinishWith<R> }
          .firstOrNull()
          ?.result
      return@async r
    }
  }

  return object : Workflow<S, E, R> {
    override val state: ReceiveChannel<WorkflowState<S, E>> = state
    override val result: Deferred<R?> = result

    override fun abandon() {
      workflowJob.cancel(CancellationException("Workflow abandoned."))
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
  outputChannel.send(currentReaction)

  while (currentReaction is EnterState) {
    currentReaction = reactor.onReact(currentReaction.state, eventChannel)
    outputChannel.send(currentReaction)
  }
}
