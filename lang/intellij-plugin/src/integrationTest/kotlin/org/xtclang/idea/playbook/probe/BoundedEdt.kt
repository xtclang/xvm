package org.xtclang.idea.playbook.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Window
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.text.JTextComponent

/**
 * Runs [block] on the EDT in [modality] and answers what it answers, as invokeAndWait does, but fails once
 * [HANG_LIMIT] passes without the EDT having run it: a call that waits for a modality that does not come fails as
 * hung instead of holding its caller, and the driver's, for good. And it fails at once when the IDE is in a modal
 * context that [modality] cannot run in, such as a dialog that waits for an answer while the call is non-modal, with the
 * dialog's title and text; a modal progress, which ends by itself, is waited for. A block that has not started by then
 * never runs.
 */
internal fun <T> boundedEdt(
    modality: ModalityState,
    block: () -> T,
): T {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) return block()
    val result = CompletableFuture<T>()
    val abandoned = AtomicBoolean()
    application.invokeLater(
        {
            if (!abandoned.get()) {
                runCatching(block).fold(result::complete, result::completeExceptionally)
            }
        },
        modality,
    )
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(HANG_SECONDS)
    // the modal context is the EDT's to answer, in any modality, as a dialog's own event loop runs such a question;
    // one question at a time, as a busy EDT answers none
    var asked: CompletableFuture<String?>? = null
    try {
        while (true) {
            try {
                return result.get(POLL_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                if (System.nanoTime() >= deadline) {
                    abandoned.set(true)
                    error("The EDT did not run a probe's call within $HANG_LIMIT, in $modality: is a dialog open?")
                }
                if (modality != ModalityState.any() && asked?.isDone != false) {
                    asked?.getNow(null)?.takeUnless { result.isDone }?.let { dialog ->
                        abandoned.set(true)
                        error("A modal dialog holds the EDT, which a probe's call in $modality waits for: $dialog")
                    }
                    asked =
                        CompletableFuture<String?>().also { question ->
                            application.invokeLater({ question.complete(runCatching(::holdingDialog).getOrNull()) }, ModalityState.any())
                        }
                }
            }
        }
    } catch (failure: ExecutionException) {
        throw failure.cause ?: failure
    }
}

/** How often [boundedEdt] looks at the IDE's modality while it waits. */
private const val POLL_MILLIS = 250L

/**
 * The modal dialog, by its title and text, that holds the EDT from a non-modal call: the IDE is in a modal context,
 * and the newest showing modal dialog is not a progress, which would end by itself; null for none. A modal context
 * without a showing modal dialog is a modal progress that has not shown its window yet, such as the one that saves the
 * project's settings, and holds nothing. On the EDT only, as the modal context and Swing's windows are its.
 */
private fun holdingDialog(): String? {
    if (!LaterInvocator.isInModalContext()) return null
    val dialog =
        Window
            .getWindows()
            .filterIsInstance<Dialog>()
            .lastOrNull { it.isShowing && it.isModal } ?: return null
    if (descendants(dialog).any { it is JProgressBar }) return null
    val text =
        descendants(dialog)
            .mapNotNull { component ->
                when (component) {
                    is JTextComponent -> component.text
                    is JLabel -> component.text
                    else -> null
                }
            }.map { it.replace(Regex("<[^>]+>"), " ").trim() }
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" ")
            .take(300)
    return "\"${dialog.title.orEmpty().ifEmpty { (dialog as? JDialog)?.name.orEmpty() }}\": $text"
}

private fun descendants(component: Component): Sequence<Component> =
    sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
