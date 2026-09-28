package org.xtclang.idea.playbook

import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Observe readiness promptly without shortening the existing failure deadlines. */
internal fun awaitUi(
    message: String? = null,
    timeout: Duration = 5.seconds,
    errorMessage: (() -> String)? = null,
    condition: () -> Boolean,
) = waitFor(message, timeout, 100.milliseconds, errorMessage, condition)

internal fun <T> awaitUi(
    message: String? = null,
    timeout: Duration = 5.seconds,
    errorMessage: ((T) -> String)? = null,
    getter: () -> T,
    checker: (T) -> Boolean,
): T = waitFor(message, timeout, 100.milliseconds, errorMessage, getter, checker)

internal fun <T> awaitUiNotNull(
    message: String? = null,
    timeout: Duration = 5.seconds,
    getter: () -> T?,
): T = waitNotNull(message, timeout, 100.milliseconds, getter = getter)
