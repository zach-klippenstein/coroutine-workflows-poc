package com.zachklipp.workflows.app

import com.zachklipp.workflows.WorkflowState
import com.zachklipp.workflows.app.HelloScreen.EnteringName
import com.zachklipp.workflows.app.HelloScreen.Landing
import com.zachklipp.workflows.app.HelloScreen.ShowingGreeting
import javafx.application.Application
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.Stage
import javafx.stage.StageStyle.UNIFIED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

fun main(args: Array<String>) = Application.launch(HelloApp::class.java, *args)

class HelloApp : Application(), CoroutineScope {
  private lateinit var appJob: Job
  //  private val container = Pane()
  override val coroutineContext: CoroutineContext by lazy { appJob + Dispatchers.JavaFx }

  override fun start(primaryStage: Stage) {
    appJob = Job()
    with(primaryStage) {
      initStyle(UNIFIED)
      minWidth = 500.0
      minHeight = 500.0
      scene = Scene(Pane())
//      sceneProperty().addListener { _, oldValue, newValue ->
//        if (oldValue == null && newValue != oldValue) show()
//      }
    }

    val workflow = HelloStarter(this).start()
    launch(onCompletion = { cause ->
      println("Main coroutine completed: $cause")
      primaryStage.close()
    }) {
      launch(CoroutineName("workflow result handler")) {
        workflow.result.await()
        println("Workflow finished, cancelling app job.")
        appJob.cancel(CancellationException("Workflow finished."))
      }
      launch(CoroutineName("workflow state handler")) {
        var currScreen: HelloScreen? = null
        var currScreenHolder: ScreenHolder<out HelloScreen>? = null

        workflow.state.consumeEach { state ->
          primaryStage.title = state.state.title
          val screen = state.state

          if (currScreen == null || screen::class != currScreen!!::class) {
            println("showing ${screen::class.simpleName}")
            currScreenHolder = screenHolderForScreen(screen)
            primaryStage.scene.root = currScreenHolder!!.view
            primaryStage.show()
          }

          currScreenHolder!!.bindNode(state)
          currScreen = screen
        }
      }
    }
  }
}

abstract class ScreenHolder<S : HelloScreen> {
  val view: Parent by lazy { onCreateNode() }

  @Suppress("UNCHECKED_CAST")
  fun bindNode(state: WorkflowState<HelloScreen, HelloEvent>) {
    val screen = state.state as? S
        ?: throw IllegalArgumentException("Invalid data type: ${state.state::class}")

    onBindNode(WorkflowState(screen, state.eventHandler))
  }

  abstract fun onCreateNode(): Parent
  abstract fun onBindNode(state: WorkflowState<S, HelloEvent>)
}

private fun screenHolderForScreen(screen: HelloScreen): ScreenHolder<out HelloScreen> =
  when (screen) {
    Landing -> LandingScreenHolder()
    is EnteringName -> EnteringNameScreenHolder()
    is ShowingGreeting -> ShowingGreetingScreenHolder()
  }
