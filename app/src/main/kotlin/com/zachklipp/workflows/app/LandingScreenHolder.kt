package com.zachklipp.workflows.app

import com.zachklipp.workflows.WorkflowState
import com.zachklipp.workflows.app.HelloEvent.OnGoToGreeting
import com.zachklipp.workflows.app.HelloScreen.Landing
import javafx.geometry.Pos.CENTER
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox

class LandingScreenHolder : ScreenHolder<Landing>() {
  private lateinit var button: Button

  override fun onCreateNode(): Parent = VBox(
      Label("Welcome!"),
      Button("Proceed…").also { button = it }
  ).apply {
    alignment = CENTER
  }

  override fun onBindNode(state: WorkflowState<Landing, HelloEvent>) {
    button.setOnMouseClicked { state.sendEvent(OnGoToGreeting) }
  }
}
