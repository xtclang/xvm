package org.xtclang.idea

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Gives an Ecstasy file a PSI of its own language: a flat [XtcPsiFile] of [XtcLexer]'s tokens.
 *
 * Without a parser definition, IntelliJ makes a `.x` file's PSI plain text, whose language is TEXT, and the
 * features it looks up by the PSI's language, such as the commenter that plugin.xml registers for Ecstasy, never
 * apply. The PSI has no syntax: the language server analyzes the file, and TextMate highlights it.
 */
class XtcParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = XtcLexer()

    override fun createParser(project: Project?): PsiParser =
        PsiParser { root, builder ->
            val file = builder.mark()
            while (!builder.eof()) builder.advanceLexer()
            file.done(root)
            builder.treeBuilt
        }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    // the parser makes no element but the file's
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = XtcPsiFile(viewProvider)

    private companion object {
        val FILE = IFileElementType(XtcIntelliJLanguage)
    }
}

/** An Ecstasy file's PSI: its tokens, with no structure. */
class XtcPsiFile(
    viewProvider: FileViewProvider,
) : PsiFileBase(viewProvider, XtcIntelliJLanguage) {
    override fun getFileType(): FileType = XtcFileType

    override fun toString(): String = "Ecstasy file"
}
