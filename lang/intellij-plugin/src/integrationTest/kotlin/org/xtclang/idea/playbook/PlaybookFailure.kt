package org.xtclang.idea.playbook

import com.intellij.driver.sdk.WaitForException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

internal class PlaybookCleanupFailure(
    cause: Throwable,
) : IllegalStateException("Could not restore the playbook workspace", cause)

/** An unfinished operation or failed cleanup cannot share an IDE with subsequent cases. */
internal fun Throwable.mustStopPlaybook(): Boolean =
    this is WaitForException || this is TimeoutException || this is CancellationException ||
        this is InterruptedException || this is PlaybookCleanupFailure ||
        cause?.mustStopPlaybook() == true || suppressed.any(Throwable::mustStopPlaybook)
