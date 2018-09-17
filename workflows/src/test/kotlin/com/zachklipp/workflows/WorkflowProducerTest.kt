package com.zachklipp.workflows

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.none
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowProducerTest {
  @Test fun happyPath() = runBlocking {
    val workflow = simpleEchoWorkflow()

    // Workflow should be running.
    val initialState = workflow.state.receive()
    assertEquals("initial state", initialState.state)

    initialState.sendEvent("doing things!")

    // Workflow should now be finished.
    assertFalse(initialState.eventHandler("nope"))
    assertTrue(workflow.state.none())
    assertEquals("got event: doing things!", workflow.result.await())
  }

  @Test fun appliesContext() {
    runBlocking {
      workflow<Nothing, Nothing, Unit>(CoroutineName("test coroutine")) {
        println("coroutineContext = $coroutineContext")
        assertTrue("test coroutine" in coroutineContext.toString())
      }
    }
  }

  @Test fun throws() = runBlocking {
    val workflow = workflow<String, String, String> {
      throw RuntimeException("fail")
    }

    assertFailsWith<RuntimeException>("fail") { workflow.state.none() }
    assertFailsWith<RuntimeException>("fail") { workflow.result.await() }
  }

  @Test fun cancellingStateNormallyCancelsChannels() = runBlocking {
    val workflow = simpleEchoWorkflow()

    workflow.state.cancel(CancellationException("abandon"))

    assertTrue(workflow.state.isClosedForReceive)
    assertFailsWith<CancellationException>("abandon") { workflow.result.await() }
  }

  @Test fun cancellingStateExceptionallyCancelsChannels() = runBlocking {
    val workflow = simpleEchoWorkflow()

    workflow.state.cancel(RuntimeException("fail"))

    assertTrue(workflow.state.isClosedForReceive)
    assertFailsWith<RuntimeException>("fail") { workflow.result.await() }
  }

  @Test fun cancellingResultNormallyCancelsChannels() = runBlocking {
    val workflow = simpleEchoWorkflow()

    workflow.result.cancel(CancellationException("abandon"))

    assertFailsWith<CancellationException>("abandon") { workflow.state.none() }
    assertFailsWith<CancellationException>("abandon") { workflow.result.await() }
  }

  @Test fun cancellingResultExceptionallyCancelsChannels() = runBlocking {
    val workflow = simpleEchoWorkflow()

    workflow.result.cancel(RuntimeException("fail"))

    assertFailsWith<RuntimeException>("fail") { workflow.state.none() }
    assertFailsWith<RuntimeException>("fail") { workflow.result.await() }
  }

  @Test fun abandonClosesChannels() = runBlocking {
    val workflow = simpleEchoWorkflow()

    workflow.abandon()

    assertFailsWith<CancellationException>("Workflow abandoned.") {
      workflow.state.none()
    }
    assertFailsWith<CancellationException>("Workflow abandoned.") {
      workflow.result.await()
    }
  }
}

private fun CoroutineScope.simpleEchoWorkflow() = workflow<String, String, String> {
  send("initial state")
  val event = receive()
  return@workflow "got event: $event"
}
