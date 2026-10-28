package org.xtclang.idea.lsp

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.eclipse.lsp4j.DidChangeConfigurationParams

// TODO LSP4IJ: UP08 — preserve explicit null configuration fields in the client Gson configuration.
// Remove this adapter once reset-to-discovery survives a wire round trip without it.

/** Explicit null settings reset configuration; Gson's normal omission would keep the old value. */
internal object ConfigurationJson : TypeAdapterFactory {
    override fun <T> create(
        gson: Gson,
        type: TypeToken<T>,
    ): TypeAdapter<T>? {
        if (type.rawType != DidChangeConfigurationParams::class.java) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun read(input: JsonReader): T = delegate.read(input)

            override fun write(
                output: JsonWriter,
                value: T,
            ) {
                val previous = output.serializeNulls
                output.serializeNulls = true
                try {
                    delegate.write(output, value)
                } finally {
                    output.serializeNulls = previous
                }
            }
        }
    }
}
