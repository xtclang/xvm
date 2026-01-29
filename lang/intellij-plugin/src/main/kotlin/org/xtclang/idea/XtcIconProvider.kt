package org.xtclang.idea

import com.intellij.ide.IconProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

/**
 * Provides custom icons for XTC files (.x extension).
 *
 * We use IconProvider instead of FileType registration because:
 * 1. TextMate plugin handles the file type and provides syntax highlighting
 * 2. Registering our own FileType would override TextMate's highlighting
 * 3. IconProvider lets us add custom icons without interfering with TextMate
 */
class XtcIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile && element.name.endsWith(".x")) {
            return XTC_ICON
        }
        return null
    }

    companion object {
        val XTC_ICON: Icon? by lazy {
            try {
                IconLoader.getIcon("/icons/xtc.svg", XtcIconProvider::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
