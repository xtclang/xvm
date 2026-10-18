package org.xtclang.idea.editor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Where the Enter repair takes a block's indentation from: the first line of its declaration. */
class XtcEnterHandlerDelegateTest {
    private fun start(vararg lines: String): Int = declarationStart(lines.lastIndex) { lines[it] }

    @Test
    fun `a declaration on one line starts at its brace line`() {
        assertThat(start("module OnType {", "    void run() {")).isEqualTo(1)
        assertThat(start("        Int x = 1;", "        if (True) {")).isEqualTo(1)
    }

    @Test
    fun `a continued declaration starts at its first line`() {
        assertThat(start("module OnType {", "    class Point", "            implements Const {")).isEqualTo(1)
        assertThat(start("    }", "    void run(Int a,", "             Int b) {")).isEqualTo(1)
        assertThat(start("module OnType {", "    @Override", "    void run() {")).isEqualTo(1)
    }

    @Test
    fun `a comment or blank line above is not part of the declaration`() {
        assertThat(start("    // a note", "    void run() {")).isEqualTo(1)
        assertThat(start("     */", "    void run() {")).isEqualTo(1)
        assertThat(start("", "    class Point {")).isEqualTo(1)
        assertThat(start("    class Point {")).isEqualTo(0)
    }
}
