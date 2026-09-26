package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.ModuleRepository
import org.xvm.asm.constants.MethodConstant
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.WorkspaceEdit
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.tool.ModuleInfo
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CancellationException

/**
 * Worker-only whole-graph queries. Capture every configured module before compiling any of them.
 * Proof compilations never update the live diagnostics, source build cache or editor buffers.
 * Only detached locations/edits leave this object; compiler identities die with each query.
 */
internal class XdkProjectQueries(
    private val project: XdkProject,
    private val overlays: Map<String, String>,
    private val dependencies: XdkDependencies,
    private val compileTree: (ModuleInfo, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    private val cancelled: () -> Boolean,
) {
    private val sources = project.buildOrder().associateWith { XdkSources.capture(it.root, overlays, cancelled) }
    private val texts = sources.values.flatMap { it.inputs.text.entries }.associate { it.key.path to it.value }
    private val uris = sources.values.flatMap { it.sourceUris.entries }.associate { it.toPair() }

    fun navigation(): XdkWorkspaceNavigation? {
        val facts = compile(texts) ?: return null
        val models = facts.models
        val declared = models.associate { it.sourceName to it.id }
        val aliases = models.flatMap { it.symbols }.distinctBy { it.id }
            .groupBy { symbol -> facts.constants[symbol.id] ?: symbol.location() ?: symbol.id }
            .values.flatMap { group ->
                val canonical = group.firstOrNull { declared[it.declarationSource] == it.id.snapshot } ?: group.first()
                group.map { it.id to canonical.id }
            }.toMap()
        val views = SemanticModel.joined(models, aliases).associateBy { uris.getValue(requireNotNull(it.sourceName)) }
        return if (isCurrent()) XdkWorkspaceNavigation(views, revision()) else null
    }

    private fun revision(): String {
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                fun value(text: String) {
                    val utf8 = text.toByteArray(Charsets.UTF_8)
                    output.writeInt(utf8.size)
                    output.write(utf8)
                }
                project.buildOrder().forEach { module ->
                    value(module.name)
                    value(module.uri)
                    value(module.dependencies.sorted().joinToString("\u0000"))
                }
                texts.toSortedMap().forEach { (path, text) -> value(path); value(text) }
                dependencies.modules.toSortedMap().forEach { (name, artifact) -> value(name); value(artifact.revision) }
            }
        }.toByteArray()
        return "graph:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }

    fun symbols(query: String): List<SymbolInfo> {
        val facts = compile(texts, allowIncomplete = true) ?: return emptyList()
        val symbols = facts.models.flatMap { model ->
            model.symbols.filter { symbol ->
                symbol.declarationSource == model.sourceName && symbol.declaration != null &&
                    symbol.kind in setOf(SemanticModel.SymbolKind.TYPE, SemanticModel.SymbolKind.METHOD,
                        SemanticModel.SymbolKind.PROPERTY, SemanticModel.SymbolKind.MODULE, SemanticModel.SymbolKind.PACKAGE) &&
                    (query.isBlank() || symbol.name.contains(query, ignoreCase = true))
            }.map { symbol ->
                val range = requireNotNull(symbol.declaration)
                SymbolInfo.of(symbol.name, when (symbol.kind) {
                    SemanticModel.SymbolKind.METHOD -> SymbolInfo.SymbolKind.METHOD
                    SemanticModel.SymbolKind.PROPERTY -> SymbolInfo.SymbolKind.PROPERTY
                    SemanticModel.SymbolKind.MODULE -> SymbolInfo.SymbolKind.MODULE
                    SemanticModel.SymbolKind.PACKAGE -> SymbolInfo.SymbolKind.PACKAGE
                    else -> SymbolInfo.SymbolKind.CLASS
                }, Location(uris.getValue(requireNotNull(model.sourceName)), range.start.line, range.start.column,
                    range.end.line, range.end.column))
            }
        }
        return if (isCurrent()) symbols.distinctBy { it.location }.sortedBy { it.name } else emptyList()
    }

    fun references(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        val facts = compile(texts) ?: return emptyList()
        val source = XdkSources.file(uri)?.path ?: return emptyList()
        val model = facts.models.singleOrNull { it.sourceName == source } ?: return emptyList()
        val target = model.symbolAt(line, column) ?: return emptyList()
        val declaration = target.location()
        val constant = facts.constants[target.id]
        val found =
            facts.models.flatMap { view ->
                view.occurrences
                    .filter { occurrence ->
                        val symbol = occurrence.symbol?.let(view::symbol)
                        (
                            symbol?.id == target.id || (declaration != null && symbol?.location() == declaration) ||
                                (constant != null && symbol?.id?.let(facts.constants::get) == constant)
                        ) &&
                            (includeDeclaration || occurrence.role != SemanticModel.Role.DECLARATION)
                    }.map { occurrence ->
                        val range = occurrence.range
                        Location(
                            uris.getValue(requireNotNull(view.sourceName)),
                            range.start.line,
                            range.start.column,
                            range.end.line,
                            range.end.column,
                        )
                    }
            }
        return if (isCurrent()) {
            found.distinct().sortedWith(
                compareBy(Location::uri, Location::startLine, Location::startColumn),
            )
        } else {
            emptyList()
        }
    }

    /** Rename ordinary source methods and their complete compiler override family in this graph. */
    fun rename(
        uri: String,
        line: Int,
        column: Int,
        name: String,
    ): WorkspaceEdit? {
        val source = XdkSources.file(uri)?.path ?: return null
        val before = compile(texts) ?: return null
        val model = before.models.singleOrNull { it.sourceName == source } ?: return null
        val symbol = model.symbolAt(line, column) ?: return null
        val target = before.constants[symbol.id] ?: return null
        if (symbol.declarationSource !in texts || symbol.declaration == null) return null
        val targets = when {
            symbol.kind == SemanticModel.SymbolKind.TYPE -> {
                // Member-file names participate in module assembly; file-renaming is a separate operation.
                val root = XdkSources.file(uris.getValue(symbol.declarationSource)) ?: return null
                if (root.nameWithoutExtension == symbol.name && sources.keys.none { it.root == root }) return null
                setOf(target)
            }
            symbol.kind in setOf(SemanticModel.SymbolKind.METHOD, SemanticModel.SymbolKind.PROPERTY) &&
                SemanticModel.Modifier.STATIC in symbol.modifiers && symbol.name != "construct" -> setOf(target)
            target is MethodConstant -> methodFamily(before, target) ?: return null
            else -> return null
        }
        val ids = before.constants.filterValues { it in targets }.keys
        val plan = XdkRename.plan(before, texts, source, line, column, name, ids) ?: return null
        val after = compile(plan.proposed) ?: return null
        if (!XdkRename.preservesBindings(before, after, plan) || !isCurrent()) return null
        return WorkspaceEdit(plan.edits.keys.associate { uris.getValue(it) to plan.textEdits(it) }, versioned = true)
    }

    /** Candidate syntax is insufficient: every edit must compile and preserve all existing bindings. */
    fun importActions(uri: String): List<CodeAction> {
        val source = XdkSources.file(uri)?.path ?: return emptyList()
        val text = texts[source] ?: return emptyList()
        val before = compile(texts) ?: return emptyList()
        val actions = XdkImports.candidates(text).mapNotNull { candidate ->
            checkCurrent()
            val plan = XdkRename.Plan(texts, mapOf(source to candidate.edits))
            val after = compile(plan.proposed) ?: return@mapNotNull null
            if (!XdkRename.preservesBindings(before, after, plan)) return@mapNotNull null
            CodeAction(candidate.title, candidate.kind,
                edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true))
        }
        return if (isCurrent()) actions else emptyList()
    }

    private fun methodFamily(
        facts: CompilerRenameFacts,
        target: MethodConstant,
    ): Set<MethodConstant>? {
        val family = linkedSetOf(target)
        do {
            val previousSize = family.size
            facts.methods.chains.filter { chain -> chain.methods.any(family::contains) }.forEach { chain ->
                if (!chain.supported) return null
                family += chain.methods
            }
        } while (family.size != previousSize)
        // A binary/library contract or synthetic method cannot be edited from configured sources.
        if (!facts.methods.declarations.containsAll(family)) return null
        return family
    }

    private fun compile(text: Map<String, String>, allowIncomplete: Boolean = false): CompilerRenameFacts? {
        checkCurrent()
        val artifacts = dependencies.modules.filterKeys { name -> sources.keys.none { it.name == name } }.toMutableMap()
        val attempts =
            sources.mapNotNull { (module, source) ->
                checkCurrent()
                if (module.dependencies.any { it !in artifacts && it !in XdkLibraries.moduleNames }) {
                    if (allowIncomplete) return@mapNotNull null
                    return null
                }
                val open = XdkDependencies(artifacts.values.toList()).open()
                val heard = ErrorList()
                val errors = ErrorListener.cancellable(heard, cancelled)
                val compilation = compileTree(XdkSources.replay(module.root, source.inputs, text), open.repository, errors)
                checkCurrent()
                if (!compilation.succeeded() || heard.hasSeriousErrors() ||
                    compilation.file().module.name != module.name
                ) {
                    if (allowIncomplete) return@mapNotNull null
                    return null
                }
                val facts = compilation.projectRenameFacts(open, errors)
                if (heard.hasSeriousErrors() || errors.isAbortDesired) return null
                artifacts[module.name] = compilation.toDependency()
                facts
            }
        checkCurrent()
        return CompilerRenameFacts(
            attempts.flatMap { it.models },
            attempts.flatMap { it.constants.entries }.associate { it.toPair() },
            CompilerMethodRelations(
                attempts.flatMapTo(linkedSetOf()) { it.methods.declarations },
                attempts.flatMap { it.methods.chains },
            ),
        )
    }

    private fun checkCurrent() {
        if (cancelled()) throw CancellationException()
    }

    /** Reject changed closed sources/membership even when their watcher notification is delayed. */
    private fun isCurrent(): Boolean {
        checkCurrent()
        return try {
            sources
                .all { (module, source) -> XdkSources.capture(module.root, overlays, cancelled).inputs == source.inputs }
                .also { checkCurrent() }
        } catch (_: IOException) {
            false
        }
    }

    private fun SemanticModel.Symbol.location(): SemanticModel.SourceLocation? =
        declaration?.let { range -> declarationSource?.let { SemanticModel.SourceLocation(it, range) } }
}
