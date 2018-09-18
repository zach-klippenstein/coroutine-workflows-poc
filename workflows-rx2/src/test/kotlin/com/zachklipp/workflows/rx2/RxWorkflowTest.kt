package com.zachklipp.workflows.rx2

import com.zachklipp.workflows.workflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class RxWorkflowTest : CoroutineScope {
  override val coroutineContext = Dispatchers.Unconfined

  @Test fun initialState() {
    val workflow = workflow<String, Nothing, Unit> {
      send("hello world")
      hangForever()
    }
        .toRxWorkflow()

    val stateSub = workflow.state.test()
    val resultSub = workflow.result.test()
    stateSub.values()
        .single()
        .let { state -> assertEquals("hello world", state.state) }
    stateSub.assertNotComplete()
    resultSub.assertNotComplete()
  }

  @Test fun result() {
    val workflow = workflow<Nothing, Nothing, String> { "done" }
        .toRxWorkflow()

    val stateSub = workflow.state.test()
    val resultSub = workflow.result.test()
    stateSub.assertNoValues()
        .assertComplete()
    resultSub.assertValue { it == "done" }
  }

  @Test fun stateAndResult() {
    val workflow = workflow<String, Nothing, String> {
      send("hello")
      "world"
    }.toRxWorkflow()

    val stateSub = workflow.state.test()
    val resultSub = workflow.result.test()
    stateSub.values()
        .single()
        .let { state -> assertEquals("hello", state.state) }
    stateSub.assertComplete()
    resultSub.assertValue { it == "world" }
  }
}

private suspend fun hangForever(): Nothing = suspendCoroutine<Nothing> {}
