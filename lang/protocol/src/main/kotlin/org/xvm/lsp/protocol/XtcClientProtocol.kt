package org.xvm.lsp.protocol

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

/**
 * The notifications and requests of the language server's `xtc/` extension of LSP that the client answers, beside
 * LSP's own: the IntelliJ plugin's language client implements them, and the language server calls them through lsp4j,
 * whose proxy of the client this interface is. A client that implements none still builds; its runs have nowhere to
 * report. Those with a default here a client may leave out.
 *
 * lsp4j finds each message by its annotation here, which an implementation must not repeat: an endpoint that finds a
 * message's name on two methods does not start.
 */
interface XtcClientProtocol : LanguageClient {
    /** Output of an embedded run, for the console of its run ID. */
    @JsonNotification("xtc/runOutput")
    fun runOutput(params: RunOutputParams)

    /** A run's single terminal outcome. */
    @JsonNotification("xtc/runExited")
    fun runExited(params: RunExitedParams)

    /** An event of the tests that a run with `tests` runs, ahead of the run's exit; see [RunParams]. */
    @JsonNotification("xtc/testEvent")
    fun testEvent(params: TestEventParams) {}

    /** One DAP message from a debug session to the client. */
    @JsonNotification("xtc/debugMessage")
    fun debugMessage(params: DebugMessageParams) {}

    /**
     * The bundled libraries that the workspace's own modules replace, each time they change: while there are any, the
     * client builds and runs with Gradle, as the server builds and runs only with the bundled XDK.
     */
    @JsonNotification("xtc/replacedLibraries")
    fun replacedLibraries(params: ReplacedLibrariesParams) {}

    /**
     * The Gradle project of each source module that the workspace's imported build model names, each time they change:
     * the client runs a module's `runXtc` and `testXtc` by their full path, such as `:xdk:lib-json:testXtc`, which in a
     * composite build reaches the included build that a bare name does not. Empty without a build model.
     */
    @JsonNotification("xtc/gradleProjects")
    fun gradleProjects(params: GradleProjectsParams) {}

    /**
     * Runs a debug session's module with its Gradle task or the xtc command, as [DebugTaskParams] says, and answers
     * once the task has ended; canceling the request ends the task. A client that runs no tasks fails it.
     */
    @JsonRequest("xtc/debugTask")
    fun debugTask(params: DebugTaskParams): CompletableFuture<DebugTaskResult> =
        CompletableFuture.failedFuture(UnsupportedOperationException("This client does not run debug tasks (${params.buildMode})"))
}
