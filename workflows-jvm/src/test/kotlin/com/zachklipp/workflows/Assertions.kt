package com.zachklipp.workflows

import kotlin.test.assertEquals
import kotlin.test.fail

internal inline fun <reified T : Throwable> assertFailsWith(
  message: String? = null,
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
