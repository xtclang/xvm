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
    fun `Enter in an opened block before more of the line takes the rest of the line into the block`() {
        editor.open("Braces.x", "module M {\n    Int run() {\n        <caret>return 1;\n    }\n}\n") {
            type("if (True) {\n")
            assertThat(editor.document.text).containsPattern("""if \(True\) \{\n[ \t]*return 1;\n[ \t]*}\n    }\n}\n$""")
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
