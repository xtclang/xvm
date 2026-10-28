package org.xvm.lsp.server

import com.google.gson.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ResolveReportsTest {
    @Test
    fun `handles preserve detached values and reject foreign stale tampered and evicted requests`() {
        val reports = ResolveReports<String>(limit = 1, characterLimit = 20)
        val handle = reports.remember(7, "entry", "payload", 7)
        assertThat(reports.resolve(JsonPrimitive(handle), 7, "entry")).isEqualTo("payload")
        listOf(
            { reports.resolve(handle, 8, "entry") },
            { reports.resolve(handle, 7, "other") },
            { ResolveReports<String>().resolve(handle, 7, "entry") },
            { reports.resolve(mapOf("id" to handle), 7, "entry") },
        ).forEach { request ->
            assertThatThrownBy { request() }.hasMessageContaining("expired or changed")
        }
        assertThat(reports.remember(7, "large", "oversized", 21)).isNull()
        assertThat(reports.resolve(handle, 7, "entry")).isEqualTo("payload")
        reports.remember(7, "next", "replacement", 11)
        assertThatThrownBy { reports.resolve(handle, 7, "entry") }
            .hasMessageContaining("expired or changed")
        reports.clear()
    }
}
