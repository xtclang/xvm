package org.xvm.lsp.util

import java.net.URI

/** Short source labels for progress UI; no filesystem access or source contents. */
internal object ProgressLabels {
    fun source(
        uri: String,
        roots: List<String> = emptyList(),
    ): String {
        fun path(value: String) = runCatching { URI(value).path?.trimEnd('/') }.getOrNull()
        val source = path(uri)
        val root = roots.mapNotNull(::path).filter { source?.startsWith("$it/") == true }.maxByOrNull { it.length }
        val label =
            when {
                source.isNullOrEmpty() -> {
                    uri.substringBefore('?').substringBefore('#')
                }

                root != null -> {
                    source.removePrefix("$root/")
                }

                else -> {
                    source
                        .split('/')
                        .filter(String::isNotEmpty)
                        .takeLast(2)
                        .joinToString("/")
                }
            }
        return label.replace(Regex("[\\p{Cntrl}]"), " ").take(160).ifEmpty { "workspace" }
    }
}
