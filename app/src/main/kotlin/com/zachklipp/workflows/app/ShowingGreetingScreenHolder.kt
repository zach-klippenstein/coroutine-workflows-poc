package com.zachklipp.workflows.app

import com.zachklipp.workflows.WorkflowState
import com.zachklipp.workflows.app.HelloEvent.OnExit
import com.zachklipp.workflows.app.HelloEvent.OnRestart
import com.zachklipp.workflows.app.HelloScreen.ShowingGreeting
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import javafx.geometry.Pos.CENTER
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox

class ShowingGreetingScreenHolder : ScreenHolder<ShowingGreeting>(ShowingGreeting::class.java) {
  private lateinit var label: Label
  private lateinit var restart: Button
  private lateinit var exit: Button

  override fun onCreateNode(): Parent = VBox(
      Label().also { label = it },
      Button("Restart").also { restart = it },
      Button("Finished").also { exit = it }
  ).apply {
    alignment = CENTER
  }

  override fun onBindNode(
    screens: Observable<WorkflowState<ShowingGreeting, HelloEvent>>
  ): Disposable = screens.subscribe { state ->
    label.text = "Welcome, ${state.state.name}!"
    restart.setOnMouseClicked { state.sendEvent(OnRestart) }
    exit.setOnMouseClicked { state.sendEvent(OnExit) }
  }
}
