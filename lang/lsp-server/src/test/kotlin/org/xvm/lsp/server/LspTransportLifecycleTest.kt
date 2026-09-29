package org.xvm.lsp.server

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.mock.MockAdapter

class LspTransportLifecycleTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `EOF and failed transport release adapter resources`(failedRead: Boolean) {
        val closed = AtomicInteger()
        val adapter =
            object : Adapter by MockAdapter() {
                override fun close() {
                    closed.incrementAndGet()
                }
            }
        val input =
            if (failedRead) {
                object : InputStream() {
                    override fun read(): Int = throw IOException("client disconnected")
                }
            } else {
                ByteArrayInputStream(byteArrayOf())
            }
        val server = XtcLanguageServer(adapter)
        launchStdio(server, input, ByteArrayOutputStream())
        server.close()
        assertThat(closed.get()).isEqualTo(1)
    }

    @Test
    fun `shutdown EOF and exit close a native adapter only once`() {
        val closed = AtomicInteger()
        val adapter =
            object : Adapter by MockAdapter() {
                override fun close() {
                    closed.incrementAndGet()
                }
            }
        val statuses = buildList {
            val server = XtcLanguageServer(adapter, ::add)
            server.shutdown().join()
            launchStdio(server, ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
            server.exit()
        }
        assertThat(closed.get()).isEqualTo(1)
        assertThat(statuses).containsExactly(0)
    }

    @Test
    fun `exit still terminates the process when resource cleanup fails`() {
        val adapter =
            object : Adapter by MockAdapter() {
                override fun close(): Unit = error("close failed")
            }
        val statuses = buildList {
            val result = runCatching { XtcLanguageServer(adapter, ::add).exit() }
            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        }
        assertThat(statuses).containsExactly(1)
    }
}
