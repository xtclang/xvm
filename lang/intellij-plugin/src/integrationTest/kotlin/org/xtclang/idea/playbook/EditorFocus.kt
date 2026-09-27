package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.waitFor
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

/** Native popups require focus; AppIcon activates the window without Driver's title-bar mouse click. */
fun Driver.focusEditor(editor: JEditorUiComponent) {
    val window = cast(ideFrame().component, Window::class)
    withContext(OnDispatcher.EDT) {
        if (!window.isFocused()) utility(NativeAppFocus::class).getInstance().requestFocus(window)
    }
    // AppIcon activation is asynchronous. Requesting component focus in that same EDT event
    // can be superseded when the window restores its previous focus owner.
    waitFor("native IDE window focus", 10.seconds) { withContext(OnDispatcher.EDT) { window.isFocused() } }
    withContext(OnDispatcher.EDT) { if (!editor.component.isFocusOwner()) editor.component.requestFocus() }
    waitFor("native editor focus", 10.seconds) {
        withContext(OnDispatcher.EDT) { window.isFocused() && editor.component.isFocusOwner() }
    }
}

/** Fail on desktop interference instead of repeatedly activating the IDE or timing out on a dismissed popup. */
fun Driver.requirePopupFocus() {
    // Choosers and slow-request progress dialogs can own focus while their IDE frame is
    // inactive. Follow window ownership instead of mistaking those dialogs for another app.
    val frame = cast(ideFrame().component, ActiveWindow::class)
    waitFor(
        message = "Native popup check lost IDE focus",
        timeout = 2.seconds,
        errorMessage = {
            withContext(OnDispatcher.EDT) {
                val manager = utility(NativeKeyboardFocus::class).getCurrentKeyboardFocusManager()
                "IDE frame=${frame.getName()}, active=${manager.getActiveWindow()?.getName()}, " +
                    "focused=${manager.getFocusedWindow()?.getName()}"
            }
        },
    ) {
        withContext(OnDispatcher.EDT) {
            val active = utility(NativeKeyboardFocus::class).getCurrentKeyboardFocusManager().getActiveWindow()
            frame.isActive() || generateSequence(active) { it.getOwner() }.any { it == frame }
        }
    }
}

@Remote("java.awt.Window")
interface ActiveWindow {
    fun isActive(): Boolean

    fun getName(): String?

    fun getOwner(): ActiveWindow?
}

@Remote("java.awt.KeyboardFocusManager")
interface NativeKeyboardFocus {
    fun getCurrentKeyboardFocusManager(): NativeKeyboardFocus

    fun getActiveWindow(): ActiveWindow?

    fun getFocusedWindow(): ActiveWindow?
}

@Remote("com.intellij.ui.AppIcon")
interface NativeAppFocus {
    fun getInstance(): NativeAppFocus

    fun requestFocus(window: Window)
}
