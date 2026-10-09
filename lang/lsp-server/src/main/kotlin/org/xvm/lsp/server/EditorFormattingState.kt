package org.xvm.lsp.server

import com.google.gson.Gson
import org.xvm.lsp.adapter.FormattingConfig
import java.util.concurrent.atomic.AtomicReference

/** A late configuration reply cannot undo a newer preference or restore a reset value. */
internal class EditorFormattingState : AutoCloseable {
    private data class Snapshot(
        val revision: Long = 0,
        val config: FormattingConfig? = null,
        val closed: Boolean = false,
    )

    private val current = AtomicReference(Snapshot())
    val config: FormattingConfig?
        get() = current.get().config

    @Synchronized
    fun request(): Long = current.updateAndGet { it.copy(revision = it.revision + 1) }.revision

    @Synchronized
    fun accept(
        revision: Long,
        raw: Any?,
        install: (FormattingConfig?) -> Unit,
    ): Boolean {
        if (current.get().let { it.closed || it.revision != revision }) return false
        val config = parse(raw)
        current.set(Snapshot(revision, config))
        install(config)
        return true
    }

    @Synchronized
    override fun close() {
        current.updateAndGet { it.copy(closed = true) }
    }

    companion object {
        fun parse(raw: Any?): FormattingConfig? {
            if (raw == null) return null
            val element = Gson().toJsonTree(raw)
            if (element.isJsonNull) return null
            require(element.isJsonObject) { "Ecstasy formatting settings must be an object" }
            val settings = element.asJsonObject

            fun integer(
                name: String,
                default: Int,
                maximum: Int,
            ): Int =
                settings[name]?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) {
                        "$name must be an integer"
                    }
                    val value = it.asString.toIntOrNull()
                    require(value != null && value in 1..maximum) {
                        "$name must be an integer from 1 to $maximum"
                    }
                    value
                } ?: default
            val spaces =
                settings["insertSpaces"]?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) {
                        "insertSpaces must be a boolean"
                    }
                    it.asBoolean
                } ?: true
            return FormattingConfig(
                integer("indentSize", 4, 32),
                integer("continuationIndentSize", 8, 32),
                spaces,
                integer("maxLineWidth", 120, 1000),
            )
        }
    }
}
