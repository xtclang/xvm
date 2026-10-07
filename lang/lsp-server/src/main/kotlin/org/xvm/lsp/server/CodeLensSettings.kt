package org.xvm.lsp.server

import com.google.gson.Gson

internal object CodeLensSettings {
    fun references(raw: Any?): Boolean {
        val value = Gson().toJsonTree(raw)
        if (value.isJsonNull) return true
        require(value.isJsonObject) { "Ecstasy codeLens settings must be an object" }
        val references = value.asJsonObject["references"] ?: return true
        require(references.isJsonPrimitive && references.asJsonPrimitive.isBoolean) { "codeLens.references must be a boolean" }
        return references.asBoolean
    }
}
