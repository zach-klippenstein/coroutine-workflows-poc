package com.zachklipp.workflows

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.flatMap
import kotlinx.coroutines.channels.map

/**
 * @return `true` if the event was successfully delivered to the [Workflow].
 */
typealias EventHandler<E> = (E) -> Boolean

/**
 * Represents both the [output state][state] of a [Workflow] state machine and an
 * [EventHandler function][eventHandler] that allows sending events into the state machine.
 *
 * @param state The current state of the [Workflow].
 * @param eventHandler [EventHandler] that will send events to the state machine. Most code should
 * not call this directly, but instead use one of [sendEvent] or [offerEvent] which indicate whether
 * the caller cares about the event actually being accepted by the state machine or not.
 */
data class WorkflowState<out State : Any, in Event : Any>(
  val state: State,
  val eventHandler: EventHandler<Event>
) {
  /** Sends an event and requires [eventHandler] to return `true`. */
  fun sendEvent(event: Event) {
    check(eventHandler(event)) { "Sending event to workflow failed: $event" }
  }

  /** Sends an event and ignores [eventHandler]'s return value. */
  fun offerEvent(event: Event) {
    eventHandler(event)
  }
}

/**
 * The [state] and [result] outputs' cancellation are bound together. Cancelling either one causes
 * the other (and the entire workflow) to be cancelled.
 */
interface Workflow<out State : Any, in Event : Any, out Result : Any> {
  /**
   * Emits the workflows' states, is closed when it's finished, or cancelled when abandoned.
   */
  val state: ReceiveChannel<WorkflowState<State, Event>>

  /**
   * Emits the workflow result when it's finished, or cancelled if the workflow is abandoned.
   */
  val result: Deferred<Result>

  /**
   * Stops the workflow, cancelling the outputs if it hasn't finished yet.
   */
  // TODO does this method even need to exist? i suspect all the use cases for it will end up
  // implicitly cancelling the result/state channel anyway
  fun abandon()
}

/**
 * Allows accessing [state][Workflow.state] and [result][Workflow.result] simultaneously using
 * destructuring.
 */
inline val <S : Any, E : Any, R : Any> Workflow<S, E, R>.outputs
  get() = Pair(state, result)
