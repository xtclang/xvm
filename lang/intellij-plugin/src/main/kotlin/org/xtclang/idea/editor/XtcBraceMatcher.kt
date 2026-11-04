package org.xtclang.idea.editor

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.textmate.editor.TextMateBraceMatcher

/**
 * TextMate's brace matching for an Ecstasy file, without pairing a typed bracket.
 *
 * An Ecstasy file has the language's flat PSI, so IntelliJ pairs a typed `(` or `[` as it does in a language of
 * its own, with the brace matcher of the editor's TextMate tokens. That matcher knows a closing bracket only where a
 * token ends at it, which the Ecstasy grammar's tokens seldom do, so the `)` typed next is inserted beside the
 * paired one instead of over it. Plain text pairs nothing, as an Ecstasy file did before its PSI, and neither does
 * this matcher; it still finds the braces that the caret highlights.
 */
class XtcBraceMatcher : TextMateBraceMatcher() {
    override fun isPairedBracesAllowedBeforeType(
        lbraceType: IElementType,
        contextType: IElementType?,
    ): Boolean = false
}
