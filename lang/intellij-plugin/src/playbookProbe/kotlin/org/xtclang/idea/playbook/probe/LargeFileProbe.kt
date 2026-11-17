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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.DocumentUtil
import com.redhat.devtools.lsp4ij.LSPFileSupport
import java.lang.reflect.AccessibleObject
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
        val replacement = text.take(text.length / 4) + "replacement\n"
        val document = DocumentImpl(text)
        val retained =
            List(markers) { index ->
                val offset = index * (text.length / markers)
                document.createRangeMarker(offset, offset + 5)
            }
        try {
            val before = document.rangeMarkersSize
            val invalidBelowValid = IntervalTreeShape.invalidBelowValid(listOf(document), text, replacement)
            val start = TimeSource.Monotonic.markNow()
            WriteAction.run<RuntimeException> {
                DocumentUtil.executeInBulk(document, bulk) {
                    document.setText(replacement)
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
                    "invalidBelowValid" to invalidBelowValid,
                    "remainingMarkers" to document.rangeMarkersSize,
                    "milliseconds" to elapsed.inWholeMilliseconds,
                ),
            )
        } finally {
            retained.forEach(RangeMarker::dispose)
        }
    }

    /**
     * How far the editor's decoration of the file has come, for a wait for all of it: the number of semantic tokens
     * LSP4IJ holds for the file's current text, absent until they are there, how many of the file's highlighters carry
     * the attributes LSP4IJ paints a semantic token with, and whether IntelliJ has finished highlighting the file.
     */
    @JvmStatic
    fun decoration(
        project: Project,
        file: VirtualFile,
    ): String = ReadAction.computeBlocking<String, RuntimeException> { Gson().toJson(decoration(project, document(file))) }

    @JvmStatic
    fun snapshot(
        project: Project,
        file: VirtualFile,
    ): String = ReadAction.computeBlocking<String, RuntimeException> { Gson().toJson(snapshot(project, document(file))) }

    @JvmStatic
    fun replace(
        project: Project,
        file: VirtualFile,
        text: String,
    ): String {
        val document = ReadAction.computeBlocking<DocumentImpl, RuntimeException> { document(file) }
        val before = ReadAction.computeBlocking<Map<String, Any?>, RuntimeException> { snapshot(project, document) }
        val invalidBelowValid =
            ReadAction.computeBlocking<Int?, RuntimeException> {
                IntervalTreeShape.invalidBelowValid(
                    DocumentMarkupModel.getExistingMarkupModels(document),
                    document.immutableCharSequence,
                    text,
                )
            }
        val start = TimeSource.Monotonic.markNow()
        WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }
        val elapsed = start.elapsedNow()
        return Gson().toJson(
            mapOf(
                "phase" to "editor-replacement",
                "before" to before,
                "invalidBelowValid" to invalidBelowValid,
                "after" to ReadAction.computeBlocking<Map<String, Any?>, RuntimeException> { snapshot(project, document) },
                "milliseconds" to elapsed.inWholeMilliseconds,
            ),
        )
    }

    private fun document(file: VirtualFile): DocumentImpl {
        ApplicationManager.getApplication().assertIsDispatchThread()
        return requireNotNull(FileDocumentManager.getInstance().getDocument(file)) as DocumentImpl
    }

    /**
     * The tokens are those of the file's current text: LSP4IJ keeps a request's future only while the PSI file is as it
     * was when the request was sent, and the PSI file is the document's text once the document is committed. Every
     * token type of the server's legend is a standard one, which LSP4IJ's colors provider maps to an `LSP_` key, so
     * each token of the text becomes one such highlighter.
     */
    private fun decoration(
        project: Project,
        document: DocumentImpl,
    ): Map<String, Any?> {
        val file = requireNotNull(FileDocumentManager.getInstance().getFile(document))
        val tokens =
            PsiManager
                .getInstance(project)
                .findFile(file)
                ?.takeIf { PsiDocumentManager.getInstance(project).isCommitted(document) }
                ?.let { psi ->
                    LSPFileSupport
                        .getSupport(psi)
                        .semanticTokensSupport
                        .validLSPFuture
                        ?.takeIf { it.isDone && !it.isCompletedExceptionally }
                        ?.getNow(null)
                        ?.semanticTokens
                        ?.data
                        ?.let { it.size / 5 }
                }
        return mapOf(
            "semanticTokens" to tokens,
            "semanticHighlighters" to
                DocumentMarkupModel.getExistingMarkupModels(document).sumOf { model ->
                    model.allHighlighters.count { it.textAttributesKey?.externalName?.startsWith("LSP_") == true }
                },
            "highlightingDone" to HighlightingDone.finished(project, file.path),
        )
    }

    private fun snapshot(
        project: Project,
        document: DocumentImpl,
    ): Map<String, Any?> {
        val kinds =
            buildList {
                document.processRangeMarkers { marker ->
                    add(marker.javaClass.name)
                    true
                }
            }
        return mapOf(
            "phase" to "editor-snapshot",
            "decoration" to decoration(project, document),
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

/**
 * What a document change costs the interval trees of its range markers and highlighters without the platform's fix
 * for UP17 (IJPL-259475, intellij-community `5079f25b88`): the most nodes of a tree that the change invalidates below a
 * single node that it leaves valid. A tree removes the invalidated nodes one at a time and, after each removal,
 * recomputes `maxEnd` for every valid node above it, walking again all the invalidated nodes below that node not yet
 * removed, so the change takes time in the square of this number. Invalidated nodes above cost nothing, as the
 * recomputation passes over them. With the fix, `maxEnd` is recomputed once after the removals.
 *
 * The number depends on where the change falls in the tree, not on how many nodes the tree has. A tree built in
 * ascending order, as the daemon installs a file's highlighters, has its root at 0.2 to 0.4 of its nodes: a change
 * that invalidates the root and everything to its right costs little, and one that starts beyond the root costs
 * the square of what follows. It is read by reflection from 2026.2's `IntervalTreeImpl` before the change, and is null
 * where the platform's internals differ.
 */
internal object IntervalTreeShape {
    private val platform = DocumentImpl::class.java.classLoader
    private val tree by lazy { Class.forName("com.intellij.openapi.editor.impl.IntervalTreeImpl", false, platform) }
    private val node by lazy { Class.forName("com.intellij.openapi.editor.impl.IntervalTreeImpl\$IntervalNode", false, platform) }
    private val root by lazy {
        accessible(Class.forName("com.intellij.openapi.editor.impl.RedBlackTree", false, platform).getDeclaredField("root"))
    }
    private val left by lazy { accessible(node.getDeclaredMethod("getLeft")) }
    private val right by lazy { accessible(node.getDeclaredMethod("getRight")) }
    private val start by lazy { accessible(node.getDeclaredMethod("intervalStart")) }
    private val end by lazy { accessible(node.getDeclaredMethod("intervalEnd")) }
    private val delta by lazy { accessible(node.getDeclaredField("delta")) }

    /** The largest number over the trees that [owners] hold, such as a document or its markup models, for replacing [old] by [new]. */
    fun invalidBelowValid(
        owners: List<Any>,
        old: CharSequence,
        new: CharSequence,
    ): Int? =
        runCatching {
            val (from, to) = changed(old, new)
            owners.flatMap(::trees).maxOfOrNull { largest(root.get(it), 0, from, to) } ?: 0
        }.getOrNull()

    private fun <T : AccessibleObject> accessible(member: T): T = member.apply { isAccessible = true }

    private fun trees(owner: Any): List<Any> =
        generateSequence<Class<*>>(owner.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { tree.isAssignableFrom(it.type) }
            .mapNotNull { accessible(it).get(owner) }
            .toList()

    // the part of the old text that DocumentImpl.replaceString replaces: without the prefix and suffix the texts share
    private fun changed(
        old: CharSequence,
        new: CharSequence,
    ): Pair<Int, Int> {
        val shorter = minOf(old.length, new.length)
        val prefix = (0 until shorter).firstOrNull { old[it] != new[it] } ?: shorter
        val suffix = (0 until shorter - prefix).firstOrNull { old[old.length - 1 - it] != new[new.length - 1 - it] } ?: (shorter - prefix)
        return prefix to old.length - suffix
    }

    // the most invalidated nodes joined to a child of a valid node in this subtree; above is the sum of the ancestors' deltas
    private fun largest(
        node: Any?,
        above: Int,
        from: Int,
        to: Int,
    ): Int {
        if (node == null) return 0
        val shift = above + delta.getInt(node)
        val valid = !changes(node, shift, from, to)
        return children(node).maxOfOrNull { child ->
            maxOf(if (valid) invalidated(child, shift, from, to) else 0, largest(child, shift, from, to))
        } ?: 0
    }

    // the invalidated nodes joined to this one through invalidated nodes, itself included
    private fun invalidated(
        node: Any?,
        above: Int,
        from: Int,
        to: Int,
    ): Int {
        if (node == null) return 0
        val shift = above + delta.getInt(node)
        return if (changes(node, shift, from, to)) 1 + children(node).sumOf { invalidated(it, shift, from, to) } else 0
    }

    // as RangeMarkerTree.collectAffectedMarkersAndShiftSubtrees finds them: a node whose range meets the changed one
    private fun changes(
        node: Any,
        shift: Int,
        from: Int,
        to: Int,
    ): Boolean = from <= end.invoke(node) as Int + shift && to >= start.invoke(node) as Int + shift

    private fun children(node: Any): List<Any> = listOfNotNull(left.invoke(node), right.invoke(node))
}
