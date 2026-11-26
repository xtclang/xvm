package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

/**
 * Writes [text] as App.x in this directory, opens the directory as a workspace, which must compile it clean, and gives
 * [check] the adapter and App.x's URI; App.x must not change.
 */
internal fun Path.workspace(
    text: String,
    check: (XdkAdapter, String) -> Unit,
) {
    val source = resolve("App.x").toFile().apply { writeText(text) }
    val uri = source.toURI().toString()
    XdkAdapter().use { adapter ->
        adapter.initializeWorkspace(listOf(toString()))
        assertThat(adapter.compile(uri, text).diagnostics).describedAs(text).isEmpty()
        check(adapter, uri)
        assertThat(source.readText()).isEqualTo(text)
    }
}

/**
 * Writes [library] as Library.x and [consumer], which imports it, as Consumer.x in this directory, makes them the source
 * modules of an adapter, after opening the directory as its workspace when [initialized], and gives [check] the adapter and
 * the two URIs; neither file may change.
 */
internal fun Path.libraryAndConsumer(
    library: String,
    consumer: String,
    initialized: Boolean = false,
    check: (XdkAdapter, String, String) -> Unit,
) {
    val libraryUri = source("Library", library)
    val consumerUri = source("Consumer", consumer)
    XdkAdapter().use { adapter ->
        if (initialized) adapter.initializeWorkspace(listOf(toString()))
        adapter.replaceSourceModules(
            listOf(XdkSourceModule("Library", libraryUri), XdkSourceModule("Consumer", consumerUri, setOf("Library"))),
        )
        check(adapter, libraryUri, consumerUri)
        assertThat(resolve("Library.x").toFile().readText()).isEqualTo(library)
        assertThat(resolve("Consumer.x").toFile().readText()).isEqualTo(consumer)
    }
}

/** Renames to [name] the symbol at the [offset] of [text], the document at [uri]. */
internal fun XdkAdapter.renameAt(
    uri: String,
    text: String,
    offset: Int,
    name: String,
): WorkspaceEdit? {
    val prefix = text.take(offset)
    return rename(uri, prefix.count { it == '\n' }, prefix.substringAfterLast('\n').length, name)
}

/**
 * Renames the `read` at the [offset] of [text], as App.x of this directory's [workspace], to `fetch`, which must change
 * every `read` and compile; and when [reversible], renaming the first `fetch` back must restore [text].
 */
internal fun Path.assertReadRenamed(
    text: String,
    offset: Int,
    reversible: Boolean = false,
) {
    workspace(text) { adapter, uri ->
        val changed = apply(text, requireNotNull(adapter.renameAt(uri, text, offset, "fetch")), uri)
        assertThat(changed).isEqualTo(text.replace("read", "fetch"))
        assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        if (reversible) {
            val reverse = requireNotNull(adapter.renameAt(uri, changed, changed.indexOf("fetch"), "read"))
            assertThat(apply(changed, reverse, uri)).isEqualTo(text)
        }
    }
}
