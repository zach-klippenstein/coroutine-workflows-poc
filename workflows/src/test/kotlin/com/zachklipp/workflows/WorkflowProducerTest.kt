package com.zachklipp.workflows

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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
    assertFailsWith<IllegalStateException>("Workflow not ready to accept nope") {
      initialState.sendEvent("nope")
    }
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

  @Test fun channelToWorkflow() = runBlocking {
    val channel = Channel<String>()
    val workflow = channel.toWorkflow()

    assertTrue(workflow.state.isEmpty)
    assertFalse(workflow.result.isCompleted)

    channel.send("foo")

    assertEquals("foo", workflow.state.receive().state)
    assertTrue(workflow.state.isEmpty)
    assertFalse(workflow.result.isCompleted)

    channel.close()

    assertTrue(workflow.state.none())
    assertEquals(Unit, workflow.result.await())
  }

  @Test fun channelToWorkflowCloseError() = runBlocking {
    val channel = Channel<String>()
    val workflow = channel.toWorkflow()

    channel.close(RuntimeException("fail"))

    assertFailsWith<RuntimeException>("fail") { workflow.state.none() }
    assertFailsWith<RuntimeException>("fail") { workflow.result.await() }
  }

  @Test fun channelToWorkflowCancel() = runBlocking {
    val channel = Channel<String>()
    val workflow = channel.toWorkflow()

    channel.cancel()

    assertTrue(workflow.state.none())
    assertEquals(Unit, workflow.result.await())
  }

  @Test fun deferredToWorkflow() = runBlocking {
    val deferred = CompletableDeferred<String>()
    val workflow = deferred.toWorkflow()

    assertTrue(workflow.state.isEmpty)
    assertFalse(workflow.result.isCompleted)

    deferred.complete("foo")

    assertTrue(workflow.state.none())
    assertEquals("foo", workflow.result.await())
  }

  @Test fun deferredToWorkflowCompleteError() = runBlocking {
    val deferred = CompletableDeferred<String>()
    val workflow = deferred.toWorkflow()

    deferred.completeExceptionally(RuntimeException("fail"))

    assertFailsWith<RuntimeException>("fail") { workflow.state.none() }
    assertFailsWith<RuntimeException>("fail") { workflow.result.await() }
  }

  @Test fun deferredToWorkflowCancel() = runBlocking {
    val deferred = CompletableDeferred<String>()
    val workflow = deferred.toWorkflow()

    deferred.cancel()

    assertFailsWith<CancellationException> { workflow.state.none() }
    assertFailsWith<CancellationException> { workflow.result.await() }
  }
}

private fun CoroutineScope.simpleEchoWorkflow() = workflow<String, String, String> {
  send("initial state")
  val event = receive()
  return@workflow "got event: $event"
}
