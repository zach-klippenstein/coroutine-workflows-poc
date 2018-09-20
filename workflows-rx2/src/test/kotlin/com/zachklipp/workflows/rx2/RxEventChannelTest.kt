package com.zachklipp.workflows.rx2

import com.zachklipp.workflows.EventChannelSource
import com.zachklipp.workflows.rx2.Foo.Bar
import com.zachklipp.workflows.rx2.Foo.Baz
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

private sealed class Foo {
  data class Bar(val msg: String) : Foo()
  object Baz : Foo() {
    override fun toString(): String = "Baz"
  }
}

class RxEventChannelTest {
  @Test fun singleValueMatching() {
    runBlocking {
      val eventChannel = EventChannelSource<Foo>()

      val result = eventChannel.asRxEventChannel()
          .selectEvent<String> {
            onEvent<Bar> { it.msg }
          }
          .test()

      result.assertNotComplete()
      eventChannel.send(Bar("buzz"))
      assertEquals("buzz", result.values().single())
    }
  }

  @Test fun singleValueNotMatching() {
    val eventChannel = EventChannelSource<Foo>()
    val result = eventChannel.asRxEventChannel()
        .selectEvent<String> {
          onEvent<Bar> { it.msg }
        }
        .test()

    assertFailsWith<IllegalStateException>("Workflow not ready to accept Baz") {
      eventChannel.send(Baz)
    }
    result.assertNotComplete()
  }

  @Test fun selectTest() {
    runBlocking {
      val eventChannelSource = EventChannelSource<Foo>()
      val eventChannel = eventChannelSource.asRxEventChannel()

      val result1 = eventChannel.selectEvent<Unit> {
        onEvent<Bar> { fail("Expected Baz") }
        onEvent(Baz) { /* success! */ }
      }
          .test()

      eventChannelSource.send(Baz)
      result1.assertComplete()

      // Second send.
      val result2 = eventChannel.selectEvent<Unit> {
        onEvent<Bar> { assertEquals("buzz", it.msg) }
        onEvent(Baz) { fail("Expected Baz") }
      }
          .test()

      eventChannelSource.send(Bar("buzz"))
      result2.assertComplete()
    }
  }
}
