package org.xtclang.idea.dap

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.xtclang.idea.lsp.DebugMessageParams
import org.xtclang.idea.lsp.DebugSessionParams
import org.xtclang.idea.lsp.DebugSessionResponse
import org.xtclang.idea.lsp.TestEvent
import org.xtclang.idea.lsp.XtcLanguageServer
import java.io.IOException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The process LSP4IJ's DAP client debugs through, without an IDE: the framing it reads and writes, messages that wait
 * for the session, the exit code, and the ends of a session.
 */
class LanguageServerDebugProcessHandlerTest {
    @Test
    fun `framing counts UTF-8 bytes, and the decoder takes messages written in pieces or several at once`() {
        val first = """{"seq":1,"command":"evaluate","arguments":{"expression":"Grüße ✓"}}"""
        val second = """{"seq":2,"command":"threads"}"""
        assertThat(DapFrames.frame(first)).startsWith("Content-Length: ${first.toByteArray(Charsets.UTF_8).size}\r\n\r\n")

        val received = CopyOnWriteArrayList<String>()
        val decoder = DapFrames.Decoder(received::add)
        val bytes = (DapFrames.frame(first) + DapFrames.frame(second)).toByteArray(Charsets.UTF_8)
        // one byte at a time first, splitting the header and a multi-byte character, then the rest in one write
        val split = DapFrames.frame(first).toByteArray(Charsets.UTF_8).size - 3
        (0 until split).forEach { decoder.write(bytes, it, 1) }
        assertThat(received).isEmpty()
        decoder.write(bytes, split, bytes.size - split)
        assertThat(received).containsExactly(first, second)

        assertThatThrownBy { decoder.write("Content-Type: x\r\n\r\n".toByteArray(), 0, 19) }.isInstanceOf(IOException::class.java)
    }

    @Test
    fun `a line typed in the session's console while the program runs is its input, and the client's messages go on`() {
        val sessions = DebugSessions()
        val server = FakeServer().apply { started.complete(Unit) }
        val handler = LanguageServerDebugProcessHandler(sessions) { CompletableFuture.completedFuture(server.proxy) }
        val events = Events(handler)
        handler.startNotify()

        // the console writes a typed line to the stream that the client writes its messages to
        handler.processInput.write("Grüße\n".toByteArray(Charsets.UTF_8))
        handler.processInput.flush()
        handler.write("""{"seq":1,"type":"request","command":"threads"}""")
        val typed = server.messages.first().message
        assertThat(typed["command"].asString).isEqualTo("evaluate")
        assertThat(typed["arguments"].asJsonObject["expression"].asString).isEqualTo("Grüße")
        assertThat(typed["arguments"].asJsonObject["context"].asString).isEqualTo("repl")
        // without a stop, no frame: the adapter takes the line as the program's input
        assertThat(typed["arguments"].asJsonObject.has("frameId")).isFalse()
        assertThat(server.messages.map { it.message["command"].asString }).containsExactly("evaluate", "threads")

        // the answer to the input is empty, and the client never sees the request's response
        sessions.receive(DebugMessageParams("session-1", response(typed, """{"result":""}""")))
        assertThat(events.text(ProcessOutputTypes.STDOUT)).isEmpty()
        handler.destroyProcess()
    }

    @Test
    fun `a line typed in the session's console while the program is stopped runs in the stopped frame, and shows its answer`() {
        val sessions = DebugSessions()
        val server = FakeServer().apply { started.complete(Unit) }
        val handler = LanguageServerDebugProcessHandler(sessions, frameSources = {}) { CompletableFuture.completedFuture(server.proxy) }
        val events = Events(handler)
        handler.startNotify()

        fun receive(json: String) = sessions.receive(DebugMessageParams("session-1", JsonParser.parseString(json).asJsonObject))
        receive("""{"seq":3,"type":"event","event":"stopped","body":{"reason":"breakpoint","threadId":1}}""")
        receive(
            """{"seq":4,"type":"response","request_seq":2,"command":"stackTrace","success":true,""" +
                """"body":{"stackFrames":[{"id":7,"name":"deal","line":73},{"id":8,"name":"run","line":6}]}}""",
        )
        handler.processInput.write("size()\n".toByteArray())
        val typed = server.messages.last().message
        assertThat(typed["arguments"].asJsonObject["frameId"].asInt).isEqualTo(7)
        sessions.receive(DebugMessageParams("session-1", response(typed, """{"result":"42","variablesReference":0}""")))
        // the answer is console output, a DAP output event that the client prints
        val output = events.text(ProcessOutputTypes.STDOUT)
        assertThat(output).contains("\"event\":\"output\"", "\"output\":\"42\\n\"").doesNotContain("\"request_seq\":${typed["seq"]}")

        // once the client resumes the program, a typed line is its input again
        handler.write("""{"seq":5,"type":"request","command":"continue","arguments":{"threadId":1}}""")
        handler.processInput.write("\n".toByteArray())
        assertThat(
            server.messages
                .last()
                .message["arguments"]
                .asJsonObject
                .has("frameId"),
        ).isFalse()
        handler.destroyProcess()
    }

    /** The session's successful response to [request], with [body]. */
    private fun response(
        request: JsonObject,
        body: String,
    ): JsonObject =
        JsonParser
            .parseString(
                """{"seq":90,"type":"response","request_seq":${request["seq"]},"command":"evaluate","success":true,"body":$body}""",
            ).asJsonObject

    @Test
    fun `messages the client writes before the session exists wait for it, then go in order`() {
        val server = FakeServer()
        val handler = LanguageServerDebugProcessHandler(DebugSessions()) { CompletableFuture.completedFuture(server.proxy) }
        handler.startNotify()
        handler.write("""{"seq":1,"type":"request","command":"initialize"}""")
        handler.write("""{"seq":2,"type":"request","command":"launch"}""")
        assertThat(server.messages).isEmpty()

        server.started.complete(Unit)
        assertThat(server.messages.map { it.sessionId to it.message["seq"].asInt }).containsExactly("session-1" to 1, "session-1" to 2)
        handler.write("""{"seq":3,"type":"request","command":"configurationDone"}""")
        assertThat(server.messages.map { it.message["seq"].asInt }).containsExactly(1, 2, 3)
    }

    @Test
    fun `the session's messages are framed output, its exited code is the exit code, and ending the process ends it`() {
        val sessions = DebugSessions()
        val server = FakeServer().apply { started.complete(Unit) }
        val handler = LanguageServerDebugProcessHandler(sessions) { CompletableFuture.completedFuture(server.proxy) }
        val events = Events(handler)
        handler.startNotify()

        val event = """{"seq":5,"type":"event","event":"exited","body":{"exitCode":42}}"""
        sessions.receive(DebugMessageParams("session-1", JsonParser.parseString(event).asJsonObject))
        assertThat(events.text(ProcessOutputTypes.STDOUT)).isEqualTo(DapFrames.frame(JsonParser.parseString(event).toString()))

        handler.destroyProcess()
        assertThat(server.ended).containsExactly("session-1")
        assertThat(events.exitCode).isEqualTo(42)
        // the session is gone: later messages go nowhere
        sessions.receive(DebugMessageParams("session-1", JsonObject()))
        assertThat(events.text(ProcessOutputTypes.STDOUT)).isEqualTo(DapFrames.frame(JsonParser.parseString(event).toString()))
    }

    @Test
    fun `the program's output and its tests' events go to their receivers, as a test run's Tests tab reads them`() {
        val sessions = DebugSessions()
        val server = FakeServer().apply { started.complete(Unit) }
        val output = StringBuffer()
        val tested = ConcurrentLinkedQueue<TestEvent>()
        val handler =
            LanguageServerDebugProcessHandler(sessions, output = { output.append(it) }, tested = { tested += it }) {
                CompletableFuture.completedFuture(server.proxy)
            }
        val events = Events(handler)
        handler.startNotify()

        val message = """{"seq":3,"type":"event","event":"output","body":{"category":"stdout","output":"inside a\n"}}"""
        sessions.receive(DebugMessageParams("session-1", JsonParser.parseString(message).asJsonObject))
        val test =
            """{"seq":4,"type":"event","event":"xtcTest",""" +
                """"body":{"event":"started","id":"M:void a()","parent":"M:","selector":"a","container":false}}"""
        sessions.receive(DebugMessageParams("session-1", JsonParser.parseString(test).asJsonObject))
        // a Gradle task's events come from its output, as the same event
        sessions.tested("session-1", TestEvent(TestEvent.FINISHED, id = "M:void a()", status = "passed"))
        val stopped = """{"seq":5,"type":"event","event":"stopped","body":{}}"""
        sessions.receive(DebugMessageParams("session-1", JsonParser.parseString(stopped).asJsonObject))
        assertThat(output.toString()).isEqualTo("inside a\n")
        assertThat(tested).containsExactly(
            TestEvent(TestEvent.STARTED, id = "M:void a()", parent = "M:", selector = "a", container = false),
            TestEvent(TestEvent.FINISHED, id = "M:void a()", status = "passed"),
        )
        // LSP4IJ reads every DAP message, and not the session's own event
        assertThat(events.text(ProcessOutputTypes.STDOUT)).contains("\"event\":\"output\"", "\"event\":\"stopped\"")
        assertThat(events.text(ProcessOutputTypes.STDOUT)).doesNotContain(DebugSessions.XTC_TEST)
        handler.destroyProcess()
    }

    @Test
    fun `a stack trace's source files go to be found before the client reads its frames`() {
        val sessions = DebugSessions()
        val server = FakeServer().apply { started.complete(Unit) }
        val order = CopyOnWriteArrayList<String>()
        val handler =
            LanguageServerDebugProcessHandler(sessions, frameSources = { files -> order += files.map(Path::toString) }) {
                CompletableFuture.completedFuture(server.proxy)
            }
        handler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(
                    event: ProcessEvent,
                    outputType: Key<*>,
                ) {
                    order += "output"
                }
            },
        )
        handler.startNotify()

        val frames =
            """{"seq":16,"type":"response","request_seq":11,"command":"stackTrace","success":true,"body":{"stackFrames":[
            {"id":1,"name":"run","source":{"name":"Run.x","path":"/work/Run.x"},"line":6},
            {"id":2,"name":"forEach","source":{"name":"Collection.x","path":"/copies/ecstasy/collections/Collection.x"},"line":222},
            {"id":3,"name":"native","source":{"name":"Native.x","sourceReference":7},"line":1},
            {"id":4,"name":"again","source":{"name":"Run.x","path":"/work/Run.x"},"line":5}]}}"""
        sessions.receive(DebugMessageParams("session-1", JsonParser.parseString(frames).asJsonObject))
        assertThat(order).containsExactly(
            Path.of("/work/Run.x").toString(),
            Path.of("/copies/ecstasy/collections/Collection.x").toString(),
            "output",
        )

        // other messages go straight on
        sessions.receive(
            DebugMessageParams("session-1", JsonParser.parseString("""{"seq":17,"type":"event","event":"stopped"}""").asJsonObject),
        )
        assertThat(order).endsWith("output", "output").hasSize(4)
    }

    @Test
    fun `a language server that stops ends the session's process with the reason`() {
        val sessions = DebugSessions()
        val server = FakeServer().apply { started.complete(Unit) }
        val handler = LanguageServerDebugProcessHandler(sessions) { CompletableFuture.completedFuture(server.proxy) }
        val events = Events(handler)
        handler.startNotify()

        sessions.endAll("The Ecstasy language server stopped")
        assertThat(events.text(ProcessOutputTypes.STDERR)).contains("The Ecstasy language server stopped")
        assertThat(events.exitCode).isEqualTo(1)
        assertThat(server.ended).isEmpty()
    }

    @Test
    fun `a session that cannot start ends the process with the reason`() {
        val handler =
            LanguageServerDebugProcessHandler(DebugSessions()) { CompletableFuture.failedFuture(IllegalStateException("no server")) }
        val events = Events(handler)
        handler.startNotify()

        assertThat(events.text(ProcessOutputTypes.STDERR)).contains("could not start a debug session: no server")
        assertThat(events.exitCode).isEqualTo(1)
    }

    private fun LanguageServerDebugProcessHandler.write(json: String) {
        processInput.write(DapFrames.frame(json).toByteArray(Charsets.UTF_8))
        processInput.flush()
    }

    /** Answers `xtc/debugStart` once [started] completes, and records the session's messages and its end. */
    private class FakeServer {
        val started = CompletableFuture<Unit>()
        val messages = CopyOnWriteArrayList<DebugMessageParams>()
        val ended = CopyOnWriteArrayList<String>()

        val proxy: XtcLanguageServer =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XtcLanguageServer::class.java)) { _, method, args ->
                when (method.name) {
                    "debugStart" -> started.thenApply { DebugSessionResponse("session-1") }
                    "debugMessage" -> messages += args[0] as DebugMessageParams
                    "debugEnd" -> ended += (args[0] as DebugSessionParams).sessionId
                    else -> throw UnsupportedOperationException(method.name)
                }.let { if (method.returnType == Void.TYPE) null else it }
            } as XtcLanguageServer
    }

    /** Records a process's output by type and its exit code. */
    private class Events(
        handler: LanguageServerDebugProcessHandler,
    ) : ProcessListener {
        private val texts = ConcurrentLinkedQueue<Pair<Key<*>, String>>()

        @Volatile
        var exitCode: Int? = null

        init {
            handler.addProcessListener(this)
        }

        fun text(type: Key<*>) = texts.filter { it.first == type }.joinToString("") { it.second }

        override fun onTextAvailable(
            event: ProcessEvent,
            outputType: Key<*>,
        ) {
            texts += outputType to event.text
        }

        override fun processTerminated(event: ProcessEvent) {
            exitCode = event.exitCode
        }
    }
}
