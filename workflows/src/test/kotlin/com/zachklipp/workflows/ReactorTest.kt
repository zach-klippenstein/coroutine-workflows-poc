package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.none
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ReactorTest {
  @Test fun initialState() = runBlocking {
    val workflow = reactor("initial", reactor = ::testReactor)

    assertEquals(actual = workflow.state.receive().state, expected = "initial")
    assertFalse(workflow.result.isCompleted)

    workflow.abandon()
  }

  @Test fun initiallyFinished() = runBlocking {
    val workflow = reactor(FinishWith("done"), reactor = ::testReactor)

    assertTrue(workflow.state.none())
    assertTrue(workflow.state.isClosedForReceive)
    assertEquals(actual = workflow.result.await(), expected = "done")
  }

  @Test fun states() = runBlocking {
    val workflow = reactor("initial", reactor = ::testReactor)
    val state = workflow.state.receive()
    assertEquals(actual = state.state, expected = "initial")

    state.sendEvent("on(next)")

    assertEquals(actual = workflow.state.receive().state, expected = "next")

    workflow.abandon()
  }

  @Test fun finishes() = runBlocking {
    val workflow = reactor("initial", reactor = ::testReactor)

    workflow.state.receive()
        .let {
          assertEquals(actual = it.state, expected = "initial")
          it.sendEvent("finish(alldone)")
        }
    assertTrue(workflow.state.none())
    assertTrue(workflow.state.isClosedForReceive)
    assertTrue(workflow.result.isCompleted)
    assertEquals(actual = workflow.result.await(), expected = "alldone")
  }

  @Test fun whenReactorThrows() = runBlocking {
    val workflow = reactor("initial", reactor = ::testReactor)

    workflow.state.receive()
        .sendEvent("throw(fail)")

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

suspend fun testReactor(
  state: String,
  events: ReceiveChannel<String>
): Reaction<String, String> {
  println("waiting for command… [$coroutineContext]")
  val event = events.receive()
  println("In state '$state', got event '$event'…")
  return parseReaction(event).also { println("next state: $it") }
}

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

internal inline fun <reified T : Throwable> assertFailsWith(
  message: String?=null,
  code: () -> Unit
) {
  try {
    code()
    fail("Expected exception to be thrown.")
  } catch (e: Throwable) {
    if (e is T) {
      if (message != null) assertEquals(message, e.message)
    } else {
      throw AssertionError(
          "Expected an exception of type ${T::class}, " +
              "but ${e::class} was thrown instead.", e
      )
    }
  }
}
