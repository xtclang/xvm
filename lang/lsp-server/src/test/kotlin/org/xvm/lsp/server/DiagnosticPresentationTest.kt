package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.DiagnosticCapabilities
import org.eclipse.lsp4j.DiagnosticTag
import org.eclipse.lsp4j.DiagnosticsTagSupport
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.fromLsp
import org.xvm.lsp.model.toLsp

/**
 * Checks pull-diagnostic capability negotiation independently of push diagnostics. Related information and
 * related-document results must obey the client features that apply to their own response path.
 */
class DiagnosticPresentationTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `pull diagnostics negotiate related information independently of push and related documents`(support: Boolean) {
        val presentation =
            ClientPresentation.read(
                InitializeParams().apply {
                    capabilities =
                        ClientCapabilities().apply {
                            textDocument =
                                TextDocumentClientCapabilities().apply {
                                    publishDiagnostics =
                                        PublishDiagnosticsCapabilities().apply {
                                            relatedInformation = !support
                                        }
                                    diagnostic =
                                        DiagnosticCapabilities().apply {
                                            relatedInformation = support
                                            relatedDocumentSupport = true
                                        }
                                }
                        }
                },
            )
        assertThat(presentation.diagnosticRelatedInformation).isEqualTo(!support)
        assertThat(presentation.pullDiagnosticRelatedInformation).isEqualTo(support)
        val reports = DiagnosticReports()
        val root = "file:///Root.x"
        val consumer = "file:///Consumer.x"
        val origin = Location("file:/External.x", 2, 3, 2, 10)
        val error = Diagnostic.error(origin, "External failure")
        reports.record(root, listOf(error))
        reports.record(consumer, listOf(error))
        val first =
            reports
                .document(
                    root,
                    null,
                    setOf(root, consumer),
                    presentation.pullDiagnosticRelatedInformation,
                ).left
        val unchanged =
            reports
                .document(
                    root,
                    first.resultId,
                    setOf(root, consumer),
                    presentation.pullDiagnosticRelatedInformation,
                ).right
        assertThat(unchanged.resultId).isEqualTo(first.resultId)
        val workspace =
            reports.workspace(
                setOf(root, consumer),
                emptyMap(),
                emptyMap(),
                presentation.pullDiagnosticRelatedInformation,
            )
        val diagnostics =
            first.items +
                first.relatedDocuments
                    .getValue(consumer)
                    .left.items +
                unchanged.relatedDocuments
                    .getValue(consumer)
                    .left.items +
                workspace.items.flatMap { it.left.items }
        assertThat(diagnostics).hasSize(5).allSatisfy { diagnostic ->
            assertThat(diagnostic.message.left).contains(origin.uri, "External failure")
            assertThat(diagnostic.range.start.line).isZero()
            if (support) {
                assertThat(
                    diagnostic.relatedInformation
                        .single()
                        .location.uri,
                ).isEqualTo(origin.uri)
                assertThat(
                    diagnostic.relatedInformation
                        .single()
                        .location.range.start.line,
                ).isEqualTo(2)
            } else {
                assertThat(diagnostic.relatedInformation).isNull()
            }
        }
        val cached =
            reports.workspace(
                setOf(root, consumer),
                workspace.items.associate { it.left.uri to it.left.resultId },
                emptyMap(),
                presentation.pullDiagnosticRelatedInformation,
            )
        assertThat(cached.items).allSatisfy { assertThat(it.isRight).isTrue() }
    }

    @Test
    fun `tags reach only response paths whose client declares them`() {
        val presentation =
            ClientPresentation.read(
                InitializeParams().apply {
                    capabilities =
                        ClientCapabilities().apply {
                            textDocument =
                                TextDocumentClientCapabilities().apply {
                                    publishDiagnostics =
                                        PublishDiagnosticsCapabilities().apply {
                                            setTagSupport(DiagnosticsTagSupport(listOf(DiagnosticTag.Unnecessary)))
                                        }
                                    diagnostic = DiagnosticCapabilities()
                                }
                        }
                },
            )
        assertThat(presentation.diagnosticTags).containsExactly(DiagnosticTag.Unnecessary)
        assertThat(presentation.pullDiagnosticTags).isEmpty()

        val uri = "file:///Tags.x"
        val unreachable = Diagnostic.error(Location(uri, 2, 4, 2, 8), "unreachable").copy(tags = setOf(Diagnostic.Tag.UNNECESSARY))
        assertThat(unreachable.toLsp().tags).containsExactly(DiagnosticTag.Unnecessary)
        assertThat(Diagnostic.fromLsp(uri, unreachable.toLsp()).tags).containsExactly(Diagnostic.Tag.UNNECESSARY)

        val reports = DiagnosticReports().apply { record(uri, listOf(unreachable)) }

        fun pulled(tags: Set<DiagnosticTag>) =
            reports
                .document(uri, null, emptySet(), relatedInformation = false, tags = tags)
                .left.items
                .single()
                .tags
        assertThat(pulled(presentation.pullDiagnosticTags)).isNull()
        assertThat(pulled(setOf(DiagnosticTag.Unnecessary))).containsExactly(DiagnosticTag.Unnecessary)
    }
}
