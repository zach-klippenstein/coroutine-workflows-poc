package com.zachklipp.workflows

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The `AssertionError` constructors in the Kotlin test lib don't have the version of the
 * constructor that takes a cause.
 */
expect open class AssertionError(
  message: String?,
  cause: Throwable?
) : Error

/** Maps to the JVM-only `runBlocking` function. */
expect fun <T> runBlocking(
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend CoroutineScope.() -> T
): T
