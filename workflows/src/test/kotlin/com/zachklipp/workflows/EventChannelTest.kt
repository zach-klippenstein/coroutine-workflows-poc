package com.zachklipp.workflows

import com.zachklipp.workflows.Foo.Bar
import com.zachklipp.workflows.Foo.Baz
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

private sealed class Foo {
  data class Bar(val msg: String) : Foo()
  object Baz : Foo() {
    override fun toString(): String = "Baz"
  }
}

class EventChannelTest {
  @Test fun singleValueMatching() = runBlocking {
    val eventChannel = EventChannelSource<Foo>()
    val result = async(Unconfined) {
      with(eventChannel) {
        select<String> {
          events {
            onEvent<Bar> { it.msg }
          }
        }
      }
    }

    assertFalse(result.isCompleted)
    eventChannel.send(Bar("buzz"))
    assertEquals("buzz", result.await())
  }

  @Test fun singleValueNotMatching()  {
    val eventChannel = EventChannelSource<Foo>()
    val result = GlobalScope.async(Unconfined) {
      with(eventChannel) {
        select<String> {
          events {
            onEvent<Bar> { it.msg }
          }
        }
      }
    }

    assertFalse(result.isCompleted)
    assertFailsWith<IllegalStateException>("Workflow not ready to accept Baz") {
      eventChannel.send(Baz)
    }
  }

  @Test fun selectTest() = runBlocking {
    val eventChannel = EventChannelSource<Foo>()
    launch(Unconfined) {
      with(eventChannel) {

        // First send.
        select<Unit> {
          events {
            onEvent<Bar> { fail("Expected Baz") }
            onEvent(Baz) { /* success! */ }
          }
        }

        // Second send.
        select<Unit> {
          events {
            onEvent<Bar> { assertEquals("buzz", it.msg) }
            onEvent(Baz) { fail("Expected Baz") }
          }
        }
      }
    }

    eventChannel.send(Baz)
    eventChannel.send(Bar("buzz"))
  }
}
