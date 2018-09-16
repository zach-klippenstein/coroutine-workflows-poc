package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.none
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WorkflowTest {
  @Test fun initialState() = runBlocking {
    val workflow = MyReactor().toWorkflow(this, EnterState("initial"))

    assertEquals(actual = workflow.state.receive().state, expected = "initial")
    assertFalse(workflow.result.isCompleted)

    workflow.abandon()
  }

  @Test fun initiallyFinished() = runBlocking {
    val workflow = MyReactor().toWorkflow(this, FinishWith("done"))

    assertTrue(workflow.state.none())
    assertEquals(actual = workflow.result.await(), expected = "done")

    workflow.abandon()
  }

  @Test fun states() = runBlocking {
    val workflow = MyReactor().toWorkflow(this, EnterState("initial"))

    val state = workflow.state.receive()
    assertEquals(actual = state.state, expected = "initial")

    assertTrue(state.eventHandler("on(next)"))
    assertEquals(actual = workflow.state.receive().state, expected = "next")

    workflow.abandon()
  }

  @Test fun abandonClosesChannels() = runBlocking {
    val workflow = MyReactor().toWorkflow(this, EnterState("initial"))

    workflow.abandon()

    assertTrue(workflow.result.isCancelled)
    assertTrue(workflow.state.isClosedForReceive)
    try {
      workflow.result.await()
      fail("expected a CancellationException")
    } catch (e: CancellationException) {
      // success!
    }
    try {
      assertNull(workflow.state.receiveOrNull())
      fail("expected a CancellationException")
    } catch (e: CancellationException) {
      // success!
    }
  }
}

private class MyReactor : Reactor<String, String, String> {
  override suspend fun onReact(
    state: String,
    events: ReceiveChannel<String>
  ): Reaction<String, String> {
    val event = events.receive()
    println("In state '$state', got event '$event'…")
    return parseReaction(event)
  }

  companion object {
    private val COMMAND_PATTERN = """^(\w+)\((\w+)\)$""".toRegex()
    private const val CONTINUE_COMMAND = "on"
    private const val FINISH_COMMAND = "finish"

    private fun parseReaction(command: String): Reaction<String, String> {
      val (command, value) = COMMAND_PATTERN.matchEntire(command)?.destructured
          ?: throw IllegalArgumentException("Expected command to match $COMMAND_PATTERN: $command")
      return when (command) {
        CONTINUE_COMMAND -> EnterState(value)
        FINISH_COMMAND -> FinishWith(value)
        else -> throw IllegalArgumentException("Unrecognized command: $command")
      }
    }
  }
}
