package org.xtclang.idea

import com.intellij.lang.Commenter

/**
 * Tells IntelliJ what comment syntax XTC uses, enabling Ctrl+/ (toggle line comment)
 * and Ctrl+Shift+/ (toggle block comment).
 *
 * ## Why this lives in the IntelliJ plugin, not the LSP server
 *
 * Comment toggling is a **text editing action**, not a language intelligence feature.
 * When the user presses Ctrl+/, the IDE needs to insert or remove `//` immediately —
 * no round-trip to the language server. The LSP specification has no "toggle comment"
 * request; it intentionally leaves this to the client.
 *
 * What the LSP server *does* handle is **formatting comments** after they exist:
 * the `textDocument/onTypeFormatting` handler auto-continues `*` prefixes inside
 * doc comments on Enter, and `textDocument/formatting` aligns comment interior lines.
 * Those are formatting concerns that benefit from AST context. But inserting the
 * comment delimiters themselves is purely a client-side operation.
 */
class XtcCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "// "

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
