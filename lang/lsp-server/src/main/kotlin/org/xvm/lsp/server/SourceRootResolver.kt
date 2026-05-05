package org.xvm.lsp.server

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Resolves extra `.x` source roots beyond the workspace folders the client opens.
 *
 * The workspace indexer only walks `.x` files inside the workspace, so any module that
 * exists only as a compiled `.xtc` binary (or whose source lives outside the user's open
 * project) is invisible to navigation. This resolver lets callers point the indexer at
 * additional source-tree roots so e.g. the XDK standard-library sources can be discovered
 * even when the user is working on an unrelated project.
 *
 * Sources, all merged (deduped):
 *  1. LSP `initializationOptions.xtcSourceRoots` -- an array of paths sent by the client
 *  2. System property `-Dxtc.sourceRoots=<a>${File.pathSeparator}<b>`
 *  3. Env var `XTC_SOURCE_ROOTS=<a>${File.pathSeparator}<b>`
 *
 * The returned paths are absolute filesystem strings (not URIs), matching the contract of
 * [org.xvm.lsp.index.WorkspaceIndexer.scanWorkspace]. Non-existent paths are passed through
 * verbatim -- the indexer is the single warning point for missing directories so we don't
 * double-log the same dropped path during startup.
 */
internal object SourceRootResolver {
    const val INIT_OPTION_KEY = "xtcSourceRoots"
    const val SYSTEM_PROPERTY = "xtc.sourceRoots"
    const val ENV_VAR = "XTC_SOURCE_ROOTS"

    private val logger = LoggerFactory.getLogger(SourceRootResolver::class.java)

    /**
     * Resolve extra source roots from all configured inputs.
     *
     * @param initializationOptions raw init-options value from the LSP `initialize` request --
     *                              accepts `Map<*, *>`, Gson `JsonObject`/`JsonElement`, or null
     * @param systemProperty        defaults to `System.getProperty("xtc.sourceRoots")`
     * @param envVar                defaults to `System.getenv("XTC_SOURCE_ROOTS")`
     */
    fun resolve(
        initializationOptions: Any?,
        systemProperty: String? = System.getProperty(SYSTEM_PROPERTY),
        envVar: String? = System.getenv(ENV_VAR),
    ): List<String> {
        val fromInit = LspJsonOptions.stringList(initializationOptions, INIT_OPTION_KEY)
        val fromSys = parsePathList(systemProperty)
        val fromEnv = parsePathList(envVar)
        val merged = (fromInit + fromSys + fromEnv).distinct()
        if (merged.isNotEmpty()) {
            logger.info(
                "source roots: {} extra root(s) (init={}, sys={}, env={})",
                merged.size,
                fromInit.size,
                fromSys.size,
                fromEnv.size,
            )
        }
        return merged
    }

    private fun parsePathList(raw: String?): List<String> =
        raw
            ?.split(File.pathSeparatorChar)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}
