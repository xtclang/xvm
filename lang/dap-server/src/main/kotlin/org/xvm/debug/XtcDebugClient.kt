package org.xvm.debug

import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification

/** The DAP client of an Ecstasy debug session: DAP's events, and this server's own. */
interface XtcDebugClient : IDebugProtocolClient {
    /** The DAP event `xtcTest`: an event of the tests that a launch with `tests` runs, as xunit reported it. */
    @JsonNotification("xtcTest")
    fun xtcTest(event: TestEvent) {}
}
