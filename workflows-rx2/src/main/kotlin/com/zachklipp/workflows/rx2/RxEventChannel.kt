package com.zachklipp.workflows.rx2

import com.zachklipp.workflows.EventChannel
import com.zachklipp.workflows.EventSelectBuilder
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.selects.select

/**
 * The RxJava analog of [EventChannel].
 */
interface RxEventChannel<E : Any> {
  fun <R> selectEvent(block: RxEventSelectBuilder<E, R>.() -> Unit): Single<out R>
}

class RxEventSelectBuilder<E : Any, R> internal constructor(
  @PublishedApi internal val builder: EventSelectBuilder<E, R>
) {
  fun <F : E> onEvent(
    expectedEvent: F,
    block: () -> R
  ) {
    builder.onEvent(expectedEvent) { block() }
  }

  inline fun <reified F : E> onEvent(noinline block: (F) -> R) {
    builder.onEvent<F> { block(it) }
  }

  fun <F : E> onEvent(
    predicateMapper: (E) -> F?,
    block: (F) -> R
  ) {
    builder.onEvent(predicateMapper) { block(it) }
  }
}

internal fun <E : Any> EventChannel<E>.asRxEventChannel(): RxEventChannel<E> {
  return object : RxEventChannel<E> {
    override fun <R> selectEvent(block: RxEventSelectBuilder<E, R>.() -> Unit): Single<out R> {
      return GlobalScope.rxSingle(Dispatchers.Unconfined) {
        select<R> {
          events {
            block(RxEventSelectBuilder(this))
          }
        }
      }
    }
  }
}
