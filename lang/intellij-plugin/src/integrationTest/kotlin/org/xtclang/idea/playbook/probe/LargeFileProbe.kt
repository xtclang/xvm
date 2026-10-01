package org.xtclang.idea.playbook.probe

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.DocumentUtil
import kotlin.time.TimeSource

/** Uses real platform documents, including an unattached control with no PSI, editor or LSP. */
object LargeFileProbe {
    @JvmStatic
    fun plain(
        markers: Int,
        bulk: Boolean,
    ): String {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val text = "abcdefghij\n".repeat(80_000)
        val document = DocumentImpl(text)
        val retained =
            List(markers) { index ->
                val offset = index * (text.length / markers)
                document.createRangeMarker(offset, offset + 5)
            }
        try {
            val before = document.rangeMarkersSize
            val start = TimeSource.Monotonic.markNow()
            WriteAction.run<RuntimeException> {
                DocumentUtil.executeInBulk(document, bulk) {
                    document.setText(text.take(text.length / 4) + "replacement\n")
                }
            }
            val elapsed = start.elapsedNow()
            check(retained.count(RangeMarker::isValid) == markers / 4)
            return Gson().toJson(
                mapOf(
                    "phase" to "plain-document",
                    "bulk" to bulk,
                    "characters" to text.length,
                    "markers" to before,
                    "remainingMarkers" to document.rangeMarkersSize,
                    "milliseconds" to elapsed.inWholeMilliseconds,
                ),
            )
        } finally {
            retained.forEach(RangeMarker::dispose)
        }
    }

    @JvmStatic
    fun snapshot(file: VirtualFile): String =
        ReadAction.computeBlocking<String, RuntimeException> { Gson().toJson(snapshot(document(file))) }

    @JvmStatic
    fun replace(
        project: Project,
        file: VirtualFile,
        text: String,
    ): String {
        val document = ReadAction.computeBlocking<DocumentImpl, RuntimeException> { document(file) }
        val before = ReadAction.computeBlocking<Map<String, Any>, RuntimeException> { snapshot(document) }
        val start = TimeSource.Monotonic.markNow()
        WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }
        val elapsed = start.elapsedNow()
        return Gson().toJson(
            mapOf(
                "phase" to "editor-replacement",
                "before" to before,
                "after" to ReadAction.computeBlocking<Map<String, Any>, RuntimeException> { snapshot(document) },
                "milliseconds" to elapsed.inWholeMilliseconds,
            ),
        )
    }

    private fun document(file: VirtualFile): DocumentImpl {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return requireNotNull(FileDocumentManager.getInstance().getDocument(file)) as DocumentImpl
    }

    private fun snapshot(document: DocumentImpl): Map<String, Any> {
        val kinds =
            buildList {
                document.processRangeMarkers { marker ->
                    add(marker.javaClass.name)
                    true
                }
            }
        return mapOf(
            "phase" to "editor-snapshot",
            "characters" to document.textLength,
            "markers" to document.rangeMarkersSize,
            "nodes" to document.rangeMarkersNodeSize,
            "markerClasses" to kinds.groupingBy { it }.eachCount(),
            "markupModels" to
                DocumentMarkupModel.getExistingMarkupModels(document).map { model ->
                    val highlights = model.allHighlighters
                    mapOf(
                        "class" to model.javaClass.name,
                        "highlighters" to highlights.size,
                        "attributes" to highlights.groupingBy { it.textAttributesKey?.externalName ?: "none" }.eachCount(),
                    )
                },
        )
    }
}
