package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import org.xvm.lsp.awaited
import org.xvm.lsp.server.TraceRecording
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Checks revision-owned library source documents and monikers in virtual and file-backed views. Navigation
 * must find declarations independently of which symbols a consumer previously queried.
 */
class XdkLibraryDocumentsTest {
    @Test
    fun `matching bundled sources have revision owned content and monikers in file and virtual views`() {
        XdkAdapter().use { adapter ->
            val uri = "file:///App.x"
            val text = "module App { package xml import xml.xtclang.org; void accept(xml.Document doc) {} }"
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.indexOf("Document")
            val imported = adapter.findMonikers(uri, 0, at).single()
            val location = requireNotNull(adapter.findDefinition(uri, 0, at))
            val document = requireNotNull(adapter.readOnlyDocument(location.uri))
            assertThat(URI(document.uri).scheme).isEqualTo("ecstasy-library")
            assertThat(URI(document.uri).authority).isEqualTo("xml.xtclang.org")
            assertThat(document.text).isEqualTo(Files.readString(Path.of(URI(location.uri))))
            assertThat(adapter.readOnlyDocument(document.uri)).isEqualTo(document)
            listOf(location.uri, document.uri).forEach { source ->
                assertThat(adapter.findMonikers(source, location.startLine, location.startColumn))
                    .containsExactly(imported.copy(kind = SymbolMoniker.Kind.EXPORT))
                assertThat(adapter.compile(source, "module Forged {} ").diagnostics).isEmpty()
                assertThat(adapter.prepareRename(source, location.startLine, location.startColumn)).isNull()
                assertThat(adapter.formatDocument(source, document.text, FormattingOptions(4, true))).isEmpty()
                assertThat(adapter.readOnlyDocument(source)?.text).isEqualTo(document.text)
            }
            adapter.replaceDependencies(emptyList())
            assertThat(adapter.readOnlyDocument(document.uri)).isEqualTo(document)
            assertThat(adapter.readOnlyDocument(document.uri.replace("/xml/", "/../"))).isNull()
            assertThat(adapter.readOnlyDocument("file:///etc/passwd")).isNull()
            assertThat(adapter.findMonikers(document.uri, 0, 0)).isEmpty()
            adapter.close()
            assertThat(adapter.readOnlyDocument(document.uri)).isNull()
        }
    }

    @Test
    fun `library view resolves declarations never previously queried by the consumer`() {
        XdkAdapter().use { adapter ->
            val text = "module App { String text = \"value\"; }"
            assertThat(adapter.compile("file:///App.x", text).diagnostics).isEmpty()
            val location = requireNotNull(adapter.findDefinition("file:///App.x", 0, text.indexOf("String")))
            val document = requireNotNull(adapter.readOnlyDocument(location.uri))
            val lines = document.text.lines()
            val line = lines.indexOfFirst { it.contains("Int size.get()") }
            assertThat(line).isNotNegative()
            val column = lines[line].indexOf("size")
            val moniker = adapter.findMonikers(document.uri, line, column).single()
            assertThat(moniker.kind).isEqualTo(SymbolMoniker.Kind.EXPORT)
            val consumer = "module App { Int length(String text) = text.size; }"
            assertThat(adapter.compile("file:///App.x", consumer).diagnostics).isEmpty()
            assertThat(adapter.findMonikers("file:///App.x", 0, consumer.indexOf("size")))
                .containsExactly(moniker.copy(kind = SymbolMoniker.Kind.IMPORT))
        }
    }

    @Test
    fun `a library source's imports and type names go to their declarations in the library`() {
        compiledAlone {
            // the copy that navigation or a stack frame opens, which is compiled on its own here
            val uri = copy("ecstasy/mgmt/Container.x")
            val text = Files.readString(Path.of(URI(uri)))
            val lines = text.lines()
            XdkAdapter().use { adapter ->
                adapter.compile(uri, text)
                val template = "ecstasy/reflect/ModuleTemplate.x" to "ModuleTemplate"
                assertThat(adapter.target(uri, lines, "import reflect.ModuleTemplate;", "ModuleTemplate")).isEqualTo(template)
                assertThat(
                    adapter.target(uri, lines, "import reflect.ModuleTemplate;", "reflect"),
                ).isEqualTo("ecstasy/reflect.x" to "reflect")
                // by the file's import
                assertThat(adapter.target(uri, lines, "collectInjections(ModuleTemplate template", "ModuleTemplate")).isEqualTo(template)
                // by an implicit import
                assertThat(adapter.target(uri, lines, "limitCompute(Duration max", "Duration"))
                    .isEqualTo("ecstasy/temporal/Duration.x" to "Duration")
                assertThat(adapter.target(uri, lines, "extends Service.ServiceControl", "ServiceControl"))
                    .isEqualTo("ecstasy/Service.x" to "ServiceControl")
                // a declaration's own name
                assertThat(
                    adapter.target(uri, lines, "Tuple invoke(String methodName", "invoke"),
                ).isEqualTo("ecstasy/mgmt/Container.x" to "invoke")
            }
        }
    }

    @Test
    fun `a library source's copy is compiled with its whole library, and every name in it goes to its declaration`() {
        val string = copy("ecstasy/text/String.x")
        val text = Files.readString(Path.of(URI(string)))
        val lines = text.lines()
        XdkAdapter().use { adapter ->
            // the library's module file is the scope of each of its sources
            assertThat(adapter.analysisScope(string)).endsWith("/ecstasy.x")
            assertThat(adapter.analysisScope(copy("ecstasy/mgmt/Container.x"))).isEqualTo(adapter.analysisScope(string))
            assertThat(adapter.compile(string, text).diagnostics).isEmpty()
            // a call on an expression, in a method body, to a method of another file
            assertThat(adapter.target(string, lines, "buffer.add(transform(ch));", "add")).isEqualTo("ecstasy/text/StringBuffer.x" to "add")
            assertThat(adapter.target(string, lines, "buffer.add(transform(ch));", "buffer")).isEqualTo("ecstasy/text/String.x" to "buffer")
            assertThat(adapter.target(string, lines, "appendTo(buf);", "appendTo")).isEqualTo("ecstasy/text/String.x" to "appendTo")
            assertThat(adapter.target(string, lines, "StringBuffer buffer = new StringBuffer(size);", "size"))
                .isEqualTo("ecstasy/text/String.x" to "size")
            val row = lines.indexOfFirst { it.contains("buffer.add(transform(ch));") }
            assertThat(adapter.getHoverInfo(string, row, lines[row].indexOf("add"))).contains("add")
        }
        // with the setting off, each copy is compiled on its own
        compiledAlone {
            XdkAdapter().use { adapter -> assertThat(adapter.analysisScope(string)).endsWith("/ecstasy/text/String.x") }
        }
    }

    @Test
    fun `a project's compile preempts a library's, which then serves each copy of the library until the last closes`() {
        val string = copy("ecstasy/text/String.x")
        val container = copy("ecstasy/mgmt/Container.x")
        val lines = text(string).lines()
        preemptible { adapter, compiles, held ->
            val first = adapter.compileAsync(string, text(string))
            held.awaited()
            // the server cancels a scope's earlier result when another of its documents opens, which leaves the compile
            val second = adapter.compileAsync(container, text(container))
            first.cancel(false)
            // a project file's compile preempts the library's, held until then, and the library compiles after it
            assertThat(adapter.compileAsync("file:///App.x", "module App {}").awaited().diagnostics).isEmpty()
            assertThat(second.awaited().diagnostics).isEmpty()
            assertThat(adapter.compileAsync(string, text(string)).awaited().diagnostics).isEmpty()
            assertThat(compiles.get()).isEqualTo(2)
            // closing one copy keeps the compile for the other, and closing the last lets it go
            adapter.closeDocument(container)
            assertThat(adapter.target(string, lines, "buffer.add(transform(ch));", "add")).isEqualTo("ecstasy/text/StringBuffer.x" to "add")
            assertThat(adapter.getCachedResult(string)).isNotNull()
            adapter.closeDocument(string)
            assertThat(adapter.getCachedResult(string)).isNull()
            assertThat(compiles.get()).isEqualTo(2)
        }
    }

    @Test
    fun `a library is compiled as a whole only with a heap that fits the compile`() {
        assertThat(XdkLibrarySources.fitsLibraries(384L shl 20)).isFalse()
        assertThat(XdkLibrarySources.fitsLibraries(512L shl 20)).isFalse()
        assertThat(XdkLibrarySources.fitsLibraries(1L shl 30)).isTrue()
        // the server's tests run with 1 GB, which the whole library's case needs
        assertThat(XdkLibrarySources.fitsLibraries()).isTrue()
    }

    /** The text of the copy [uri]. */
    private fun text(uri: String): String = Files.readString(Path.of(URI(uri)))

    /**
     * Runs [body] with an adapter that counts its compiles of the ecstasy library, of which it holds the first, once
     * [held] says it has started, until a project's work preempts it: the compile queue's trace says so after it has
     * told the compile to stop, and the compiler, called then, stops at once.
     */
    private fun preemptible(body: (adapter: XdkAdapter, compiles: AtomicInteger, held: CountDownLatch) -> Unit) {
        CompilerTestSupport.configure()
        val support = EmbeddingSupport.instance()
        val compiles = AtomicInteger()
        val held = CountDownLatch(1)
        val preempted = CountDownLatch(1)
        TraceRecording { if (it["event"]?.asString == "preempted") preempted.countDown() }.use {
            XdkAdapter(
                { source, repository, errors -> support.compileModule(source, repository, errors) },
                { sources, repository, errors ->
                    if (sources.sourceFile.name == "ecstasy.x" && compiles.incrementAndGet() == 1) {
                        held.countDown()
                        preempted.awaited()
                    }
                    support.compileModule(sources, repository, errors)
                },
                { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
            ).use { body(it, compiles, held) }
        }
    }

    /** The URI of the read-only copy of the bundled ecstasy source at [path], as navigation opens it. */
    private fun copy(path: String): String = Path.of(requireNotNull(XdkLibrarySources.file("ecstasy.xtclang.org", path))).toUri().toString()

    /**
     * The file, below the copies' folder, of the declaration that [name] on the line of [lines] that has [line] names, in
     * the copy [uri], and the name it declares.
     */
    private fun XdkAdapter.target(
        uri: String,
        lines: List<String>,
        line: String,
        name: String,
    ): Pair<String, String> {
        val row = lines.indexOfFirst { it.contains(line) }
        val location = requireNotNull(findDefinition(uri, row, lines[row].indexOf(name))) { "$name in $line" }
        val declared = Files.readAllLines(Path.of(URI(location.uri)))[location.startLine]
        return location.uri.substringAfter("xtc-library-sources-").substringAfter('/') to
            declared.substring(location.startColumn, location.endColumn)
    }

    /** Runs [test] with each library source's copy compiled on its own, as the editors' setting turns the library compile off. */
    private fun <T> compiledAlone(test: () -> T): T {
        val previous = System.getProperty(XdkLibrarySources.COMPILE_LIBRARIES)
        System.setProperty(XdkLibrarySources.COMPILE_LIBRARIES, "false")
        try {
            return test()
        } finally {
            if (previous == null) {
                System.clearProperty(XdkLibrarySources.COMPILE_LIBRARIES)
            } else {
                System.setProperty(XdkLibrarySources.COMPILE_LIBRARIES, previous)
            }
        }
    }
}
