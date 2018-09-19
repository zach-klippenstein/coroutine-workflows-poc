package com.zachklipp.workflows

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * @return `true` if the event was successfully delivered to the [Workflow].
 */
typealias EventHandler<E> = (E) -> Unit

/**
 * Represents both the [output state][state] of a [Workflow] state machine and an
 * [EventHandler function][sendEvent] that allows sending events into the state machine.
 *
 * @param state The current state of the [Workflow].
 * @param sendEvent [EventHandler] that will send events to the state machine.
 * Throws an [IllegalStateException] if the workflow is not ready to accept the event.
 */
data class WorkflowState<out State : Any, in Event : Any>(
  val state: State,
  val sendEvent: EventHandler<Event>
)

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
