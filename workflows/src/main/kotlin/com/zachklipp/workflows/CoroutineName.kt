package com.zachklipp.workflows

import kotlin.coroutines.AbstractCoroutineContextElement

/**
 * Temporary shim to map to the JVM `CoroutineName`, since it's not in `core-common` yet.
 */
expect class CoroutineName(name: String) : AbstractCoroutineContextElement
