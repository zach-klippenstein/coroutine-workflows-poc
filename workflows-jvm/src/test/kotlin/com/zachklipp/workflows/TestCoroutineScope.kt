package com.zachklipp.workflows

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * TODO write documentation.
 */

class CoroutineTestScope : TestRule {
  private lateinit var testContext: CoroutineContext

  override fun apply(
    base: Statement,
    description: Description
  ): Statement = object : Statement() {
    override fun evaluate() {
      runBlocking(CoroutineName(description.displayName)) {
        testContext = coroutineContext
        base.evaluate()
      }
    }
  }

  operator fun invoke(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
  ) {
    GlobalScope.launch(testContext + context) {
      block()
      testContext[Job]!!.cancelChildren(CancellationException("test finished"))
    }
  }
}
