package org.xtclang.idea.lsp

import com.google.gson.JsonParser
import com.redhat.devtools.lsp4ij.JSONUtils
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test

class DiagnosticQuickFixesTest {
    @Test
    fun `identical full reports refresh annotations that own replaced lazy fixes`() {
        val diagnostic = Diagnostic(Range(Position(2, 8), Position(2, 15)), "Unknown method")
        val report = RelatedFullDocumentDiagnosticReport(listOf(diagnostic)).apply { resultId = "same-result" }
        val response =
            ResponseMessage().apply {
                id = "pull"
                result = DocumentDiagnosticReport(report)
            }

        fun present() = ((DiagnosticQuickFixes.incoming(response) as ResponseMessage).result as DocumentDiagnosticReport).left.items
        val first = present()
        val second = present()
        assertThat(LSPDiagnosticUtils.isDiagnosticsChanged(report.items, report.items)).isFalse()
        assertThat(LSPDiagnosticUtils.isDiagnosticsChanged(first, second)).isTrue()
        assertThat(first.single().range).isEqualTo(diagnostic.range)
        val gson = JSONUtils.getLsp4jGson()
        val displayed = gson.toJsonTree(first.single()).asJsonObject.apply { remove("data") }
        assertThat(displayed).isEqualTo(gson.toJsonTree(diagnostic))
        assertThat(diagnostic.data).isNull()
        assertThat((response.result as DocumentDiagnosticReport).left).isSameAs(report)
    }

    @Test
    fun `quick fix requests restore opaque server data and leave client diagnostics untouched`() {
        listOf(null, JsonParser.parseString("{\"nested\":[1,true,\"payload\"]}")).forEach { payload ->
            val diagnostic = Diagnostic(Range(Position(2, 8), Position(2, 15)), "Unknown method").apply { data = payload }
            val response =
                ResponseMessage().apply {
                    id = "pull"
                    result = DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(diagnostic)))
                }
            val presented = ((DiagnosticQuickFixes.incoming(response) as ResponseMessage).result as DocumentDiagnosticReport).left.items
            val params = CodeActionParams(TextDocumentIdentifier("file:///Missing.x"), diagnostic.range, CodeActionContext(presented))
            val request =
                RequestMessage().apply {
                    id = "fix"
                    method = "textDocument/codeAction"
                    this.params = params
                }
            val restored = DiagnosticQuickFixes.outgoing(request) as RequestMessage
            val actual = (restored.params as CodeActionParams).context.diagnostics.single()
            assertThat(JSONUtils.getLsp4jGson().toJsonTree(actual.data)).isEqualTo(JSONUtils.getLsp4jGson().toJsonTree(payload))
            assertThat(restored.id).isEqualTo(request.id)
            assertThat(restored.method).isEqualTo(request.method)
            assertThat(presented.single().data).isNotEqualTo(actual.data)
            assertThat(params.context.diagnostics.single()).isSameAs(presented.single())
        }
    }

    @Test
    fun `unrelated and empty reports retain message identity`() {
        val ordinary =
            RequestMessage().apply {
                id = "ordinary"
                method = "textDocument/hover"
            }
        assertThat(DiagnosticQuickFixes.outgoing(ordinary)).isSameAs(ordinary)
        val empty =
            ResponseMessage().apply {
                id = "empty"
                result =
                    DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList()))
            }
        assertThat(DiagnosticQuickFixes.incoming(empty)).isSameAs(empty)
    }
}
