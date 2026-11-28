package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Validates server launch options and retention limits before publishing machine settings. Rejects launch
 * overrides, contradictory arguments and impossible heap policies while preserving each accepted option as
 * one argument.
 */
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

    @Test fun `the memory policy gives the JVM's own heap, a chosen maximum, or ZGC that adapts, unless the options choose`() {
        assertThat(ServerJvmOptions.memory(MemoryPolicy.DEFAULT, "", emptyList())).isEmpty()
        assertThat(ServerJvmOptions.memory(MemoryPolicy.DEFAULT, "4G", listOf("-Xss1M"))).containsExactly("-Xmx4G")
        assertThat(ServerJvmOptions.memory(MemoryPolicy.ADAPTIVE, "4G", emptyList())).isEqualTo(ServerJvmOptions.ADAPTIVE)
        listOf("-Xmx2G", "-XX:+UseG1GC", "-XX:+UseZGC").forEach {
            assertThat(ServerJvmOptions.memory(MemoryPolicy.ADAPTIVE, "4G", listOf(it))).describedAs(it).isEmpty()
        }
        assertThatThrownBy { ServerJvmOptions.memory(MemoryPolicy.DEFAULT, "lots", emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        // the JVM's own heap, with no policy chosen
        val store = ServerRuntimeSettings()
        assertThat(store.launchArguments()).noneMatch { it.startsWith("-Xmx") || it == "-XX:+UseZGC" }
        val before = store.state
        store.install(before, before.copy(memoryPolicy = MemoryPolicy.ADAPTIVE))
        assertThat(store.launchArguments()).startsWith(*ServerJvmOptions.ADAPTIVE.toTypedArray())
        assertThatThrownBy { store.install(store.state, store.state.copy(memoryPolicy = MemoryPolicy.DEFAULT, maxHeap = "lots")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `retention validates before publishing the immutable machine snapshot`() {
        val store = ServerRuntimeSettings()
        val before = store.state
        assertThatThrownBy { store.install(before, before.copy(logTotalSizeMb = 1)) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(store.state).isEqualTo(before)
        val next = before.copy(vmOptions = "-Xmx1G", logHistoryDays = 2, logMaxFileMb = 1, logTotalSizeMb = 3, logRetainedSessions = 2)
        store.install(before, next)
        assertThat(store.launchArguments())
            .contains("-Xmx1G", "-Dxtc.logs.historyDays=2", "-Dxtc.logs.totalSizeMb=3")
            .doesNotContain("-XX:+UseZGC")
        assertThatThrownBy { store.install(before, before) }.hasMessageContaining("changed")
        assertThat(store.state).isEqualTo(next)
    }

    @Test fun `an opened XDK source is compiled with its whole library unless the setting turns it off`() {
        val store = ServerRuntimeSettings()
        val before = store.state
        assertThat(store.launchArguments()).contains("-Dxtc.lsp.compileLibraries=true")
        store.install(before, before.copy(compileLibraries = false))
        assertThat(store.launchArguments()).contains("-Dxtc.lsp.compileLibraries=false").doesNotContain("-Dxtc.lsp.compileLibraries=true")
    }

    @Test fun `a workspace module replaces the bundled library of its name unless the setting turns it off`() {
        val store = ServerRuntimeSettings()
        val before = store.state
        assertThat(store.launchArguments()).contains("-Dxtc.lsp.replaceLibraries=true")
        store.install(before, before.copy(replaceLibraries = false))
        assertThat(store.launchArguments()).contains("-Dxtc.lsp.replaceLibraries=false").doesNotContain("-Dxtc.lsp.replaceLibraries=true")
    }
}
