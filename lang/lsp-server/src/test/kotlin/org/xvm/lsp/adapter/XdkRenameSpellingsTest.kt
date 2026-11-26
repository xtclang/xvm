package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkLexical
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.XdkRenameSpellings

/**
 * The rename's note on the string literals and comments that spell the old name: which spellings count, words only and
 * a template's literal only, and how the note reads, counted and with its first places, beside the rename's own edits.
 */
class XdkRenameSpellingsTest {
    @Test
    fun `comments and string literals spell a name as a word, and a template's expressions are code`() {
        val text =
            """
            module Spell { // pick is here, not picked or repick
                /** Calls [pick]. */
                String run() = ${'$'}"{pick()} and pick" + "pick" + 'p';
                String pick() = "pick_up";
            }
            """.trimIndent()
        val spellings = XdkLexical.spellings(text, "pick")
        assertThat(spellings.map { text.substring(it.offset, it.offset + 4) }).containsOnly("pick")
        assertThat(spellings.map { XdkRename.position(text, it.offset).line to it.comment })
            .containsExactly(0 to true, 1 to true, 2 to false, 2 to false)
        assertThat(XdkLexical.spellings("module Bad { String text = \"unfinished", "text")).isEmpty()
    }

    @Test
    fun `the note counts the comments and literals of each kind, names the first and leaves out the rename's own edits`() {
        val first = "module A { // value\n    String a = \"value\";\n}"
        val second = "module B {\n    // value, value, value\n    Int value = 1;\n}"
        // as if the rename changed the comment's first spelling, which it then does not leave as it is
        val edit = XdkRename.Edit(second.indexOf("value"), second.indexOf("value") + 5, "count")
        val sources = mapOf("/b/B.x" to second, "/a/A.x" to first, "/c/C.x" to "module C { // value\n}")
        assertThat(XdkRenameSpellings.note("value", sources, mapOf("/b/B.x" to listOf(edit))))
            .isEqualTo(
                "Ecstasy: 1 string literal and 3 comments in the modules this rename changes spell 'value' and are not " +
                    "changed by it: A.x:1, A.x:2, B.x:2 and 1 more.",
            )
        assertThat(XdkRenameSpellings.note("value", mapOf("/a/A.x" to "module A { // value\n}"), emptyMap()))
            .isEqualTo("Ecstasy: 1 comment in the modules this rename changes spells 'value' and is not changed by it: A.x:1.")
        assertThat(XdkRenameSpellings.note("value", mapOf("/b/B.x" to "module B { Int value = 1; }"), emptyMap())).isNull()
    }
}
