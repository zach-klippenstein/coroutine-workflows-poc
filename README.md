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

## Running the sample app

```
./gradlew run
```

## Running the unit tests

```
./gradlew test
```
