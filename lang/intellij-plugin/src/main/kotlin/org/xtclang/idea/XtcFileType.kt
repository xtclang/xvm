package org.xtclang.idea

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icons for XTC files and tools.
 * Note: File type registration is handled by the TextMate bundle,
 * which provides both the file type and syntax highlighting.
 */
object XtcIcons {
    val FILE: Icon? by lazy {
        try {
            IconLoader.getIcon("/icons/xtc.svg", XtcIcons::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
