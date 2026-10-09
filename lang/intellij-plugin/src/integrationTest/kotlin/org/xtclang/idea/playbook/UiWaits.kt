package org.xtclang.idea.playbook

import com.intellij.driver.sdk.WaitForException
import com.intellij.openapi.diagnostic.fileLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val logger = fileLogger()

/** Observe readiness promptly without shortening the existing failure deadlines. */
internal fun awaitUi(
    message: String? = null,
    timeout: Duration = 5.seconds,
    errorMessage: (() -> String)? = null,
    condition: () -> Boolean,
) {
    awaitUi(
        message,
        timeout,
        errorMessage = errorMessage?.let { describe -> { _: Boolean -> describe() } },
        getter = condition,
        checker = { it },
    )
}

internal fun <T> awaitUi(
    message: String? = null,
    timeout: Duration = 5.seconds,
    errorMessage: ((T) -> String)? = null,
    getter: () -> T,
    checker: (T) -> Boolean,
): T {
    // TODO LSP4IJ: UP29 — Driver waitFor checks a successful remote predicate again before
    // returning. A connection restart between those reads produces an immediate false timeout.
    // Keep this until Driver accepts each observation once and uses a monotonic deadline.
    val started = TimeSource.Monotonic.markNow()
    message?.let { logger.info("Await: '$it' with timeout $timeout") }
    while (true) {
        val result = getter()
        if (checker(result)) {
            message?.let { logger.info("Await: '$it' completed in ${started.elapsedNow()}") }
            return result
        }
        val remaining = timeout - started.elapsedNow()
        if (remaining <= Duration.ZERO) {
            throw WaitForException(
                timeout,
                errorMessage?.invoke(result)
                    ?: ("Failed: $message" + if (result is Boolean) "" else ". Actual: $result"),
            )
        }
        Thread.sleep(minOf(remaining, 100.milliseconds).inWholeMilliseconds.coerceAtLeast(1))
    }
}

internal fun <T> awaitUiNotNull(
    message: String? = null,
    timeout: Duration = 5.seconds,
    getter: () -> T?,
): T = awaitUi(message, timeout, getter = getter, checker = { it != null })!!
