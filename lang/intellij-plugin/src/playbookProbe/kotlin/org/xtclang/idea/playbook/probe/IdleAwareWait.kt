package org.xtclang.idea.playbook.probe

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How long a wait sees nothing in progress, without a break, before it fails as stuck, as the tours' waits do. */
private val IDLE = 60.seconds

/** How often the wait asks what is in progress, which asks the language server too. */
private val ASK = 1.seconds

/**
 * Polls [done] until it holds, as the tours' waits do: it fails with [failure] and the IDE's state once nothing has
 * been in progress for a minute, which no wait for a result can outlast, and else at [HANG_LIMIT], which is only a bound
 * against a hang. [done] is not run on the EDT; it runs what it needs there itself.
 */
internal fun awaitInProgress(
    failure: () -> String,
    done: () -> Boolean,
) {
    val started = TimeSource.Monotonic.markNow()
    var idleSince: TimeSource.Monotonic.ValueTimeMark? = null
    var asked = started
    while (!done()) {
        check(started.elapsedNow() < HANG_LIMIT) { "${failure()}: not done within $HANG_LIMIT" }
        if (asked.elapsedNow() >= ASK) {
            asked = TimeSource.Monotonic.markNow()
            idleSince = if (TourUi.activity().isEmpty()) idleSince ?: asked else null
            check(idleSince == null || idleSince.elapsedNow() < IDLE) {
                "${failure()}: nothing in progress for $IDLE, the language server ${TourUi.serverState().ifEmpty { "not started" }}"
            }
        }
        Thread.sleep(100)
    }
}
