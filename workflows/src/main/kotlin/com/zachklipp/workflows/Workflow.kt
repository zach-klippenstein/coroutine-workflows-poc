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

/**
 * Returns a [Workflow] that maps each emission of [S1] to [S2].
 *
 * This function [consumes][ReceiveChannel.consume] all elements of the original [ReceiveChannel].
 */
inline fun <S1 : Any, S2 : Any, E : Any, R : Any> Workflow<S1, E, R>.mapState(
  crossinline transform: (S1) -> S2
): Workflow<S2, E, R> = object : Workflow<S2, E, R> {
  override val state: ReceiveChannel<WorkflowState<S2, E>> =
    this@mapState.state.map { (state, eventHandler) ->
      WorkflowState(transform(state), eventHandler)
    }
  override val result: Deferred<R> get() = this@mapState.result

  override fun abandon() = this@mapState.abandon()
}

/**
 * Returns a [Workflow] that maps each emitted [S1] state value to a [ReceiveChannel] of [S2], and
 * emits each element from that channel as its state.
 *
 * This function [consumes][ReceiveChannel.consume] all elements of the original [ReceiveChannel].
 */
inline fun <S1 : Any, S2 : Any, E : Any, R : Any> Workflow<S1, E, R>.flatMapState(
  crossinline transform: (S1) -> ReceiveChannel<S2>
): Workflow<S2, E, R> = object : Workflow<S2, E, R> {
  override val state: ReceiveChannel<WorkflowState<S2, E>> =
    this@flatMapState.state.flatMap { (state, eventHandler) ->
      transform(state).map { WorkflowState(it, eventHandler) }
    }
  override val result: Deferred<R> get() = this@flatMapState.result

  override fun abandon() = this@flatMapState.abandon()
}

/**
 * Returns a [Workflow] that accepts events of type [E2] and converts them to [E1] before forwarding
 * to the original [Workflow].
 *
 * This function [consumes][ReceiveChannel.consume] all elements of the original [ReceiveChannel].
 */
inline fun <S : Any, E1 : Any, E2 : Any, R : Any> Workflow<S, E1, R>.mapEvent(
  crossinline transform: (E2) -> E1
): Workflow<S, E2, R> = object : Workflow<S, E2, R> {
  override val state: ReceiveChannel<WorkflowState<S, E2>> =
    this@mapEvent.state.map { (state, eventHandler) ->
      WorkflowState<S, E2>(state) { eventHandler(transform(it)) }
    }
  override val result: Deferred<R> get() = this@mapEvent.result

  override fun abandon() = this@mapEvent.abandon()
}

/**
 * Returns a [Workflow] that maps the result type from [R1] to [R2].
 */
inline fun <S : Any, E : Any, R1 : Any, R2 : Any> Workflow<S, E, R1>.mapResult(
  crossinline transform: (R1) -> R2
): Workflow<S, E, R2> = object : Workflow<S, E, R2> {
  override val state: ReceiveChannel<WorkflowState<S, E>> get() = this@mapResult.state
  override val result: Deferred<R2> = GlobalScope.async(Dispatchers.Unconfined) {
    this@mapResult.result.await()
        .let(transform)
  }

  override fun abandon() = this@mapResult.abandon()
}
