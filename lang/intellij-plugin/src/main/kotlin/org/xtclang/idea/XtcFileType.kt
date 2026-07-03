package org.xtclang.idea

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * Native IntelliJ file type for Ecstasy source files.
 *
 * TextMate still provides syntax highlighting through [XtcTextMateBundleProvider],
 * and LSP4IJ still routes language features by file-name pattern. This file type
 * exists so IntelliJ knows that the installed Ecstasy plugin owns `*.x` files and
 * does not offer unrelated Marketplace plugins for the extension.
 */
object XtcFileType : LanguageFileType(XtcIntelliJLanguage) {
    override fun getName(): String = "Ecstasy"

    override fun getDescription(): String = "Ecstasy source file"

    override fun getDefaultExtension(): String = "x"

    override fun getIcon(): Icon? = XtcIconProvider.XTC_ICON
}
