package com.zachklipp.workflows.app

import com.zachklipp.workflows.Finish
import com.zachklipp.workflows.Reaction
import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reactor
import com.zachklipp.workflows.Workflow
import com.zachklipp.workflows.app.HelloEvent.OnExit
import com.zachklipp.workflows.app.HelloEvent.OnFinishedEnteringName
import com.zachklipp.workflows.app.HelloEvent.OnGoToGreeting
import com.zachklipp.workflows.app.HelloEvent.OnNameChanged
import com.zachklipp.workflows.app.HelloEvent.OnRestart
import com.zachklipp.workflows.app.HelloScreen.EnteringName
import com.zachklipp.workflows.app.HelloScreen.Landing
import com.zachklipp.workflows.app.HelloScreen.ShowingGreeting
import com.zachklipp.workflows.toWorkflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel

sealed class HelloScreen(val title: String) {
  object Landing : HelloScreen("Welcome!")
  data class EnteringName(val name: String = "") : HelloScreen("Welcome, $name!")
  data class ShowingGreeting(val name: String) : HelloScreen("Welcome, $name!")
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
}

internal suspend fun helloReact(
  screen: HelloScreen,
  events: ReceiveChannel<HelloEvent>
): Reaction<HelloScreen, Unit> {
  val event = events.receive()

  // Handle the common events.
  when (event) {
    OnRestart -> return EnterState(Landing)
    OnExit -> return Finish
  }

  return when (screen) {
    Landing -> when (event) {
      OnGoToGreeting -> EnterState(EnteringName())
      else -> null
    }
    is EnteringName -> when (event) {
      is OnNameChanged -> EnterState(screen.copy(name = event.name))
      OnFinishedEnteringName -> EnterState(ShowingGreeting(screen.name))
      else -> null
    }
    is ShowingGreeting -> null // no other events accepted on this screen
  } ?: throw IllegalStateException("invalid event in $screen: $event")
}
