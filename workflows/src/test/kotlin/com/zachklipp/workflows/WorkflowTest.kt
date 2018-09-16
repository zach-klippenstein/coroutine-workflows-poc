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
    assertTrue(workflow.state.isClosedForReceive)
    assertEquals(actual = workflow.result.await(), expected = "done")
  }

  @Test fun abandonClosesChannels() = runBlocking {
    val workflow = MyReactor().toWorkflow(this, EnterState("initial"))

    workflow.abandon()

    val (state, result) = workflow.outputs
    assertTrue(result.isCancelled)
    assertTrue(state.isClosedForReceive)
    try {
      result.await()
      fail("expected a CancellationException")
    } catch (e: CancellationException) {
      // success!
    }
    try {
      assertNull(state.receiveOrNull())
      fail("expected a CancellationException")
    } catch (e: CancellationException) {
      // success!
    }
  }

  @Test fun states() = runBlocking {
    val workflow = MyReactor().toWorkflow(this, EnterState("initial"))

    val state = workflow.state.receive()
    assertEquals(actual = state.state, expected = "initial")

    assertTrue(state.eventHandler("on(next)"))

    assertEquals(actual = workflow.state.receive().state, expected = "next")

    workflow.abandon()
  }

  @Test fun finishes() = runBlocking {
    val workflow = MyReactor().toWorkflow(this, EnterState("initial"))

    workflow.state.receive()
        .let {
          assertEquals(actual = it.state, expected = "initial")
          assertTrue(it.eventHandler("finish(alldone)"))
        }
    assertTrue(workflow.state.none())
    assertTrue(workflow.state.isClosedForReceive)
    assertTrue(workflow.result.isCompleted)
    assertEquals(actual = workflow.result.await(), expected = "alldone")
  }

  @Test fun whenReactorThrows() = runBlocking {
    val workflow = MyReactor().toWorkflow(this, EnterState("initial"))

    workflow.state.receive()
        .eventHandler("throw(fail)")

    try {
      workflow.state.receive()
    } catch (e: RuntimeException) {
      assertEquals(actual = e.message, expected = "fail")
    }
    try {
      workflow.result.await()
    } catch (e: RuntimeException) {
      assertEquals(actual = e.message, expected = "fail")
    }

    workflow.abandon()
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
    private const val CONTINUE_VERB = "on"
    private const val THROW_VERB = "throw"
    private const val FINISH_VERB = "finish"

    private fun parseReaction(command: String): Reaction<String, String> {
      val (verb, value) = COMMAND_PATTERN.matchEntire(command)?.destructured
          ?: throw IllegalArgumentException("Expected command to match $COMMAND_PATTERN: $command")
      return when (verb) {
        CONTINUE_VERB -> EnterState(value)
        THROW_VERB -> throw RuntimeException(value)
        FINISH_VERB -> FinishWith(value)
        else -> throw IllegalArgumentException("Unrecognized command: $command")
      }
    }
  }
}
