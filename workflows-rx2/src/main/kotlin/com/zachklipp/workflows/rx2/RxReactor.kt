package com.zachklipp.workflows.rx2

import com.zachklipp.workflows.Reaction
import com.zachklipp.workflows.Reactor
import com.zachklipp.workflows.Workflow
import com.zachklipp.workflows.reactor
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.rx2.await

/**
 * Given the current [state][State], and a channel of [events][Event], returns a promise of a
 * command value that indicates either the next state for the state machine or the final result.
 *
 * @param State The type that contains all the internal state for the state machine.
 * Usually a sealed class.
 * @param Event The type that represents all possible events the state machine takes as input.
 * Usually a sealed class or enum.
 * @param Result The type that represents all the possible terminal states of the state machine.
 */
typealias RxReactor<State, Event, Result> =
    suspend RxEventChannel<Event>.(state: State) -> Single<out Reaction<State, Result>>

/**
 * Returns a running [Workflow] initially in [initialState] that is defined by [reactor].
 *
 * @param initialState The initial state to pass to [reactor][RxReactor].
 * @param reactor See [RxReactor] for documentation.
 */
fun <S : Any, E : Any, R : Any> rxReactor(
  initialState: S,
  reactor: RxReactor<S, E, R>
): RxWorkflow<S, E, R> {
  return GlobalScope.reactor(initialState, Unconfined, reactor.asReactor())
      .toRxWorkflow()
}

private fun <S : Any, E : Any, R : Any> RxReactor<S, E, R>.asReactor(): Reactor<S, E, R> =
  { state ->
    val rxEventChannel = this.asRxEventChannel()
    this@asReactor.invoke(rxEventChannel, state)
        .await()
  }
