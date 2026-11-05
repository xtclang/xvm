package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.adapter.CodeAction
import java.util.concurrent.atomic.AtomicReference

/**
 * What every code action request of one project revision starts from: the compile of its sources, and each file's
 * import cleanup, which does not depend on the range a request asks about. Editors ask for code actions each time the
 * cursor moves, so the requests of one revision share these instead of proving them again. Only detached facts and
 * actions are kept, for the latest revision only.
 */
internal class XdkCodeActionCache {
    private val current = AtomicReference(Entry(""))

    /** One revision's compile facts, once compiled, and the import cleanup of each source proved so far. */
    private data class Entry(
        val revision: String,
        val before: CompilerRenameFacts? = null,
        val imports: Map<String, List<CodeAction>> = emptyMap(),
    )

    /** The compile facts of [revision]: [compile]'s, unless a request of the revision already compiled them. */
    fun before(
        revision: String,
        compile: () -> CompilerRenameFacts?,
    ): CompilerRenameFacts? = entry(revision).before ?: compile()?.also { facts -> update(revision) { it.copy(before = facts) } }

    /** The import cleanup of [source] in [revision]: [prove]'s, unless a request of the revision already proved it. */
    fun imports(
        revision: String,
        source: String,
        prove: () -> List<CodeAction>,
    ): List<CodeAction> =
        entry(revision).imports[source]
            ?: prove().also { actions -> update(revision) { it.copy(imports = it.imports + (source to actions)) } }

    fun clear() = current.set(Entry(""))

    private fun entry(revision: String): Entry = current.get().takeIf { it.revision == revision } ?: Entry(revision)

    /** A later revision's facts replace the previous revision's; facts of the same revision add to them. */
    private fun update(
        revision: String,
        change: (Entry) -> Entry,
    ) {
        current.updateAndGet { change(if (it.revision == revision) it else Entry(revision)) }
    }
}
