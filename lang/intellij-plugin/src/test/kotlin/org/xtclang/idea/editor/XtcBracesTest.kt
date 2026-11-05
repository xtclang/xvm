package org.xtclang.idea.editor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor

/** Enter after a `{` that the code leaves open closes its block, in an editor with TextMate's tokens. */
class XtcBracesTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `Enter after an opened block closes it`() {
        editor.open("Braces.x", "module M {\n    void run() {\n        Int x = 1;<caret>\n    }\n}\n") {
            type("\nif (x == 1) {\n")
            assertThat(editor.document.text)
                .isEqualTo("module M {\n    void run() {\n        Int x = 1;\n        if (x == 1) {\n            \n        }\n    }\n}\n")
            assertThat(editor.caretModel.offset).isEqualTo(editor.document.text.indexOf("\n        }"))
        }
    }

    @Test
    fun `Enter in an opened block before more of the line takes the rest of the line into the block, indented`() {
        editor.open("Braces.x", "module M {\n    Int run() {\n        <caret>return 1;\n    }\n}\n") {
            type("if (True) {\n")
            // indented here, not by the language server's on-type formatting, which LSP4IJ waits for only so long
            assertThat(editor.document.text)
                .isEqualTo("module M {\n    Int run() {\n        if (True) {\n            return 1;\n        }\n    }\n}\n")
            assertThat(editor.caretModel.offset).isEqualTo(editor.document.text.indexOf("return 1;"))
        }
    }

    @Test
    fun `a typed condition with a call keeps its parentheses where they are typed`() {
        editor.open("Braces.x", "module M {\n    Int run(Int[] hand) {\n        <caret>return 1;\n    }\n}\n") {
            type("if (hand.empty()) {\n")
            assertThat(editor.document.text)
                .isEqualTo(
                    "module M {\n    Int run(Int[] hand) {\n        if (hand.empty()) {\n            return 1;\n        }\n    }\n}\n",
                )
        }
    }

    @Test
    fun `braces in strings and comments are not the code's`() {
        val source = "module M {\n    String s = \"{\"; // {\n    void run() {<caret>}\n}\n"
        editor.open("Braces.x", source.replace("{<caret>}", "{<caret>")) {
            type("\n")
            assertThat(editor.document.text.count { it == '}' }).isEqualTo(2)
        }
        editor.open("Braces.x", source) {
            type("\n")
            assertThat(editor.document.text.count { it == '}' }).isEqualTo(2)
        }
    }
}
