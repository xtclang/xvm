package org.xtclang.idea

import com.intellij.openapi.actionSystem.IdeActions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Ctrl+/ and Ctrl+Shift+/ comment Ecstasy code with XtcCommenter, which IntelliJ finds by the PSI's language. */
class XtcCommenterTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `Comment with Line Comment comments the selected lines and again uncomments them`() {
        val source = "module M {\n    Int run() {\n<selection>        Int x = 1;\n        return x;\n</selection>    }\n}\n"
        editor.open("Comments.x", source) {
            performEditorAction(IdeActions.ACTION_COMMENT_LINE)
            // XtcCommenter's prefix, at the first column as the code style's default says
            assertThat(editor.document.text)
                .isEqualTo("module M {\n    Int run() {\n//         Int x = 1;\n//         return x;\n    }\n}\n")
            performEditorAction(IdeActions.ACTION_COMMENT_LINE)
            assertThat(editor.document.text).isEqualTo(source.replace("<selection>", "").replace("</selection>", ""))
        }
    }

    @Test
    fun `Comment with Block Comment wraps the selection`() {
        editor.open("Comments.x", "module M {\n    Int x = <selection>1 + 2</selection>;\n}\n") {
            performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
            assertThat(editor.document.text).isEqualTo("module M {\n    Int x = /*1 + 2*/;\n}\n")
        }
    }
}
