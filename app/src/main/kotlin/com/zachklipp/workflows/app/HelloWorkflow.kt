package com.zachklipp.workflows.app

import com.zachklipp.workflows.Reaction.Companion.Finish
import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.app.HelloEvent.OnExit
import com.zachklipp.workflows.app.HelloEvent.OnFinishedEnteringName
import com.zachklipp.workflows.app.HelloEvent.OnGoToGreeting
import com.zachklipp.workflows.app.HelloEvent.OnNameChanged
import com.zachklipp.workflows.app.HelloEvent.OnRestart
import com.zachklipp.workflows.app.HelloScreen.EnteringName
import com.zachklipp.workflows.app.HelloScreen.Landing
import com.zachklipp.workflows.app.HelloScreen.ShowingGreeting
import com.zachklipp.workflows.reactor
import com.zachklipp.workflows.rx2.RxWorkflow
import com.zachklipp.workflows.rx2.toRxWorkflow
import kotlinx.coroutines.CoroutineScope

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
  data class OnFinishedEnteringName(val name: String) : HelloEvent()
}

typealias HelloWorkflow = RxWorkflow<HelloScreen, HelloEvent, Unit>

class HelloStarter(scope: CoroutineScope) : CoroutineScope by scope {
  fun start(): HelloWorkflow = reactor<HelloScreen, HelloEvent, Unit>(Landing) { screen, events ->
    val event = events.receive()

    // Handle the common events.
    when (event) {
      OnRestart -> return@reactor EnterState(Landing)
      OnExit -> return@reactor Finish
    }

    when (screen) {
      Landing -> when (event) {
        OnGoToGreeting -> EnterState(EnteringName())
        else -> null
      }
      is EnteringName -> when (event) {
        is OnNameChanged -> EnterState(screen.copy(name = event.name))
        is OnFinishedEnteringName -> EnterState(ShowingGreeting(event.name))
        else -> null
      }
      is ShowingGreeting -> null // no other events accepted on this screen
    } ?: throw IllegalStateException("invalid event in $screen: $event")
  }.toRxWorkflow()
}
