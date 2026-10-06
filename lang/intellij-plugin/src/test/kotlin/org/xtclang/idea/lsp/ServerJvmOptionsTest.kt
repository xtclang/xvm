package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ServerJvmOptionsTest {
    @Test fun `supported options remain separate arguments`() {
        val options = listOf("-Xms256M", "-Xmx2G", "-Xss1M", "-XX:ActiveProcessorCount=4", "-XX:+UseG1GC")
        assertThat(ServerJvmOptions.validate(options)).isEqualTo(options)
    }

    @Test fun `reject launch overrides duplicates conflicts and impossible heaps`() {
        listOf(
            listOf("-jar"),
            listOf("@options"),
            listOf("-Dxtc.adapter=mock"),
            listOf("-javaagent:agent.jar"),
            listOf("-Xmx1G -Xss1M"),
            listOf("-Xmx2G", "-Xmx1G"),
            listOf("-Xms2G", "-Xmx1G"),
            listOf("-XX:+UseG1GC", "-XX:+UseZGC"),
            listOf("-Xmx999999999999999999999G"),
        ).forEach {
            assertThatThrownBy { ServerJvmOptions.validate(it) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
