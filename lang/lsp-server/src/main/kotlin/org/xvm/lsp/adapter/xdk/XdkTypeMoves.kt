package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import java.io.File

/** Plan qualification changes from resolved type identities, never from matching source text. */
internal object XdkTypeMoves {
    data class Proposal(
        val edits: Map<String, List<XdkRename.Edit>>,
        val resources: Map<String, String>,
        val qualifications: Map<String, List<XdkRename.Edit>>,
        val destinations: List<Destination>,
        val imports: Map<String, List<XdkRename.Edit>>,
        val dependencies: Map<String, Set<String>>,
    ) {
        /** Even an unused moved declaration must acquire the intended compiler owner. */
        fun provesDestinations(facts: CompilerRenameFacts): Boolean =
            destinations.all { destination ->
                facts.typePaths.any {
                    val target = it.target as? ProofIdentity.Source
                    target?.location?.sourceName == destination.source && target.format == Constant.Format.Class &&
                        it.module == destination.module && it.path == destination.path
                }
            }
    }

    data class Destination(
        val source: String,
        val module: String,
        val path: List<String>,
    )

    fun plan(
        facts: CompilerRenameFacts,
        operations: Map<File, File>,
        texts: Map<String, String>,
        directories: Set<File>,
        project: XdkProject,
    ): Proposal? {
        val moves =
            operations.map { (source, destination) ->
                if (source.extension != "x" || destination.extension != "x" || !destination.parentFile.isDirectory) return null
                val owner =
                    facts.typePaths.singleOrNull {
                        val target = it.target as? ProofIdentity.Source
                        target?.location?.sourceName == source.path && target.name == source.nameWithoutExtension &&
                            target.format == Constant.Format.Class
                    } ?: return null
                val targetModule = project.modules.values.singleOrNull { it.uri == project.scope(destination.path) } ?: return null
                val namespace = namespace(facts, targetModule.name, destination.parentFile, texts, directories) ?: return null
                // An inline declaration can occupy the destination without a file of its own.
                // Reject before replay rather than presenting a duplicate component to the compiler.
                if (facts.typePaths.any {
                        it.module == targetModule.name && it.path == namespace + destination.nameWithoutExtension &&
                            it.target != owner.target
                    }
                ) {
                    return null
                }
                val files = XdkSourceMoves.plan(source, destination, texts, directories) ?: return null
                val rename =
                    XdkRename.fileNamePlan(facts, texts, source.path, source.nameWithoutExtension, destination.nameWithoutExtension)
                        ?: return null
                Move(owner, targetModule.name, namespace + source.nameWithoutExtension, files, rename)
            }
        fun owner(file: String): String? = project.modules.values.singleOrNull { it.uri == project.scope(file) }?.name
        fun movedOwner(file: String): String? = moves.singleOrNull { file in it.files.paths }?.module ?: owner(file)
        fun target(name: TypeName): Move? = moves.singleOrNull { name.module == it.owner.module && name.path.take(it.owner.path.size) == it.owner.path }
        fun changesModule(name: TypeName): Boolean {
            val file = name.location.sourceName ?: return false
            return target(name)?.let { it.module != it.owner.module } == true || movedOwner(file) != owner(file)
        }
        val dependencies = facts.typeNames.filter(::changesModule).mapNotNull { name ->
            val file = name.location.sourceName ?: return null
            val local = movedOwner(file) ?: return null
            val module = target(name)?.module ?: name.module
            if (local == module || module == "ecstasy.xtclang.org") null else local to module
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        val imports = dependencies.mapValues { (module, required) ->
            val root = project.modules.getValue(module).root.path
            XdkMoveImports.plan(texts.getValue(root), texts.filterKeys { movedOwner(it) == module }.values, required) ?: return null
        }
        val qualifications =
            facts.typeNames
                .mapNotNull { name ->
                    val file = name.location.sourceName ?: return@mapNotNull null
                    val text = texts[file] ?: return null
                    val owners = moves.filter { name.module == it.owner.module && name.path.take(it.owner.path.size) == it.owner.path }
                    if (owners.size > 1) return null
                    val movedTarget = owners.singleOrNull()
                    val movedUse = moves.any { file in it.files.paths }
                    if (movedTarget == null && !movedUse) return@mapNotNull null
                    val start = XdkRename.offset(text, name.location.range.start) ?: return null
                    val end = XdkRename.offset(text, name.location.range.end) ?: return null
                    val spelling = XdkQualifiedName.parse(text.substring(start, end)) ?: return null
                    val written = spelling.names
                    val terminal = XdkRename.offset(text, name.terminal.start) ?: return null
                    val alias =
                        facts.models
                            .firstOrNull { it.sourceName == file }
                            ?.imports
                            .orEmpty()
                            .any { candidate -> candidate.uses.any { it.start == name.location.range.start } }
                    if (!name.imported && alias) return@mapNotNull null
                    if (written.last() != name.path.last()) return@mapNotNull null
                    val desired = movedTarget?.let { it.path + name.path.drop(it.owner.path.size) } ?: name.path
                    val localModule = movedOwner(file) ?: return null
                    val targetModule = movedTarget?.module ?: name.module
                    val qualified =
                        if (localModule == targetModule) {
                            desired
                        } else if (changesModule(name) && targetModule != "ecstasy.xtclang.org") {
                            listOf(imports.getValue(localModule).aliases.getValue(targetModule)) + desired
                        } else {
                            // Preserve the importing module's alias. A bare imported name remains bound
                            // through its rewritten import clause; never invent a module import here.
                            if (written.size <= name.path.size || written.takeLast(name.path.size) != name.path) return@mapNotNull null
                            written.dropLast(name.path.size) + desired
                        }
                    if (written == qualified) return@mapNotNull null
                    val suffix =
                        written
                            .asReversed()
                            .zip(qualified.asReversed())
                            .takeWhile { it.first == it.second }
                            .size
                    if (suffix == 0) return null
                    val prefix = spelling.prefix(written.size - suffix, qualified.dropLast(suffix))
                    if (start + prefix.end > terminal) return null
                    file to prefix.copy(start = start, end = start + prefix.end)
                }.groupBy({ it.first }, { it.second })
                .mapValues { (_, value) -> value.distinct().sortedBy { it.start } }
        val importEdits = imports.map { (module, planned) -> project.modules.getValue(module).root.path to listOf(planned.edit) }.toMap()
        val edits =
            (qualifications.entries + moves.flatMap { it.rename.edits.entries } + importEdits.entries)
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, changes) -> changes.flatten().distinct().sortedWith(compareBy({ it.start }, { it.end })) }
        if (edits.values.any { !XdkRename.disjoint(it) }) return null
        val resources = moves.flatMap { it.files.resources.entries }.groupBy({ it.key }, { it.value })
        if (resources.values.any { it.distinct().size != 1 }) return null
        val destinations =
            moves.zip(operations.values).map { (move, destination) ->
                Destination(destination.path, move.module, move.path.dropLast(1) + destination.nameWithoutExtension)
            }
        return Proposal(edits, resources.mapValues { it.value.first() }, qualifications, destinations, importEdits, dependencies)
    }

    /**
     * Extend the nearest compiler-owned package through captured implicit package directories.
     * A companion source can change ownership, so only already resolved packages may cross it.
     * Replay must subsequently prove the final declaration path, including unused declarations.
     */
    private fun namespace(
        facts: CompilerRenameFacts,
        module: String,
        destination: File,
        texts: Map<String, String>,
        directories: Set<File>,
    ): List<String>? {
        val ancestors = generateSequence(destination) { it.parentFile }.toList()
        val resolved =
            ancestors.firstNotNullOfOrNull { candidate ->
                facts.typePaths
                    .singleOrNull { it.module == module && directory(it.target) == candidate }
                    ?.let { candidate to it.path }
            } ?: return null
        val implicit = ancestors.takeWhile { it != resolved.first }.asReversed()
        if (implicit.any {
                it !in directories || !XdkRename.identifier(it.name) || File(it.parentFile, "${it.name}.x").path in texts
            }
        ) {
            return null
        }
        return resolved.second + implicit.map { it.name }
    }

    private data class Move(
        val owner: TypePath,
        val module: String,
        val path: List<String>,
        val files: XdkSourceMoves,
        val rename: XdkRename.Plan,
    )

    private fun directory(identity: ProofIdentity): File? =
        when (identity) {
            is ProofIdentity.Directory -> {
                File(identity.path)
            }

            is ProofIdentity.Source -> {
                identity.location.sourceName
                    ?.let(::File)
                    ?.takeIf {
                        identity.format == Constant.Format.Module ||
                            (identity.format == Constant.Format.Package && it.nameWithoutExtension == identity.name)
                    }?.let { File(it.parentFile, it.nameWithoutExtension) }
            }

            else -> {
                null
            }
        }
}
