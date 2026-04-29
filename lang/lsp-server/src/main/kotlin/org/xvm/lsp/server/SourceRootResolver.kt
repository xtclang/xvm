package org.xvm.lsp.server

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves extra `.x` source roots beyond the workspace folders the client opens.
 *
 * The workspace indexer only walks `.x` files inside the workspace, so any module that
 * exists only as a compiled `.xtc` binary (or whose source lives outside the user's open
 * project) is invisible to navigation. This resolver lets callers point the indexer at
 * additional source-tree roots so e.g. the XDK standard-library sources can be discovered
 * even when the user is working on an unrelated project.
 *
 * Sources, all merged (dedup, non-existent paths dropped with a warning):
 *  1. LSP `initializationOptions.xtcSourceRoots` -- an array of paths sent by the client
 *  2. System property `-Dxtc.sourceRoots=<a>${File.pathSeparator}<b>`
 *  3. Env var `XTC_SOURCE_ROOTS=<a>${File.pathSeparator}<b>`
 *
 * The returned paths are absolute filesystem paths (not URIs), matching the contract of
 * [org.xvm.lsp.index.WorkspaceIndexer.scanWorkspace].
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
        val fromInit = parseInitOptions(initializationOptions)
        val fromSys = parsePathList(systemProperty)
        val fromEnv = parsePathList(envVar)
        val merged = (fromInit + fromSys + fromEnv).distinct()
        if (merged.isEmpty()) return emptyList()

        val (existing, missing) = merged.partition { Files.isDirectory(Path.of(it)) }
        if (missing.isNotEmpty()) {
            logger.warn("source roots: dropping {} non-existent path(s): {}", missing.size, missing)
        }
        if (existing.isNotEmpty()) {
            logger.info(
                "source roots: {} extra root(s) (init={}, sys={}, env={})",
                existing.size,
                fromInit.size,
                fromSys.size,
                fromEnv.size,
            )
        }
        return existing
    }

    private fun parsePathList(raw: String?): List<String> =
        raw
            ?.split(File.pathSeparatorChar)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    private fun parseInitOptions(raw: Any?): List<String> {
        if (raw == null) return emptyList()
        val array =
            when (raw) {
                is Map<*, *> -> raw[INIT_OPTION_KEY]
                is JsonObject -> raw.get(INIT_OPTION_KEY)
                is JsonElement -> if (raw.isJsonObject) raw.asJsonObject.get(INIT_OPTION_KEY) else null
                else -> null
            } ?: return emptyList()

        return when (array) {
            is List<*> -> array.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            is JsonArray -> array.mapNotNull { stringOrNull(it) }
            is JsonElement -> if (array.isJsonArray) array.asJsonArray.mapNotNull { stringOrNull(it) } else emptyList()
            else -> emptyList()
        }
    }

    private fun stringOrNull(elem: JsonElement?): String? =
        elem
            ?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf(String::isNotEmpty)
}
