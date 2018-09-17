# coroutine-workflows-poc

This is a proof-of-concept for entirely coroutine-based reactor + workflows.

* Uses stable coroutines (Kotlin 1.3-M2).
* Uses an RC version of the coroutines library to get 1.3-compatible channels.
* Build scripts are all written in the Kotlin DSL.

The API is vaguely shaped like the real one, or as close as I could remember from the last time
I saw it.

## Contents

There are two modules:

1. workflows – the generic `Workflow` and `Reactor` types.
2. app – a super dumb sample app with 3 screens, using JavaFX.

## Running the sample app

```
./gradlew run
```

## Running the unit tests

```
./gradlew test
```
