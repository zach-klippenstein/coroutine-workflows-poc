package com.zachklipp.workflows

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.toChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A [CoroutineScope] that is also a [ReceiveChannel] for receiving workflow events, and a
 * [SendChannel] for emitting workflow state.
 */
interface WorkflowProducerScope<S : Any, E : Any> : CoroutineScope,
    SendChannel<S>,
    ReceiveChannel<E>

/**
 * Creates a [Workflow] by launching a coroutine that is basically a combination of
 * `produce` (for states), `actor` (for events), and `async` (for the result).
 *
 * The [lambda][block] is invoked from a new child coroutine that can do all three workflow things:
 *  - Emit state by calling [send][SendChannel.send], or any of the other methods on [SendChannel].
 *  - Receive events by calling [receive][ReceiveChannel.receive], or any of the other methods on
 *    [ReceiveChannel].
 *  - Finish the workflow by returning a value.
 *
 * @param context additional to [CoroutineScope.coroutineContext] context of the
 * coroutine.
 * @param start coroutine start option. The default value is [CoroutineStart.DEFAULT].
 * @param onCompletion optional completion handler for the coroutine (see [Job.invokeOnCompletion]).
 * @param block the coroutine code.
 */
fun <S : Any, E : Any, R : Any> CoroutineScope.workflow(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  onCompletion: CompletionHandler? = null,
  block: suspend WorkflowProducerScope<S, E>.() -> R
): Workflow<S, E, R> {
  val stateChannel = Channel<S>()
  val eventChannel = Channel<E>()
  val result = async(
      context = context,
      start = start,
      onCompletion = { cause ->
        // Cancelling the result also cancels the channel (and vice versa).
        // If the workflow finishes normally the channel will be closed normally first, so this will
        // be a no-op.
        stateChannel.cancel(cause)
        eventChannel.cancel(cause)
        onCompletion?.invoke(cause)
      }) {
    val result = object : WorkflowProducerScope<S, E>,
        CoroutineScope by this,
        SendChannel<S> by stateChannel,
        ReceiveChannel<E> by eventChannel {}.block()
    stateChannel.close()
    eventChannel.close()
    return@async result
  }
  stateChannel.invokeOnClose { cause -> if (cause is CancellationException) result.cancel(cause) }
  eventChannel.invokeOnClose { cause -> if (cause is CancellationException) result.cancel(cause) }

  return object : Workflow<S, E, R> {
    override val state: ReceiveChannel<WorkflowState<S, E>> = stateChannel
        .map { WorkflowState(it, eventChannel::offer) }
    override val result: Deferred<R> get() = result

    override fun abandon() {
      // Ignore the return value – we don't really care if the workflow was already finished.
      result.cancel(CancellationException("Workflow abandoned."))
    }
  }
}

fun <R : Any> finishedWorkflow(result: R): Workflow<Nothing, Any, R> =
  CompletableDeferred(result).toWorkflow()

/**
 * Converts a [ReceiveChannel] to a [Workflow] that doesn't accept any input and has no result.
 *
 * This function [consumes][ReceiveChannel.consume] all elements of the original [ReceiveChannel].
 */
fun <S : Any> ReceiveChannel<S>.toWorkflow(
  context: CoroutineContext = Dispatchers.Unconfined
): Workflow<S, Nothing, Unit> = GlobalScope.workflow(context) {
  this@toWorkflow.toChannel(this)
}

/**
 * Converts a [Deferred] to an already-finished [Workflow] with the result of the [Deferred].
 */
fun <R : Any> Deferred<R>.toWorkflow(
  context: CoroutineContext = Dispatchers.Unconfined
): Workflow<Nothing, Any, R> = GlobalScope.workflow(context) {
  this@toWorkflow.await()
}
