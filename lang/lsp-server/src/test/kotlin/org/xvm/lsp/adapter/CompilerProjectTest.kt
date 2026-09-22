package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Composition
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.BuildRepository
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import org.xvm.lsp.adapter.xdk.semanticSnapshots
import org.xvm.tool.ModuleInfo
import java.io.File
import java.io.IOException
import java.nio.file.Path

/** API feasibility probes. These do not enable project features in the shipped adapter. */
class CompilerProjectTest {
    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `file and directory convenience entry points use the same module assembly`(useDirectory: Boolean) {
        val root = fixture()
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val output = BuildRepository()
        val succeeded = EmbeddingSupport.instance().compile(if (useDirectory) root.parentFile else root, null, output, errors)
        assertThat(succeeded).describedAs(errors.errors.toString()).isTrue()
        assertThat(output.loadModule("Project").getChild("Child")).isNotNull()
        assertThat(errors.errors).isEmpty()
    }

    @Test
    fun `cancelled tree and file inputs do not parse or invent an error`() {
        val root = fixture()
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val listener = ErrorListener.cancellable(errors) { true }
        val sources =
            object : ModuleInfo(root, false) {
                override fun readSource(file: File): CharArray = error("Cancelled source must not be read")
            }
        val support = EmbeddingSupport.instance()
        val result = support.compileModule(sources, null, listener)
        assertThat(result.succeeded()).isFalse()
        assertThat(result.parsed()).isNull()
        assertThat(support.compile(root, null, null, listener)).isFalse()
        assertThat(errors.errors).isEmpty()
    }

    @Test
    fun `member overlays compile together and diagnostics retain the member source`() {
        val root = fixture()
        val member = directory.resolve("Project/Child.x").toFile()
        val disk = member.readText()
        val invalid = disk.replace("String answer()", "MissingType missing; String answer()")
        val errors = ErrorList()
        val failed = compile(root, mapOf(File(member.parentFile, "./Child.x") to invalid), errors)
        assertThat(failed.succeeded()).isFalse()
        val unresolved = errors.errors.single { it.code == "COMPILER-38" }
        val site = unresolved.site() as ErrorListener.Site.In
        assertThat(site.source().fileName).isEqualTo(member.path)
        assertThat(site.source().toString(site.lPosStart(), site.lPosEnd())).isEqualTo("MissingType")
        assertThat(member.readText()).isEqualTo(disk)

        val fixed = compile(root, mapOf(member to disk.replace("disk", "overlay")))
        assertThat(fixed.succeeded()).isTrue()
        assertThat(nodes(fixed.parsed()).mapNotNull { it.source?.fileName }.toSet())
            .contains(root.path, member.path)
        assertThat(member.readText()).isEqualTo(disk)
    }

    @Test
    fun `copied source views share identities and retain cross file declaration locations`() {
        val root = fixture()
        val compilation = compile(root)
        assertThat(compilation.succeeded()).isTrue()
        val views = compilation.semanticSnapshots()
        val main = views.single { it.sourceName == root.path }
        val member = views.single { it.sourceName == directory.resolve("Project/Child.x").toString() }
        val declaration = member.occurrences.single { it.name == "answer" && it.role == SemanticModel.Role.DECLARATION }
        val use = main.occurrences.single { it.name == "answer" }
        assertThat(use.symbol).isEqualTo(declaration.symbol)
        assertThat(main.definitionLocationAt(use.range.start.line, use.range.start.column))
            .isEqualTo(SemanticModel.SourceLocation(member.sourceName, declaration.range))
        assertThat(main.definitionAt(use.range.start.line, use.range.start.column)).isNull()

        val echo = main.occurrences.single { it.name == "echo" && it.role == SemanticModel.Role.DECLARATION }
        val references = views.flatMap { view -> view.occurrences.filter { it.symbol == echo.symbol }.map { view.sourceName to it } }
        assertThat(references).hasSize(3)
        assertThat(references.map { it.first }.toSet()).containsExactlyInAnyOrder(main.sourceName, member.sourceName)
        assertThat(views.map { it.id }.toSet()).hasSize(1)
        assertThatThrownBy { compilation.semanticSnapshot() }.hasMessageContaining("Use semanticSnapshots()")

        // A later compilation cannot alter earlier copied locations or re-use their IDs.
        val next = compile(root).semanticSnapshots()
        assertThat(next.map { it.id }.toSet()).doesNotContain(main.id)
        assertThat(main.definitionLocationAt(use.range.start.line, use.range.start.column)?.sourceName).isEqualTo(member.sourceName)
    }

    @Test
    fun `equal offsets and names in different files remain distinct in semantic views`() {
        val root = fixture()
        val members =
            listOf("Alpha", "Bravo").map { name ->
                directory.resolve("Project/$name.x").toFile().apply { writeText("class $name { Int shared = 1; }") }
            }
        val compilation = compile(root)
        assertThat(compilation.succeeded()).isTrue()
        val views = compilation.semanticSnapshots()
        val declarations =
            members.map { file ->
                views.single { it.sourceName == file.path }.occurrences.single { it.name == "shared" }
            }
        assertThat(declarations[0].range).isEqualTo(declarations[1].range)
        assertThat(declarations[0].symbol).isNotEqualTo(declarations[1].symbol)
    }

    @Test
    fun `cross file overloads and same named parameters preserve distinct bindings`() {
        val root = fixture()
        val compilation = compile(root)
        assertThat(compilation.succeeded()).isTrue()
        val views = compilation.semanticSnapshots()
        val main = views.single { it.sourceName == root.path }
        val member = views.single { it.sourceName != root.path }
        val calls = main.occurrences.filter { it.name == "choose" }
        val declarations = member.occurrences.filter { it.name == "choose" }
        assertThat(calls).hasSize(2)
        assertThat(calls.map { it.symbol }.toSet()).hasSize(2)
        assertThat(calls.map { it.symbol }).containsExactlyInAnyOrderElementsOf(declarations.map { it.symbol })
        assertThat(
            calls.map { call ->
                main
                    .type(
                        main
                            .symbol(call.symbol!!)!!
                            .signature!!
                            .returns
                            .single(),
                    )!!
                    .displayName
            },
        ).containsExactly("String", "Int")
        val mainParameters =
            main.occurrences
                .filter { it.name == "value" }
                .map { it.symbol }
                .toSet()
        val memberParameters =
            member.occurrences
                .filter { it.name == "value" }
                .map { it.symbol }
                .toSet()
        assertThat(mainParameters).isNotEmpty().doesNotContainAnyElementsOf(memberParameters)
    }

    @Test
    fun `cross file selected calls resolve to source declarations by compiler identity`() {
        val root = fixture()
        val compilation = compile(root)
        assertThat(compilation.succeeded()).isTrue()
        ConstantPool.withPool(compilation.pool()).use {
            val nodes = nodes(compilation.parsed())
            val declaration = nodes.filterIsInstance<MethodDeclarationStatement>().single { it.nameToken.valueText == "answer" }
            val call = nodes.filterIsInstance<InvocationExpression>().single { it.resolvedMethod?.name == "answer" }
            assertThat(call.resolvedMethod).isEqualTo(declaration.component.identityConstant)
            assertThat(call.source.fileName).isEqualTo(root.path)
            assertThat(declaration.source.fileName).isEqualTo(directory.resolve("Project/Child.x").toString())

            val echo = nodes.filterIsInstance<MethodDeclarationStatement>().single { it.nameToken.valueText == "echo" }
            val references = nodes.filterIsInstance<InvocationExpression>().filter { it.resolvedMethod == echo.component.identityConstant }
            assertThat(references.map { it.source.fileName }.toSet())
                .containsExactlyInAnyOrder(root.path, directory.resolve("Project/Child.x").toString())
        }
    }

    @Test
    fun `generic hierarchy and receiver members expose substituted callable types`() {
        val compilation = compile(fixture())
        assertThat(compilation.succeeded()).isTrue()
        ConstantPool.withPool(compilation.pool()).use {
            val child = compilation.module().getChild("Child") as ClassStructure
            val base = compilation.module().getChild("Base") as ClassStructure
            val parent = child.contributionsAsList.single { it.composition == Composition.Extends }.typeConstant
            assertThat(parent.getSingleUnderlyingClass(true)).isEqualTo(base.identityConstant)
            assertThat(parent.paramTypes.toList()).containsExactly(compilation.pool().typeString())
            val subtypes =
                listOf(base, child).filter { type ->
                    type.contributionsAsList.any {
                        it.composition == Composition.Extends &&
                            it.typeConstant.getSingleUnderlyingClass(true) == base.identityConstant
                    }
                }
            assertThat(subtypes).containsExactly(child)

            val errors = ErrorList()
            val members = child.formalType.ensureAccess(Access.PUBLIC).ensureTypeInfo(errors)
            assertThat(members.methods.keys.map { it.name }).contains("answer", "echo", "choose").doesNotContain("secret")
            val echo = members.methods.values.single { it.signature.name == "echo" }
            assertThat(echo.signature.params.toList()).containsExactly(compilation.pool().typeString())
            assertThat(echo.signature.returns.toList()).containsExactly(compilation.pool().typeString())
            val calls = nodes(compilation.parsed()).filterIsInstance<InvocationExpression>().filter { it.resolvedMethod?.name == "echo" }
            assertThat(calls).hasSize(2).allSatisfy { call ->
                assertThat(call.type).isEqualTo(compilation.pool().typeString())
                val receiver = (call.invokedExpression as NameExpression).leftExpression
                if (receiver != null) assertThat(receiver.type).isNotNull()
            }
            assertThat(errors.errors).isEmpty()
        }
    }

    @Test
    fun `incomplete member overlay is unavailable and does not reuse the last valid tree`() {
        val root = fixture()
        assertThat(compile(root).succeeded()).isTrue()
        val member = directory.resolve("Project/Child.x").toFile()
        val errors = ErrorList()
        val result = compile(root, mapOf(member to "class Child { void run() { this. } }"), errors)
        assertThat(result.succeeded()).isFalse()
        assertThat(result.parsed()).isNull()
        assertThat(errors.errors).anySatisfy { error ->
            assertThat(error.code).startsWith("PARSER-")
            assertThat((error.site() as ErrorListener.Site.In).source().fileName).isEqualTo(member.path)
        }
    }

    @Test
    fun `source read failure reaches the host`() {
        val root = fixture()
        val sources =
            object : ModuleInfo(root, false) {
                override fun readSource(file: File): CharArray = throw IOException("probe read failure")
            }
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(sources, null, errors)
        assertThat(result.succeeded()).isFalse()
        assertThat(errors.errors).anySatisfy { assertThat(it.code).isEqualTo("LAUNCHER-04") }
    }

    @Test
    fun `new files outside disk discovery are an explicit prototype boundary`() {
        val root = fixture()
        assertThatThrownBy { OverlaySources(root, mapOf(directory.resolve("Project/New.x").toFile() to "class New {}")) }
            .isInstanceOf(UnsupportedOperationException::class.java)
            .hasMessageContaining("Overlay-only source discovery")
    }

    private fun fixture(): File {
        directory = directory.toRealPath()
        val root = directory.resolve("Project.x").toFile()
        root.writeText(
            """
            module Project {
                class Base<Element> { Element echo(Element value) = value; }
                String run() { Child child = new Child(); return child.answer() + child.echo("root") + child.choose("text"); }
                Int number() { Child child = new Child(); return child.choose(1); }
            }
            """.trimIndent(),
        )
        val member = directory.resolve("Project/Child.x").toFile()
        member.parentFile.mkdirs()
        member.writeText(
            """
            class Child extends Base<String> {
                String answer() = echo("disk");
                String choose(String value) = value;
                Int choose(Int value) = value;
                private Int secret() = 42;
            }
            """.trimIndent(),
        )
        return root
    }

    private fun compile(
        root: File,
        overlays: Map<File, String> = emptyMap(),
        errors: ErrorList = ErrorList(),
    ): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        return EmbeddingSupport.instance().compileModule(OverlaySources(root, overlays), null, errors)
    }

    /** Fresh discovery per attempt; source files and resources retain their original disk identity. */
    private class OverlaySources(
        root: File,
        overlays: Map<File, String>,
    ) : ModuleInfo(root, false) {
        private val text = overlays.mapKeys { it.key.canonicalFile }

        init {
            if (text.keys.any { !it.isFile }) throw UnsupportedOperationException("Overlay-only source discovery is not implemented")
        }

        override fun readSource(file: File): CharArray = text[file]?.toCharArray() ?: super.readSource(file)
    }

    private fun nodes(root: AstNode): List<AstNode> =
        buildList {
            fun visit(node: AstNode) {
                add(node)
                node.children().forEachRemaining { visit(it) }
            }
            visit(root)
        }
}
