package org.xvm.lsp.run

import org.xvm.debug.XtcDebugServer
import org.xvm.lsp.HANG_SECONDS
import kotlin.time.Duration.Companion.seconds

/**
 * The execution worker's limits for tests that start real workers: a worker may take as long as it needs to start, a
 * program to stop and a lazy variable's code to run, and an idle worker stays, so that only a hang reaches a limit and
 * no test depends on how fast the machine runs. A test of a limit's expiry sets that one short, for a worker or program
 * that never meets it; the debugger's own lazy-value limit is javatools' to test, with code that never ends.
 */
internal val HANG_LIMITS =
    RunWorkerProcess.Limits(
        startup = HANG_SECONDS.seconds,
        stop = HANG_SECONDS.seconds,
        idle = HANG_SECONDS.seconds,
        onDemand = HANG_SECONDS.seconds,
    )

/**
 * A debug session's limits for tests whose console runs real code, such as a call: it runs to its end however slow the
 * machine is, and a cancel, not the limit, ends code that loops. The console's own limit is tested with code that
 * never ends.
 */
internal val HANG_DEBUG_LIMITS = XtcDebugServer.Limits(console = HANG_SECONDS.seconds)
