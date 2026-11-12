package org.xtclang.idea.playbook.probe

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long, in seconds, the playbook waits for what it expects before a case fails as hung. It ends only a hang: an
 * hour, which no IDE reaches however slow or loaded the machine is, so no case's result depends on speed. It is here,
 * in the probe package, so that the probes the IDE runs and the playbook that drives it share it.
 *
 * `-Dxtc.playbook.hang=<seconds>` (`-PintellijPlaybookHang`) shortens it, in the playbook and in the IDE it drives, for a
 * quicker look at a case that hangs; a run with it says nothing about a slow machine.
 */
val HANG_SECONDS: Long = System.getProperty("xtc.playbook.hang")?.toLongOrNull()?.takeIf { it > 0 } ?: 3_600L

/** [HANG_SECONDS] as a duration, for the waits that take one. */
val HANG_LIMIT: Duration = HANG_SECONDS.seconds
