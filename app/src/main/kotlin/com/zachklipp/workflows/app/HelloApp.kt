package com.zachklipp.workflows.app

import com.zachklipp.workflows.WorkflowState
import com.zachklipp.workflows.app.HelloScreen.EnteringName
import com.zachklipp.workflows.app.HelloScreen.Landing
import com.zachklipp.workflows.app.HelloScreen.ShowingGreeting
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.SerialDisposable
import javafx.application.Application
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.Stage
import javafx.stage.StageStyle.UNIFIED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

fun main(args: Array<String>) = Application.launch(HelloApp::class.java, *args)

class HelloApp : Application() {
  override fun start(primaryStage: Stage) {
    val subs = CompositeDisposable()
    with(primaryStage) {
      initStyle(UNIFIED)
      minWidth = 500.0
      minHeight = 500.0
      scene = Scene(Pane())
      setOnHidden { subs.clear() }
    }

    val workflow = HelloStarter(CoroutineScope(Dispatchers.JavaFx)).start()

    GlobalScope.launch {
      helloCliApp(workflow)
    }

    // Wire up the screens.
    subs.add(
        workflow.state.map { Pair(it.state::class, it.state) }
            .distinctUntilChanged { (type, _) -> type }
            .map { (_, screen) ->
              screenHolderForScreen(screen::class)
                  .apply { bindNode(workflow.state) }
            }
            .scan { oldHolder, newHolder -> oldHolder.dispose(); newHolder }
            .subscribe {
              primaryStage.scene.root = it.view
              primaryStage.show()
            })

    // Render the window title.
    subs.add(
        workflow.state.map { it.state.title }
            .subscribe { primaryStage.title = it })

    // Close the app when the workflow finishes.
    subs.add(
        workflow.result.subscribe {
          println("Workflow result emitted, closing primary stage…")
          primaryStage.close()
        })
  }
}

abstract class ScreenHolder<S : HelloScreen>(
  private val screenClass: Class<S>
) {
  lateinit var view: Parent private set
  private val sub = SerialDisposable()

  fun bindNode(screens: Observable<WorkflowState<HelloScreen, HelloEvent>>) {
    view = onCreateNode()
    val filteredScreens = screens.filter { screenClass.isInstance(it.state) }
        .map {
          @Suppress("UNCHECKED_CAST")
          WorkflowState(it.state as S, it.eventHandler)
        }
    onBindNode(filteredScreens)
  }

  fun dispose() = sub.dispose()

  protected abstract fun onCreateNode(): Parent
  protected abstract fun onBindNode(screens: Observable<WorkflowState<S, HelloEvent>>): Disposable
}

private fun <T : HelloScreen> screenHolderForScreen(
  screenType: KClass<T>
): ScreenHolder<out HelloScreen> = when (screenType) {
  Landing::class -> LandingScreenHolder()
  EnteringName::class -> EnteringNameScreenHolder()
  ShowingGreeting::class -> ShowingGreetingScreenHolder()
  else -> throw IllegalArgumentException("invalid screen type: $screenType")
}
