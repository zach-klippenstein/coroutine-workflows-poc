package com.zachklipp.workflows

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel

typealias EventHandler<E> = (E) -> Boolean

data class WorkflowState<out State : Any, in Event : Any>(
  val state: State,
  val eventHandler: EventHandler<Event>
) {
  /** Sends an event and requires [eventHandler] to return `true`. */
  fun sendEvent(event: Event) {
    check(eventHandler(event))
  }

  /** Sends an event and ignores [eventHandler]'s return value. */
  fun offerEvent(event: Event) {
    eventHandler(event)
  }
}

interface Workflow<out State : Any, in Event : Any, out Result : Any> {
  val state: ReceiveChannel<WorkflowState<State, Event>>
  val result: Deferred<Result?>

  // TODO does this method even need to exist? i suspect all the use cases for it will end up
  // implicitly cancelling the result/state channel anyway
  fun abandon()
}

val <S : Any, E : Any, R : Any> Workflow<S, E, R>.outputs
  get() = Pair(state, result)
