package org.xtclang.idea.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xtclang.idea.awaited
import java.nio.file.Files
import java.nio.file.Path

class ConnectionProcessTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `cancelled startup never creates a child in the upstream provider`() {
        withProvider { provider ->
            provider.stop()
            assertThatThrownBy { provider.start() }
                .isInstanceOf(CannotStartProcessException::class.java)
            assertThat(provider.pid).isNull()
        }
    }

    @Test
    fun `repeated start owns one process and stopping reaps it`() {
        withProvider { provider ->
            provider.start()
            val pid = requireNotNull(provider.pid)
            // a provider owns one process: LSP4IJ creates a new provider for each start
            assertThatThrownBy { provider.start() }
                .isInstanceOf(CannotStartProcessException::class.java)
            assertThat(provider.pid).isEqualTo(pid)
            val process = ProcessHandle.of(pid).orElseThrow()
            provider.stop()
            process.onExit().awaited()
            assertThat(process.isAlive).isFalse()
            assertThatThrownBy { provider.start() }
                .isInstanceOf(CannotStartProcessException::class.java)
        }
    }

    private fun withProvider(check: (OSProcessStreamConnectionProvider) -> Unit) {
        val source =
            Files.writeString(
                directory.resolve("Probe.java"),
                "class Probe { public static void main(String[] args) throws Exception { System.in.read(); } }",
            )
        val provider =
            object :
                OSProcessStreamConnectionProvider(
                    GeneralCommandLine(
                        ProcessHandle
                            .current()
                            .info()
                            .command()
                            .orElseThrow(),
                        source.toString(),
                    ),
                ) {
                fun child(): Process? = processHandler?.process
            }
        try {
            check(provider)
        } finally {
            provider.child()?.let { child ->
                if (child.isAlive) child.destroyForcibly()
                check(child.awaited())
            }
        }
    }
}
