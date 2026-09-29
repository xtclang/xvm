package org.xvm.lsp.adapter.xdk

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import org.xvm.asm.ErrorListener
import org.xvm.asm.XvmStructure
import org.xvm.compiler.Source
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.util.Severity as XtcSeverity

/**
 * The compiler places a diagnostic in one of three ways, and Site being a closed set is what lets
 * this be exhaustive rather than a hunt for whichever field happens to be populated.
 */
internal fun ErrorListener.ErrorInfo.toDiagnostic(
    source: Source,
    sourceUris: Map<String, String>,
    declarations: Map<XvmStructure, Location>,
): Diagnostic {
    val uri = source.fileName
    val where = site()
    val sourceUri =
        if (where is ErrorListener.Site.In) {
            sourceUris[where.source().fileName]
                ?: if (where.source() === source) uri else where.source().diagnosticUri()
        } else {
            null
        }
    return Diagnostic(
        location =
            when (where) {
                is ErrorListener.Site.In ->
                    sourceUri?.let { spanOf(it, where) } ?: wholeDocument(uri)

                is ErrorListener.Site.At -> declarations[where.xs()] ?: wholeDocument(uri)

                // a whole-compilation failure belongs to the document, not to a line in it
                else -> wholeDocument(uri)
            },
        severity = severity.toLspSeverity(),
        message =
            if (where is ErrorListener.Site.In && sourceUri == null) {
                "In ${where.source().fileName ?: "an unidentified source"}: $message"
            } else {
                message
            },
        code = code,
        source = "xtc",
    )
}

/** Only absolute source identities can be published as navigable locations. */
private fun Source.diagnosticUri(): String? {
    val name = fileName ?: return null
    val file = File(name)
    if (file.isAbsolute) return file.toURI().toString()
    return try {
        URI(name).takeIf { it.isAbsolute }?.toString()
    } catch (_: URISyntaxException) {
        null
    }
}

private fun spanOf(
    uri: String,
    where: ErrorListener.Site.In,
): Location =
    Location(
        uri = uri,
        startLine = Source.calculateLine(where.lPosStart()),
        startColumn = Source.calculateOffset(where.lPosStart()),
        endLine = Source.calculateLine(where.lPosEnd()),
        endColumn = Source.calculateOffset(where.lPosEnd()),
    )

private fun wholeDocument(uri: String): Location = Location(uri, 0, 0, 0, 0)

private fun XtcSeverity.toLspSeverity(): Diagnostic.Severity =
    when (this) {
        XtcSeverity.FATAL,
        XtcSeverity.ERROR -> Diagnostic.Severity.ERROR
        XtcSeverity.WARNING -> Diagnostic.Severity.WARNING
        XtcSeverity.INFO -> Diagnostic.Severity.INFORMATION
        XtcSeverity.NONE -> Diagnostic.Severity.HINT
    }
