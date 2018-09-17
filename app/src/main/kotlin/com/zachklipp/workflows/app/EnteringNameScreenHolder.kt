package com.zachklipp.workflows.app

import com.zachklipp.workflows.WorkflowState
import com.zachklipp.workflows.app.HelloEvent.OnFinishedEnteringName
import com.zachklipp.workflows.app.HelloEvent.OnNameChanged
import com.zachklipp.workflows.app.HelloEvent.OnRestart
import com.zachklipp.workflows.app.HelloScreen.EnteringName
import javafx.beans.value.ChangeListener
import javafx.geometry.Orientation.HORIZONTAL
import javafx.geometry.Pos.CENTER
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.FlowPane

class EnteringNameScreenHolder : ScreenHolder<EnteringName>() {
  private lateinit var nameField: TextField
  private lateinit var proceed: Button
  private lateinit var restart: Button
  private var textListener: ChangeListener<in String>? = null

  override fun onCreateNode(): Parent = FlowPane(
      HORIZONTAL,
      Label("Enter name:"),
      TextField().also { nameField = it },
      Button("Proceed…").also { proceed = it },
      Button("Restart").also { restart = it }
  ).apply {
    alignment = CENTER
  }

  override fun onBindNode(state: WorkflowState<EnteringName, HelloEvent>) {
    nameField.textProperty()
        .apply {
          if (textListener != null) removeListener(textListener)
          textListener = ChangeListener { _, _, newValue ->
            state.sendEvent(OnNameChanged(newValue))
          }
          addListener(textListener)
        }
    nameField.setOnAction { state.sendEvent(OnFinishedEnteringName) }
    proceed.setOnMouseClicked { state.sendEvent(OnFinishedEnteringName) }
    restart.setOnMouseClicked { state.sendEvent(OnRestart) }
  }
}
