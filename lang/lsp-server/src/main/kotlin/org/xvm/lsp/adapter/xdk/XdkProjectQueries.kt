package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.ModuleRepository
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.WorkspaceEdit
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.util.ExecutionTrace
import org.xvm.tool.ModuleInfo
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference

/**
 * Worker-only whole-graph queries. Capture every configured module before compiling any of them.
 * Proof compilations never update the live diagnostics, source build cache or editor buffers. Only
 * detached locations/edits leave this object; compiler identities die with each query.
 */
internal class XdkProjectQueries(
    private val project: XdkProject,
    private val overlays: Map<String, String>,
    private val dependencies: XdkDependencies,
    private val compileTree: (ModuleInfo, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    private val cancelled: () -> Boolean,
    private val cache: AtomicReference<Map<String, XdkWorkspaceNavigation>> =
        AtomicReference(emptyMap()),
    private val discoverImports: Boolean = false,
    private val diagnosticCache: XdkDiagnosticIndex = XdkDiagnosticIndex(),
) {
    private val captured =
        project.buildOrder().associateWith { module ->
            try {
                Result.success(
                    XdkSources.capture(module.root, overlays, module.resourceFiles, cancelled),
                )
            } catch (failure: IOException) {
                Result.failure(failure)
            }
        }
    private val sources =
        captured.mapNotNull { (module, result) -> result.getOrNull()?.let { module to it } }.toMap()
    private val texts =
        sources.values.flatMap { it.inputs.text.entries }.associate { it.key.path to it.value }
    private val uris = sources.values.flatMap { it.sourceUris.entries }.associate { it.toPair() }

    /** Read-only graph compilation, with no live AST installation or artificial open buffers. */
    fun diagnostics(): List<CompilationResult> {
        val revision = revision()
        val previous = diagnosticCache.snapshot()
        if (previous.revision == revision && isCurrent()) return previous.results
        val builds = linkedMapOf<String, XdkDiagnosticIndex.Build>()
        val artifacts = dependencies.modules.filterKeys { it !in project.modules }.toMutableMap()
        val results =
            project.buildOrder().map { module ->
                checkCurrent()
                val source = sources[module]
                val uri = source?.uri(module.root) ?: module.uri
                val missing =
                    module.dependencies.filter {
                        it !in artifacts && it !in XdkLibraries.moduleNames
                    }
                if (source == null || missing.isNotEmpty()) {
                    CompilationResult.withDiagnostics(
                        uri,
                        listOf(
                            Diagnostic(
                                location = Location(uri, 0, 0, 0, 0),
                                severity = Diagnostic.Severity.ERROR,
                                message =
                                    if (source == null) {
                                        "Cannot read source for ${module.name}"
                                    } else {
                                        "Dependencies unavailable: ${missing.joinToString()}"
                                    },
                                code =
                                    if (source == null) {
                                        "SOURCE-UNAVAILABLE"
                                    } else {
                                        "DEPENDENCY-FAILED"
                                    },
                                source = "xtc",
                            ),
                        ),
                        emptyList(),
                        source?.documentUris ?: setOf(module.uri),
                    )
                } else {
                    val sourceDependencies =
                        project.buildOrder(module.uri).mapTo(hashSetOf()) { it.name }
                    val inputs =
                        artifacts.filterKeys {
                            it !in project.modules || it in sourceDependencies
                        }
                    val key =
                        XdkDiagnosticIndex.Key(
                            module.name,
                            source.inputs,
                            inputs.mapValues { it.value.revision },
                        )
                    val build =
                        previous.builds[module.uri]?.takeIf { it.key == key }
                            ?: run {
                                val open = XdkDependencies(inputs.values.toList()).open()
                                val heard = ErrorList()
                                val errors = ErrorListener.cancellable(heard, cancelled)
                                val compilation = compileTree(source, open.repository, errors)
                                checkCurrent()
                                val declarations =
                                    XdkAst.declarationLocations(
                                        compilation.sourceTrees(),
                                        source.sourceUris,
                                    )
                                val fallback = Source("", source.uri(module.root))
                                val diagnostics =
                                    heard.errors.map {
                                        it.toDiagnostic(fallback, source.sourceUris, declarations)
                                    }
                                val artifact =
                                    if (compilation.succeeded() && !heard.hasSeriousErrors()) {
                                        compilation.toDependency()
                                    } else {
                                        null
                                    }
                                val items =
                                    if (artifact != null && artifact.module != module.name) {
                                        listOf(
                                            Diagnostic(
                                                location = Location(uri, 0, 0, 0, 0),
                                                severity = Diagnostic.Severity.ERROR,
                                                message =
                                                    "Expected module ${module.name}, found ${artifact.module}",
                                                code = "PROJECT-MODULE",
                                                source = "xtc",
                                            ),
                                        )
                                    } else {
                                        diagnostics
                                    }
                                XdkDiagnosticIndex.Build(
                                    key,
                                    CompilationResult.withDiagnostics(
                                        uri,
                                        items,
                                        emptyList(),
                                        source.documentUris,
                                    ),
                                    artifact?.takeIf { it.module == module.name },
                                )
                            }
                    builds[module.uri] = build
                    build.artifact?.let { artifacts[module.name] = it }
                    build.result
                }
            }
        if (!isCurrent()) throw CancellationException()
        diagnosticCache.replace(XdkDiagnosticIndex.Snapshot(revision, results, builds.toMap()))
        return results
    }

    fun navigation(): XdkWorkspaceNavigation? {
        val revision = revision()
        cache.get()[revision]?.let {
            return if (isCurrent()) it else null
        }
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
                    val canonical =
                        group.firstOrNull { declared[it.declarationSource] == it.id.snapshot }
                            ?: group.first()
                    group.map { it.id to canonical.id }
                }.toMap()
        val views =
            SemanticModel.joined(models, aliases).associateBy {
                uris.getValue(requireNotNull(it.sourceName))
            }
        if (!isCurrent()) return null
        val complete =
            project.modules.values.all { module ->
                models.any { it.sourceName == module.root.path }
            }
        val dependencySources =
            dependencies.modules.values
                .flatMap { it.declarations.values }
                .mapNotNull { it.sourceName }
                .distinct()
                .mapNotNull { name -> XdkSources.sourceUri(name)?.let { name to it } }
                .toMap()
        return XdkWorkspaceNavigation(views, revision, complete, dependencySources).also {
            cache.set(mapOf(revision to it))
        }
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
                            value(
                                sources[module]
                                    ?.inputs
                                    ?.resources
                                    ?.revision
                                    .orEmpty(),
                            )
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
        return "graph:" +
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
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
    ): WorkspaceEdit? = renameProposal(uri, line, column, name)?.takeIf { it.sourceModules == null }?.edit

    fun renameProposal(
        uri: String,
        line: Int,
        column: Int,
        name: String,
    ): XdkRenameProposal? = compile(texts)?.let { renameProposal(it, uri, line, column, name) }

    private fun renameProposal(
        before: CompilerRenameFacts,
        uri: String,
        line: Int,
        column: Int,
        name: String,
    ): XdkRenameProposal? {
        val source = XdkSources.file(uri)?.path ?: return null
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
            if (!preservesBindings(before, after, plan) || !isCurrent()) return null
            return XdkRenameProposal(
                WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                scope = renameScope(),
            )
        }
        val selected = model.symbolAt(line, column) ?: return null
        val target = before.constants[selected.id] ?: return null
        val symbol =
            before.models
                .asSequence()
                .flatMap { it.symbols.asSequence() }
                .firstOrNull {
                    before.constants[it.id] in members(target) &&
                        it.declaration != null &&
                        it.declarationSource in texts
                } ?: selected.takeIf { target is ProofIdentity.Directory } ?: return null
        val declarationSource =
            (target as? ProofIdentity.Directory)?.path ?: symbol.declarationSource ?: return null
        if (
            target !is ProofIdentity.Directory &&
            (declarationSource !in texts || symbol.declaration == null)
        ) {
            return null
        }
        val targets =
            when {
                target is ProofIdentity.Parameter -> {
                    parameterFamily(before, target) ?: return null
                }

                symbol.kind in
                    setOf(
                        SemanticModel.SymbolKind.TYPE,
                        SemanticModel.SymbolKind.PACKAGE,
                        SemanticModel.SymbolKind.MODULE,
                    ) -> {
                    setOf(target)
                }

                symbol.kind in
                    setOf(SemanticModel.SymbolKind.METHOD, SemanticModel.SymbolKind.PROPERTY) &&
                    SemanticModel.Modifier.STATIC in symbol.modifiers &&
                    symbol.name != "construct" -> {
                    setOf(target)
                }

                symbol.kind == SemanticModel.SymbolKind.PROPERTY -> {
                    propertyFamily(before, target) ?: return null
                }

                symbol.kind == SemanticModel.SymbolKind.METHOD -> {
                    methodFamily(before, target) ?: return null
                }

                else -> {
                    return null
                }
            }
        val ids = before.constants.filterValues { members(it).all(targets::contains) }.keys
        val base = XdkRename.plan(before, texts, source, line, column, name, ids) ?: return null
        val file = File(declarationSource)
        val renamedModule =
            project.modules.values.singleOrNull {
                it.root == file && symbol.kind == SemanticModel.SymbolKind.MODULE
            }
        val moduleName =
            renamedModule?.let {
                if (it.name.substringBefore('.') != symbol.name) return null
                name + it.name.removePrefix(symbol.name)
            }
        if (
            renamedModule != null &&
            (
                (moduleName != renamedModule.name && moduleName in project.modules) ||
                    moduleName in XdkLibraries.moduleNames
            )
        ) {
            return null
        }
        val moves =
            if (target is ProofIdentity.Directory && name != symbol.name) {
                XdkSourceMoves.directory(
                    file,
                    name,
                    texts,
                    sources.values.flatMap { it.inputs.directories }.toSet(),
                ) ?: return null
            } else if (
                symbol.kind in
                setOf(
                    SemanticModel.SymbolKind.TYPE,
                    SemanticModel.SymbolKind.PACKAGE,
                    SemanticModel.SymbolKind.MODULE,
                ) && name != symbol.name && file.nameWithoutExtension == symbol.name
            ) {
                XdkSourceMoves.plan(
                    file,
                    name,
                    texts,
                    sources.values.flatMap { it.inputs.directories }.toSet(),
                ) ?: return null
            } else {
                XdkSourceMoves()
            }
        val graph =
            if (renamedModule == null) {
                project
            } else {
                XdkProject(
                    project.modules.values.map { module ->
                        XdkSourceModule(
                            if (module === renamedModule) {
                                requireNotNull(moduleName)
                            } else {
                                module.name
                            },
                            File(moves.paths[module.root.path] ?: module.root.path)
                                .toURI()
                                .toString(),
                            module.dependencies.mapTo(linkedSetOf()) {
                                if (it == renamedModule.name) requireNotNull(moduleName) else it
                            },
                            module.resourceRoots,
                        )
                    },
                )
            }
        val plan = XdkRename.Plan(texts, base.edits, moves.paths)
        val after = compile(plan.proposed, moves = moves.paths, graph = graph) ?: return null
        if (!preservesBindings(before, after, plan) || !isCurrent()) return null
        return XdkRenameProposal(
            WorkspaceEdit(
                plan.edits.keys.associate { uris.getValue(it) to plan.textEdits(it) },
                versioned = true,
                renames =
                    moves.resources.entries.associate { (from, to) ->
                        // Directory URIs must have the same shape even before the destination
                        // exists.
                        (uris[from] ?: File(from).toURI().toString().removeSuffix("/")) to
                            File(to).toURI().toString().removeSuffix("/")
                    },
            ),
            graph.modules.values.toList().takeIf {
                renamedModule != null && !discoverImports && !project.sameConfiguration(graph)
            },
            project.modules.values.toList().takeIf {
                renamedModule != null && !discoverImports && !project.sameConfiguration(graph)
            },
            renameScope(),
        )
    }

    /** Complete transaction for hosts that persist the graph with the requested resource moves. */
    fun renameFilesProposal(requested: Map<String, String>): XdkRenameProposal? {
        val operations =
            requested.entries
                .associate { (from, to) ->
                    (XdkSources.file(from) ?: return null) to (XdkSources.file(to) ?: return null)
                }.filter { (from, to) -> from != to }
        if (operations.isEmpty()) return XdkRenameProposal(WorkspaceEdit(emptyMap(), versioned = true))
        if (
            operations.values.distinct().size != operations.size ||
            operations.any { (from, to) ->
                !from.exists() ||
                    !to.parentFile.isDirectory ||
                    to.exists() ||
                    Files.isSymbolicLink(from.toPath()) ||
                    (
                        from.isDirectory &&
                            from.walkTopDown().any { Files.isSymbolicLink(it.toPath()) }
                    ) ||
                    to.toPath().startsWith(from.toPath())
            }
        ) {
            return null
        }
        // Overlapping parent/child requests have ambiguous application order; do not guess.
        if (
            operations.keys.any { parent ->
                operations.keys.any { it != parent && it.toPath().startsWith(parent.toPath()) }
            }
        ) {
            return null
        }
        val before = compile(texts) ?: return null
        val directories = sources.values.flatMap { it.inputs.directories }.toSet()
        val rootMoves =
            operations
                .filter { (from, to) ->
                    from.parentFile != to.parentFile && project.modules.values.any { it.root == from }
                }.map { (from, to) ->
                    // Changing a root's name and ownership together needs a combined symbol proof.
                    if (from.name != to.name || !to.parentFile.isDirectory) return null
                    // Discovery cannot persist resource roots left outside the moved companion tree.
                    if (discoverImports) return null
                    XdkSourceMoves.plan(from, to, texts, directories) ?: return null
                }
        val typeMoves =
            operations
                .filter { (from, to) ->
                    from.isFile && from.parentFile != to.parentFile && from.path in texts &&
                        project.modules.values.none { it.root == from }
                }.map { (from, to) ->
                    XdkTypeMoves.plan(before, from, to, texts, directories, project) ?: return null
                }
        val proposals =
            operations
                .map { (from, to) ->
                    if (from.parentFile != to.parentFile) return@map null
                    val selected =
                        before.models
                            .asSequence()
                            .flatMap { model ->
                                model.symbols.asSequence().mapNotNull { symbol ->
                                    val directory =
                                        (before.constants[symbol.id] as? ProofIdentity.Directory)
                                            ?.path == from.path
                                    val declaration =
                                        symbol.declarationSource == from.path &&
                                            symbol.name == from.nameWithoutExtension &&
                                            symbol.kind in
                                            setOf(
                                                SemanticModel.SymbolKind.TYPE,
                                                SemanticModel.SymbolKind.PACKAGE,
                                                SemanticModel.SymbolKind.MODULE,
                                            )
                                    when {
                                        declaration && symbol.declaration != null -> {
                                            Triple(
                                                uris[from.path] ?: from.toURI().toString(),
                                                symbol.declaration.start,
                                                symbol.name,
                                            )
                                        }

                                        directory -> {
                                            model.occurrences
                                                .firstOrNull { it.symbol == symbol.id }
                                                ?.let {
                                                    Triple(
                                                        uris[model.sourceName]
                                                            ?: return@mapNotNull null,
                                                        it.range.start,
                                                        symbol.name,
                                                    )
                                                }
                                        }

                                        else -> {
                                            null
                                        }
                                    }
                                }
                            }.firstOrNull() ?: return@map null
                    val name = if (from.isDirectory) to.name else to.nameWithoutExtension
                    if (from.isFile && to.extension != "x") return null
                    val proposal =
                        renameProposal(
                            before,
                            selected.first,
                            selected.second.line,
                            selected.second.column,
                            name,
                        ) ?: return null
                    if (
                        proposal.edit.renames.none { (old, new) ->
                            XdkSources.file(old) == from && XdkSources.file(new) == to
                        }
                    ) {
                        return null
                    }
                    proposal
                }.filterNotNull()
        val allMoves =
            operations.entries.map { it.key to it.value } +
                proposals
                    .flatMap { it.edit.renames.entries }
                    .map { (from, to) ->
                        requireNotNull(XdkSources.file(from)) to requireNotNull(XdkSources.file(to))
                    } +
                (typeMoves.map { it.resources } + rootMoves.map { it.resources })
                    .flatMap { it.entries }
                    .map { File(it.key) to File(it.value) }
        if (allMoves.groupBy({ it.first }, { it.second }).values.any { it.distinct().size > 1 }) {
            return null
        }
        val resources = allMoves.toMap()
        if (resources.keys.any { parent ->
                resources.keys.any { child -> child != parent && child.toPath().startsWith(parent.toPath()) } ||
                    resources.values.any { it.toPath().startsWith(parent.toPath()) }
            }
        ) {
            return null
        }
        val paths =
            (texts.keys.map(::File) + directories)
                .mapNotNull { file ->
                    val move =
                        resources.entries.firstOrNull { (from, _) ->
                            file == from || file.toPath().startsWith(from.toPath())
                        } ?: return@mapNotNull null
                    file.path to
                        move.value
                            .toPath()
                            .resolve(move.key.toPath().relativize(file.toPath()))
                            .toString()
                }.toMap()
        if (paths.isEmpty() || paths.values.distinct().size != paths.size) return null
        val names =
            project.buildOrder().associate { module ->
                val destination = operations[module.root]
                module.name to
                    if (
                        destination != null &&
                        destination.nameWithoutExtension != module.root.nameWithoutExtension
                    ) {
                        destination.nameWithoutExtension +
                            module.name.removePrefix(module.root.nameWithoutExtension)
                    } else {
                        module.name
                    }
            }
        val graph =
            XdkProject(
                project.buildOrder().map { module ->
                    XdkSourceModule(
                        names.getValue(module.name),
                        File(paths[module.root.path] ?: module.root.path).toURI().toString(),
                        module.dependencies.mapTo(linkedSetOf()) { names[it] ?: it },
                        (
                            module.resourceFiles ?: sources[module]?.inputs?.resources?.roots?.takeIf {
                                File(paths[module.root.path] ?: module.root.path).parentFile != module.root.parentFile
                            }
                        )?.map { resource ->
                            val move =
                                resources.entries.firstOrNull {
                                    resource.toPath().startsWith(it.key.toPath())
                                }
                            (
                                move
                                    ?.value
                                    ?.toPath()
                                    ?.resolve(move.key.toPath().relativize(resource.toPath()))
                                    ?.toFile() ?: resource
                            ).toURI()
                                .toString()
                        },
                    )
                },
            )
        val renamed =
            proposals
                .flatMap { it.edit.changes.entries }
                .groupBy({ requireNotNull(XdkSources.file(it.key)).path }, { it.value })
                .mapValues { (path, changes) ->
                    val text = texts[path] ?: return null
                    changes
                        .flatten()
                        .distinct()
                        .map { edit ->
                            XdkRename.Edit(
                                XdkRename.offset(
                                    text,
                                    SemanticModel.Position(
                                        edit.range.start.line,
                                        edit.range.start.column,
                                    ),
                                ) ?: return null,
                                XdkRename.offset(
                                    text,
                                    SemanticModel.Position(
                                        edit.range.end.line,
                                        edit.range.end.column,
                                    ),
                                ) ?: return null,
                                edit.newText,
                            )
                        }.sortedBy { it.start }
                        .also { ordered ->
                            if (
                                ordered.zipWithNext().any { (first, next) ->
                                    first.end > next.start
                                }
                            ) {
                                return null
                            }
                        }
                }
        val qualifications =
            typeMoves
                .flatMap { it.edits.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, edits) -> edits.flatten().distinct() }
        val edits =
            (renamed.entries + qualifications.entries)
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, edits) -> edits.flatten().distinct().sortedBy { it.start } }
        if (edits.values.any { changes ->
                changes.zipWithNext().any { (first, next) -> first.end > next.start || first.start == next.start }
            }
        ) {
            return null
        }
        val plan = XdkRename.Plan(texts, edits, paths, qualifications = qualifications)
        val resourceMoves = XdkResourceMoves(resources.mapKeys { it.key.path }.mapValues { it.value.path }, edits.keys)
        val after = compile(plan.proposed, moves = paths, graph = graph, resourceMoves = resourceMoves) ?: return null
        if (!preservesBindings(before, after, plan) || !isCurrent()) return null
        val replacement = !discoverImports && !project.sameConfiguration(graph)
        return XdkRenameProposal(
            WorkspaceEdit(
                edits.keys.associate { uris.getValue(it) to plan.textEdits(it) },
                versioned = true,
                renames =
                    resources.entries.associate { (from, to) ->
                        from.toURI().toString().removeSuffix("/") to to.toURI().toString().removeSuffix("/")
                    },
            ),
            graph.modules.values
                .toList()
                .takeIf { replacement },
            project.modules.values
                .toList()
                .takeIf { replacement },
            renameScope(),
        )
    }

    private fun renameScope(): XdkRenameScope =
        XdkRenameScope(
            if (discoverImports) {
                XdkRenameScope.Boundary.DISCOVERED_GRAPH
            } else {
                XdkRenameScope.Boundary.CONFIGURED_GRAPH
            },
            project.buildOrder(),
            uris.values.sorted(),
            revision(),
        )

    private fun preservesBindings(
        before: CompilerRenameFacts,
        after: CompilerRenameFacts,
        plan: XdkRename.Plan,
    ): Boolean {
        // Compilation still checks the complete graph. Binding/dispatch equivalence is needed
        // for every edited module and its transitive consumers; independent roots have identical
        // inputs and no path to an edited declaration. Unsupported dynamic bindings in those
        // unrelated roots must not veto an otherwise proven edit.
        val affected =
            (plan.edits.keys + plan.moves.keys)
                .mapNotNull { project.scope(it) }
                .flatMapTo(linkedSetOf(), project::affected)
        val movedScopes =
            affected.mapTo(linkedSetOf()) { scope ->
                val path = requireNotNull(XdkSources.file(scope)).path
                File(plan.moves[path] ?: path).toURI().toString()
            }
        // Independent source graphs may share resources, so compare resource values across every
        // module even when their declaration/dispatch proof can stay scoped to source consumers.
        return (plan.moves.isEmpty() || XdkRename.preservesResources(before, after, plan)) &&
            XdkRename.preservesBindings(before.within(affected), after.within(movedScopes), plan)
    }

    /**
     * Candidate syntax is insufficient: every edit must compile and preserve all existing bindings.
     */
    fun codeActions(
        uri: String,
        range: Range,
    ): List<CodeAction> {
        val source = XdkSources.file(uri)?.path ?: return emptyList()
        val text = texts[source] ?: return emptyList()
        val before = compile(texts, Proof.REPAIR) ?: return emptyList()
        val complete =
            before.models.all { it.status == SemanticModel.Status.COMPLETE } &&
                project.modules.values.all { module ->
                    before.models.any { it.sourceName == module.root.path }
                }
        val actions =
            if (!complete) {
                autoImports(uri, source, text, range, before)
            } else {
                XdkImports.candidates(text).mapNotNull { candidate ->
                    checkCurrent()
                    val plan = XdkRename.Plan(texts, mapOf(source to candidate.edits))
                    val after = compile(plan.proposed) ?: return@mapNotNull null
                    if (!XdkRename.preservesBindings(before, after, plan)) return@mapNotNull null
                    CodeAction(
                        candidate.title,
                        candidate.kind,
                        edit =
                            WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                    )
                }
            }
        val members =
            XdkMemberActions.actions(before.memberActions, source, range).mapNotNull { candidate ->
                checkCurrent()
                val edit = candidate.edit(text) ?: return@mapNotNull null
                val plan = XdkRename.Plan(texts, mapOf(source to edit.all))
                val after = compile(plan.proposed) ?: return@mapNotNull null
                if (
                    !XdkRename.preservesMemberAdditions(
                        before,
                        after,
                        plan,
                        candidate.members,
                        edit.member,
                    )
                ) {
                    return@mapNotNull null
                }
                CodeAction(
                    candidate.title,
                    // The class may be valid until constructed, and a construction diagnostic can
                    // be elsewhere. This is a class intention, not a diagnostic-attached quick fix.
                    CodeAction.CodeActionKind.REFACTOR_REWRITE,
                    edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                )
            }
        val extraction =
            if (complete) {
                XdkLocalExtraction.candidate(text, range)?.let { candidate ->
                    checkCurrent()
                    val plan =
                        XdkRename.Plan(
                            texts,
                            mapOf(source to candidate.edits),
                            relocations = mapOf(source to listOf(candidate.relocation)),
                        )
                    val after = compile(plan.proposed) ?: return@let null
                    if (!XdkRename.preservesKnownBindings(before, after, plan)) return@let null
                    CodeAction(
                        candidate.title,
                        CodeAction.CodeActionKind.REFACTOR_EXTRACT,
                        edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                    )
                }
            } else {
                null
            }
        val inline =
            if (complete) {
                before.models.singleOrNull { it.sourceName == source }?.let { model ->
                    XdkLocalInline.candidate(text, range, model)?.let { candidate ->
                        checkCurrent()
                        val plan =
                            XdkRename.Plan(
                                texts,
                                mapOf(source to candidate.edits),
                                relocations = mapOf(source to listOf(candidate.relocation)),
                            )
                        val after = compile(plan.proposed) ?: return@let null
                        if (!XdkRename.preservesLocalRemoval(before, after, plan, candidate.removed)) return@let null
                        CodeAction(
                            "Inline returned local variable",
                            CodeAction.CodeActionKind.REFACTOR_INLINE,
                            edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                        )
                    }
                }
            } else {
                null
            }
        return if (isCurrent()) actions + members + listOfNotNull(extraction, inline) else emptyList()
    }

    private fun autoImports(
        uri: String,
        source: String,
        text: String,
        range: Range,
        before: CompilerRenameFacts,
    ): List<CodeAction> {
        val models = before.models.filter { it.sourceName == source }
        val start = SemanticModel.Position(range.start.line, range.start.column)
        val end = SemanticModel.Position(range.end.line, range.end.column)
        val names =
            models
                .flatMap { it.occurrences }
                .filter { it.symbol == null && it.range.start <= end && it.range.end >= start }
                .map { it.name }
                .distinct()
        val owner =
            project.modules.values.singleOrNull { it.uri == project.scope(uri) }
                ?: return emptyList()
        val actions =
            names
                .flatMap { XdkAutoImports.targets(it, before) }
                .distinct()
                .take(32)
                .mapNotNull { target ->
                    checkCurrent()
                    val graph = importGraph(owner, target) ?: return@mapNotNull null
                    val edit =
                        XdkAutoImports.edit(text, owner.name, target) ?: return@mapNotNull null
                    val plan = XdkRename.Plan(texts, mapOf(source to listOf(edit)))
                    val after = compile(plan.proposed, graph = graph) ?: return@mapNotNull null
                    if (!XdkRename.preservesKnownBindings(before, after, plan)) {
                        return@mapNotNull null
                    }
                    CodeAction(
                        "Import '${target.path}' from ${target.module}",
                        CodeAction.CodeActionKind.QUICKFIX,
                        edit =
                            WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                    )
                }
        return if (isCurrent()) actions else emptyList()
    }

    /** A completion and its imports are one proof; never publish an import for an unfitted name. */
    fun importCompletions(
        uri: String,
        expectedText: String,
        prefix: PartialSemanticModel.MemberPrefix,
        visible: Set<String>,
    ): List<CompletionItem> {
        val source = XdkSources.file(uri)?.path ?: return emptyList()
        val text = texts[source]?.takeIf { it == expectedText } ?: return emptyList()
        val start = XdkRename.offset(text, prefix.range.start) ?: return emptyList()
        val end = XdkRename.offset(text, prefix.range.end) ?: return emptyList()
        val written = text.substring(start, end)
        if (!written.startsWith(prefix.text) || !XdkRename.identifier(written)) return emptyList()
        val owner = project.modules.values.singleOrNull { it.uri == project.scope(uri) } ?: return emptyList()
        val before = compile(texts, Proof.REPAIR) ?: return emptyList()
        val items =
            XdkAutoImports
                .matchingTargets(prefix.text, before)
                .filter { it.name !in visible }
                .take(8)
                .mapNotNull { target ->
                    checkCurrent()
                    val graph = importGraph(owner, target) ?: return@mapNotNull null
                    val imported = XdkAutoImports.edit(text, owner.name, target) ?: return@mapNotNull null
                    // LSP requires additional edits to be disjoint from the replacement edit.
                    if (imported.start in start..end) return@mapNotNull null
                    val replacement = XdkRename.Edit(start, end, target.name)
                    val plan = XdkRename.Plan(texts, mapOf(source to listOf(replacement, imported)))
                    val after = compile(plan.proposed, graph = graph) ?: return@mapNotNull null
                    if (!XdkRename.preservesKnownBindings(before, after, plan)) return@mapNotNull null
                    val edits = plan.textEdits(source)
                    CompletionItem(
                        target.name,
                        CompletionItem.CompletionKind.CLASS,
                        "${target.path} — import from ${target.module}",
                        target.name,
                        textEdit = edits.first(),
                        sortText = "7:${target.name}:${target.module}:${target.path}",
                        additionalTextEdits = edits.drop(1),
                    )
                }
        return if (isCurrent()) items else emptyList()
    }

    private fun importGraph(
        owner: XdkSourceModule,
        target: XdkAutoImports.Target,
    ): XdkProject? {
        val addedSource = target.module != owner.name && target.module in project.modules && target.module !in owner.dependencies
        if (!addedSource) return project
        if (!discoverImports) return null
        return try {
            XdkProject(
                project.modules.values.map {
                    if (it.name == owner.name) XdkSourceModule(it.name, it.uri, it.dependencies + target.module, it.resourceRoots) else it
                },
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun propertyFamily(
        facts: CompilerRenameFacts,
        target: ProofIdentity,
    ): Set<ProofIdentity>? {
        val family = linkedSetOf(target)
        do {
            val previous = family.size
            facts.properties.chains
                .filter { chain -> chain.members.any(family::contains) }
                .forEach { chain ->
                    if (!chain.supported) return null
                    family += chain.members
                }
        } while (family.size != previous)
        if (!facts.properties.declarations.containsAll(family)) return null
        return family
    }

    private fun methodFamily(
        facts: CompilerRenameFacts,
        target: ProofIdentity,
    ): Set<ProofIdentity>? {
        val family = members(target).toMutableSet()
        do {
            val previousSize = family.size
            facts.methods.chains
                .filter { chain -> chain.members.flatMap(::members).any(family::contains) }
                .forEach { chain ->
                    if (!chain.supported) return null
                    family += chain.members.flatMap(::members)
                }
            // A written union call couples otherwise independent contracts: renaming only one
            // leg would remove that call from the union's common method set.
            facts.constants.values.filterIsInstance<ProofIdentity.Alternatives>()
                .map(::members)
                .filter { it.any(family::contains) }
                .forEach { family += it }
        } while (family.size != previousSize)
        // A binary/library contract or synthetic method cannot be edited from configured sources.
        if (!facts.methods.declarations.containsAll(family)) return null
        return family
    }

    private fun members(identity: ProofIdentity): Set<ProofIdentity> =
        when (identity) {
            is ProofIdentity.Alternatives -> {
                identity.targets.flatMapTo(linkedSetOf(), ::members)
            }

            is ProofIdentity.Composed -> {
                identity.members.flatMapTo(linkedSetOf(), ::members)
            }

            is ProofIdentity.Parameter -> {
                members(identity.method).mapTo(linkedSetOf()) {
                    ProofIdentity.Parameter(it, identity.index)
                }
            }

            else -> {
                setOf(identity)
            }
        }

    /** Parameter names belong to callable slots, including differently named overrides. */
    private fun parameterFamily(
        facts: CompilerRenameFacts,
        target: ProofIdentity.Parameter,
    ): Set<ProofIdentity>? {
        val declarations =
            facts.models
                .flatMap { it.symbols }
                .filter {
                    it.declaration != null && it.declarationSource in texts
                }.mapNotNull { symbol -> facts.constants[symbol.id]?.let { it to symbol } }
                .toMap()
        val method = declarations[target.method]
        val methods =
            if (
                method != null &&
                (
                    method.name == "construct" ||
                        SemanticModel.Modifier.STATIC in method.modifiers
                )
            ) {
                setOf(target.method)
            } else {
                methodFamily(facts, target.method) ?: return null
            }
        // InvocationExpression.testFunction rejects named arguments on function values. Escaping
        // a selected method does not export parameter spellings; direct named calls and method
        // value bindings are still checked by the complete before/after graph proof.
        val parameters = methods.mapTo(linkedSetOf()) { ProofIdentity.Parameter(it, target.index) }
        return parameters.takeIf { declarations.keys.containsAll(it) }
    }

    private enum class Proof {
        COMPLETE,
        NAVIGATION,
        REPAIR,
    }

    private fun compile(
        text: Map<String, String>,
        proof: Proof = Proof.COMPLETE,
        moves: Map<String, String> = emptyMap(),
        graph: XdkProject = project,
        resourceMoves: XdkResourceMoves? = null,
    ): CompilerRenameFacts? {
        checkCurrent()
        if (proof == Proof.COMPLETE && sources.size != graph.modules.size) return null
        val artifacts = dependencies.modules.filterKeys { it !in project.modules }.toMutableMap()
        val attempts =
            graph
                .buildOrder()
                .mapNotNull { module ->
                    val source =
                        sources.entries
                            .firstOrNull {
                                (moves[it.key.root.path] ?: it.key.root.path) == module.root.path
                            }?.value ?: return@mapNotNull null
                    checkCurrent()
                    if (
                        module.dependencies.any {
                            it !in artifacts && it !in XdkLibraries.moduleNames
                        }
                    ) {
                        if (proof != Proof.COMPLETE) return@mapNotNull null
                        return null
                    }
                    // An earlier independent root is not a dependency. Reopening all prior
                    // artifacts
                    // both admits undeclared source imports and retains quadratic compiler state.
                    // Host binaries remain available, including their transitive binary
                    // dependencies.
                    val sourceDependencies =
                        graph.buildOrder(module.uri).mapTo(hashSetOf()) { it.name }
                    val inputs =
                        artifacts.filterKeys {
                            it !in graph.modules || it in sourceDependencies
                        }
                    val open = XdkDependencies(inputs.values.toList()).open()
                    val heard = ErrorList()
                    val errors = ErrorListener.cancellable(heard, cancelled)
                    val originalRoot =
                        sources.entries
                            .single { it.value === source }
                            .key.root
                    // A snapshot belongs to one root and its companion tree. Interacting moves
                    // must not leave its members outside that tree (nor invent cross-module ownership).
                    val boundary = File(module.root.parentFile, module.root.nameWithoutExtension).toPath()
                    if ((
                            source.inputs.text.keys
                                .filter { it != originalRoot } + source.inputs.directories
                        ).any {
                            !File(moves[it.path] ?: it.path).toPath().startsWith(boundary)
                        }
                    ) {
                        return null
                    }
                    val compilation =
                        try {
                            compileTree(
                                XdkSources.replay(originalRoot, source.inputs, text, moves, resourceMoves),
                                open.repository,
                                errors,
                            )
                        } catch (_: XdkResourceMoves.UnprovenResource) {
                            return null
                        }
                    checkCurrent()
                    if (
                        !compilation.succeeded() ||
                        heard.hasSeriousErrors() ||
                        compilation.file()?.module?.name != module.name
                    ) {
                        if (proof == Proof.REPAIR) {
                            val partial = compilation.renameFacts(open)
                            val fresh = XdkDependencies(inputs.values.toList()).open()
                            val declarationErrors =
                                ErrorListener.cancellable(ErrorList(), cancelled)
                            val declarations =
                                ExecutionTrace
                                    .api(
                                        "EmbeddingSupport.analyzeDeclarations(tree)",
                                        module.uri,
                                    ) {
                                        EmbeddingSupport
                                            .instance()
                                            .analyzeDeclarations(
                                                XdkSources.replay(
                                                    originalRoot,
                                                    source.inputs,
                                                    text,
                                                    moves,
                                                ),
                                                fresh.repository,
                                                declarationErrors,
                                            )
                                    }.orElse(null)
                            checkCurrent()
                            val headers = declarations?.memberActionFacts(fresh, declarationErrors)
                            val repaired =
                                if (
                                    headers != null &&
                                    !declarationErrors.hasSeriousErrors() &&
                                    !declarationErrors.isAbortDesired
                                ) {
                                    CompilerRenameFacts.merge(
                                        mapOf("headers" to headers, "partial" to partial),
                                    )
                                } else {
                                    partial
                                }
                            return@mapNotNull module.uri to repaired
                        }
                        if (proof == Proof.NAVIGATION) return@mapNotNull null
                        return null
                    }
                    val facts =
                        compilation.projectRenameFacts(
                            open,
                            errors,
                            includeMembers = proof == Proof.REPAIR,
                        )
                    if (heard.hasSeriousErrors() || errors.isAbortDesired) {
                        checkCurrent()
                        if (proof != Proof.COMPLETE) return@mapNotNull null
                        return null
                    }
                    artifacts[module.name] = compilation.toDependency()
                    module.uri to facts
                }.toMap()
        checkCurrent()
        return CompilerRenameFacts.merge(attempts)
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
                            XdkSources
                                .capture(
                                    module.root,
                                    overlays,
                                    module.resourceFiles,
                                    cancelled,
                                ).inputs
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
        declaration?.let { range ->
            declarationSource?.let { SemanticModel.SourceLocation(it, range) }
        }
}
