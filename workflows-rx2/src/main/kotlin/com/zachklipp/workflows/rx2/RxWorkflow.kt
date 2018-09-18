package com.zachklipp.workflows.rx2

import com.zachklipp.workflows.Workflow
import com.zachklipp.workflows.WorkflowState
import io.reactivex.Maybe
import io.reactivex.Observable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.asMaybe
import kotlinx.coroutines.rx2.asObservable
import kotlin.coroutines.CoroutineContext

/**
 * A version of [Workflow] that uses RxJava types for its outputs instead of coroutines.
 *
 * Convert a [Workflow] to one of these with [toRxWorkflow].
 */
interface RxWorkflow<S : Any, E : Any, R : Any> {
  /**
   * Emits the workflows' states, and completes when it's finished or abandoned.
   *
   * @see [Workflow.state]
   */
  val state: Observable<WorkflowState<S, E>>

  /**
   * Emits the workflow result when it's finished, or nothing if it's abandoned.
   *
   * @see [Workflow.result]
   */
  val result: Maybe<out R>

  /**
   * @see [Workflow.abandon]
   */
  fun abandon()
}

/**
 * Converts a coroutine-based [Workflow] into an Rx-based [RxWorkflow].
 *
 * The [RxWorkflow] will "consume" the [Workflow], so the original [Workflow] should no longer
 * be used after this method is called (anything reading from the original's channels will fight
 * with the [RxWorkflow] for values).
 *
 * @param context The coroutine context from which the resulting observables are going to be
 * signalled. Default value is [Dispatchers.Unconfined].
 */
fun <S : Any, E : Any, R : Any> Workflow<S, E, R>.toRxWorkflow(
  context: CoroutineContext = Dispatchers.Unconfined
): RxWorkflow<S, E, R> = let { source ->
  object : RxWorkflow<S, E, R> {
    override val state: Observable<WorkflowState<S, E>> =
      source.state.asObservable(context)
          .onErrorResumeNext { cause: Throwable ->
            when (cause) {
              // Ignore cancellation - just complete instead.
              is CancellationException -> Observable.empty()
              else -> Observable.error(cause)
            }
          }
          .replay(1)
          .refCount()

    override val result: Maybe<R> = source.result.asMaybe(context)
        .onErrorResumeNext { cause: Throwable ->
          when (cause) {
            // Ignore cancellation - just complete instead.
            is CancellationException -> Maybe.empty()
            else -> Maybe.error(cause)
          }
        }

    override fun abandon() = source.abandon()
  }
}
