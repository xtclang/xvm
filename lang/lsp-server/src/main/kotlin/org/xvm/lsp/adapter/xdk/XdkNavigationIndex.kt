package org.xvm.lsp.adapter.xdk

import java.util.concurrent.atomic.AtomicReference

/**
 * One detached build per configured root. Exact captured inputs permit reuse across graph revisions;
 * no compiler, AST, constant pool or refactoring authorization survives here.
 */
internal class XdkNavigationIndex {
    private val current = AtomicReference(Snapshot())

    /** Module source inputs and dependency revisions determining reusable navigation facts. */
    data class Key(
        val name: String,
        val sources: XdkSources.Inputs,
        val dependencies: Map<String, String>,
    )

    /** Detached semantic models and declaration proofs used by cross-module navigation. */
    data class Facts(
        val models: List<SemanticModel>,
        val constants: Map<SemanticModel.SymbolId, ProofIdentity>,
    )

    /** Navigation facts and optional dependency artifact retained for a particular input key. */
    data class Build(
        val key: Key,
        val facts: Facts?,
        val artifact: XdkDependency?,
    )

    /**
     * Reusable module builds and the navigation models published together from them. Identity, rather
     * than structural equality, fences publication after retirement or close.
     */
    class Snapshot(
        val builds: Map<String, Build> = emptyMap(),
        val navigation: Map<String, XdkWorkspaceNavigation> = emptyMap(),
    )

    fun snapshot(): Snapshot = current.get()

    fun publish(
        previous: Snapshot,
        builds: Map<String, Build>,
        navigation: Map<String, XdkWorkspaceNavigation> = emptyMap(),
    ) = current.compareAndSet(previous, Snapshot(builds.toMap(), navigation.toMap()))

    /** Called under the adapter lifecycle lock, after checking the editor request is current. */
    fun record(
        uri: String,
        build: Build,
    ): Snapshot = current.updateAndGet { Snapshot(it.builds + (uri to build)) }

    /** Retain reusable roots while fencing work captured before the configuration/edit change. */
    fun retire(roots: Set<String>): Snapshot = current.updateAndGet { Snapshot(it.builds.filterKeys(roots::contains)) }

    fun clear() = current.set(Snapshot())
}
