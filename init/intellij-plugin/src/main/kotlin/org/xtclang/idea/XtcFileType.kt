package org.xtclang.idea

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * File type for XTC source files (.x extension).
 */
object XtcFileType : LanguageFileType(XtcLanguage) {
    override fun getName() = "XTC Source"
    override fun getDescription() = "XTC (Ecstasy) source file"
    override fun getDefaultExtension() = "x"
    override fun getIcon(): Icon? = XtcIcons.FILE
}

/**
 * XTC language definition.
 */
object XtcLanguage : com.intellij.lang.Language("XTC")

/**
 * Icons for XTC files and tools.
 */
object XtcIcons {
    val FILE: Icon? by lazy {
        try {
            com.intellij.openapi.util.IconLoader.getIcon("/icons/xtc.svg", XtcIcons::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
