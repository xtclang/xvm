package org.xvm.lsp.adapter

import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.tool.ModuleInfo

/**
 * An adapter that compiles and analyzes with the embedding, as the server's does, and runs [beforeCompile] with the sources
 * of each module compile before it: so a test counts the compiles, or holds one.
 */
internal fun hookedAdapter(beforeCompile: (ModuleInfo) -> Unit): XdkAdapter {
    val support = EmbeddingSupport.instance()
    return XdkAdapter(
        { source, repository, errors -> support.compileModule(source, repository, errors) },
        { sources, repository, errors ->
            beforeCompile(sources)
            support.compileModule(sources, repository, errors)
        },
        { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
    )
}
