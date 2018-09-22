package com.zachklipp.workflows.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Coroutine-based async console IO.
 */
interface Console {
  suspend fun print(text: String)
  suspend fun read(): Int
  suspend fun readLine(): String?

  companion object {
    val system by lazy { fromStreams(System.`in`, System.out) }

    fun fromStreams(
      i: InputStream,
      o: PrintStream
    ): Console = StreamConsole(i, o)
  }
}

suspend fun Console.readLine(prompt: String): String? {
  print(prompt)
  return readLine()
}

private class StreamConsole(
  private val i: InputStream,
  private val o: PrintStream
) : Console {
  override suspend fun print(text: String) = o.print(text)

  override suspend fun read(): Int {
    while (i.available() == 0) {
      delay(100, MILLISECONDS)
    }
    yield()
    return i.read()
  }

  override suspend fun readLine(): String? {
    val pipeIn = PipedInputStream(1)
    val pipeOut = PipedOutputStream(pipeIn)

    return coroutineScope {
      // Writer
      val writer = launch(Dispatchers.IO) {
        while (true) {
          while (i.available() == 0 && isActive) {
            delay(10, MILLISECONDS)
          }
          if (!isActive) return@launch
          pipeOut.write(i.read())
        }
      }

      return@coroutineScope async(Dispatchers.IO) {
        pipeIn.bufferedReader()
            .readLine()
      }.apply { invokeOnCompletion { cause -> writer.cancel(cause) } }
          .await()
    }
  }
}
