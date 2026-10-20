package org.xvm.lsp.adapter.xdk

import org.xvm.lsp.model.CompilationResult
import java.util.concurrent.atomic.AtomicReference

/** Bounded to the current graph. Only detached diagnostics, source inputs and artifacts survive. */
internal class XdkDiagnosticIndex {
    private val current = AtomicReference(Snapshot())

    /** Module source and dependency inputs that determine reusable diagnostics. */
    data class Key(
        val name: String,
        val sources: XdkSources.Inputs,
        val dependencies: Map<String, String>,
    )

    /** Diagnostics and optional emitted artifact from one set of module inputs. */
    data class Build(
        val key: Key,
        val result: CompilationResult,
        val artifact: XdkDependency?,
    )

    /** Published diagnostic results and reusable builds for one project revision. */
    data class Snapshot(
        val revision: String? = null,
        val results: List<CompilationResult> = emptyList(),
        val builds: Map<String, Build> = emptyMap(),
    )

    fun snapshot(): Snapshot = current.get()

    fun replace(snapshot: Snapshot) = current.set(snapshot)

    fun clear() = current.set(Snapshot())
}
