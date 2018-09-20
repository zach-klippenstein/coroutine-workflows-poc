package com.zachklipp.workflows

import com.zachklipp.workflows.EventChannelSource.Clause
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.intrinsics.startCoroutineCancellable
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.SelectClause1
import kotlinx.coroutines.selects.SelectInstance
import kotlinx.coroutines.selects.select

/**
 * Similar to a [ReceiveChannel], but supports selecting a subset of the values of the channel's
 * value type. See [events].
 */
interface EventChannel<E : Any> {
  /**
   * From within a [select] block, allows selecting on specific event predicates.
   *
   * Usage:
   * ```
   * select {
   *   someChannel.onReceive { value -> … }
   *   events {
   *     onEvent<MyEvent1> { event -> … }
   *     onEvent(MyEvent2) { … }
   *   }
   * }
   * ```
   */
  fun <R> SelectBuilder<R>.events(block: EventSelectBuilder<E, R>.() -> Unit)

  /**
   * Suspends until an event is sent to the workflow, and then returns it.
   */
  suspend fun receive(): E = select { events { onEvent<E>({ it }) { it } } }
}

/**
 * Implements the actual selection and send logic for an [EventChannel].
 */
class EventChannelSource<E : Any> : EventChannel<E> {
  data class Clause<E : Any, F : E, R>(
    val predicate: (E) -> F?,
    val select: SelectInstance<R>,
    val block: suspend (F) -> R
  ) {
    fun matchesEvent(event: E) = predicate(event) != null

    fun startCoroutineCancellable(receiver: E) =
      block.startCoroutineCancellable(predicate(receiver)!!, select.completion)
  }

  private var closed = false
  private val clauses = mutableListOf<Clause<E, *, *>>()
  private val disposeClauses: DisposableHandle = object : DisposableHandle {
    override fun dispose() = clauses.clear()
  }

  override fun <R> SelectBuilder<R>.events(block: EventSelectBuilder<E, R>.() -> Unit) {
    EventSelectBuilder(this, this@EventChannelSource).block()
  }

  fun send(event: E) {
    val matchingClause = clauses.firstOrNull { it.matchesEvent(event) } ?: notReady(event)
    if (closed) notReady(event)
    if (!matchingClause.select.trySelect(idempotent = null)) notReady(event)

    // If we've made it this far without throwing, we've won the selection race!
    matchingClause.startCoroutineCancellable(event)

    // We don't need to clear the clauses list because the dispose handler will take care of it.
  }

  fun close(cause: Throwable? = null) {
    closed = true

    // TODO is this right? or necessary?
    clauses.forEach {
      it.select.resumeSelectCancellableWithException(
          cause ?: CancellationException("Workflow finished.")
      )
    }
  }

  internal fun <F : E, R> addClause(clause: Clause<E, F, R>) {
    // Short-circuit if another clause already beat us to it.
    if (clause.select.isSelected) return
    // As soon as the selection race is over, clear our clause list.
    clause.select.disposeOnSelect(disposeClauses)
    clauses += clause
  }

  private fun notReady(event: E): Nothing =
    throw IllegalStateException("Workflow not ready to accept $event")
}

class EventSelectBuilder<E : Any, R> internal constructor(
  private val selectBuilder: SelectBuilder<R>,
  private val eventChannel: EventChannelSource<E>
) {
  fun <F : E> onEvent(
    expectedEvent: F,
    block: suspend () -> R
  ) {
    onEvent<F>({ event -> expectedEvent.takeIf { event == expectedEvent } }, { block() })
  }

  inline fun <reified F : E> onEvent(noinline block: suspend (F) -> R) {
    onEvent({ it as? F }, block)
  }

  fun <F : E> onEvent(
    predicateMapper: (E) -> F?,
    block: suspend (F) -> R
  ) {
    with(selectBuilder) {
      object : SelectClause1<F> {
        override fun <R> registerSelectClause1(
          select: SelectInstance<R>,
          block: suspend (F) -> R
        ) {
          @Suppress("RemoveExplicitTypeArguments")
          eventChannel.addClause<F, R>(Clause(predicateMapper, select, block))
        }
      }.invoke(block)
    }
  }
}
