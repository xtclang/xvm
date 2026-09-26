package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

/** Native popups require focus; AppIcon activates the window without Driver's title-bar mouse click. */
fun Driver.focusEditor(editor: JEditorUiComponent) {
    val window = cast(ideFrame().component, Window::class)
    withContext(OnDispatcher.EDT) {
        if (!window.isFocused()) utility(NativeAppFocus::class).getInstance().requestFocus(window)
        if (!editor.component.isFocusOwner()) editor.component.requestFocus()
    }
    waitFor("native editor focus", 10.seconds) {
        withContext(OnDispatcher.EDT) { window.isFocused() && editor.component.isFocusOwner() }
    }
}

/** Fail on desktop interference instead of repeatedly activating the IDE or timing out on a dismissed popup. */
fun Driver.requirePopupFocus() {
    // Navigation choosers are heavyweight windows: they take focus from their owning IDE frame.
    // AWT keeps that frame active. Allow the brief transition while the popup acquires focus.
    val frame = cast(ideFrame().component, ActiveWindow::class)
    waitFor("Native popup check lost IDE focus; switching applications dismisses popups", 2.seconds) {
        withContext(OnDispatcher.EDT) { frame.isActive() }
    }
}

@Remote("java.awt.Window")
interface ActiveWindow {
    fun isActive(): Boolean
}

@Remote("com.intellij.ui.AppIcon")
interface NativeAppFocus {
    fun getInstance(): NativeAppFocus

    fun requestFocus(window: Window)
}
