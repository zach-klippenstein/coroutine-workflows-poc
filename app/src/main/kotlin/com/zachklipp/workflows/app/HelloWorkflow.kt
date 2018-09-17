package com.zachklipp.workflows.app

import com.zachklipp.workflows.Reaction
import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reactor
import com.zachklipp.workflows.Workflow
import com.zachklipp.workflows.app.HelloState.EnteringName
import com.zachklipp.workflows.app.HelloState.Landing
import com.zachklipp.workflows.app.HelloState.ShowingGreeting
import com.zachklipp.workflows.mapState
import com.zachklipp.workflows.toWorkflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select

sealed class HelloState {
  object Landing : HelloState()
  data class EnteringName(val name: String = "") : HelloState()
  data class ShowingGreeting(val name: String) : HelloState()
}

data class HelloScreen(
  val key: String,
  val data: HelloScreenData
)

sealed class HelloScreenData {

}

sealed class HelloEvent {
  object OnRestart : HelloEvent()
  object OnExit : HelloEvent()
  object OnGoToGreeting : HelloEvent()
  data class OnNameChanged(val name: String) : HelloEvent()
  object OnFinishedEnteringName : HelloEvent()
}

typealias HelloWorkflow = Workflow<HelloScreen, HelloEvent, Unit>

class HelloStarter(private val coroutineScope: CoroutineScope) {
  fun start(): HelloWorkflow = Reactor(::helloReact)
      .toWorkflow(coroutineScope, EnterState(Landing))
      .mapState { TODO() }
}

internal suspend fun helloReact(
  state: HelloState,
  events: ReceiveChannel<HelloEvent>
): Reaction<HelloState, Unit> {
//  when (
//    val event = events.receive()
//    ) {
//    OnRestart -> return EnterState(Landing)
//    OnExit -> return Finish
//
//  }

  select<Any> {
    events.onReceive
  }

  return when (state) {
    Landing -> TODO()
    is EnteringName -> TODO()
    is ShowingGreeting -> TODO()
  }
}
