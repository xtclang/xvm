package org.xvm.lsp.util

import ch.qos.logback.core.PropertyDefinerBase
import java.time.Instant

/**
 * Supplies Logback with a process identity made from the PID and process start timestamp. Session and trace
 * paths use it to separate concurrent IDE servers and test JVMs, and to distinguish later PID reuse.
 */
class TraceProcessId : PropertyDefinerBase() {
    override fun getPropertyValue(): String =
        ProcessHandle.current().let {
            "${it.pid()}-${it.info().startInstant().orElseGet(Instant::now).toEpochMilli()}"
        }
}
