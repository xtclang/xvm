package org.xtclang.idea.lsp

import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ServerSettingsPersistenceTest {
    @Test fun `runtime options survive IntelliJ XML serialization`() {
        val options = ServerRuntimeSettings.Options("-Xmx768M", 2, 1, 3, 2)
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(options), ServerRuntimeSettings.Options::class.java)
        assertThat(restored).isEqualTo(options)
    }

    @Test fun `offline support session survives IntelliJ XML serialization`() {
        val session = ServerSupportLogs.Session("launch-id", "2026-10-06T10:00:00Z", "Failed launch", "/logs/server-123-456", "/traces")
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(session), ServerSupportLogs.Session::class.java)
        assertThat(restored).isEqualTo(session)
    }
}
