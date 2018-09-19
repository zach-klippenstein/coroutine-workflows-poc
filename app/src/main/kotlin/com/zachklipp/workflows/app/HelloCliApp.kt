package com.zachklipp.workflows.app

import com.zachklipp.workflows.WorkflowState
import com.zachklipp.workflows.app.HelloEvent.OnExit
import com.zachklipp.workflows.app.HelloEvent.OnFinishedEnteringName
import com.zachklipp.workflows.app.HelloEvent.OnGoToGreeting
import com.zachklipp.workflows.app.HelloEvent.OnRestart
import com.zachklipp.workflows.app.HelloScreen.EnteringName
import com.zachklipp.workflows.app.HelloScreen.Landing
import com.zachklipp.workflows.app.HelloScreen.ShowingGreeting
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.openSubscription

fun main(args: Array<String>) {
  runBlocking {
    log("starting hello…")
    val workflow = HelloStarter(this).start()
    log("Hello started, starting app…")
    helloCliApp(workflow)
    log("app finished.")
  }
}

fun log(msg: String) = System.err.run {
  println(msg)
  flush()
}

suspend fun helloCliApp(workflow: HelloWorkflow) {
  val console = Console.system
  val stateChannel = workflow.state.openSubscription()

  coroutineScope {
    var currJob: Job? = null
    var previousScreen: HelloScreen? = null

    // This is effectively a `workflow.state.subscribe { }`.
    stateChannel.consumeEach { nextState ->
      // If we get a new state before we're finished processing the old one, cancel the old one.
      if (currJob?.cancel() == true && !nextState.state::class.isInstance(previousScreen)) {
        // If the last job was cancelled and we're showing a different type of "screen" (which
        // implies a different prompt), we need to add our own newline.
        // TODO: Abstract the "current prompt" concept out to get rid of the type check hack.
        console.print("\n")
      }

      // Snapshot this so when the child coroutine reads it it's not been overwritten yet.
      val previousScreenSnapshot = previousScreen
      currJob = launch { console.handleState(previousScreenSnapshot, nextState) }
      previousScreen = nextState.state
    }

    // Clean up.
    coroutineContext[Job]!!.cancelChildren()
  }
}

private suspend fun Console.handleState(
  previousScreen: HelloScreen?,
  state: WorkflowState<HelloScreen, HelloEvent>
) {
  when (
    val screen = state.state
    ) {
    is Landing -> {
      if (readLine("Welcome! Hit enter to proceed…") == null) {
        // Input stream is closed, we can't accept any input.
        return
      } else {
        state.sendEvent(OnGoToGreeting)
      }
    }
    is EnteringName -> {
      // Don't print the prompt every time the name changes.
      val name = if (previousScreen is EnteringName) readLine(screen.name.last().toString())
      else readLine("Enter your name ('quit' to exit, 'restart' to start over): ")

      when (name) {
        null -> return
        "quit" -> state.sendEvent(OnExit)
        "restart" -> state.sendEvent(OnRestart)
        else -> state.sendEvent(OnFinishedEnteringName(name))
      }
    }
    is ShowingGreeting -> {
      readLine("Welcome ${screen.name}! Hit enter to quit.")
      state.sendEvent(OnExit)
    }
  }
}
