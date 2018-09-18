package com.zachklipp.workflows

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.flatMap
import kotlinx.coroutines.channels.map

/**
 * Returns a [Workflow] that maps each emission of [S1] to [S2].
 *
 * This function [consumes][ReceiveChannel.consume] all elements of the original [ReceiveChannel].
 */
fun <S1 : Any, S2 : Any, E : Any, R : Any> Workflow<S1, E, R>.mapState(
  transform: suspend (S1) -> S2
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
fun <S1 : Any, S2 : Any, E : Any, R : Any> Workflow<S1, E, R>.flatMapState(
  transform: suspend (S1) -> ReceiveChannel<S2>
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
fun <S : Any, E1 : Any, E2 : Any, R : Any> Workflow<S, E1, R>.mapEvent(
  transform: (E2) -> E1
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
fun <S : Any, E : Any, R1 : Any, R2 : Any> Workflow<S, E, R1>.mapResult(
  transform: suspend (R1) -> R2
): Workflow<S, E, R2> = object : Workflow<S, E, R2> {
  override val state: ReceiveChannel<WorkflowState<S, E>> get() = this@mapResult.state
  override val result: Deferred<R2> = GlobalScope.async(Dispatchers.Unconfined) {
    transform(this@mapResult.result.await())
  }

  override fun abandon() = this@mapResult.abandon()
}
