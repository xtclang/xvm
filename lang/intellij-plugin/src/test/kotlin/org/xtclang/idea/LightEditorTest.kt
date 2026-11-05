package org.xtclang.idea

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Disposer
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.server.definition.extension.ExtensionLanguageServerDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CountDownLatch

/**
 * What one light test leaves cannot reach the next, which reuses the light project and the wrappers that LSP4IJ keeps
 * for it. A test could end while its wrapper's connection still started, under load; the stub's connection then threw
 * as LSP4IJ stopped it, which ended LSP4IJ's removal of the test's server before it forgot the disposed wrapper. The
 * plugin's client asks every started wrapper for its run lenses and tests as it refreshes, which started that one
 * again, and the stub's factory connected each start to the stub of the test then running, whose requests to the
 * client went to the latest connection: the disposed wrapper's, which answers nothing. So ClosedFileProblemsTest's
 * refresh once waited forever, and the four tests after it found two wrappers.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LightEditorTest {
    /** What each connection to the server runs as it starts, on LSP4IJ's thread. */
    @Volatile
    private var connecting: () -> Unit = {}

    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(connecting = { connecting() }))

    @Test
    fun `a wrapper disposed as its connection starts is removed with its server`() {
        val started = CountDownLatch(1)
        val removed = CountDownLatch(1)
        connecting = {
            started.countDown()
            removed.awaited()
        }
        val file = editor.open("Starting.x", "module Starting {}\n") { file -> file }
        val servers = LanguageServiceAccessor.getInstance(editor.fixture.project)
        ReadAction.runBlocking<RuntimeException> { servers.getLanguageServers(file, null, null) }
        started.awaited()
        val wrapper = servers.startedServers.single()
        try {
            // as the end of a test removes its server, which disposes the wrapper and stops its connection
            LanguageServersRegistry.getInstance().removeServerDefinition(editor.fixture.project, wrapper.serverDefinition)
            assertThat(wrapper.isDisposed).isTrue()
            assertThat(servers.startedServers).doesNotContain(wrapper)
        } finally {
            removed.countDown()
        }
    }

    @Test
    fun `an earlier test's wrapper that starts again connects to that test's server`() {
        editor.open("Connected.x", "module Connected {}\n") { file -> synchronizer(file) }
        val connections = editor.server.clients.size
        val earlier = StubLanguageServer()
        // as the end of the earlier test left it, disposed with the removal of its server
        val wrapper = LanguageServerWrapper(editor.fixture.project, ExtensionLanguageServerDefinition(earlier.extension()))
        Disposer.dispose(wrapper)
        // as getInitializedServer() does, which a request for its server makes
        wrapper.start()
        awaitUntil("the disposed wrapper connects") { earlier.clients.size + editor.server.clients.size > connections }
        assertThat(earlier.clients).hasSize(1)
        assertThat(editor.server.clients).hasSize(connections)
        // the disposed wrapper answers nothing, so a refresh sent on its connection would wait forever
        editor.server.languageClient
            .refreshDiagnostics()
            .awaited()
    }

    @Test
    @Order(1)
    fun `a test that leaves a wrapper`() {
        // a wrapper of an earlier test's server, which the removal of this test's server leaves
        val servers = LanguageServiceAccessor.getInstance(editor.fixture.project)
        servers.forceCreateServer(ExtensionLanguageServerDefinition(StubLanguageServer().extension()))
        assertThat(servers.startedServers).hasSize(1)
    }

    @Test
    @Order(2)
    fun `the next test finds none`() {
        assertThat(LanguageServiceAccessor.getInstance(editor.fixture.project).startedServers).isEmpty()
    }
}
