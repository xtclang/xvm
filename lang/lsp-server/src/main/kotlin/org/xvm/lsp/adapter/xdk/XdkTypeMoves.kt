package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import java.io.File

/** Plan qualification changes from resolved type identities, never from matching source text. */
internal object XdkTypeMoves {
    data class Proposal(
        val edits: Map<String, List<XdkRename.Edit>>,
        val resources: Map<String, String>,
    )

    fun plan(
        facts: CompilerRenameFacts,
        source: File,
        destination: File,
        texts: Map<String, String>,
        directories: Set<File>,
        project: XdkProject,
    ): Proposal? {
        if (source.name != destination.name || source.extension != "x" || !destination.parentFile.isDirectory) return null
        val owner =
            facts.typePaths.singleOrNull {
                val target = it.target as? ProofIdentity.Source
                target?.location?.sourceName == source.path && target.name == source.nameWithoutExtension &&
                    target.format == Constant.Format.Class
            } ?: return null
        val namespace =
            facts.typePaths.singleOrNull {
                it.module == owner.module && directory(it.target) == destination.parentFile
            } ?: return null
        val movedPath = namespace.path + source.nameWithoutExtension
        val moves = XdkSourceMoves.plan(source, destination, texts, directories) ?: return null
        val edits =
            facts.typeNames
                .mapNotNull { name ->
                    val file = name.location.sourceName ?: return@mapNotNull null
                    val text = texts[file] ?: return null
                    val movedTarget = name.module == owner.module && name.path.take(owner.path.size) == owner.path
                    val movedUse = file in moves.paths
                    if (!movedTarget && !movedUse) return@mapNotNull null
                    val start = XdkRename.offset(text, name.location.range.start) ?: return null
                    val end = XdkRename.offset(text, name.location.range.end) ?: return null
                    val spelling = text.substring(start, end)
                    // Comments or specialized names need token-preserving edits of their own. Do not
                    // normalize arbitrary source into an apparently equivalent dotted name.
                    val written = spelling.split('.')
                    if (written.any { !XdkRename.identifier(it) }) return null
                    val terminal = XdkRename.offset(text, name.terminal.start) ?: return null
                    val alias =
                        facts.models
                            .firstOrNull { it.sourceName == file }
                            ?.imports
                            .orEmpty()
                            .any { candidate -> candidate.uses.any { it.start == name.location.range.start } }
                    if (!name.imported && alias) return@mapNotNull null
                    if (written.last() != name.path.last()) return@mapNotNull null
                    val desired = if (movedTarget) movedPath + name.path.drop(owner.path.size) else name.path
                    val localModule =
                        project.modules.values
                            .singleOrNull { it.uri == project.scope(file) }
                            ?.name
                            ?: return null
                    val qualified =
                        if (localModule == name.module) {
                            desired
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
                    val retained = written.takeLast(suffix).joinToString(".")
                    val prefixEnd = end - retained.length
                    if (prefixEnd > terminal) return null
                    val prefix = qualified.dropLast(suffix).joinToString(".").let { if (it.isEmpty()) it else "$it." }
                    file to XdkRename.Edit(start, prefixEnd, prefix)
                }.groupBy({ it.first }, { it.second })
                .mapValues { (_, value) -> value.distinct().sortedBy { it.start } }
        if (edits.values.any { changes -> changes.zipWithNext().any { (a, b) -> a.end > b.start || a.start == b.start } }) return null
        return Proposal(edits, moves.resources)
    }

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
