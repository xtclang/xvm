package org.xvm.debug

import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceBreakpoint
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS

/** Without a runtime debugger, the adapter must say so: nothing it reports may suggest debugging works. */
class XtcDebugServerTest {
    private val server = XtcDebugServer()

    @Test
    fun `breakpoints are reported unverified with the reason`() {
        val response =
            server
                .setBreakpoints(
                    SetBreakpointsArguments().apply {
                        source = Source().apply { path = "/work/App.x" }
                        breakpoints = arrayOf(SourceBreakpoint().apply { line = 3 }, SourceBreakpoint().apply { line = 7 })
                    },
                ).get(10, SECONDS)
        assertThat(response.breakpoints.map { it.line }).containsExactly(3, 7)
        assertThat(response.breakpoints).allSatisfy {
            assertThat(it.isVerified).isFalse()
            assertThat(it.message).isEqualTo(XtcDebugServer.NOT_AVAILABLE)
        }
    }

    @Test
    fun `launch and attach fail with the reason`() {
        listOf(server.launch(mutableMapOf("module" to "App")), server.attach(mutableMapOf())).forEach { request ->
            assertThatThrownBy { request.get(10, SECONDS) }
                .isInstanceOf(ExecutionException::class.java)
                .cause()
                .isInstanceOfSatisfying(ResponseErrorException::class.java) {
                    assertThat(it.responseError.message).isEqualTo(XtcDebugServer.NOT_AVAILABLE)
                }
        }
    }

    @Test
    fun `nothing runs, so there are no threads and no extra capabilities`() {
        assertThat(server.threads().get(10, SECONDS).threads).isEmpty()
        val capabilities = server.initialize(InitializeRequestArguments().apply { adapterID = "xtc" }).get(10, SECONDS)
        // the wire form shows exactly what is advertised
        assertThat(Gson().toJsonTree(capabilities).asJsonObject.keySet()).containsExactly("supportsConfigurationDoneRequest")
    }
}
