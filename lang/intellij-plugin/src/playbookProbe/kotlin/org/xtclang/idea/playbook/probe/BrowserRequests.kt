package org.xtclang.idea.playbook.probe

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/** Loaded only in the disposable test IDE; never included in the distributed XTC plugin. */
object BrowserRequests {
    private val opened = CopyOnWriteArrayList<String>()
    private var previous: BrowserLauncher? = null

    /**
     * Has the IDE record the URLs it is asked to open in a browser, instead of starting one, until [restore]: a
     * document link's Ctrl+Click opens a web target through [BrowserLauncher].
     */
    @JvmStatic
    fun record() {
        opened.clear()
        if (previous == null) previous = BrowserLauncher.instance
        replace(Recorder())
    }

    /** The URLs asked for since [record], one per line. */
    @JvmStatic
    fun opened(): String = opened.joinToString("\n")

    /** Gives the IDE its own browser launcher back. */
    @JvmStatic
    fun restore() {
        previous?.let(::replace)
        previous = null
    }

    private fun replace(launcher: BrowserLauncher) =
        (ApplicationManager.getApplication() as ComponentManagerEx).replaceRegularServiceInstance(BrowserLauncher::class.java, launcher)

    private class Recorder : BrowserLauncher() {
        override fun open(url: String) {
            opened += url
        }

        override fun browse(file: File) {
            opened += file.toURI().toString()
        }

        override fun browse(file: Path) {
            opened += file.toUri().toString()
        }

        override fun browse(
            url: String,
            browser: WebBrowser?,
            project: Project?,
        ) {
            opened += url
        }
    }
}
