package org.xvm.lsp.run

import org.xvm.debug.XtcDebugServer
import org.xvm.lsp.LIVELOCK

/**
 * The execution worker's limits for tests that start real workers: a worker may take as long as it needs to start, a
 * program to stop and a lazy variable's code to run, and an idle worker stays, up to the livelock guard, so that only a
 * livelock reaches a limit, and the watchdog, not a limit, finds a hang. A test of a limit's expiry sets that one
 * short, for a worker or program that never meets it; the debugger's own lazy-value limit is javatools' to test, with
 * code that never ends.
 */
internal val LIVELOCK_LIMITS =
    RunWorkerProcess.Limits(
        startup = LIVELOCK,
        stop = LIVELOCK,
        idle = LIVELOCK,
        onDemand = LIVELOCK,
    )

/**
 * A debug session's limits for tests whose console runs real code, such as a call: it runs to its end however slow the
 * machine is, up to the livelock guard, and a cancel, not the limit, ends code that loops. The console's own limit is
 * tested with code that never ends.
 */
internal val LIVELOCK_DEBUG_LIMITS = XtcDebugServer.Limits(console = LIVELOCK)
