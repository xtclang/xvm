package org.xvm.lsp.adapter.xdk

import java.util.concurrent.atomic.AtomicReference
import org.xvm.lsp.model.CompilationResult

/** Bounded to the current graph. Only detached diagnostics, source inputs and artifacts survive. */
internal class XdkDiagnosticIndex {
    data class Key(
        val name: String,
        val sources: XdkSources.Inputs,
        val dependencies: Map<String, String>,
    )

    data class Build(val key: Key, val result: CompilationResult, val artifact: XdkDependency?)

    data class Snapshot(
        val revision: String? = null,
        val results: List<CompilationResult> = emptyList(),
        val builds: Map<String, Build> = emptyMap(),
    )

    private val current = AtomicReference(Snapshot())

    fun snapshot(): Snapshot = current.get()

    fun replace(snapshot: Snapshot) = current.set(snapshot)

    fun clear() = current.set(Snapshot())
}
