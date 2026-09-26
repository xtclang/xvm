package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.ModuleRepository
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.PropertyConstant
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.WorkspaceEdit
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.tool.ModuleInfo
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference

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
    private val cache: AtomicReference<Map<String, XdkWorkspaceNavigation>> = AtomicReference(emptyMap()),
    private val discoverImports: Boolean = false,
) {
    private val captured =
        project.buildOrder().associateWith { module ->
            try {
                Result.success(XdkSources.capture(module.root, overlays, cancelled))
            } catch (
                failure: IOException,
            ) {
                Result.failure(failure)
            }
        }
    private val sources = captured.mapNotNull { (module, result) -> result.getOrNull()?.let { module to it } }.toMap()
    private val texts = sources.values.flatMap { it.inputs.text.entries }.associate { it.key.path to it.value }
    private val uris = sources.values.flatMap { it.sourceUris.entries }.associate { it.toPair() }

    fun navigation(): XdkWorkspaceNavigation? {
        val revision = revision()
        cache.get()[revision]?.let { return if (isCurrent()) it else null }
        val facts = compile(texts, Proof.NAVIGATION) ?: return null
        val models = facts.models
        val declared = models.associate { it.sourceName to it.id }
        val aliases =
            models
                .flatMap { it.symbols }
                .distinctBy { it.id }
                .groupBy { symbol -> facts.constants[symbol.id] ?: symbol.location() ?: symbol.id }
                .values
                .flatMap { group ->
                    val canonical = group.firstOrNull { declared[it.declarationSource] == it.id.snapshot } ?: group.first()
                    group.map { it.id to canonical.id }
                }.toMap()
        val views = SemanticModel.joined(models, aliases).associateBy { uris.getValue(requireNotNull(it.sourceName)) }
        if (!isCurrent()) return null
        val complete = project.modules.values.all { module -> models.any { it.sourceName == module.root.path } }
        return XdkWorkspaceNavigation(views, revision, complete).also { cache.set(mapOf(revision to it)) }
    }

    private fun revision(): String {
        val bytes =
            ByteArrayOutputStream()
                .also { bytes ->
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
                        uris.toSortedMap().forEach { (source, uri) ->
                            value(source)
                            value(uri)
                        }
                        texts.toSortedMap().forEach { (path, text) ->
                            value(path)
                            value(text)
                        }
                        dependencies.modules.toSortedMap().forEach { (name, artifact) ->
                            value(name)
                            value(artifact.revision)
                        }
                    }
                }.toByteArray()
        return "graph:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }

    fun symbols(query: String): List<SymbolInfo> = navigation()?.symbols(query).orEmpty()

    fun references(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> = navigation()?.references(uri, line, column, includeDeclaration).orEmpty()

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
        model.importAt(line, column)?.let { alias ->
            if (!XdkRename.identifier(name)) return null
            val text = texts.getValue(source)
            val replacements =
                (alias.uses + alias.declaration).distinct().map { range ->
                    XdkRename.Edit(
                        XdkRename.offset(text, range.start) ?: return null,
                        XdkRename.offset(text, range.end) ?: return null,
                        name,
                    )
                }
            val plan = XdkRename.Plan(texts, mapOf(source to replacements))
            val after = compile(plan.proposed) ?: return null
            if (!XdkRename.preservesBindings(before, after, plan) || !isCurrent()) return null
            return WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true)
        }
        val symbol = model.symbolAt(line, column) ?: return null
        val target = before.constants[symbol.id] ?: return null
        val declarationSource = symbol.declarationSource ?: return null
        if (declarationSource !in texts || symbol.declaration == null) return null
        val targets =
            when {
                symbol.kind == SemanticModel.SymbolKind.TYPE -> {
                    setOf(target)
                }

                symbol.kind in setOf(SemanticModel.SymbolKind.METHOD, SemanticModel.SymbolKind.PROPERTY) &&
                    SemanticModel.Modifier.STATIC in symbol.modifiers && symbol.name != "construct" -> {
                    setOf(target)
                }

                target is PropertyConstant -> {
                    propertyFamily(before, target) ?: return null
                }

                target is MethodConstant -> {
                    methodFamily(before, target) ?: return null
                }

                else -> {
                    return null
                }
            }
        val ids = before.constants.filterValues { it in targets }.keys
        val base = XdkRename.plan(before, texts, source, line, column, name, ids) ?: return null
        val file = File(declarationSource)
        val moves =
            if (symbol.kind == SemanticModel.SymbolKind.TYPE && name != symbol.name &&
                file.nameWithoutExtension == symbol.name && sources.keys.none { it.root == file }
            ) {
                val destination = File(file.parentFile, "$name.x")
                // Moving a companion directory needs a separate directory/resource-operation proof.
                if (File(file.parentFile, file.nameWithoutExtension).exists() || destination.exists() ||
                    destination.path in texts
                ) {
                    return null
                }
                mapOf(file.path to destination.path)
            } else {
                emptyMap()
            }
        val plan = XdkRename.Plan(texts, base.edits, moves)
        val after = compile(plan.proposed, moves = moves) ?: return null
        if (!XdkRename.preservesBindings(before, after, plan) || !isCurrent()) return null
        return WorkspaceEdit(
            plan.edits.keys.associate { uris.getValue(it) to plan.textEdits(it) },
            versioned = true,
            renames = moves.entries.associate { (from, to) -> uris.getValue(from) to File(to).toURI().toString() },
        )
    }

    /** Candidate syntax is insufficient: every edit must compile and preserve all existing bindings. */
    fun importActions(
        uri: String,
        range: Range,
    ): List<CodeAction> {
        val source = XdkSources.file(uri)?.path ?: return emptyList()
        val text = texts[source] ?: return emptyList()
        val before = compile(texts, Proof.REPAIR) ?: return emptyList()
        if (before.models.any { it.status != SemanticModel.Status.COMPLETE } ||
            project.modules.values.any { module -> before.models.none { it.sourceName == module.root.path } }
        ) {
            return autoImports(uri, source, text, range, before)
        }
        val actions =
            XdkImports.candidates(text).mapNotNull { candidate ->
                checkCurrent()
                val plan = XdkRename.Plan(texts, mapOf(source to candidate.edits))
                val after = compile(plan.proposed) ?: return@mapNotNull null
                if (!XdkRename.preservesBindings(before, after, plan)) return@mapNotNull null
                CodeAction(
                    candidate.title,
                    candidate.kind,
                    edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                )
            }
        return if (isCurrent()) actions else emptyList()
    }

    private fun autoImports(
        uri: String,
        source: String,
        text: String,
        range: Range,
        before: CompilerRenameFacts,
    ): List<CodeAction> {
        val model = before.models.singleOrNull { it.sourceName == source } ?: return emptyList()
        val start = SemanticModel.Position(range.start.line, range.start.column)
        val end = SemanticModel.Position(range.end.line, range.end.column)
        val names =
            model.occurrences
                .filter { it.symbol == null && it.range.start <= end && it.range.end >= start }
                .map { it.name }
                .distinct()
        val owner = project.modules.values.singleOrNull { it.uri == project.scope(uri) } ?: return emptyList()
        val actions =
            names.flatMap { XdkAutoImports.targets(it, before) }.distinct().take(32).mapNotNull { target ->
                checkCurrent()
                val addedSource = target.module != owner.name && target.module in project.modules && target.module !in owner.dependencies
                if (addedSource && !discoverImports) return@mapNotNull null
                val graph =
                    if (addedSource) {
                        try {
                            XdkProject(
                                project.modules.values.map {
                                    if (it.name == owner.name) XdkSourceModule(it.name, it.uri, it.dependencies + target.module) else it
                                },
                            )
                        } catch (_: IllegalArgumentException) {
                            return@mapNotNull null
                        }
                    } else {
                        project
                    }
                val edit = XdkAutoImports.edit(text, owner.name, target) ?: return@mapNotNull null
                val plan = XdkRename.Plan(texts, mapOf(source to listOf(edit)))
                val after = compile(plan.proposed, graph = graph) ?: return@mapNotNull null
                if (!XdkRename.preservesKnownBindings(before, after, plan)) return@mapNotNull null
                CodeAction(
                    "Import '${target.path}' from ${target.module}",
                    CodeAction.CodeActionKind.QUICKFIX,
                    edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                )
            }
        return if (isCurrent()) actions else emptyList()
    }

    private fun propertyFamily(
        facts: CompilerRenameFacts,
        target: PropertyConstant,
    ): Set<PropertyConstant>? {
        val family = linkedSetOf(target)
        do {
            val previous = family.size
            facts.properties.chains.filter { chain -> chain.properties.any(family::contains) }.forEach { chain ->
                if (!chain.supported) return null
                family += chain.properties
            }
        } while (family.size != previous)
        if (!facts.properties.declarations.containsAll(family)) return null
        return family
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

    private enum class Proof { COMPLETE, NAVIGATION, REPAIR }

    private fun compile(
        text: Map<String, String>,
        proof: Proof = Proof.COMPLETE,
        moves: Map<String, String> = emptyMap(),
        graph: XdkProject = project,
    ): CompilerRenameFacts? {
        checkCurrent()
        if (proof == Proof.COMPLETE && sources.size != graph.modules.size) return null
        val artifacts = dependencies.modules.filterKeys { it !in project.modules }.toMutableMap()
        val attempts =
            graph.buildOrder().mapNotNull { module ->
                val source = sources.entries.firstOrNull { it.key.name == module.name }?.value ?: return@mapNotNull null
                checkCurrent()
                if (module.dependencies.any { it !in artifacts && it !in XdkLibraries.moduleNames }) {
                    if (proof != Proof.COMPLETE) return@mapNotNull null
                    return null
                }
                val open = XdkDependencies(artifacts.values.toList()).open()
                val heard = ErrorList()
                val errors = ErrorListener.cancellable(heard, cancelled)
                val compilation = compileTree(XdkSources.replay(module.root, source.inputs, text, moves), open.repository, errors)
                checkCurrent()
                if (!compilation.succeeded() || heard.hasSeriousErrors() ||
                    compilation.file()?.module?.name != module.name
                ) {
                    if (proof == Proof.REPAIR) return@mapNotNull compilation.repairFacts(open)
                    if (proof == Proof.NAVIGATION) return@mapNotNull null
                    return null
                }
                val facts = compilation.projectRenameFacts(open, errors)
                if (heard.hasSeriousErrors() || errors.isAbortDesired) {
                    checkCurrent()
                    if (proof != Proof.COMPLETE) return@mapNotNull null
                    return null
                }
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
            CompilerPropertyRelations(
                attempts.flatMapTo(linkedSetOf()) { it.properties.declarations },
                attempts.flatMap { it.properties.chains },
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
            captured
                .all { (module, original) ->
                    val current =
                        try {
                            XdkSources.capture(module.root, overlays, cancelled).inputs
                        } catch (_: IOException) {
                            null
                        }
                    current == original.getOrNull()?.inputs
                }.also { checkCurrent() }
        } catch (_: IOException) {
            false
        }
    }

    private fun SemanticModel.Symbol.location(): SemanticModel.SourceLocation? =
        declaration?.let { range -> declarationSource?.let { SemanticModel.SourceLocation(it, range) } }
}
