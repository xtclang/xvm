package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.ModuleRepository
import org.xvm.asm.ModuleStructure
import org.xvm.compiler.CursorBinding
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.IncompleteExpression
import org.xvm.compiler.ast.IncompleteStatement
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import org.xvm.tool.ModuleInfo
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** A real compiler consumer of the bounded partial-analysis API; no fallback parser or mock. */
class XdkPartialAnalysisTest {
    @Test
    fun `cursor facts publish surviving sites and release retries and discarded clones`() {
        CompilerTestSupport.configure()
        val prefix = "module Editing { void run(Int item) { ite"
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source("$prefix; } }", URI), position(prefix), null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        val binding = analysis.cursorBindings().getValue(site)
        val clone = site.clone() as IncompleteStatement
        val collector = CursorBinding.Collector()
        collector.record(site, binding)
        collector.record(clone, binding)
        collector.begin(site)
        assertThat(collector.finish(analysis.sourceTrees())).isEmpty()
        collector.record(site, binding)
        collector.record(clone, binding)
        val published = collector.finish(analysis.sourceTrees())
        assertThat(published.keys).containsExactly(site)
        assertThat(collector.finish(listOf(clone))).isEmpty()
        assertThatThrownBy { (published as MutableMap).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { (binding.variables() as MutableList).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @TempDir
    lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["return ", "Int result = ", "var result = ", "result = "])
    fun `incomplete values preserve their assignment or return context`(statement: String) {
        CompilerTestSupport.configure()
        for (operation in listOf("value.", "value.indexOf(\"x\", ")) {
            val setup = if (statement == "result = ") "Int result = 0; " else ""
            val prefix = "module Editing { Int run(String value) { $setup$statement$operation"
            val text = "$prefix } Int later() = 42; }"
            val errors = ErrorList()
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), position(prefix), null, errors)
            assertThat(errors.errors.map { it.code }).describedAs(errors.errors.toString()).containsExactly(Parser.INCOMPLETE_EXPRESSION)
            assertThat(analysis.pool()).isPresent()
            val site = analysis.sites().single()
            assertThat(site.parent).isInstanceOf(IncompleteExpression::class.java)
            assertThat(site.parent.parent).isInstanceOf(
                if (statement ==
                    "return "
                ) {
                    ReturnStatement::class.java
                } else {
                    AssignmentStatement::class.java
                },
            )
            val receiver = site.receiver.orElseThrow()
            assertThat(receiver.isValidated && receiver.typeFit.isFit).isTrue()
            ConstantPool.withPool(analysis.pool().orElseThrow()).use {
                assertThat(receiver.type.valueString).contains("String")
            }
            assertThat((site.parent as IncompleteExpression).isValidated).isFalse()
            val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
            assertThat((method.component as MethodStructure).ast).isNull()
            assertThat(
                analysis
                    .semanticSnapshot(errors)
                    .sites
                    .single()
                    .members
                    .map { it.name },
            ).contains(if (site.isCall) "indexOf" else "size")
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "work(value.",
            "work(inner(value.",
            "work(text = value.",
            "return work(value.",
            "Int result = work(value.",
            "work(value.indexOf(\"x\", ",
        ],
    )
    fun `nested incomplete arguments retain one innermost cursor site`(statement: String) {
        CompilerTestSupport.configure()
        val prefix =
            "module Editing { Int work(String text) = 1; String inner(String text) = text; " +
                "Int run(String value) { $statement"
        val closing =
            when {
                statement.contains("indexOf") -> ""
                statement.contains("inner") -> "))"
                else -> ")"
            }
        val text = "$prefix$closing; } Int later() = 42; }"
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), position(prefix), null, errors)
        assertThat(errors.errors.map { it.code }).describedAs(errors.errors.toString()).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        assertThat(analysis.pool()).isPresent()
        val site = analysis.sites().single()
        val receiver = site.receiver.orElseThrow() as NameExpression
        assertThat(receiver.name).isEqualTo("value")
        assertThat(receiver.isValidated && receiver.typeFit.isFit).isTrue()
        assertThat(site.source.toRawString()).isEqualTo(text)
        assertThat(site.endPosition).isEqualTo(position(prefix))
        val copied = analysis.semanticSnapshot(errors).sites.single()
        assertThat(copied.members.map { it.name }).contains(if (site.isCall) "indexOf" else "size")
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
        assertThat((method.component as MethodStructure).ast).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "si"])
    fun `partial value clones own their nested syntax`(member: String) {
        val prefix = "module Editing { Int run(String value) { return work(value.$member"
        val text = "$prefix); } }"
        val errors = ErrorList()
        val parser = Parser.forPartialAnalysis(Source(text, URI), position(prefix), errors)
        val tree = parser.parseSource()
        val expression = descendants(tree).filterIsInstance<IncompleteExpression>().first()
        val clone = expression.clone() as IncompleteExpression
        val originalNames = descendants(expression).filterIsInstance<NameExpression>().toList()
        val clonedNames = descendants(clone).filterIsInstance<NameExpression>().toList()
        assertThat(clonedNames.map { it.name }).containsExactlyElementsOf(originalNames.map { it.name })
        originalNames.zip(clonedNames).forEach { (original, copied) ->
            assertThat(copied).isNotSameAs(original)
            assertThat(copied.isValidated).isFalse()
        }
        assertThat(clone.endPosition).isEqualTo(expression.endPosition)
        val copiedSite = descendants(clone).filterIsInstance<IncompleteStatement>().single { !it.isCall }
        assertThat(copiedSite.memberName.map { it.valueText }.orElse("")).isEqualTo(member)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
    }

    @Test
    fun `a local initializer retains the same receiver binding as ordinary compilation`() {
        CompilerTestSupport.configure()
        val prefix = "module Editing { String value = \"property\"; Int run() { Int value = value."
        val errors = ErrorList()
        val complete = EmbeddingSupport.instance().compileModule(Source("${prefix}size; return value; } }", URI), null, errors)
        assertThat(complete.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val expected =
            descendants(complete.parsed()).filterIsInstance<NameExpression>().single {
                it.name == "value" &&
                    it.parent is NameExpression
            }
        val expectedSymbol = expected.resolvedTarget.toString()
        val partialErrors = ErrorList()
        val partial = EmbeddingSupport.instance().analyzeIncomplete(Source("$prefix } }", URI), position(prefix), null, partialErrors)
        assertThat(partialErrors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val receiver =
            partial
                .sites()
                .single()
                .receiver
                .orElseThrow() as NameExpression
        assertThat(receiver.isValidated && receiver.typeFit.isFit).isTrue()
        assertThat(receiver.resolvedTarget.toString()).isEqualTo(expectedSymbol)
    }

    @Test
    fun `unsupported value prefixes remain unavailable and unknown receivers retain diagnostics`() {
        CompilerTestSupport.configure()
        for (statement in listOf("return 1 + value.", "Int result = 1 + value.", "value += value.", "work(flag ? value.")) {
            val prefix = "module Editing { Int run(String value, Boolean flag) { $statement"
            val errors = ErrorList()
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source("$prefix; } }", URI), position(prefix), null, errors)
            assertThat(errors.hasSeriousErrors()).isTrue()
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
            assertThat(analysis.pool()).isEmpty()
            assertThat(analysis.sites()).isEmpty()
        }
        val prefix = "module Editing { Int run() { return work(missing."
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source("$prefix); } }", URI), position(prefix), null, errors)
        assertThat(errors.errors.map { it.code }).contains(Parser.INCOMPLETE_EXPRESSION, "COMPILER-38").doesNotContain("EMB-5")
        val receiver =
            analysis
                .sites()
                .single()
                .receiver
                .orElseThrow()
        assertThat(receiver.isValidated && receiver.typeFit.isFit).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["value.", "value.indexOf(", "value.indexOf(\"x\", ", "value.indexOf(\"x\""])
    fun `cursor analysis retains following declarations without changing source`(operation: String) {
        for (terminator in listOf("", ";")) {
            val prefix = "module Editing { void run(String value) { $operation"
            val text = "$prefix$terminator } Int later() = 42; }"
            CompilerTestSupport.configure()
            val errors = ErrorList()
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), position(prefix), null, errors)
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
            assertThat(analysis.pool()).isPresent()
            val site = analysis.sites().single()
            assertThat(site.source.toRawString()).isEqualTo(text)
            assertThat(site.endPosition).isEqualTo(position(prefix))
            val receiver = site.receiver.orElseThrow() as NameExpression
            assertThat(receiver.isValidated && receiver.typeFit.isFit).isTrue()
            ConstantPool.withPool(analysis.pool().orElseThrow()).use {
                assertThat(receiver.type.valueString).contains("String")
            }
            val later =
                descendants(analysis.sourceTrees().single()).filterIsInstance<MethodDeclarationStatement>().single {
                    it.name ==
                        "later"
                }
            assertThat(Source.calculateOffset(later.startPosition)).isEqualTo(text.indexOf("Int later"))
            val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
            assertThat((method.component as MethodStructure).ast).isNull()

            val compileErrors = ErrorList()
            assertThat(EmbeddingSupport.instance().compileModule(Source(text, URI), null, compileErrors).succeeded()).isFalse()
            assertThat(compileErrors.hasSeriousErrors()).isTrue()
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["value.", "return value.", "Int result = value.", "return work(value.", "value.si", "return value.si", "value.indexOf("],
    )
    fun `cursor analysis in a module member uses the unsaved root and member snapshot`(statement: String) {
        CompilerTestSupport.configure()
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Editing { class Base { Int value = 1; } }")
        member.writeText("class Child extends Base { void run() {} }")
        val prefix = "class Child extends Base { Int run() { $statement"
        val closing = if (statement.contains("work(") || statement.contains("indexOf(")) ");" else ""
        val overlay = "$prefix$closing } Int later() = 42; }"
        val sources =
            object : ModuleInfo(root, false) {
                override fun readSource(file: File): CharArray =
                    when (file) {
                        root -> "module Editing { class Base { String value = \"overlay\"; Int work(String text) = text.size; } }"
                        member -> overlay
                        else -> error("Unexpected source $file")
                    }.toCharArray()
            }
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(sources, member, position(prefix), null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        assertThat(analysis.pool()).isPresent()
        val site = analysis.sites().single()
        assertThat(site.source.fileName).isEqualTo(member.path)
        assertThat(site.source.toRawString()).isEqualTo(overlay)
        val receiver = site.receiver.orElseThrow() as NameExpression
        assertThat(receiver.isValidated && receiver.typeFit.isFit).isTrue()
        ConstantPool.withPool(analysis.pool().orElseThrow()).use {
            assertThat(receiver.type.valueString).contains("String")
        }
        val snapshot = analysis.semanticSnapshot(errors)
        assertThat(snapshot.semantics.sourceName).isEqualTo(member.path)
        val copied = snapshot.sites.single()
        assertThat(snapshot.semantics.type(copied.receiverType!!)!!.displayName).contains("String")
        if (site.isCall) {
            assertThat(copied.members.map { it.name }).containsOnly("indexOf")
        } else {
            assertThat(copied.members.map { it.name }).contains("size", "indexOf")
        }
        assertThat(snapshot.semantics.symbols.mapNotNull { it.declarationSource }).contains(root.path, member.path)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        assertThat(root.readText()).contains("Int value")
        assertThat(member.readText()).doesNotContain("value.")
    }

    @Test
    fun `cursors outside supported boundaries and unrelated syntax errors yield no partial facts`() {
        CompilerTestSupport.configure()
        for ((prefix, suffix) in listOf(
            "module Editing { void run(String value) { value." to "size; } }",
            "module Editing { void run(String value) { value.si" to "ze; } }",
            "module Editing { void run(String value) { value.ind" to "(); } }",
            "module Editing { void run(String value) { value.indexOf(" to "\"x\"); } }",
            "module Editing { void run(String value) { value." to " } void broken( { }",
        )) {
            val errors = ErrorList()
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(prefix + suffix, URI), position(prefix), null, errors)
            assertThat(analysis.pool()).isEmpty()
            assertThat(analysis.sites()).isEmpty()
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["value.si", "return value.si", "Int result = value.si", "work(value.si", "getValue().si", "work(getValue().si"])
    fun `typed member prefixes retain compiler context and their original token`(statement: String) {
        CompilerTestSupport.configure()
        val prefix = "module Editing { String getValue() = \"text\"; Int work(Int n) = n; Int run(String value) { $statement"
        val text = prefix + (if (statement.startsWith("work(")) ");" else ";") + " } Int later() = 42; }"
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), position(prefix), null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        assertThat(site.source.toRawString()).isEqualTo(text)
        assertThat(site.memberName.orElseThrow().valueText).isEqualTo("si")
        assertThat(site.memberName.orElseThrow().startPosition).isEqualTo(position(prefix.dropLast(2)))
        assertThat(site.memberName.orElseThrow().endPosition).isEqualTo(position(prefix))
        val model = analysis.semanticSnapshot(errors)
        val copied = model.sites.single()
        assertThat(copied.memberPrefix!!.text).isEqualTo("si")
        assertThat(model.semantics.type(copied.receiverType!!)!!.displayName).isEqualTo("String")
        assertThat(copied.members.map { it.name }).contains("size")
        val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
        assertThat((method.component as MethodStructure).ast).isNull()
        assertThat(descendants(analysis.sourceTrees().single()).filterIsInstance<MethodDeclarationStatement>().map { it.name }.toList())
            .contains("later")
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "value.indexOf(",
            "value.indexOf(\"x\"",
            "value.indexOf(\"x\", ",
            "return value.indexOf(",
            "work(value.indexOf(",
            "getValue().indexOf(",
        ],
    )
    fun `a call cursor before a closing parenthesis retains the intact prefix`(statement: String) {
        CompilerTestSupport.configure()
        val prefix = "module Editing { String getValue() = \"text\"; Int work(Int n) = n; Int run(String value) { $statement"
        val text = prefix + (if (statement.startsWith("work(")) "));" else ");") + " } Int later() = 42; }"
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), position(prefix), null, errors)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
        val site = analysis.sites().single()
        assertThat(site.isCall).isTrue()
        assertThat(site.source.toRawString()).isEqualTo(text)
        assertThat(site.endPosition).isEqualTo(position(prefix))
        val model = analysis.semanticSnapshot(errors)
        assertThat(model.sites.single().members).isNotEmpty()
        assertThat(model.semantics.calls.map { model.semantics.symbol(it.method)?.name }).doesNotContain("indexOf")
        val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
        assertThat((method.component as MethodStructure).ast).isNull()
        assertThat(descendants(analysis.sourceTrees().single()).filterIsInstance<MethodDeclarationStatement>().map { it.name }.toList())
            .contains("later")
    }

    @Test
    fun `cursor-selected complete syntax remains valid in ordinary compilation`() {
        CompilerTestSupport.configure()
        for ((prefix, suffix) in listOf(
            "module Editing { Int run(String value) { return value.size" to "; } }",
            "module Editing { void run(String value) { value.indexOf(\"x\"" to "); } }",
        )) {
            val text = prefix + suffix
            val errors = ErrorList()
            val partial = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), position(prefix), null, errors)
            assertThat(partial.sites()).hasSize(1)
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
            val normal = ErrorList()
            assertThat(EmbeddingSupport.instance().compileModule(Source(text, URI), null, normal).succeeded())
                .describedAs(normal.errors.toString())
                .isTrue()
            assertThat(normal.errors).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "si"])
    fun `cursor analysis preserves flow narrowing and UTF-16 positions inside closing blocks`(member: String) {
        CompilerTestSupport.configure()
        for (newline in listOf("\n", "\r\n")) {
            val prefix = "module Editing {$newline void run(Object value) {$newline  if (value.is(String)) { /* 😀 */ value.$member"
            val errors = ErrorList()
            val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source("$prefix } } }", URI), position(prefix), null, errors)
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)
            val site = analysis.sites().single()
            assertThat(Source.calculateLine(site.endPosition)).isEqualTo(2)
            assertThat(Source.calculateOffset(site.endPosition)).isEqualTo(prefix.lines().last().length)
            val receiver = site.receiver.orElseThrow()
            assertThat(receiver.isValidated && receiver.typeFit.isFit).isTrue()
            ConstantPool.withPool(analysis.pool().orElseThrow()).use {
                assertThat(receiver.type.valueString).contains("String")
            }
        }
    }

    @Test
    fun `a syntax error in another member prevents partial module semantics`() {
        CompilerTestSupport.configure()
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val member = directory.resolve("Editing/Child.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        root.writeText("module Editing { class Base { String value = \"text\"; } }")
        val prefix = "class Child extends Base { void run() { value."
        member.writeText("$prefix } }")
        File(member.parentFile, "Broken.x").writeText("class Broken { void broken( { }")
        val errors = ErrorList()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(ModuleInfo(root, false), member, position(prefix), null, errors)
        assertThat(analysis.pool()).isEmpty()
        assertThat(analysis.sites()).isEmpty()
        assertThat(errors.errors).anyMatch { it.code != Parser.INCOMPLETE_EXPRESSION }
        assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
    }

    @ParameterizedTest
    @ValueSource(strings = ["value.", "value.si", "value.indexOf(", "val", "value.indexOf(startAt="])
    fun `module cursor diagnostics honor cancellation and budgets without duplicates`(operation: String) {
        CompilerTestSupport.configure()
        val root = directory.resolve("Editing.x").toFile().canonicalFile
        val prefix = "module Editing { void run(String value) { $operation"
        root.writeText(prefix + (if (operation.endsWith("(") || operation.endsWith("=")) ");" else ";") + " } }")
        val support = EmbeddingSupport.instance()
        val delivered = mutableListOf<ErrorListener.ErrorInfo>()
        val analysis = support.analyzeIncomplete(ModuleInfo(root, false), root, position(prefix), null, ErrorListener { delivered.add(it) })
        assertThat(analysis.pool()).isPresent()
        assertThat(delivered.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)

        val errors = ErrorList(ErrorList.FIRST_ERROR)
        val budgeted = support.analyzeIncomplete(ModuleInfo(root, false), root, position(prefix), null, errors)
        assertThat(budgeted.pool()).isEmpty()
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.INCOMPLETE_EXPRESSION)

        val cancelled = AtomicBoolean()
        val listener = ErrorListener.cancellable(ErrorListener.collecting { cancelled.set(true) }, cancelled::get)
        assertThat(support.analyzeIncomplete(ModuleInfo(root, false), root, position(prefix), null, listener).pool()).isEmpty()
        assertThat(cancelled.get()).isTrue()
        val unread =
            object : ModuleInfo(root, false) {
                override fun readSource(file: File): CharArray = error("A cancelled attempt must not read source")
            }
        assertThat(support.analyzeIncomplete(unread, root, position(prefix), null, listener).sourceTrees()).isEmpty()
    }

    private fun position(prefix: String): Long {
        val source = Source(prefix)
        while (source.hasNext()) source.next()
        return source.position
    }

    @Test
    fun `trailing member access resolves the receiver without producing a compiled method`() {
        val text = "module Editing { @Inject Console console; void run() { console."
        val (analysis, errors) = analyze(text)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
        assertThat(analysis.pool()).isPresent()
        val site = analysis.sites().single()
        assertThat(site.isCall).isFalse()
        val receiver = site.receiver.orElseThrow() as NameExpression
        assertThat(receiver.isValidated).isTrue()
        assertThat(receiver.resolvedTarget).isNotNull()
        ConstantPool.withPool(analysis.pool().orElseThrow()).use {
            assertThat(receiver.type.valueString).contains("Console")
        }
        val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
        assertThat(method.name).isEqualTo("run")
        assertThat((method.component as MethodStructure).ast).isNull()

        val compileErrors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(text, URI), null, compileErrors)
        assertThat(compilation.succeeded()).isFalse()
        assertThat(compilation.file()).isNull()
        assertThat(compilation.parsed()).isNull()
    }

    @Test
    fun `unfinished calls retain real argument and separator positions and resolve method parameters`() {
        val text = "module Editing { void run(String text) { text.indexOf(\"a,b\", "
        val (analysis, errors) = analyze(text)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
        val site = analysis.sites().single()
        assertThat(site.isCall).isTrue()
        assertThat(Source.calculateOffset(site.operator.startPosition)).isEqualTo(text.indexOf("(\"a,b\""))
        assertThat(site.arguments).hasSize(1)
        assertThat(site.separators.map { Source.calculateOffset(it.startPosition) }).containsExactly(text.lastIndexOf(','))
        val receiver = site.receiver.orElseThrow() as NameExpression
        val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
        val parameter = descendants(method).filterIsInstance<Parameter>().first()
        assertThat(receiver.resolvedTarget).isSameAs(parameter.resolvedTarget)
        assertThat(receiver.isValidated).isTrue()
        assertThat(site.arguments.single().isValidated).isTrue()
        assertThat((site.target as NameExpression).resolvedTarget).isNull()
        ConstantPool.withPool(analysis.pool().orElseThrow()).use {
            assertThat(receiver.type.valueString).contains("String")
            assertThat(
                site.arguments
                    .single()
                    .type.valueString,
            ).contains("String")
        }
        assertThat((method.component as MethodStructure).ast).isNull()
    }

    @Test
    fun `unrelated malformed statements prevent partial semantic analysis`() {
        val (analysis, errors) = analyze("module Editing { void run() { Int broken = ; console.")
        assertThat(errors.errors).isNotEmpty()
        assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        assertThat(analysis.pool()).isEmpty()
        assertThat(analysis.sites()).isEmpty()
    }

    @Test
    fun `scope and flow narrowing come from this attempt and survive a later compilation`() {
        val snapshots =
            listOf("String", "Int").map { type ->
                val text = "module Editing { void run(Object value) { if (value.is($type)) { value."
                val (analysis, errors) = analyze(text)
                assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
                val receiver =
                    analysis
                        .sites()
                        .single()
                        .receiver
                        .orElseThrow()
                assertThat(receiver.isValidated && receiver.typeFit.isFit).isTrue()
                val copiedType = ConstantPool.withPool(analysis.pool().orElseThrow()).use { receiver.type.valueString }
                assertThat(copiedType).contains(type)
                copiedType
            }
        assertThat(snapshots[0]).contains("String").doesNotContain("Int")
        assertThat(snapshots[1]).contains("Int").doesNotContain("String")
    }

    @Test
    fun `unknown receiver reports its real error without inventing a type`() {
        val (analysis, errors) = analyze("module Editing { void run() { missing.")
        assertThat(errors.errors.map { it.code }).contains(Parser.UNEXPECTED_EOF).doesNotContain("EMB-5")
        assertThat(errors.errors).anySatisfy { assertThat(it.code).startsWith("COMPILER-") }
        val receiver =
            analysis
                .sites()
                .single()
                .receiver
                .orElseThrow() as NameExpression
        assertThat(receiver.resolvedTarget).isNull()
        assertThat(receiver.isValidated && receiver.typeFit.isFit).isFalse()
    }

    @Test
    fun `nested calls named arguments and UTF-16 positions retain only top-level separators`() {
        for (newline in listOf("\n", "\r\n")) {
            val text =
                "module Editing {$newline void run(String text) {$newline" +
                    "  /* 😀 */ text.indexOf(text.replace(\"x\", \"y\"), start = 2, "
            val (analysis, errors) = analyze(text)
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
            val site = analysis.sites().single()
            assertThat(site.arguments).hasSize(2)
            assertThat(site.arguments).allSatisfy { assertThat(it.isValidated && it.typeFit.isFit).isTrue() }
            assertThat(site.separators).hasSize(2)
            val line = text.lines().last()
            assertThat(site.separators.map { Source.calculateOffset(it.startPosition) }).containsExactly(
                line.indexOf(", start"),
                line.lastIndexOf(','),
            )
            assertThat(Source.calculateLine(site.endPosition)).isEqualTo(2)
            assertThat(Source.calculateOffset(site.endPosition)).isEqualTo(line.length)
            assertThat((site.target as NameExpression).resolvedTarget).isNull()
        }
    }

    @Test
    fun `calls without arguments retain the callee and scope but do not select an overload`() {
        for (suffix in listOf("work(", "work(1", "work(1,")) {
            val (analysis, errors) =
                analyze("module Editing { void work(Int n) {} void work(String s) {} void run() { $suffix")
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
            val site = analysis.sites().single()
            assertThat((site.target as NameExpression).name).isEqualTo("work")
            assertThat((site.target as NameExpression).resolvedTarget).isNull()
            assertThat(site.receiver).isEmpty()
            assertThat(parents(site).filterIsInstance<MethodDeclarationStatement>().first().name).isEqualTo("run")
            assertThat(site.arguments).hasSize(if ('1' in suffix) 1 else 0)
            assertThat(site.separators).hasSize(if (suffix.endsWith(',')) 1 else 0)
        }
    }

    @Test
    fun `unsupported contexts and lexer failures never enter semantic analysis`() {
        for (body in listOf("return value.", "Int result = value.", "work(value.", "work(value(", "work(\"unterminated")) {
            val (analysis, errors) = analyze("module Editing { void run(String value) { $body")
            assertThat(errors.errors).isNotEmpty()
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
            assertThat(analysis.pool()).isEmpty()
            assertThat(analysis.sites()).isEmpty()
        }
        val (complete, errors) = analyze("module Editing { void run() {} }")
        assertThat(errors.errors).isEmpty()
        assertThat(complete.sites()).isEmpty()
        assertThat(complete.pool()).isEmpty()
    }

    @Test
    fun `EOF reaches a non-deduplicating host once and cancellation and budgets stop analysis`() {
        CompilerTestSupport.configure()
        val text = "module Editing { void run(String value) { value."
        val delivered = mutableListOf<ErrorListener.ErrorInfo>()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, ErrorListener { delivered.add(it) })
        assertThat(delivered.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
        assertThat(analysis.pool()).isPresent()

        val errors = ErrorList(ErrorList.FIRST_ERROR)
        val budgeted = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, errors)
        assertThat(errors.errors).hasSize(1)
        assertThat(budgeted.pool()).isEmpty()

        val cancelled = AtomicBoolean()
        val listener = ErrorListener.cancellable(ErrorListener.collecting { cancelled.set(true) }, cancelled::get)
        val interrupted = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, listener)
        assertThat(cancelled.get()).isTrue()
        assertThat(interrupted.pool()).isEmpty()
        val alreadyCancelled = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, listener)
        assertThat(alreadyCancelled.sourceTrees()).isEmpty()
    }

    @Test
    fun `cancellation during repository loading cannot advance into another compiler phase`() {
        CompilerTestSupport.configure()
        val cancelled = AtomicBoolean()
        val errors = ErrorList()
        val input =
            object : ModuleRepository {
                override fun getModuleNames(): Set<String> = emptySet()

                override fun loadModule(name: String): ModuleStructure? {
                    cancelled.set(true)
                    return null
                }

                override fun storeModule(module: ModuleStructure) {
                    error("An input repository must not receive compiled output")
                }
            }
        val analysis =
            EmbeddingSupport.instance().analyzeIncomplete(
                Source("module Editing { void run(String value) { value.", URI),
                input,
                ErrorListener.cancellable(errors, cancelled::get),
            )
        assertThat(cancelled.get()).isTrue()
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
        assertThat(analysis.pool()).isEmpty()
        assertThat(
            analysis
                .sites()
                .single()
                .receiver
                .orElseThrow()
                .isValidated,
        ).isFalse()
    }

    private fun analyze(text: String): Pair<EmbeddingSupport.PartialAnalysis, ErrorList> {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        return EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, errors) to errors
    }

    private fun parents(node: AstNode): Sequence<AstNode> = generateSequence(node.parent) { it.parent }

    private fun descendants(node: AstNode): Sequence<AstNode> =
        sequence {
            yield(node)
            for (child in node.children()) yieldAll(descendants(child))
        }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
