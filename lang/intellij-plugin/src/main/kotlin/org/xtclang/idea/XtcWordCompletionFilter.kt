package org.xtclang.idea

import com.intellij.lang.WordCompletionElementFilter
import com.intellij.psi.tree.IElementType

/**
 * Lets an explicit completion in an Ecstasy file offer the file's words when the language server offers nothing,
 * as it does in plain text, which an Ecstasy file was before its PSI.
 *
 * IntelliJ's word completion runs in a file of a language of its own only where that language's filter allows it.
 * LSP4IJ turns word completion off whenever the server answers with items, so the words show only in place of an
 * empty answer: a name the server does not yet complete, such as one typed at the start of a statement.
 */
class XtcWordCompletionFilter : WordCompletionElementFilter {
    // the flat PSI knows no comments, strings or identifiers, so words complete everywhere, as in plain text
    override fun isWordCompletionEnabledIn(element: IElementType): Boolean = true
}
