package com.zachklipp.workflows

import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.none
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.currentScope
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowOperationsTest {
  @Test fun mapState() = runBlocking {
    val source = workflow<Int, Nothing, Unit> {
      send(1)
      send(2)
      send(3)
    }
    val workflow = source.mapState { it.toString() }

    assertEquals(listOf("1", "2", "3"), workflow.state.map { it.state }.toList())
    assertEquals(Unit, workflow.result.await())
  }

  @Test fun mapStateSuspending() = runBlocking {
    val source = workflow<Int, Nothing, Unit> {
      send(1)
      send(2)
      send(3)
    }
    val workflow = source.mapState { yield(); it.toString() }

    assertEquals(listOf("1", "2", "3"), workflow.state.map { it.state }.toList())
    assertEquals(Unit, workflow.result.await())
  }

  @Test fun flatMapState() = runBlocking {
    val source = workflow<Int, Nothing, Unit> {
      send(1)
      send(2)
      send(3)
    }
    val workflow = source.flatMapState { value ->
      currentScope {
        produce {
          repeat(value) { send(value.toString()) }
        }
      }
    }

    assertEquals(listOf("1", "2", "2", "3", "3", "3"), workflow.state.map { it.state }.toList())
    assertEquals(Unit, workflow.result.await())
  }

  @Test fun flatMapStateSuspending() = runBlocking {
    val source = workflow<Int, Nothing, Unit> {
      send(1)
      send(2)
      send(3)
    }
    val workflow = source.flatMapState { value ->
      currentScope {
        produce {
          repeat(value) { yield(); send(value.toString()) }
        }
      }
    }

    assertEquals(listOf("1", "2", "2", "3", "3", "3"), workflow.state.map { it.state }.toList())
    assertEquals(Unit, workflow.result.await())
  }

  @Test fun mapEvent() = runBlocking {
    val source = workflow<String, String, Unit> {
      send("initial")
      repeat(3) { send(receive()) }
    }
    val workflow = source.mapEvent<String, String, Int, Unit> { it.toString() }

    workflow.state.receive()
        .let {
          assertEquals("initial", it.state)
          it.sendEvent(1)
        }
    workflow.state.receive()
        .let {
          assertEquals("1", it.state)
          it.sendEvent(2)
        }
    workflow.state.receive()
        .let {
          assertEquals("2", it.state)
          it.sendEvent(3)
        }
    assertEquals("3", workflow.state.receive().state)
    assertEquals(Unit, workflow.result.await())
  }

  @Test fun mapResult() = runBlocking {
    val source = workflow<Nothing, Nothing, Int> { 1 }
    val workflow = source.mapResult { it.toString() }

    assertTrue(workflow.state.none())
    assertEquals("1", workflow.result.await())
  }

  @Test fun mapResultSuspending() = runBlocking {
    val source = workflow<Nothing, Nothing, Int> { 1 }
    val workflow = source.mapResult { yield(); it.toString() }

    assertTrue(workflow.state.none())
    assertEquals("1", workflow.result.await())
  }
}
