package org.xtclang.idea.playbook.probe

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long, in seconds, the playbook waits for what it expects before a case fails as hung. It ends only a hang: ten
 * minutes, more than ten times the whole of the slowest case, so that no case's result depends on speed, while a hang,
 * which ends the run, costs minutes rather than an hour. It is here, in the probe package, so that the probes the IDE
 * runs and the playbook that drives it share it.
 *
 * `-Dxtc.playbook.hang=<seconds>` (`-PintellijPlaybookHang`) shortens it, in the playbook and in the IDE it drives, for a
 * quicker look at a case that hangs; a run with it says nothing about a slow machine.
 */
val HANG_SECONDS: Long = System.getProperty("xtc.playbook.hang")?.toLongOrNull()?.takeIf { it > 0 } ?: 600L

/** [HANG_SECONDS] as a duration, for the waits that take one. */
val HANG_LIMIT: Duration = HANG_SECONDS.seconds
