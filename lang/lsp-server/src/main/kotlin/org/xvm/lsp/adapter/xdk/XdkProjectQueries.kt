package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.ModuleRepository
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.CodeActionCompiles
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Worker-only whole-graph queries. Capture every configured module before compiling any of them.
 * Proof compilations never update the live diagnostics, source build cache or editor buffers. Only
 * detached locations/edits leave this object; compiler identities die with each query. One that answers
 * [fromCache] compiles nothing: it answers code actions from the [actionCache] alone, which any thread can.
 */
internal class XdkProjectQueries(
    private val project: XdkProject,
    private val overlays: Map<String, String>,
    private val dependencies: XdkDependencies,
    private val compiler: (ModuleInfo, ModuleRepository?, ErrorListener) -> EmbeddingSupport.Compilation,
    private val cancelled: () -> Boolean,
    private val discoverImports: Boolean = false,
    private val diagnosticCache: XdkDiagnosticIndex = XdkDiagnosticIndex(),
    private val navigationIndex: XdkNavigationIndex = XdkNavigationIndex(),
    private val actionCache: XdkCodeActionCache = XdkCodeActionCache(),
    // The folders discovery walks; a root moved outside them would leave the discovered graph.
    private val discoveryFolders: List<File> = emptyList(),
    private val fromCache: Boolean = false,
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

    private val compiled = AtomicInteger()

    /** The module compiles that this query ran so far, which the server's log reports. */
    val compiles: Int get() = compiled.get()

    private fun compileTree(
        input: ModuleInfo,
        repository: ModuleRepository?,
        errors: ErrorListener,
    ): EmbeddingSupport.Compilation = compiler(input, repository, errors).also { compiled.incrementAndGet() }

    /**
     * The diagnostics last reported, when the graph's inputs are those they were made from, or null: a check that
     * reads the inputs, with no compile, lexing or parsing.
     */
    fun reported(): List<CompilationResult>? = diagnosticCache.snapshot().takeIf { it.revision == revision() }?.results

    /** Read-only graph compilation, with no live AST installation or artificial open buffers. */
    fun diagnostics(): List<CompilationResult> {
        val revision = revision()
        val previous = diagnosticCache.snapshot()
        val previousNavigation = navigationIndex.snapshot()
        val navigationBuilds = previousNavigation.builds.filterKeys { uri -> project.modules.values.any { it.uri == uri } }.toMutableMap()
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
                                        Diagnostic.Codes.SOURCE_UNAVAILABLE
                                    } else {
                                        Diagnostic.Codes.DEPENDENCY_FAILED
                                    },
                                source = Diagnostic.SOURCE,
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
                                val compilation = compileTree(source.freshCompilationInput(), open.repository, errors)
                                checkCurrent()
                                val semantics =
                                    if (compilation.succeeded() && !heard.hasSeriousErrors()) {
                                        compilation.compiledSemantics(open, errors, navigation = true)
                                    } else {
                                        null
                                    }
                                val navigation = semantics?.navigation
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
                                val artifact = semantics?.artifact
                                val items =
                                    if (artifact != null && artifact.module != module.name) {
                                        listOf(
                                            Diagnostic(
                                                location = Location(uri, 0, 0, 0, 0),
                                                severity = Diagnostic.Severity.ERROR,
                                                message =
                                                    "Expected module ${module.name}, found ${artifact.module}",
                                                code = Diagnostic.Codes.PROJECT_MODULE,
                                                source = Diagnostic.SOURCE,
                                            ),
                                        )
                                    } else {
                                        diagnostics
                                    }
                                val validArtifact = artifact?.takeIf { it.module == module.name }
                                navigationBuilds[module.uri] =
                                    XdkNavigationIndex.Build(
                                        XdkNavigationIndex.Key(module.name, source.inputs, inputs.mapValues { it.value.revision }),
                                        navigation?.takeIf { validArtifact != null },
                                        validArtifact,
                                    )
                                XdkDiagnosticIndex.Build(
                                    key,
                                    CompilationResult.withDiagnostics(
                                        uri,
                                        items,
                                        emptyList(),
                                        source.documentUris,
                                    ),
                                    artifact?.takeIf { it.module == module.name },
                                    // the module's tests, which the editors list without opening its files
                                    XdkTests.of(compilation.sourceTrees(), declarations),
                                )
                            }
                    builds[module.uri] = build
                    build.artifact?.let { artifacts[module.name] = it }
                    build.result
                }
            }
        if (!isCurrent()) throw CancellationException()
        diagnosticCache.replace(XdkDiagnosticIndex.Snapshot(revision, results, builds.toMap()))
        navigationIndex.publish(previousNavigation, navigationBuilds)
        return results
    }

    fun navigation(): XdkWorkspaceNavigation? {
        val revision = revision()
        val previous = navigationIndex.snapshot()
        previous.navigation[revision]?.let {
            return if (isCurrent()) it else null
        }
        val builds = navigationBuilds(previous) ?: return null
        val models = builds.values.flatMap { it.facts?.models.orEmpty() }
        val constants =
            builds.values
                .flatMap {
                    it.facts
                        ?.constants
                        .orEmpty()
                        .entries
                }.associate { it.toPair() }
        val declared = models.mapNotNull { model -> model.sourceName?.let { it to model.id } }.toMap()
        val aliases =
            models
                .flatMap { it.symbols }
                .distinctBy { it.id }
                .groupBy { symbol -> constants[symbol.id] ?: symbol.location() ?: symbol.id }
                .values
                .flatMap { group ->
                    val canonical =
                        group.firstOrNull { declared[it.declarationSource] == it.id.snapshot }
                            ?: group.first()
                    group.map { it.id to canonical.id }
                }.toMap()
        // Implicit package directories contribute synthetic, unnamed source views. Merge their
        // shared semantic facts, but only real source files can be workspace navigation targets.
        val views =
            SemanticModel
                .joined(models, aliases)
                .mapNotNull { model ->
                    model.sourceName?.let { uris.getValue(it) to model }
                }.toMap()
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
            navigationIndex.publish(previous, builds, mapOf(revision to it))
        }
    }

    /** Navigation may reuse exact detached builds; edit/refactoring proofs below always compile. */
    private fun navigationBuilds(previous: XdkNavigationIndex.Snapshot): Map<String, XdkNavigationIndex.Build>? {
        val artifacts = dependencies.modules.filterKeys { it !in project.modules }.toMutableMap()
        val builds =
            buildMap {
                project.buildOrder().forEach { module ->
                    checkCurrent()
                    val source = sources[module] ?: return@forEach
                    if (module.dependencies.any { it !in artifacts && it !in XdkLibraries.moduleNames }) return@forEach
                    val closure = project.buildOrder(module.uri).mapTo(hashSetOf()) { it.name }
                    val inputs = artifacts.filterKeys { it !in project.modules || it in closure }
                    val key = XdkNavigationIndex.Key(module.name, source.inputs, inputs.mapValues { it.value.revision })
                    val build =
                        previous.builds[module.uri]?.takeIf { it.key == key } ?: run {
                            val open = XdkDependencies(inputs.values.toList()).open()
                            val heard = ErrorList()
                            val errors = ErrorListener.cancellable(heard, cancelled)
                            val compilation = compileTree(source.freshCompilationInput(), open.repository, errors)
                            checkCurrent()
                            val semantics =
                                if (compilation.succeeded() && !heard.hasSeriousErrors() &&
                                    compilation.file()?.module?.name == module.name
                                ) {
                                    compilation.compiledSemantics(open, errors, navigation = true)
                                } else {
                                    null
                                }
                            checkCurrent()
                            if (semantics != null && !heard.hasSeriousErrors()) {
                                XdkNavigationIndex.Build(key, semantics.navigation, semantics.artifact)
                            } else {
                                XdkNavigationIndex.Build(key, null, null)
                            }
                        }
                    put(module.uri, build)
                    build.artifact?.let { artifacts[module.name] = it }
                }
            }
        if (!isCurrent()) return null
        return builds
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

    fun referenceLenses(uri: String) = navigation()?.referenceLenses(uri).orEmpty()

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
        val occurrence = model.occurrenceAt(line, column) ?: return null
        val target =
            before.callables[SemanticModel.SourceLocation(source, occurrence.range)] ?: before.constants[selected.id] ?: return null
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
                target is ProofIdentity.Parameter -> parameterFamily(before, target) ?: return null

                symbol.kind in
                    setOf(
                        SemanticModel.SymbolKind.TYPE,
                        SemanticModel.SymbolKind.PACKAGE,
                        SemanticModel.SymbolKind.MODULE,
                    ) -> setOf(target)

                symbol.kind in
                    setOf(SemanticModel.SymbolKind.METHOD, SemanticModel.SymbolKind.PROPERTY) &&
                    SemanticModel.Modifier.STATIC in symbol.modifiers &&
                    symbol.name != "construct" -> setOf(target)

                symbol.kind == SemanticModel.SymbolKind.PROPERTY -> propertyFamily(before, target) ?: return null

                symbol.kind == SemanticModel.SymbolKind.METHOD -> methodFamily(before, target) ?: return null

                else -> return null
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
        val inputOperations =
            requested.entries
                .associate { (from, to) ->
                    (XdkSources.file(from) ?: return null) to (XdkSources.file(to) ?: return null)
                }.filter { (from, to) -> from != to }
        val operations = XdkMoveOperations.ordered(inputOperations) ?: return null
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
        val before = compile(texts) ?: return null
        val directories = sources.values.flatMap { it.inputs.directories }.toSet()
        val rootOperations =
            operations.filter { (from, to) ->
                from.parentFile != to.parentFile && project.modules.values.any { it.root == from }
            }
        val rootRenames =
            rootOperations.map { (from, to) ->
                if (to.extension != "x") return null
                XdkRename.fileNamePlan(before, texts, from.path, from.nameWithoutExtension, to.nameWithoutExtension) ?: return null
            }
        val rootMoves =
            rootOperations.map { (from, to) ->
                // Discovery persists no resource roots and finds a root only under its folders. A moved root must use
                // no resources and stay where the walk finds it, so that the proof's graph is the one discovery builds.
                if (discoverImports) {
                    val module = project.modules.values.first { it.root == from }
                    if (sources[module]
                            ?.inputs
                            ?.text
                            ?.values
                            ?.any(XdkLexical::mayUseResources) != false ||
                        !XdkWorkspaceDiscovery.includes(discoveryFolders, to)
                    ) {
                        return null
                    }
                }
                XdkSourceMoves.plan(from, to, texts, directories) ?: return null
            }
        val typeOperations =
            operations
                .filter { (from, to) ->
                    from.isFile && from.parentFile != to.parentFile && from.path in texts &&
                        project.modules.values.none { it.root == from }
                }
        val typeMove =
            XdkTypeMoves.plan(before, typeOperations, texts, directories, project) ?: return null
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

                                        else -> null
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
                (listOf(typeMove.resources) + rootMoves.map { it.resources })
                    .flatMap { it.entries }
                    .map { File(it.key) to File(it.value) }
        if (allMoves.groupBy({ it.first }, { it.second }).values.any { it.distinct().size > 1 }) {
            return null
        }
        val resources = XdkMoveOperations.ordered(allMoves.toMap()) ?: return null
        val movedInputs = XdkMoveInputs.capture(resources.keys, cancelled) ?: return null

        // A destination module may acquire its first resource expression with an incoming type.
        // Capture its configured lookup roots too, even when its original sources used none.
        fun captureResources(): Map<XdkSourceModule, XdkResources>? =
            try {
                project.modules.values.associateWith { module ->
                    XdkResources.capture(module.root, module.resourceFiles, texts.values, cancelled)
                }
            } catch (_: IOException) {
                null
            }
        val resourceSnapshots = captureResources() ?: return null
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
        if (names.values.distinct().size != names.size ||
            names.any { (before, after) -> before != after && after in XdkLibraries.moduleNames }
        ) {
            return null
        }
        val graph =
            try {
                XdkProject(
                    project.buildOrder().map { module ->
                        XdkSourceModule(
                            names.getValue(module.name),
                            File(paths[module.root.path] ?: module.root.path).toURI().toString(),
                            (module.dependencies + typeMove.dependencies[module.name].orEmpty()).mapTo(linkedSetOf()) { names[it] ?: it },
                            (
                                module.resourceFiles ?: sources[module]?.inputs?.resources?.roots?.takeIf {
                                    // a configured graph keeps a moved root's resource roots; discovery deduces them again
                                    !discoverImports &&
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
            } catch (_: IllegalArgumentException) {
                return null
            }
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

        fun planned(move: XdkTypeMoves.Proposal): XdkRename.Plan? {
            val edits =
                (renamed.entries + rootRenames.flatMap { it.edits.entries } + move.edits.entries)
                    .groupBy({ it.key }, { it.value })
                    .mapValues { (_, edits) -> edits.flatten().distinct().sortedWith(compareBy({ it.start }, { it.end })) }
            if (edits.values.any { !XdkRename.disjoint(it) }) return null
            return XdkRename.Plan(texts, edits, paths, qualifications = move.qualifications, imports = move.imports)
        }

        fun compiled(
            plan: XdkRename.Plan,
            failed: (List<ErrorListener.ErrorInfo>) -> Unit = {},
        ): CompilerRenameFacts? {
            val resourceMoves =
                XdkResourceMoves(resources.mapKeys { it.key.path }.mapValues { it.value.path }, plan.edits.keys, movedInputs.entries)
            return compile(
                plan.proposed,
                moves = paths,
                graph = graph,
                resourceMoves = resourceMoves,
                resourceSnapshots = resourceSnapshots,
                failed = failed,
            )
        }

        // A qualifier's first name can be a property or a local where a reference stands, which hides the package it
        // should name, and the compiler rejects the reference. Such a file imports the moved type instead of
        // qualifying it, and the whole move is proven again, until it compiles or no further file needs the import.
        fun proven(importing: Set<String>): Triple<XdkTypeMoves.Proposal, XdkRename.Plan, CompilerRenameFacts>? {
            val move =
                if (importing.isEmpty()) typeMove else XdkTypeMoves.plan(before, typeOperations, texts, directories, project, importing)
            val plan = move?.let(::planned) ?: return null
            val errors = mutableListOf<ErrorListener.ErrorInfo>()
            compiled(plan, errors::addAll)?.let { return Triple(move, plan, it) }
            val shadowed = importing + XdkTypeMoves.shadowed(move, plan, errors)
            return if (shadowed.size > importing.size) proven(shadowed) else null
        }
        val (move, plan, after) = proven(emptySet()) ?: return null
        val edits = plan.edits
        if (!move.provesDestinations(after) || !preservesBindings(before, after, plan, graph) ||
            !isCurrent() || !movedInputs.isCurrent(cancelled) ||
            resourceSnapshots != captureResources()
        ) {
            return null
        }
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
        graph: XdkProject = project,
    ): Boolean {
        // Compilation still checks the complete graph. Binding/dispatch equivalence is needed
        // for every edited module and its transitive consumers; independent roots have identical
        // inputs and no path to an edited declaration. Unsupported dynamic bindings in those
        // unrelated roots must not veto an otherwise proven edit.
        val affected =
            (plan.edits.keys + plan.moves.keys)
                .mapNotNull { project.scope(it) }
                .flatMapTo(linkedSetOf(), project::affected) +
                plan.moves.values
                    .mapNotNull { graph.scope(it) }
                    .flatMap(graph::affected)
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

    /** A code action request, by what its answer depends on in one revision. */
    private data class ActionRequest(
        val uri: String,
        val range: Range,
        val diagnostics: List<Diagnostic>,
        val kinds: Set<CodeActionKind>,
    )

    /**
     * The code actions that [codeActions] answers, from the cache alone, and whether they are all of them: the
     * revision's answer to the same request, or else those that its cached compile and proofs prove, which can be
     * none.
     */
    fun cachedCodeActions(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic>,
        kinds: Set<CodeActionKind>,
    ): Pair<List<CodeAction>, Boolean> {
        check(fromCache) { "A query that compiles answers in full" }
        actionCache.answer(revision(), ActionRequest(uri, range, diagnostics, kinds))?.let { return it to true }
        return codeActions(uri, range, diagnostics, kinds) to false
    }

    /**
     * Candidate syntax is insufficient: every edit must compile and preserve all existing bindings. A quick
     * fix names the request's [diagnostics] it resolves, those overlapping the code it repairs, so editors
     * can offer it on the problem itself. The answer is kept for the revision's next request of the same. [compiled]
     * gets the module compiles it ran, also when it is cancelled.
     */
    fun codeActions(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic> = emptyList(),
        kinds: Set<CodeActionKind> = CodeActionKind.entries.toSet(),
        compiled: (CodeActionCompiles) -> Unit = {},
    ): List<CodeAction> {
        val start = compiles
        // the compiles of the revision's compile, when this request made it, rather than found it in the cache
        val made = AtomicReference<Int?>()
        try {
            return answer(uri, range, diagnostics, kinds, made)
        } finally {
            val base = made.get()
            compiled(CodeActionCompiles(base ?: 0, compiles - start - (base ?: 0), base == null))
        }
    }

    private fun answer(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic>,
        kinds: Set<CodeActionKind>,
        made: AtomicReference<Int?>,
    ): List<CodeAction> {
        fun resolved(target: SemanticModel.Range) =
            diagnostics.filter {
                val at = it.location
                SemanticModel.Position(at.endLine, at.endColumn) >= target.start &&
                    SemanticModel.Position(at.startLine, at.startColumn) <= target.end
            }
        val source = XdkSources.file(uri)?.path ?: return emptyList()
        val text = texts[source] ?: return emptyList()
        // The requests of one revision share its compile and its import cleanup. A request cancelled or replaced
        // meanwhile keeps nothing: its compiles may have stopped short, and checkCurrent() throws before it is kept.
        val revision = revision()
        val base =
            if (fromCache) {
                actionCache.base(revision)
            } else {
                actionCache.base(revision) { since ->
                    val start = compiles
                    try {
                        build(texts, Proof.REPAIR, since = since)?.also { checkCurrent() }
                    } finally {
                        made.set(compiles - start)
                    }
                }
            } ?: return emptyList()
        val before = base.facts
        val complete =
            before.models.all { it.status == SemanticModel.Status.COMPLETE } &&
                project.modules.values.all { module ->
                    before.models.any { it.sourceName == module.root.path }
                }
        val quickFixes = CodeActionKind.QUICKFIX in kinds
        val actions =
            if (!complete) {
                if (quickFixes) autoImports(uri, source, text, range, before, revision, ::resolved) else emptyList()
            } else if (CodeActionKind.SOURCE !in kinds && CodeActionKind.SOURCE_ORGANIZE_IMPORTS !in kinds) {
                emptyList()
            } else if (fromCache) {
                actionCache.imports(revision, source).orEmpty().filter { it.kind in kinds }
            } else {
                // Both kinds come from the same removal proofs, so a revision proves the cleanup once, for both.
                actionCache
                    .imports(revision, source) {
                        fun proved(candidate: XdkImports.Candidate): CodeAction? {
                            checkCurrent()
                            val plan = XdkRename.Plan(texts, mapOf(source to candidate.edits))
                            val after = compile(plan.proposed, reuse = base) ?: return null
                            if (!XdkRename.preservesBindings(before, after, plan)) return null
                            return CodeAction(
                                candidate.title,
                                candidate.kind,
                                edit =
                                    WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                            )
                        }
                        val imports = XdkImports.imports(text)
                        val removals =
                            XdkImports.removals(imports).mapNotNull { (import, candidate) ->
                                proved(candidate)?.let { import to it }
                            }
                        // Organize imports removes every import proved unused on its own and sorts the rest;
                        // when the removals do not prove together, it only sorts.
                        val unused = removals.map { it.first }.toSet()
                        val organized =
                            XdkImports.organize(text, imports, unused)?.let(::proved)
                                ?: if (unused.isEmpty()) null else XdkImports.organize(text, imports, emptySet())?.let(::proved)
                        (listOfNotNull(organized) + removals.map { it.second }).also { checkCurrent() }
                    }.filter { it.kind in kinds }
            }
        val missing =
            if (complete || !quickFixes) {
                emptyList()
            } else {
                before.missingMethods
                    .filter { it.selected(source, range) }
                    .distinctBy { Triple(it.destination, it.declaration, it.insertion) }
                    .take(8)
                    .mapNotNull { candidate ->
                        checkCurrent()
                        val destination = candidate.destination.sourceName ?: return@mapNotNull null
                        val targetText = texts[destination] ?: return@mapNotNull null
                        if (!Files.isWritable(File(destination).toPath())) return@mapNotNull null
                        val importSource = candidate.importSource ?: destination
                        val importText = texts[importSource] ?: return@mapNotNull null
                        val edit = candidate.edit(targetText, importText) ?: return@mapNotNull null
                        val changes =
                            listOf(destination to listOf(edit.member), importSource to edit.imports)
                                .filter { it.second.isNotEmpty() }
                                .groupBy({ it.first }, { it.second })
                                .mapValues { (_, edits) -> edits.flatten() }
                        if (changes.keys.any { it !in uris || !Files.isWritable(File(it).toPath()) }) return@mapNotNull null
                        val plan = XdkRename.Plan(texts, changes)
                        val after = shared(revision, plan.proposed) ?: return@mapNotNull null
                        if (!XdkRename.preservesKnownBindings(before, after, plan) || !candidate.bindsNewMethod(after, plan, edit.member)) {
                            return@mapNotNull null
                        }
                        CodeAction(
                            candidate.title,
                            CodeAction.CodeActionKind.QUICKFIX,
                            diagnostics = resolved(candidate.callee.range),
                            edit = WorkspaceEdit(changes.keys.associate { uris.getValue(it) to plan.textEdits(it) }, versioned = true),
                        )
                    }
            }
        val declarations =
            if (complete || !quickFixes) {
                emptyList()
            } else {
                // Header and body attempts to have separate snapshot identities for the same source.
                before.models
                    .filter { it.sourceName == source }
                    .flatMap { model -> XdkMissingDeclarations.candidates(text, range, model, before) }
                    .distinct()
                    .mapNotNull { candidate ->
                        checkCurrent()
                        if (!Files.isWritable(File(source).toPath())) return@mapNotNull null
                        val plan = XdkRename.Plan(texts, mapOf(source to listOf(candidate.edit)))
                        val after = shared(revision, plan.proposed, Proof.REPAIR) ?: return@mapNotNull null
                        if (!XdkRename.preservesKnownBindings(before, after, plan) || !candidate.proves(after, plan)) {
                            return@mapNotNull null
                        }
                        CodeAction(
                            candidate.title,
                            CodeAction.CodeActionKind.QUICKFIX,
                            diagnostics = resolved(candidate.use.range),
                            edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                        )
                    }
            }
        val rewrites = CodeActionKind.REFACTOR_REWRITE in kinds
        val extracts = CodeActionKind.REFACTOR_EXTRACT in kinds
        val inlines = CodeActionKind.REFACTOR_INLINE in kinds
        val members =
            XdkMemberActions.actions(before.memberActions.takeIf { rewrites }.orEmpty(), source, range).mapNotNull { candidate ->
                checkCurrent()
                val edit = candidate.edit(text) ?: return@mapNotNull null
                val plan = XdkRename.Plan(texts, mapOf(source to edit.all))
                val after = shared(revision, plan.proposed) ?: return@mapNotNull null
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
            if (complete && extracts) {
                XdkLocalExtraction.candidate(text, range)?.let { candidate ->
                    checkCurrent()
                    val plan =
                        XdkRename.Plan(
                            texts,
                            mapOf(source to candidate.edits),
                            relocations = mapOf(source to listOf(candidate.relocation)),
                        )
                    val after = shared(revision, plan.proposed) ?: return@let null
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
            if (complete && inlines) {
                before.models.singleOrNull { it.sourceName == source }?.let { model ->
                    XdkLocalInline.candidate(text, range, model, before)?.let { candidate ->
                        checkCurrent()
                        val plan =
                            XdkRename.Plan(
                                texts,
                                mapOf(source to candidate.edits),
                                relocations = mapOf(source to listOf(candidate.relocation)),
                            )
                        val after = shared(revision, plan.proposed, Proof.REPAIR) ?: return@let null
                        if (candidate.expression != null &&
                            !XdkRename.preservesRelocatedType(before, after, plan, candidate.expression)
                        ) {
                            return@let null
                        }
                        if (!XdkRename.preservesLocalRemoval(before, after, plan, candidate.removed)) return@let null
                        CodeAction(
                            candidate.title,
                            CodeAction.CodeActionKind.REFACTOR_INLINE,
                            edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                        )
                    }
                }
            } else {
                null
            }
        val memberInline =
            if (complete && inlines) {
                before.models.singleOrNull { it.sourceName == source }?.let { model ->
                    XdkMemberInline.candidate(text, range, model, before)?.let { candidate ->
                        checkCurrent()
                        val plan = XdkRename.Plan(texts, mapOf(source to listOf(candidate.edit)))
                        val after = shared(revision, plan.proposed, Proof.REPAIR) ?: return@let null
                        if (!XdkRename.preservesMemberInline(before, after, plan, candidate)) return@let null
                        CodeAction(
                            candidate.title,
                            CodeAction.CodeActionKind.REFACTOR_INLINE,
                            edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                        )
                    }
                }
            } else {
                null
            }
        val memberRemoval =
            if (complete && inlines) {
                before.models.singleOrNull { it.sourceName == source }?.let { model ->
                    XdkMemberInline.everyUse(text, range, model, before)?.let { candidate ->
                        checkCurrent()
                        val plan = XdkRename.Plan(texts, mapOf(source to candidate.edits))
                        val after = shared(revision, plan.proposed, Proof.REPAIR) ?: return@let null
                        if (!XdkRename.preservesMemberRemoval(before, after, plan, candidate)) return@let null
                        CodeAction(
                            candidate.title,
                            CodeAction.CodeActionKind.REFACTOR_INLINE,
                            edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                        )
                    }
                }
            } else {
                null
            }
        val removal =
            if (complete && rewrites) {
                before.models.singleOrNull { it.sourceName == source }?.let { model ->
                    XdkLocalRemoval.candidate(text, range, model, before.removableLocals)?.let { candidate ->
                        checkCurrent()
                        val plan = XdkRename.Plan(texts, mapOf(source to listOf(candidate.edit)))
                        val after = shared(revision, plan.proposed) ?: return@let null
                        if (!XdkRename.preservesLocalRemoval(before, after, plan, candidate.removed)) return@let null
                        CodeAction(
                            "Remove unused local variable",
                            CodeAction.CodeActionKind.REFACTOR_REWRITE,
                            edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                        )
                    }
                }
            } else {
                null
            }
        val safeDelete =
            if (complete && rewrites) {
                before.models.singleOrNull { it.sourceName == source }?.let { model ->
                    XdkSafeDelete.candidate(text, range, model, before)?.let { candidate ->
                        checkCurrent()
                        val plan = XdkRename.Plan(texts, mapOf(source to listOf(candidate.edit)))
                        val after = shared(revision, plan.proposed) ?: return@let null
                        if (!XdkRename.preservesSafeDelete(before, after, plan, candidate)) return@let null
                        // A public member's consumers are the configured modules', which the title names.
                        val modules = project.modules.keys.sorted()
                        CodeAction(
                            if (candidate.private) {
                                "Safely delete private member '${candidate.name}'"
                            } else {
                                "Safely delete member '${candidate.name}', unused in " +
                                    modules.dropLast(1).joinToString(", ").ifEmpty { null }.let { first ->
                                        if (first == null) modules.last() else "$first and ${modules.last()}"
                                    }
                            },
                            CodeAction.CodeActionKind.REFACTOR_REWRITE,
                            edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                        )
                    }
                }
            } else {
                null
            }
        val method =
            if (complete && extracts) {
                before.models.singleOrNull { it.sourceName == source }?.let { model ->
                    XdkMethodExtraction.candidate(text, range, model, before.extraction)?.let { candidate ->
                        checkCurrent()
                        val plan =
                            XdkRename.Plan(
                                texts,
                                mapOf(source to candidate.edits),
                                relocations = mapOf(source to listOf(candidate.relocation)),
                            )
                        val after = shared(revision, plan.proposed, Proof.REPAIR) ?: return@let null
                        if (!XdkRename.preservesMethodExtraction(before, after, plan, candidate)) return@let null
                        CodeAction(
                            if (candidate.statements) "Extract statements to private method" else "Extract expression to private method",
                            CodeAction.CodeActionKind.REFACTOR_EXTRACT,
                            edit = WorkspaceEdit(mapOf(uri to plan.textEdits(source)), versioned = true),
                        )
                    }
                }
            } else {
                null
            }
        if (!isCurrent()) return emptyList()
        val answer =
            before.models
                .filter { CodeActionKind.SOURCE in kinds && it.sourceName == source && Files.isWritable(File(source).toPath()) }
                .flatMap { XdkDocumentation.actions(uri, text, range, it) }
                .distinct() +
                actions + missing + declarations + members +
                listOfNotNull(
                    extraction,
                    inline,
                    memberInline,
                    memberRemoval,
                    removal,
                    safeDelete,
                    method,
                )
        if (!fromCache) actionCache.answered(revision, ActionRequest(uri, range, diagnostics, kinds), answer)
        return answer
    }

    /**
     * A code action's proof compile of [proposed], which the requests of one [revision] share: editors ask on every
     * cursor move, and a quick fix's request and the intention list's prove the same candidate. A request cancelled or
     * replaced meanwhile keeps nothing, as its compile may have stopped short; checkCurrent() throws before it is kept.
     * It compiles only the modules that [proposed] changes and those that depend on them, with the revision's compile of
     * the others.
     */
    private fun shared(
        revision: String,
        proposed: Map<String, String>,
        proof: Proof = Proof.COMPLETE,
    ): CompilerRenameFacts? =
        if (fromCache) {
            actionCache.compiled(revision, proof to proposed)
        } else {
            actionCache.compiled(revision, proof to proposed) {
                compile(proposed, proof, reuse = actionCache.base(revision)).also { checkCurrent() }
            }
        }

    private fun autoImports(
        uri: String,
        source: String,
        text: String,
        range: Range,
        before: CompilerRenameFacts,
        revision: String,
        resolved: (SemanticModel.Range) -> List<Diagnostic>,
    ): List<CodeAction> {
        val models = before.models.filter { it.sourceName == source }
        val start = SemanticModel.Position(range.start.line, range.start.column)
        val end = SemanticModel.Position(range.end.line, range.end.column)
        val unresolved =
            models
                .flatMap { it.occurrences }
                .filter { it.symbol == null && it.range.start <= end && it.range.end >= start }
        val names = unresolved.map { it.name }.distinct()
        val owner =
            project.modules.values.singleOrNull { it.uri == project.scope(uri) }
                ?: return emptyList()
        val packages = before.within(setOf(owner.uri)).packageImports
        val actions =
            names
                .flatMap { XdkAutoImports.targets(it, before) }
                .distinct()
                .take(32)
                .mapNotNull { target ->
                    checkCurrent()
                    val graph = importGraph(owner, target) ?: return@mapNotNull null
                    val edit =
                        XdkAutoImports.edit(text, owner.name, target, packages) ?: return@mapNotNull null
                    val plan = XdkRename.Plan(texts, mapOf(source to listOf(edit)))
                    val after =
                        (
                            when {
                                graph === project -> shared(revision, plan.proposed)
                                fromCache -> null
                                else -> compile(plan.proposed, graph = graph)
                            }
                        )
                            ?: return@mapNotNull null
                    if (!XdkRename.preservesKnownBindings(before, after, plan)) {
                        return@mapNotNull null
                    }
                    CodeAction(
                        "Import '${target.path}' from ${target.module}",
                        CodeAction.CodeActionKind.QUICKFIX,
                        diagnostics =
                            unresolved
                                .filter { it.name == target.name }
                                .flatMap { resolved(it.range) }
                                .distinct(),
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
        // A name typed as a statement of its own, before the rest of the statement, leaves code that no completion of
        // it makes compile. The proof is then the code without the name: it compiles, and it compiles with the same
        // bindings once the import is added. Nothing in scope at the name declares the target's name, or the cursor
        // would complete it, so there the name is the import's.
        val vacated = texts + (source to text.removeRange(start, end))
        val base = if (XdkAutoImports.parses(vacated.getValue(source))) compile(vacated) else null
        val before = base ?: compile(texts, Proof.REPAIR) ?: return emptyList()
        val packages = before.within(setOf(owner.uri)).packageImports
        val items =
            XdkAutoImports
                .matchingTargets(prefix.text, before)
                .filter { it.name !in visible }
                .take(8)
                .mapNotNull { target ->
                    checkCurrent()
                    val graph = importGraph(owner, target) ?: return@mapNotNull null
                    val imported =
                        XdkAutoImports.edit((if (base == null) texts else vacated).getValue(source), owner.name, target, packages)
                            ?: return@mapNotNull null
                    // LSP requires additional edits to be disjoint from the replacement edit; an import made in the code
                    // without the name is at the same offset in the code with it only when it comes before the name.
                    val disjoint = imported.start < start || (base == null && imported.start > end)
                    if (!disjoint) return@mapNotNull null
                    val replacement = XdkRename.Edit(start, end, target.name)
                    val plan = XdkRename.Plan(texts, mapOf(source to listOf(replacement, imported)))
                    val proof = if (base == null) plan else XdkRename.Plan(vacated, mapOf(source to listOf(imported)))
                    val after = compile(proof.proposed, graph = graph) ?: return@mapNotNull null
                    if (!XdkRename.preservesKnownBindings(before, after, proof)) return@mapNotNull null
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
            (facts.constants.values + facts.callables.values)
                .filterIsInstance<ProofIdentity.Alternatives>()
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
            is ProofIdentity.Alternatives -> identity.targets.flatMapTo(linkedSetOf(), ::members)

            is ProofIdentity.Composed -> identity.members.flatMapTo(linkedSetOf(), ::members)

            is ProofIdentity.Parameter -> {
                members(identity.method).mapTo(linkedSetOf()) {
                    ProofIdentity.Parameter(it, identity.index)
                }
            }

            else -> setOf(identity)
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

    /** Whether a proposed edit must preserve a complete compilation or repair an already incomplete one. */
    private enum class Proof {
        COMPLETE,
        REPAIR,
    }

    private fun compile(
        text: Map<String, String>,
        proof: Proof = Proof.COMPLETE,
        moves: Map<String, String> = emptyMap(),
        graph: XdkProject = project,
        resourceMoves: XdkResourceMoves? = null,
        resourceSnapshots: Map<XdkSourceModule, XdkResources> = emptyMap(),
        failed: (List<ErrorListener.ErrorInfo>) -> Unit = {},
        reuse: XdkCodeActionCache.Base? = null,
    ): CompilerRenameFacts? = build(text, proof, moves, graph, resourceMoves, resourceSnapshots, failed, reuse)?.facts

    /**
     * The compile of [text], and what a proof, or the next revision's compile, needs of it to compile only some modules.
     * With [reuse], the revision's compile of this query's own sources, it compiles only the modules whose sources [text]
     * changes and the modules that depend on them: the others have the inputs they had, so they keep their facts and
     * artifacts, and a module that did not compile then does not now. With [since], an earlier revision's compile, the
     * revision's own compile of its sources does the same for the modules whose sources changed since, unless it cannot
     * be sure of the rest ([changedSince]). A move, another graph or resources of their own compile it all.
     */
    private fun build(
        text: Map<String, String>,
        proof: Proof = Proof.COMPLETE,
        moves: Map<String, String> = emptyMap(),
        graph: XdkProject = project,
        resourceMoves: XdkResourceMoves? = null,
        resourceSnapshots: Map<XdkSourceModule, XdkResources> = emptyMap(),
        failed: (List<ErrorListener.ErrorInfo>) -> Unit = {},
        reuse: XdkCodeActionCache.Base? = null,
        since: XdkCodeActionCache.Base? = null,
    ): XdkCodeActionCache.Base? {
        checkCurrent()
        if (proof == Proof.COMPLETE && sources.size != graph.modules.size) return null
        val artifacts = dependencies.modules.filterKeys { it !in project.modules }.toMutableMap()
        val missingDestinations = mutableMapOf<String, Map<SemanticModel.SourceLocation, XdkMissingMethods.Destination>>()
        val reported = mutableMapOf<String, List<String>>()
        val inputs = sources.entries.associate { (module, source) -> module.name to source.inputs }
        val revisions = dependencies.modules.mapValues { it.value.revision }
        val plain = moves.isEmpty() && graph === project && resourceMoves == null && resourceSnapshots.isEmpty()
        val prior = if (reuse != null) reuse.takeIf { plain } else since?.takeIf { plain && proof == Proof.REPAIR && text === texts }
        // the modules whose own sources changed, by root URI
        val changed =
            when {
                prior == null -> null

                prior === reuse ->
                    (text.keys + texts.keys)
                        .filter { path -> text[path] != texts[path] }
                        .mapNotNullTo(hashSetOf()) { path -> project.scope(File(path)) }

                else -> changedSince(prior, inputs, revisions)
            }
        val edited = changed?.flatMapTo(hashSetOf(), project::affected)
        // as a module's compile rejects a source that no module of the graph holds
        if (edited != null && text.keys.any { graph.scope(it) == null }) return null
        // the edited modules, by name, that a module depending on them compiles against as it did
        val alike = hashSetOf<String>()
        // the edited modules whose private declarations alone moved, which only a module that did not compile names
        val privatelyMoved = hashSetOf<String>()

        // A module whose own sources are unchanged, and whose edited dependencies all compiled to an artifact with
        // the API they had, compiles as it did: it keeps its compile, unless its facts name one of them by revision.
        // A module that compiled keeps it too when only their private declarations moved, as it cannot name them.
        fun unchangedBy(module: XdkSourceModule): Boolean {
            val upstream = graph.buildOrder(module.uri).filter { it.uri != module.uri && it.uri in edited.orEmpty() }
            return prior != null &&
                changed != null &&
                module.uri !in changed &&
                upstream.all { it.name in alike || (it.name in privatelyMoved && module.name in prior.artifacts) } &&
                prior.facts
                    .module(module.uri)
                    ?.binaryModules
                    ?.none { name -> upstream.any { it.name == name } } == true
        }
        val attempts =
            graph
                .buildOrder()
                .mapNotNull { module ->
                    if (prior != null && edited != null && (module.uri !in edited || unchangedBy(module))) {
                        if (module.uri in edited) alike += module.name
                        checkCurrent()
                        val artifact = prior.artifacts[module.name]
                        if (artifact == null && proof == Proof.COMPLETE) return null
                        artifact?.let { artifacts[module.name] = it }
                        prior.destinations[module.name]?.let { missingDestinations[module.name] = it }
                        prior.errors[module.name]?.let { reported[module.name] = it }
                        return@mapNotNull prior.facts.module(module.uri)?.let { module.uri to it }
                    }
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
                    val destinations =
                        missingDestinations
                            .filterKeys(sourceDependencies::contains)
                            .values
                            .flatMap { it.entries }
                            .associate { it.toPair() }
                    val open = XdkDependencies(inputs.values.toList()).open()
                    val heard = ErrorList()
                    val errors = ErrorListener.cancellable(heard, cancelled)
                    val originalRoot =
                        sources.entries
                            .single { it.value === source }
                            .key.root
                    // Reassign captured source members to their proposed module, never to a
                    // guessed filesystem owner. Each module still uses its own resource policy.
                    if (text.keys.any { graph.scope(it) == null }) {
                        return null
                    }
                    val compilation =
                        try {
                            compileTree(
                                XdkSources.replay(
                                    originalRoot,
                                    resourceSnapshots.entries.firstOrNull { it.key.root == originalRoot }?.value?.let {
                                        source.inputs.copy(resources = it)
                                    } ?: source.inputs,
                                    text,
                                    moves,
                                    resourceMoves,
                                    if (moves.isEmpty()) listOf(source.inputs) else sources.values.map { it.inputs },
                                ),
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
                            val partial = compilation.renameFacts(open, includeMissingMethods = true, missingDestinations = destinations)
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
                            val headers =
                                declarations?.memberActionFacts(
                                    fresh,
                                    declarationErrors,
                                    partial.missingMethodInputs,
                                    destinations,
                                )
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
                            reported[module.name] = described(heard.errors)
                            return@mapNotNull module.uri to repaired
                        }
                        failed(heard.errors)
                        return null
                    }
                    val facts =
                        compilation.projectRenameFacts(
                            open,
                            errors,
                            includeMembers = proof == Proof.REPAIR,
                        )
                    reported[module.name] = described(heard.errors)
                    if (heard.hasSeriousErrors() || errors.isAbortDesired) {
                        checkCurrent()
                        if (proof != Proof.COMPLETE) return@mapNotNull null
                        failed(heard.errors)
                        return null
                    }
                    val artifact = compilation.toDependency()
                    artifacts[module.name] = artifact
                    // only a module that others depend on needs its API compared
                    val before = prior?.artifacts?.get(module.name)
                    if (before != null && edited != null && project.affected(module.uri).any { it != module.uri }) {
                        when {
                            before.api == artifact.api -> alike += module.name
                            before.accessibleApi == artifact.accessibleApi -> privatelyMoved += module.name
                        }
                    }
                    if (proof == Proof.REPAIR) {
                        missingDestinations[module.name] =
                            compilation.missingMethodDestinations(module.dependencies + XdkLibraries.moduleNames)
                    }
                    module.uri to facts
                }.toMap()
        checkCurrent()
        return XdkCodeActionCache.Base(
            CompilerRenameFacts.merge(attempts),
            artifacts.filterKeys(project.modules::containsKey),
            missingDestinations,
            reported,
            inputs,
            project,
            revisions,
        )
    }

    /**
     * The modules whose sources, as [inputs] has them by module name, changed since [since], an earlier revision's
     * compile, by root URI: a revision's compile that starts from it compiles them, and the modules that depend on
     * them. Null when it may not start from it, and compiles every module: the graph or its modules changed, or the
     * binary dependencies' [revisions], or a module upstream of a changed one did not compile then.
     */
    private fun changedSince(
        since: XdkCodeActionCache.Base,
        inputs: Map<String, XdkSources.Inputs>,
        revisions: Map<String, String>,
    ): Set<String>? {
        if (since.project?.sameConfiguration(project) != true || since.dependencies != revisions) return null
        if (inputs.keys != project.modules.keys || since.inputs.keys != inputs.keys) return null
        val changed = project.buildOrder().filter { since.inputs[it.name] != inputs[it.name] }
        if (changed.any { module -> project.buildOrder(module.uri).any { it.name != module.name && it.name !in since.artifacts } }) {
            return null
        }
        return changed.mapTo(hashSetOf()) { it.uri }
    }

    /** What a module's compile reported, each error as a line that two compiles of the same give alike. */
    private fun described(errors: List<ErrorListener.ErrorInfo>): List<String> =
        errors.map {
            "${it.severity} ${it.code} ${it.source?.fileName}:${it.line}:${it.offset}-${it.endLine}:${it.endOffset} ${it.messageText}"
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
