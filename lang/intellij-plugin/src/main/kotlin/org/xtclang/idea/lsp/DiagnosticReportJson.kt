package org.xtclang.idea.lsp

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import org.eclipse.lsp4j.FullDocumentDiagnosticReport
import org.eclipse.lsp4j.UnchangedDocumentDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter
import org.eclipse.lsp4j.jsonrpc.messages.Either

// TODO LSP4IJ: UP06 — remove this adapter when the bundled LSP4J correctly decodes relatedDocuments
// unions by kind. The underlying defect is in LSP4J 1.0.0; keep full/unchanged transport
// regressions.

/**
 * LSP4J 1.0.0 discriminates the outer diagnostic report, but not the Either values inside
 * relatedDocuments. Both alternatives are JSON objects; use their protocol kind instead of letting
 * the ambiguous default adapter terminate the connection's message reader.
 */
internal object DiagnosticReportJson : TypeAdapterFactory {
    private val report =
        object :
            TypeToken<Either<FullDocumentDiagnosticReport, UnchangedDocumentDiagnosticReport>>() {}

    override fun <T> create(
        gson: Gson,
        type: TypeToken<T>,
    ): TypeAdapter<T>? {
        if (type != report) return null
        @Suppress("UNCHECKED_CAST") // The exact parameterized TypeToken above establishes T.
        return EitherTypeAdapter(
            gson,
            report,
            { it.isJsonObject && it.asJsonObject["kind"]?.asString == "full" },
            { it.isJsonObject && it.asJsonObject["kind"]?.asString == "unchanged" },
        )
            as TypeAdapter<T>
    }
}
