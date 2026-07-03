package org.xtclang.idea

import com.intellij.ide.IconProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

/**
 * Provides custom icons for XTC files (.x extension).
 */
class XtcIconProvider : IconProvider() {
    override fun getIcon(
        element: PsiElement,
        flags: Int,
    ): Icon? = XTC_ICON.takeIf { element is PsiFile && element.name.endsWith(".x") }

    companion object {
        val XTC_ICON: Icon? by lazy {
            runCatching { IconLoader.getIcon("/icons/xtc.svg", XtcIconProvider::class.java) }.getOrNull()
        }
    }
}
