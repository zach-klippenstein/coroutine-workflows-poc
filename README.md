# coroutine-workflows-poc

This is a proof-of-concept for entirely coroutine-based reactor + workflows.

* Uses stable coroutines (Kotlin 1.3-M2).
* Uses an EAP version of the coroutines library to get 1.3-compatible channels.
* Build scripts are all written in the Kotlin DSL.
* The workflow + reactor library is _almost entirely pure, multiplatform_ Kotlin – only a few
  trivial things need to be aliased to their JVM counterparts.

The API is vaguely shaped like the real one, or as close as I could remember from the last time
I saw it.

## Contents

There are three modules:

1. workflows – the generic `Workflow` and `Reactor` types. This is a multiplatform common module!
   (Almost) pure Kotlin, no JVM.
2. workflows-jvm – defines a few aliases to JVM-only constructs:
     * `CoroutineName` is, I think probably mistakenly, still in `kotlin-coroutines-core`.
     * A couple utilities for unit tests.
3. app – a super dumb sample app with 3 screens, using JavaFX.

## Workflow

`Workflow` is the core interface this library exports. It looks (effectively) like this:

```kotlin
interface Workflow<out State : Any, in Event : Any, out Result : Any> {
  val state: ReceiveChannel<Pair<State, (Event)->Boolean>>
  val result: Deferred<Result>

  fun abandon()
}
```

There are a bunch of ways to create a `Workflow`. The most powerful and flexible is with a
coroutine builder, similar to `produce`, `actor`, and `async` combined. Here's an example of how to
use it:

```kotlin
currentScope {
  val context = CoroutineName("my workflow") + Dispatchers.IO
  val workflow = workflow(context) { // this: WorkflowProducerScope<S, E>
    // this is a SendChannel<S>, so you can emit state like:
    send("initial state")

    // this is also a ReceiveChannel<E>, so you can listen for events:
    selectWhile {
      onReceive { event -> handleEvent(event) }
    }

    // …and lastly, the value returned by the lambda is the "result" of the workflow.
    return@workflow "finished"
  }
}
```

If you have a `ReceiveChannel<S>`, you can turn it into a `Workflow` real easy:

```kotlin
currentScope {
  val workflow = myChannel.asWorkflow()
}
```

## Reactor

A `Reactor` is just a way to define a workflow using a generator function that takes the current
state and returns the next state.

```kotlin
currentScope {
  val workflow = generateWorkflow("initial state") { state, events ->
    when (state) {
      "initial state" -> when (val event = events.receive()) {
        "continue" -> EnterState("second state")
        "finish" -> FinishWith("all done")
        else -> throw IllegalStateException("invalid event")
      }
      "second state" -> when (val event = events.receive()) {
        "finish" -> FinishWith("all done")
        else -> throw IllegalStateException("invalid event")
      }
    }
  }
}
```

## Running the sample app

```
./gradlew run
```

## Running the unit tests

```
./gradlew test
```
