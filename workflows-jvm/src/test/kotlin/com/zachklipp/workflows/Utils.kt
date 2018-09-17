package com.zachklipp.workflows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

actual typealias AssertionError = java.lang.AssertionError

actual fun <T> runBlocking(
  context: CoroutineContext,
  block: suspend CoroutineScope.() -> T
): T = runBlocking(context, block)
