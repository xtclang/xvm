package org.xtclang.idea.dap

import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.redhat.devtools.lsp4ij.dap.console.DAPConsoleView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import org.xtclang.idea.awaitUntil
import org.xtclang.idea.lsp.DebugMessageParams
import org.xtclang.idea.lsp.DebugSessionParams
import org.xtclang.idea.lsp.DebugSessionResponse
import org.xtclang.idea.lsp.XtcLanguageServer
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * LSP4IJ's own debug console, attached to the session's process as LSP4IJ attaches it, takes a typed line to the
 * session once [DebugConsoleInput] has given it input, as an evaluate request with the context repl.
 */
class DebugConsoleInputTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `a line typed in LSP4IJ's debug console reaches the session as a repl evaluation`() {
        val messages = CopyOnWriteArrayList<DebugMessageParams>()
        val server =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XtcLanguageServer::class.java)) { _, method, args ->
                when (method.name) {
                    "debugStart" -> CompletableFuture.completedFuture(DebugSessionResponse("session-1"))
                    "debugMessage" -> messages += args[0] as DebugMessageParams
                    "debugEnd" -> (args[0] as DebugSessionParams).sessionId
                    else -> throw UnsupportedOperationException(method.name)
                }.let { if (method.returnType == Void.TYPE) null else it }
            } as XtcLanguageServer
        val handler = LanguageServerDebugProcessHandler(DebugSessions(), frameSources = {}) { CompletableFuture.completedFuture(server) }
        runInEdtAndWait {
            val project = editor.fixture.project
            val console = DAPConsoleView(project, GlobalSearchScope.allScope(project), false, false)
            Disposer.register(editor.fixture.testRootDisposable, console)
            console.attachToProcess(handler)
            handler.startNotify()
            DebugConsoleInput.attach(console, handler)
            console.component
            console.type(checkNotNull(console.editor), "label\n")
            // the console sends the line on another thread
            awaitUntil("the console's line") {
                messages.isNotEmpty() || false.also { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
            }
        }
        val request = messages.single().message
        assertThat(request["command"].asString).isEqualTo("evaluate")
        assertThat(request["arguments"].asJsonObject["expression"].asString).isEqualTo("label")
        assertThat(request["arguments"].asJsonObject["context"].asString).isEqualTo("repl")
        handler.destroyProcess()
    }
}
