package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.CodeAction
import java.util.concurrent.atomic.AtomicReference

/**
 * What the code action requests of one project revision share: the compile of its sources, each file's import cleanup,
 * which does not depend on the range a request asks about, and the latest proof compiles. Editors ask for code actions
 * each time the cursor moves, and more than one request can prove the same candidate, so the requests of one revision
 * share these instead of compiling them again. Only detached facts, artifacts and actions are kept, for the latest
 * revision only.
 */
internal class XdkCodeActionCache {
    private val current = AtomicReference(Entry(""))

    /**
     * One revision's compile, once compiled, the import cleanup of each source proved so far, and its latest proof
     * compiles, newest first, each with what it compiled and its facts, or null when it did not compile.
     */
    private data class Entry(
        val revision: String,
        val base: Base? = null,
        val imports: Map<String, List<CodeAction>> = emptyMap(),
        val proofs: List<Pair<Any, CompilerRenameFacts?>> = emptyList(),
    )

    /**
     * A revision's compile: its facts, and what lets a proof compile only the modules its edit affects, the others being
     * as they were: the artifact of each module that compiled, by its name, and the missing-method destinations of each.
     */
    data class Base(
        val facts: CompilerRenameFacts,
        val artifacts: Map<String, XdkDependency>,
        val destinations: Map<String, Map<SemanticModel.SourceLocation, XdkMissingMethods.Destination>>,
    )

    /** The compile of [revision]: [compile]'s, unless a request of the revision already compiled it. */
    fun base(
        revision: String,
        compile: () -> Base?,
    ): Base? = base(revision) ?: compile()?.also { base -> update(revision) { it.copy(base = base) } }

    /** The compile of [revision], if a request of the revision compiled it. */
    fun base(revision: String): Base? = entry(revision).base

    /** The import cleanup of [source] in [revision]: [prove]'s, unless a request of the revision already proved it. */
    fun imports(
        revision: String,
        source: String,
        prove: () -> List<CodeAction>,
    ): List<CodeAction> =
        entry(revision).imports[source]
            ?: prove().also { actions -> update(revision) { it.copy(imports = it.imports + (source to actions)) } }

    /**
     * A proof compile of what [key] names, the proposed sources and how they compile, in [revision]: [compile]'s,
     * unless one of the revision's latest [PROOFS] compiled the same. A compile that failed is kept too, as it costs
     * as much.
     */
    fun compiled(
        revision: String,
        key: Any,
        compile: () -> CompilerRenameFacts?,
    ): CompilerRenameFacts? {
        entry(revision).proofs.firstOrNull { it.first == key }?.let { return it.second }
        return compile().also { facts -> update(revision) { it.copy(proofs = (listOf(key to facts) + it.proofs).take(PROOFS)) } }
    }

    fun clear() = current.set(Entry(""))

    private fun entry(revision: String): Entry = current.get().takeIf { it.revision == revision } ?: Entry(revision)

    /** A later revision's facts replace the previous revision's; facts of the same revision add to them. */
    private fun update(
        revision: String,
        change: (Entry) -> Entry,
    ) {
        current.updateAndGet { change(if (it.revision == revision) it else Entry(revision)) }
    }

    private companion object {
        /** Each is a whole project's facts: enough for the candidates of a few cursor positions, and no more. */
        const val PROOFS = 4
    }
}
