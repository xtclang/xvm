package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSources
import org.xvm.tool.ModuleInfo
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

class XdkModuleSessionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `source snapshots preserve implicit packages containing only resources`() {
        val (root, _) = fixture()
        val resources = directory.resolve("Project/spare").toFile()
        resources.mkdirs()
        File(resources, "data.txt").writeText("resource")
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val input = XdkSources.capture(root, emptyMap()) { false }
        val result = EmbeddingSupport.instance().compileModule(input, null, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        assertThat(result.module().getChild("spare")).isNotNull()
    }

    @Test
    fun `member edits invalidate the whole module and preserve source URI aliases`() {
        val (root, member) = fixture()
        val rootUri = root.toURI().toString()
        val alias = member.parentFile.toURI().toString() + "../Project/Child.x"
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(rootUri, root.readText()).success).isTrue()
            val errors = adapter.compile(alias, member.readText().replace("String answer()", "MissingType missing; String answer()"))
            assertThat(errors.success).isFalse()
            assertThat(errors.documentUris).containsExactlyInAnyOrder(rootUri, alias)
            assertThat(
                errors.diagnostics
                    .single { it.code == "COMPILER-38" }
                    .location.uri,
            ).isEqualTo(alias)
            val fixed = adapter.compile(alias, member.readText())
            assertThat(fixed.diagnostics).isEmpty()
            assertThat(
                adapter
                    .getCachedResult(rootUri)
                    ?.symbols
                    ?.single()
                    ?.name,
            ).isEqualTo("Project")
            assertThat(
                adapter
                    .getCachedResult(alias)
                    ?.symbols
                    ?.single()
                    ?.name,
            ).isEqualTo("Child")
            adapter.closeDocument(alias)
            assertThat(adapter.compile(rootUri, root.readText()).diagnostics).isEmpty()
            adapter.closeDocument(rootUri)
            assertThat(adapter.findWorkspaceSymbols("")).isEmpty()
        }
    }

    @Test
    fun `new unsaved members and implicit packages participate without writing files`() {
        val (root, _) = fixture()
        val added = directory.resolve("Project/pkg/Added.x").toFile()
        XdkAdapter().use { adapter ->
            adapter.compile(root.toURI().toString(), root.readText())
            val result = adapter.compile(added.toURI().toString(), "class Added extends Base<String> {}")
            assertThat(result.success).describedAs(result.diagnostics.toString()).isTrue()
            assertThat(result.documentUris).contains(added.toURI().toString())
            assertThat(
                adapter
                    .findWorkspaceSymbols("Added")
                    .single()
                    .location.uri,
            ).isEqualTo(added.toURI().toString())
            assertThat(added.exists()).isFalse()
            assertThat(added.parentFile.exists()).isFalse()
        }
    }

    @Test
    fun `definitions and references span module members while highlights remain local`() {
        val (root, member) = fixture()
        val rootUri = root.toURI().toString()
        val memberUri = member.toURI().toString()
        XdkAdapter().use { adapter ->
            val result = adapter.compile(rootUri, root.readText())
            assertThat(result.success).describedAs(result.diagnostics.toString()).isTrue()
            val call = root.readLines()[2].indexOf("answer")
            assertThat(adapter.findDefinition(rootUri, 2, call)?.uri).isEqualTo(memberUri)
            val echo = root.readLines()[1].indexOf("echo")
            val references = adapter.findReferences(rootUri, 1, echo, true)
            assertThat(references).hasSize(3)
            assertThat(references.map { it.uri }.toSet()).containsExactlyInAnyOrder(rootUri, memberUri)
            assertThat(adapter.getDocumentHighlights(rootUri, 1, echo)).hasSize(2)
            assertThat(adapter.findWorkspaceSymbols("Child")).hasSize(1)
        }
    }

    @Test
    fun `hierarchy follows generic extends and interface implements edges across files`() {
        val (root, member) = fixture()
        val source = root.readText().replace("class Base<Element>", "interface Named {} class Base<Element> implements Named")
        XdkAdapter().use { adapter ->
            val uri = root.toURI().toString()
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            val base = adapter.prepareTypeHierarchy(uri, 1, source.lines()[1].indexOf("Base")).single()
            val child = adapter.getSubtypes(base).single()
            assertThat(child.name).isEqualTo("Child")
            assertThat(child.uri).isEqualTo(member.toURI().toString())
            val parent = adapter.getSupertypes(child).single()
            assertThat(parent.name).isEqualTo("Base")
            assertThat(parent.detail).contains("String")
            val implemented = adapter.getSupertypes(base).single()
            assertThat(implemented.name).isEqualTo("Named")
            assertThat(implemented.kind).isEqualTo(org.xvm.lsp.model.SymbolInfo.SymbolKind.INTERFACE)
            assertThat(adapter.getSubtypes(implemented).single().name).isEqualTo("Base")
            adapter.compile(uri, source)
            assertThat(adapter.getSubtypes(base)).isEmpty()
        }
    }

    @Test
    fun `broken member parsing clears module navigation and correction restores it`() {
        val (root, member) = fixture()
        XdkAdapter().use { adapter ->
            val uri = root.toURI().toString()
            val call = root.readLines()[2].indexOf("answer")
            assertThat(adapter.compile(uri, root.readText()).success).isTrue()
            assertThat(adapter.findDefinition(uri, 2, call)).isNotNull()
            assertThat(adapter.compile(member.toURI().toString(), "class Child {").success).isFalse()
            assertThat(adapter.findDefinition(uri, 2, call)).isNull()
            assertThat(adapter.findWorkspaceSymbols("")).isEmpty()
            assertThat(adapter.compile(member.toURI().toString(), member.readText()).success).isTrue()
            assertThat(adapter.findDefinition(uri, 2, call)?.uri).isEqualTo(member.toURI().toString())
        }
    }

    @Test
    fun `cancellation while loading a member does not invent a parser fatal`() {
        val (root, member) = fixture()
        val cancelled = AtomicBoolean()
        val sources =
            object : ModuleInfo(root, false) {
                override fun readSource(file: File): CharArray {
                    val text = super.readSource(file)
                    if (file == member) cancelled.set(true)
                    return text
                }
            }
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(sources, null, ErrorListener.cancellable(errors, cancelled::get))
        assertThat(cancelled.get()).isTrue()
        assertThat(compilation.succeeded()).isFalse()
        assertThat(compilation.parsed()).isNull()
        assertThat(errors.errors).isEmpty()
    }

    @Test
    fun `editing a sibling cancels running module work and installs only the latest inputs`() {
        val (root, member) = fixture()
        CompilerTestSupport.configure()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val embedding = EmbeddingSupport.instance()
        XdkAdapter(
            { source, errors -> embedding.compileModule(source, null, errors) },
            { sources, errors ->
                if (first.getAndSet(false)) {
                    started.countDown()
                    check(release.await(10, SECONDS))
                }
                embedding.compileModule(sources, null, errors)
            },
        ).use { adapter ->
            val old = adapter.compileAsync(root.toURI().toString(), root.readText())
            try {
                check(started.await(10, SECONDS))
                val latest =
                    adapter.compileAsync(
                        member.toURI().toString(),
                        member.readText().replace("String answer()", "MissingType missing; String answer()"),
                    )
                assertThat(old.isCancelled).isTrue()
                assertThat(adapter.getCachedResult(root.toURI().toString())).isNull()
                release.countDown()
                assertThat(latest.get(20, SECONDS).diagnostics).anyMatch { it.code == "COMPILER-38" }
                assertThat(adapter.getCachedResult(member.toURI().toString())?.diagnostics).anyMatch { it.code == "COMPILER-38" }
            } finally {
                release.countDown()
            }
        }
    }

    private fun fixture(): Pair<File, File> {
        directory = directory.toRealPath()
        val root = directory.resolve("Project.x").toFile()
        root.writeText(
            """
            module Project {
                class Base<Element> { Element echo(Element value) = value; }
                String run() { Child child = new Child(); return child.answer() + child.echo("root"); }
            }
            """.trimIndent(),
        )
        val member = directory.resolve("Project/Child.x").toFile()
        member.parentFile.mkdirs()
        member.writeText("class Child extends Base<String> { String answer() = echo(\"member\"); }")
        return root to member
    }
}
