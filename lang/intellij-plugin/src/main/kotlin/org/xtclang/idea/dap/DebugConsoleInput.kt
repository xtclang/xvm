package org.xtclang.idea.dap

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.ConsoleViewRunningState
import com.intellij.execution.process.ProcessHandler

// TODO LSP4IJ: UP71 — DAPConsoleView attaches without stdin: its DAPConsoleState makes the console's running state
//  with attachToStdIn false, so a line typed in a debug session's Console goes nowhere, and a program that reads its
//  input cannot be debugged. The console gets the running state that a run's console has, which writes a typed line
//  to the process handler, where LanguageServerDebugProcessHandler takes it as a debug console line, as VS Code
//  sends one: an evaluate request with the context repl.

/** Makes a line typed in LSP4IJ's debug console reach the session that the console shows. */
internal object DebugConsoleInput {
    /**
     * Gives [console], which LSP4IJ attached to [handler] for its output only, a running state that also writes the
     * lines typed in it to [handler]'s input; a console that is not running is left as it is. Call it on the EDT.
     */
    fun attach(
        console: ConsoleViewImpl,
        handler: ProcessHandler,
    ) {
        val running = console.state as? ConsoleViewRunningState ?: return
        // the console has no setter for its state, which only its constructor and attachToProcess set
        val state = ConsoleViewImpl::class.java.getDeclaredField("state").apply { isAccessible = true }
        state.set(console, ConsoleViewRunningState(console, handler, running.dispose(), true, true))
    }
}
