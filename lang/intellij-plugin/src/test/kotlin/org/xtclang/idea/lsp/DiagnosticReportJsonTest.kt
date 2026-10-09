package org.xtclang.idea.lsp

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.redhat.devtools.lsp4ij.JSONUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.services.TextDocumentService
import org.junit.jupiter.api.Test

class DiagnosticReportJsonTest {
    @Test
    fun `related full and unchanged reports decode without killing the message reader`() {
        // Include the real method's response adapter as well as the client's Gson configuration.
        assertThatThrownBy { read(handler(false), wire) }
            .isInstanceOf(JsonParseException::class.java)
            .hasMessageContaining("Ambiguous Either")

        val report = read(json, wire)
        assertThat(report.relatedFullDocumentDiagnosticReport.resultId).isEqualTo("root-1")
        val related = report.relatedFullDocumentDiagnosticReport.relatedDocuments
        assertThat(related.getValue("file:///Library.x").left.resultId).isEqualTo("library-1")
        assertThat(related.getValue("file:///Library.x").left.items).hasSize(1)
        assertThat(related.getValue("file:///Other.x").right.resultId).isEqualTo("other-1")
        val response =
            ResponseMessage().apply {
                setId("1")
                result = report
            }
        assertThat(JsonParser.parseString(json.serialize(response)).asJsonObject["result"])
            .isEqualTo(JsonParser.parseString(wire))
    }

    @Test
    fun `unchanged outer reports retain related changes and reject unknown kinds`() {
        val unchanged =
            wire.replace(
                "\"kind\":\"full\",\"resultId\":\"root-1\",\"items\":[],",
                "\"kind\":\"unchanged\",\"resultId\":\"root-1\",",
            )
        val report = read(json, unchanged)
        assertThat(report.relatedUnchangedDocumentDiagnosticReport.relatedDocuments).hasSize(2)
        assertThatThrownBy {
            read(
                json,
                wire.replace("\"kind\":\"unchanged\"", "\"kind\":\"unknown\""),
            )
        }.isInstanceOf(JsonParseException::class.java)
    }

    private fun handler(compatible: Boolean) =
        MessageJsonHandler(ServiceEndpoints.getSupportedMethods(TextDocumentService::class.java)) {
            JSONUtils.configureCompatibilityAdapters(it)
            if (compatible) it.registerTypeAdapterFactory(DiagnosticReportJson)
        }.apply { setMethodProvider { "textDocument/diagnostic" } }

    private fun read(
        handler: MessageJsonHandler,
        body: String,
    ) = (handler.parseMessage("""{"jsonrpc":"2.0","id":"1","result":$body}""") as ResponseMessage)
        .result as DocumentDiagnosticReport

    private val json = handler(true)

    private val wire =
        """
        {"kind":"full","resultId":"root-1","items":[],"relatedDocuments":{
          "file:///Library.x":{"kind":"full","resultId":"library-1","items":[{
            "range":{"start":{"line":2,"character":3},"end":{"line":2,"character":7}},
            "severity":1,"message":"Unresolved name"
          }]},
          "file:///Other.x":{"kind":"unchanged","resultId":"other-1"}
        }}
        """.trimIndent()
}
