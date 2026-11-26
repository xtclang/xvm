package org.xvm.lsp.server

import org.eclipse.lsp4j.Range
import org.xvm.lsp.adapter.xdk.XdkBuiltModule
import org.xvm.lsp.adapter.xdk.XdkModuleTests
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.XdkTest
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.toLsp
import org.xvm.lsp.protocol.BuildDiagnostic
import org.xvm.lsp.protocol.BuildLocation
import org.xvm.lsp.protocol.BuildPosition
import org.xvm.lsp.protocol.BuildRange
import org.xvm.lsp.protocol.BuildRelatedInformation
import org.xvm.lsp.protocol.BuiltModule
import org.xvm.lsp.protocol.DocumentDiagnostics
import org.xvm.lsp.protocol.ModuleTests
import org.xvm.lsp.protocol.ServerTest
import org.xvm.lsp.protocol.SourceModuleConfiguration
import org.xvm.lsp.protocol.TestLocation
import org.eclipse.lsp4j.Diagnostic as LspDiagnostic

// The server's own types as the protocol that it shares with its clients carries them, where they leave the server.

/** A source module, without the compiler's and the filesystem's state. */
internal fun XdkSourceModule.toProtocol() = SourceModuleConfiguration(name, uri, dependencies.sorted(), resourceRoots)

internal fun XdkBuiltModule.toProtocol() = BuiltModule(name, uri, succeeded, skipped, artifact, reused, revision)

/** A build's diagnostics, grouped by document, each as `publishDiagnostics` would publish it with its own ([Diagnostic.toLsp]). */
internal fun List<Diagnostic>.toProtocol(): List<DocumentDiagnostics> =
    groupBy { it.location.uri }.map { (uri, diagnostics) -> DocumentDiagnostics(uri, diagnostics.map { it.toProtocol(uri) }) }

/** The diagnostic as it is published with the document [publicationUri] ([Diagnostic.toLsp]). */
internal fun Diagnostic.toProtocol(publicationUri: String = location.uri): BuildDiagnostic = toLsp(publicationUri).toProtocol()

internal fun XdkModuleTests.toProtocol() = ModuleTests(module, uri, tests.toProtocol())

private fun XdkTest.toProtocol(): ServerTest =
    ServerTest(
        selector,
        name,
        kind,
        location?.let { TestLocation(it.uri, it.startLine, it.startColumn, it.endLine, it.endColumn) },
        children.map { it.toProtocol() },
    )

private fun LspDiagnostic.toProtocol() =
    BuildDiagnostic(
        range = range.toProtocol(),
        severity = severity?.value,
        code = code?.get()?.toString(),
        source = source,
        message = message?.map({ it }, { it.value }).orEmpty(),
        tags = tags?.map { it.value },
        relatedInformation =
            relatedInformation?.map { BuildRelatedInformation(BuildLocation(it.location.uri, it.location.range.toProtocol()), it.message) },
    )

private fun Range.toProtocol() = BuildRange(BuildPosition(start.line, start.character), BuildPosition(end.line, end.character))
