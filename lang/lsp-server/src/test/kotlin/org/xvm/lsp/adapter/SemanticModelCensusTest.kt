package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.lsp.Heavy
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.ProofIdentity
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkSources
import org.xvm.lsp.adapter.xdk.XdkWorkspaceDiscovery
import org.xvm.lsp.adapter.xdk.projectRenameFacts
import org.xvm.lsp.adapter.xdk.toDependency
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The semantic model's census over the XDK's own libraries, the 22 modules of all 21 compiled from the repository's
 * sources in build order, each against the ones before it, as a workspace that holds the repository compiles them: every
 * binding of their rename facts that a proof cannot prove, an occurrence without a symbol (unbound) or one bound
 * through an identity that no declaration or artifact proves (unproven), and every dispatch chain of a method or property
 * of their classes that holds such an identity, sorted by the construct that wrote it. A rename is refused where such a
 * binding spells the old or new name, and every other proof (a safe delete, an inline, an extraction, a member's
 * addition) anywhere in its module; Find References and highlighting miss what is unbound.
 *
 * No construct that compiles is known to leave a name unbound, or a binding that no proof can place (a symbol with no
 * identity and no declaration in the module's sources, which refuses every proof, a rename's too), so either fails the
 * test, with its examples. The unproven bindings are counted only: the report, by construct and by library with
 * examples, goes to `lang/lsp-server/build/semantic-model-census.txt`, and its summary lines, which start with
 * `census`, to the output. Heavy: it compiles every library from source once.
 *
 * Run alone: `./gradlew :lang:lsp-server:test --tests '*SemanticModelCensusTest' -PlspTests=heavy
 * -PincludeBuildLang=true -PincludeBuildAttachLang=true`.
 */
@Heavy
class SemanticModelCensusTest {
    @Test
    fun `no binding of the libraries' rename facts is unbound, and the unproven ones are counted by construct`() {
        CompilerTestSupport.configure()
        val root = File(requireNotNull(System.getProperty("xtc.composite.root")) { "xtc.composite.root is not set" })
        val folders =
            root
                .listFiles { file -> file.isDirectory && file.name.startsWith("lib_") }!!
                .map { it.resolve("src/main/x") }
                .filter { it.isDirectory }
        val project = XdkProject(XdkWorkspaceDiscovery.scan(folders, emptyMap(), emptyList()) { false })
        // the 21 libraries hold 22 modules: lib_runner holds runnerClient too
        assertThat(project.buildOrder()).hasSize(22)
        val artifacts = linkedMapOf<String, XdkDependency>()
        val gaps = mutableListOf<Gap>()
        project.buildOrder().forEach { module ->
            val sources = XdkSources.capture(module.root, emptyMap(), module.resourceFiles) { false }
            val open = XdkDependencies(artifacts.values.toList()).open()
            val errors = ErrorList()
            val compilation = EmbeddingSupport.instance().compileModule(sources.freshCompilationInput(), open.repository, errors)
            assertThat(compilation.succeeded() && !errors.hasSeriousErrors()).describedAs("${module.name}: ${errors.errors.take(5)}").isTrue
            gaps += Census(module.name, compilation.projectRenameFacts(open, errors), requireNotNull(compilation.parsed())).gaps()
            artifacts[module.name] = compilation.toDependency()
        }
        val report = report(gaps)
        File("build").mkdirs()
        File("build/semantic-model-census.txt").writeText(report.joinToString("\n", postfix = "\n"))
        report.filter { it.startsWith("census") }.forEach(::println)
        assertThat(gaps.filter { it.refusing }.map(Gap::toString)).isEmpty()
    }

    /** A binding that a proof cannot prove: where it is, the name it spells, and the construct that wrote it. */
    private data class Gap(
        val module: String,
        val file: String,
        val line: Int,
        val column: Int,
        val name: String,
        val unbound: Boolean,
        val kind: Kind,
        val construct: String,
        val text: String,
    ) {
        val where get() = "${File(file).name}:$line:$column"

        /** Whether every proof is refused on it, a rename's too: an unbound occurrence, or a binding no proof can place. */
        val refusing get() = unbound || "unplaceable" in construct

        override fun toString() = "${if (unbound) "unbound" else "unproven"} $construct $module $where $name | $text"
    }

    /** What a gap is: an occurrence of a name, a call's method, or a dispatch chain of a method or property. */
    private enum class Kind { OCCURRENCE, CALL, CHAIN }

    /** One module's census, over its rename [facts] and its [ast], which tells the construct of each gap. */
    private class Census(
        val module: String,
        val facts: CompilerRenameFacts,
        ast: AstNode,
    ) {
        private val nodes = index(ast)
        private val texts = facts.models.mapNotNullTo(hashSetOf()) { it.sourceName }
        private val lines = hashMapOf<String, List<String>>()

        fun gaps(): List<Gap> =
            facts.models.filter { it.sourceName != null }.flatMap { model ->
                val source = requireNotNull(model.sourceName)
                model.occurrences.mapNotNull { occurrence ->
                    val location = SemanticModel.SourceLocation(source, occurrence.range)
                    val callable = facts.callables[location]
                    val symbol = occurrence.symbol?.let(model::symbol)
                    when {
                        callable == null && symbol == null ->
                            gap(
                                location,
                                occurrence.name,
                                true,
                                Kind.OCCURRENCE,
                                "unbound ${node(location)}",
                            )

                        // a variable that the model resolved but cannot place, which no source declares and no
                        // identity proves, is left out on both sides of a proof (XdkRename.unplaced)
                        callable == null && symbol?.kind == SemanticModel.SymbolKind.VARIABLE && symbol.declaration == null &&
                            facts.constants[symbol.id] == null -> null

                        else -> unproven(callable, symbol)?.let { gap(location, occurrence.name, false, Kind.OCCURRENCE, it) }
                    }
                } +
                    model.calls.mapNotNull { call ->
                        val location = SemanticModel.SourceLocation(source, call.callee)
                        val callable = facts.callables[location]
                        val symbol = model.symbol(call.method)
                        // a call's method that has no symbol is one that no proof can place, as one without an identity
                        val judged = if (callable == null && symbol == null) "unplaceable without a symbol" else unproven(callable, symbol)
                        judged?.let { gap(location, symbol?.name ?: "?", false, Kind.CALL, "call $it") }
                    }
            } + chains()

        /**
         * The dispatch chains, of each method and property of the module's classes, that hold an unproven identity: a
         * proof other than a rename's compares them exactly, and a rename's by their spelling, as it does a binding.
         */
        private fun chains(): List<Gap> =
            (facts.methods.chains.map { "method" to it } + facts.properties.chains.map { "property" to it })
                .mapNotNull { (kind, chain) ->
                    val parts = listOf(chain.owner) + chain.members + chain.cycles + chain.alternatives
                    val shape = parts.firstNotNullOfOrNull(::shape) ?: return@mapNotNull null
                    val owner = chain.owner as? ProofIdentity.Source
                    val members = chain.members.joinToString(",", "[", "]") { (it as? ProofIdentity.Source)?.name ?: label(it) }
                    gap(owner?.location, "${owner?.name ?: label(chain.owner)}.$members", false, Kind.CHAIN, "$kind chain $shape")
                }

        private fun label(identity: ProofIdentity) = if (identity is ProofIdentity.Unproven) "?" else identity.javaClass.simpleName

        private fun gap(
            location: SemanticModel.SourceLocation?,
            name: String,
            unbound: Boolean,
            kind: Kind,
            construct: String,
        ): Gap {
            val file = location?.sourceName
            return Gap(
                module,
                file ?: "?",
                location
                    ?.range
                    ?.start
                    ?.line
                    ?.plus(1) ?: 0,
                location
                    ?.range
                    ?.start
                    ?.column
                    ?.plus(1) ?: 0,
                name,
                unbound,
                kind,
                construct,
                file
                    ?.let { lines.getOrPut(it) { File(it).readLines() } }
                    ?.getOrNull(requireNotNull(location).range.start.line)
                    ?.trim()
                    .orEmpty(),
            )
        }

        /**
         * The construct of a binding the proof cannot prove, or null for one it proves, as XdkRename's comparison does
         * (XdkRename.edges): one through an unproven identity, which a rename compares by its spelling and every other
         * proof refuses, or one that it cannot place at all, a symbol with no identity and no declaration in the
         * module's sources, which refuses every proof, a rename's too.
         */
        private fun unproven(
            callable: ProofIdentity?,
            symbol: SemanticModel.Symbol?,
        ): String? {
            if (callable != null) return shape(callable)?.let { "callable $it" }
            symbol ?: return null
            val declared = symbol.declaration != null && symbol.declarationSource in texts
            val identity = facts.constants[symbol.id] ?: return if (declared) null else "${symbol.kind} unplaceable"
            return when (identity) {
                is ProofIdentity.Composed, is ProofIdentity.Alternatives, is ProofIdentity.Super, is ProofIdentity.Receiver,
                is ProofIdentity.PrimaryConstructor, is ProofIdentity.Generated, is ProofIdentity.Parameter,
                is ProofIdentity.MethodFormal,
                -> shape(identity)

                is ProofIdentity.Unproven -> if (declared) null else shape(identity)

                else -> null
            }?.let { "${symbol.kind} $it" }
        }

        /** Where [identity] holds an unproven identity, as the path to it, or null when it holds none. */
        private fun shape(identity: ProofIdentity): String? {
            fun within(
                label: String,
                vararg parts: ProofIdentity,
            ) = parts.firstNotNullOfOrNull(::shape)?.let { "$label($it)" }
            return when (identity) {
                is ProofIdentity.Unproven -> "Unproven"

                is ProofIdentity.Parameter -> within("Parameter", identity.method)

                is ProofIdentity.MethodFormal -> within("MethodFormal", identity.method)

                is ProofIdentity.PrimaryConstructor -> within("PrimaryConstructor", identity.owner)

                is ProofIdentity.Generated -> within("Generated", identity.owner, *identity.contracts.toTypedArray())

                is ProofIdentity.Super -> within("Super", identity.method)

                is ProofIdentity.Receiver -> within("Receiver", identity.owner)

                is ProofIdentity.Method -> within("Method", identity.parent)

                is ProofIdentity.Alternatives -> within("Alternatives", *identity.targets.toTypedArray())

                is ProofIdentity.TypeShape -> within("TypeShape", *identity.components.toTypedArray())

                is ProofIdentity.Composed ->
                    within(
                        "Composed",
                        identity.owner,
                        *(identity.members + identity.delegates + identity.cycles + identity.alternatives).toTypedArray(),
                    )

                else -> null
            }
        }

        /** The AST nodes whose names are written at [location], as their kinds and what each resolved to. */
        private fun node(location: SemanticModel.SourceLocation): String =
            nodes[location]?.joinToString(",").takeUnless { it.isNullOrEmpty() } ?: "no name node"

        private fun index(ast: AstNode): Map<SemanticModel.SourceLocation, List<String>> {
            val seen = Collections.newSetFromMap(IdentityHashMap<AstNode, Boolean>())
            val all = mutableListOf(ast)
            seen += ast
            var next = 0
            while (next < all.size) all[next++].childNodes().forEach { if (seen.add(it)) all += it }
            val index = hashMapOf<SemanticModel.SourceLocation, MutableList<String>>()
            all.forEach { node ->
                val source = node.source?.fileName ?: return@forEach
                when (node) {
                    is NameExpression ->
                        index.getOrPut(at(source, node.nameToken)) { mutableListOf() } +=
                            "Name(${node.resolvedTarget?.javaClass?.simpleName}<${node.parent?.javaClass?.simpleName})"

                    is NamedTypeExpression ->
                        node.nameBindings.forEach {
                            index.getOrPut(at(source, it.name())) { mutableListOf() } +=
                                "Type(${it.target()?.javaClass?.simpleName}<${node.parent?.javaClass?.simpleName})"
                        }

                    else -> {}
                }
            }
            return index
        }

        private fun at(
            source: String,
            token: Token,
        ) = SemanticModel.SourceLocation(
            source,
            SemanticModel.Range(
                SemanticModel.Position(Source.calculateLine(token.startPosition), Source.calculateOffset(token.startPosition)),
                SemanticModel.Position(Source.calculateLine(token.endPosition), Source.calculateOffset(token.endPosition)),
            ),
        )
    }

    /** The census's report: totals, then by construct with examples, then by library. */
    private fun report(gaps: List<Gap>): List<String> {
        val unbound = gaps.filter { it.unbound }
        val unproven = gaps.filterNot { it.refusing }
        val head =
            "census total unbound=${unbound.size} unplaceable=${gaps.count { it.refusing && !it.unbound }} unproven=${unproven.size} " +
                "(occurrences=${unproven.count { it.kind == Kind.OCCURRENCE }}, calls=${unproven.count { it.kind == Kind.CALL }}, " +
                "chains=${unproven.count { it.kind == Kind.CHAIN }})"
        val constructs =
            gaps.groupBy { it.construct }.entries.sortedByDescending { it.value.size }.flatMap { (construct, each) ->
                val names =
                    each
                        .groupingBy { it.name }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(8)
                listOf("census construct ${each.size} $construct names=${names.joinToString { "${it.key}=${it.value}" }}") +
                    each.distinctBy { it.name }.take(4).map { "  e.g. ${it.module} ${it.where} ${it.name} | ${it.text}" }
            }
        val modules =
            gaps.groupBy { it.module }.entries.sortedByDescending { it.value.size }.map { (module, each) ->
                "census module $module unbound=${each.count { it.unbound }} unproven=${each.count { !it.unbound }}"
            }
        return listOf(head) + constructs + modules + listOf("all:") + gaps.map { "  $it" }
    }
}
