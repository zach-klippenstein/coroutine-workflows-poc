package com.zachklipp.workflows

import com.zachklipp.workflows.Reaction.EnterState
import com.zachklipp.workflows.Reaction.FinishWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.channels.none
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReactorTest {
  @Test fun initialState() = runBlocking(CoroutineName("test main")) {
    val workflow = reactor("initial", context = CoroutineName("reactor")) { testReactor(it) }

    assertEquals(actual = workflow.state.receive().state, expected = "initial")
    assertFalse(workflow.result.isCompleted)

    workflow.abandon()
  }

  @Test fun states() = runBlocking {
    val workflow = reactor("initial", Unconfined) { testReactor(it) }
    val state = workflow.state.receive()
    assertEquals(actual = state.state, expected = "initial")

    state.sendEvent("on(next)")

    assertEquals(actual = workflow.state.receive().state, expected = "next")

    workflow.abandon()
  }

  @Test fun finishes() = runBlocking {
    val workflow = reactor("initial", Unconfined) { testReactor(it) }

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
    val workflow = reactor("initial", Unconfined) { testReactor(it) }

    workflow.state.receive()
        .sendEvent("throw(fail)")

    assertFailsWith<RuntimeException>("fail") { workflow.state.receive() }
    assertFailsWith<RuntimeException>("fail") { workflow.result.await() }

    workflow.abandon()
  }

  @Test fun reactInvokedFromScopeDispatcher() = runBlocking(CoroutineName("main")) {
    // This should be a TestCoroutineContext dispatcher, but that's JVM-only for some reason.
    val reactorDispatcher = Dispatchers.Default
    val workflow = withContext(reactorDispatcher) { reactor(Unit, reactor = dispatcherReactor) }

    val actualDispatcher = workflow.result.await()
    assertNotEquals(coroutineContext.dispatcher, actualDispatcher)
    assertEquals(reactorDispatcher, actualDispatcher)
  }

  @Test fun reactInvokedFromPassedDispatcher() = runBlocking(CoroutineName("main")) {
    val reactorDispatcher = Dispatchers.Default
    val workflow = reactor(Unit, reactorDispatcher, reactor = dispatcherReactor)

    val actualDispatcher = workflow.result.await()
    assertNotEquals(coroutineContext.dispatcher, actualDispatcher)
    assertEquals(reactorDispatcher, actualDispatcher)
  }

  @Test fun reactInvokedFromThisDispatcher() = runBlocking {
    val workflow = reactor(Unit, reactor = dispatcherReactor)

    assertEquals(coroutineContext.dispatcher, workflow.result.await())
  }

  /**
   * Gotcha: if we use withContext, the reactor is run _entirely_ within the enclosing _coroutine_.
   * All we really want is to share the dispatcher.
   * What this incorrect behavior means that cancelling the workflow *won't* cancel the react
   * method, since it's actually running in the parent coroutine.
   */
  @Test fun abandoningWorkflowCancelsReactor() = runBlocking(CoroutineName("test main")) {
    val workflow = reactor<Unit, Nothing, Unit>(Unit, CoroutineName("reactor")) { _ ->
      suspendCancellableCoroutine<Nothing> { /* Suspend forever */ }
    }
    assertEquals(Unit, workflow.state.receive().state)

    workflow.abandon()

    assertFailsWith<CancellationException> { workflow.state.none() }
    assertFailsWith<CancellationException> { workflow.result.await() }
  }
}

suspend fun ReactorScope<String>.testReactor(state: String): Reaction<String, String> {
  println("waiting for command… [$coroutineContext]")
  val event = receive()
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

private val dispatcherReactor: Reactor<Unit, Nothing, CoroutineDispatcher> = {
  FinishWith(coroutineContext.dispatcher)
}

private val CoroutineContext.dispatcher: CoroutineDispatcher
  get() = this[ContinuationInterceptor] as CoroutineDispatcher
