package org.xvm.lsp.util

import ch.qos.logback.core.PropertyDefinerBase
import java.time.Instant

/** Keep concurrent IDE servers and test JVMs in separate trace files, including PID reuse. */
class TraceProcessId : PropertyDefinerBase() {
    override fun getPropertyValue(): String =
        ProcessHandle.current().let {
            "${it.pid()}-${it.info().startInstant().orElseGet(Instant::now).toEpochMilli()}"
        }
}
