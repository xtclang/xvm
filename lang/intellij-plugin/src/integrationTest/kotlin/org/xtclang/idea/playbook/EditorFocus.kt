package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.remote.Window
import kotlin.time.Duration.Companion.seconds

/** Close transient UI between isolated scenarios, including after an assertion fails. */
fun Driver.dismissPopups() {
    withContext(OnDispatcher.EDT) {
        utility(NativeEventQueue::class).getInstance().getPopupManager().closeAllPopups()
        service<EditorHints>().hideAllHints()
    }
}

@Remote("com.intellij.ide.IdeEventQueue")
interface NativeEventQueue {
    fun getInstance(): NativeEventQueue

    fun getPopupManager(): NativePopupManager
}

@Remote("com.intellij.ide.IdePopupManager")
interface NativePopupManager {
    fun closeAllPopups(): Boolean
}

/**
 * Native popups require focus; AppIcon activates the window without Driver's title-bar mouse click.
 */
fun Driver.focusEditor(editor: JEditorUiComponent) {
    val window = cast(ideFrame().component, Window::class)
    val nativeEditor = editor.editor
    withContext(OnDispatcher.EDT) {
        if (!window.isFocused()) utility(NativeAppFocus::class).getInstance().activate(window)
    }
    // AppIcon activation is asynchronous. Requesting component focus in that same EDT event
    // can be superseded when the window restores its previous focus owner.
    awaitUi("native IDE window focus", 10.seconds) {
        withContext(OnDispatcher.EDT) { window.isFocused() }
    }
    withContext(OnDispatcher.EDT) {
        utility(NativeEditorUi::class).focusEditor(nativeEditor)
    }
    awaitUi(
        "native editor focus",
        10.seconds,
        errorMessage = {
            withContext(OnDispatcher.EDT) {
                utility(NativeEditorUi::class).focusState(nativeEditor)
            }
        },
    ) {
        withContext(OnDispatcher.EDT) {
            window.isFocused() && utility(NativeEditorUi::class).isEditorActive(nativeEditor)
        }
    }
}

/**
 * Restore application focus without replaying the action that opened a dialog or applied an edit.
 */
internal fun Driver.restorePopupFocus(editor: Editor): Boolean {
    fun focused() =
        withContext(OnDispatcher.EDT) { utility(NativeEditorUi::class).hasFocus(editor) }
    if (focused()) return false
    val window = cast(ideFrame().component, Window::class)
    withContext(OnDispatcher.EDT) {
        utility(PlaybookProgress::class).focus(singleProject(), true)
        utility(NativeAppFocus::class).getInstance().activate(window)
    }
    try {
        awaitUi(
            "restored native IDE focus",
            10.seconds,
            errorMessage = {
                withContext(OnDispatcher.EDT) { utility(NativeEditorUi::class).focusState(editor) }
            },
        ) {
            focused()
        }
    } finally {
        withContext(OnDispatcher.EDT) {
            utility(PlaybookProgress::class).focus(singleProject(), false)
        }
    }
    println("IntelliJ playbook: restored IDE focus without pointer input")
    return true
}

/** Only the unapplied popup inspection can be reopened; callers accept edits after the wait. */
internal class PopupInspection(
    private val driver: Driver,
    private val editor: JEditorUiComponent,
    private val reopen: () -> Unit,
) {
    private val document = driver.cast(editor.document, DocumentVersion::class)
    private val stamp = document.getModificationStamp()
    private val originalText = editor.text
    private val nativeEditor = editor.editor

    fun recover() {
        if (driver.restorePopupFocus(nativeEditor)) {
            reopenUnapplied()
            println("IntelliJ playbook: reopened an unapplied popup after focus loss")
        }
    }

    /** A cold intention request may populate its cache without opening a menu. */
    fun reopenUnapplied() {
        check(document.getModificationStamp() == stamp) {
            "Source changed during popup inspection; refusing to replay an action. " +
                "Before: ${originalText.take(300)}; now: ${editor.text.take(300)}"
        }
        driver.withContext(OnDispatcher.EDT) {
            driver.utility(NativeEditorUi::class).closeCompletion(nativeEditor)
        }
        driver.dismissPopups()
        driver.focusEditor(editor)
        reopen()
    }
}

internal fun JEditorUiComponent.scrollToCaretNow() {
    val nativeEditor = editor
    driver.withContext(OnDispatcher.EDT) {
        driver.utility(NativeEditorUi::class).scrollToCaret(nativeEditor)
    }
}

@Remote("org.xtclang.idea.playbook.probe.EditorUi", plugin = "org.xtclang.playbook.probe")
internal interface NativeEditorUi {
    fun focusEditor(editor: Editor)

    fun isEditorActive(editor: Editor): Boolean

    fun focusState(editor: Editor): String

    fun renameState(editor: Editor): String

    fun renameAvailable(editor: Editor): Boolean

    fun interruptFocus(): Window

    fun closeCompletion(editor: Editor)

    fun hasFocus(editor: Editor): Boolean

    fun scrollToCaret(editor: Editor)
}

@Remote("com.intellij.ui.AppIcon")
interface NativeAppFocus {
    fun getInstance(): NativeAppFocus

    fun requestFocus(window: Window)

    fun requestFocus()
}

private fun NativeAppFocus.activate(window: Window) {
    requestFocus(window)
    // On macOS the window overload uses requestForeground(false). The application overload
    // uses true, which also restores the IDE when another application owns desktop focus.
    requestFocus()
}
