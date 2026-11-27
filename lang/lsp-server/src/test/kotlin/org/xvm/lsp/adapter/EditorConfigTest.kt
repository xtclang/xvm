package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Files
import java.nio.file.Path

class EditorConfigTest {
    @TempDir
    lateinit var root: Path

    @ParameterizedTest(name = "[{0}] on {1}: {2}")
    @CsvSource(
        delimiter = '|',
        value = [
            "*            | App.x               | true",
            "*            | src/main/App.x      | true",
            "*.x          | src/main/App.x      | true",
            "*.x          | src/main/App.xtc    | false",
            "*.{x,xtc}    | App.xtc             | true",
            "*.{x,{y,z}}  | App.z               | true",
            "App?.x       | App1.x              | true",
            "App?.x       | App12.x             | false",
            "[AB]pp.x     | Bpp.x               | true",
            "[!AB]pp.x    | Bpp.x               | false",
            "[!AB]pp.x    | Cpp.x               | true",
            "src/*.x      | src/App.x           | true",
            "src/*.x      | lib/src/App.x       | false",
            "src/*.x      | src/main/App.x      | false",
            "/src/*.x     | src/App.x           | true",
            "src/**.x     | src/main/App.x      | true",
            "src/**/*.x   | src/App.x           | true",
            "**/main/*.x  | src/main/App.x      | true",
            "file{1..3}.x | file2.x             | true",
            "file{1..3}.x | file4.x             | false",
            "file{-3..-1}.x | file-2.x          | true",
            "{single}.x   | {single}.x          | true",
            "{single}.x   | single.x            | false",
            "a\\*b.x      | a*b.x               | true",
            "a\\*b.x      | aXb.x               | false",
            "a+b(c).x     | a+b(c).x            | true",
        ],
    )
    fun `a section's glob matches as the EditorConfig specification says`(
        glob: String,
        path: String,
        matches: Boolean,
    ) {
        assertThat(Glob(glob).matches(path)).describedAs("[%s] on %s as %s", glob, path, Glob(glob)).isEqualTo(matches)
    }

    @Test
    fun `a nearer file and a later section win, unset gives the editor's value back, and root ends the search`() {
        write(".editorconfig", "root = true\n[*]\nindent_size = 3\ntab_width = 9\nmax_line_length = 80\n")
        write("outer/.editorconfig", "[*.x]\nindent_size = 2\n[*.x]\nindent_size = 6\nmax_line_length = unset\n")
        write("outer/inner/.editorconfig", "# a comment\n; another\n[*.x]\nINDENT_SIZE = 5\nij_formatter_off_tag = @Fmt:Off\n")
        val file = write("outer/inner/App.x", "")
        // a standard property's value in lowercase, another's as it is written
        assertThat(EditorConfig.properties(file))
            .isEqualTo(mapOf("indent_size" to "5", "tab_width" to "9", "ij_formatter_off_tag" to "@Fmt:Off"))
        write("outer/.editorconfig", "root = true\n[*.x]\nindent_style = Tab\n")
        assertThat(EditorConfig.properties(file))
            .isEqualTo(mapOf("indent_style" to "tab", "indent_size" to "5", "ij_formatter_off_tag" to "@Fmt:Off"))
    }

    @Test
    fun `the indentation follows the specification's defaults between indent_style, indent_size and tab_width`() {
        val editor = FormattingConfig(indentSize = 4, continuationIndentSize = 8, insertSpaces = true)

        fun applied(vararg properties: Pair<String, String>) = EditorConfig.apply(mapOf(*properties), base(editor, tabSize = 8), root)

        // indent_style = tab with no indent_size indents by the tab width, the editor's when tab_width is not set
        applied("indent_style" to "tab").let {
            assertThat(it.config.insertSpaces).isFalse()
            assertThat(it.config.indentSize).isEqualTo(8)
            assertThat(it.options.insertSpaces).isFalse()
        }
        assertThat(applied("indent_style" to "tab", "tab_width" to "3").config.indentSize).isEqualTo(3)
        // the tab width is the indent_size when tab_width is not set
        assertThat(applied("indent_size" to "2").let { it.config.indentSize to it.options.tabSize }).isEqualTo(2 to 2)
        assertThat(applied("indent_size" to "tab", "tab_width" to "6").let { it.config.indentSize to it.options.tabSize }).isEqualTo(6 to 6)
        assertThat(applied("indent_size" to "2", "tab_width" to "8").let { it.config.indentSize to it.options.tabSize }).isEqualTo(2 to 8)
    }

    @Test
    fun `the line width, whitespace and IntelliJ's names set what the standard has no name for`() {
        val editor = FormattingConfig(continuationIndentSize = 8, maxLineWidth = 120, wrapComments = false)
        val applied =
            EditorConfig.apply(
                mapOf(
                    "max_line_length" to "100",
                    "trim_trailing_whitespace" to "false",
                    "insert_final_newline" to "false",
                    "ij_continuation_indent_size" to "6",
                    "ij_ecstasy_continuation_indent_size" to "4",
                    "ij_ecstasy_wrap_comments" to "true",
                ),
                base(editor),
                root,
            )
        assertThat(applied.config).isEqualTo(editor.copy(maxLineWidth = 100, continuationIndentSize = 4, wrapComments = true))
        assertThat(applied.options.trimTrailingWhitespace).isFalse()
        assertThat(applied.options.insertFinalNewline).isFalse()

        fun continuation(vararg properties: Pair<String, String>) =
            EditorConfig.apply(mapOf(*properties), base(editor), root).config.continuationIndentSize
        assertThat(continuation("ij_continuation_indent_size" to "6")).isEqualTo(6)
        assertThat(continuation("continuation_indent_size" to "5")).isEqualTo(5)
        assertThat(continuation("continuation_indent_size" to "5", "ij_continuation_indent_size" to "6")).isEqualTo(6)
        assertThat(EditorConfig.apply(mapOf("max_line_length" to "off"), base(editor), root).config.maxLineWidth).isZero()
    }

    @Test
    fun `IntelliJ's names set the formatter markers, and a tag that is no regular expression stays the editor's`() {
        val editor = FormattingConfig()
        val markers =
            EditorConfig
                .apply(
                    mapOf(
                        "ij_formatter_tags_enabled" to "TRUE",
                        "ij_formatter_off_tag" to "@Fmt:Off",
                        "ij_formatter_on_tag" to "@Fmt:On",
                    ),
                    base(editor),
                    root,
                ).config
        assertThat(markers).isEqualTo(editor.copy(formatterTagsEnabled = true, formatterOffTag = "@Fmt:Off", formatterOnTag = "@Fmt:On"))
        val regexp =
            EditorConfig
                .apply(
                    mapOf("ij_formatter_tags_accept_regexp" to "true", "ij_formatter_off_tag" to "no(", "ij_formatter_on_tag" to "on\\d"),
                    base(editor),
                    root,
                ).config
        assertThat(regexp).isEqualTo(editor.copy(formatterTagsAcceptRegexp = true, formatterOnTag = "on\\d"))
    }

    @Test
    fun `a value the formatter cannot use leaves the editor's`() {
        val editor = FormattingConfig()
        val applied =
            EditorConfig.apply(
                mapOf(
                    "indent_style" to "spaces",
                    "indent_size" to "0",
                    "max_line_length" to "5000",
                    "insert_final_newline" to "yes",
                    "end_of_line" to "crlf",
                    "charset" to "latin1",
                ),
                base(editor),
                root,
            )
        assertThat(applied).isEqualTo(base(editor))
    }

    @Test
    fun `an editor that turns EditorConfig off keeps its own settings`() {
        write(".editorconfig", "root = true\n[*.x]\nindent_size = 2\n")
        val uri = write("App.x", "").toUri().toString()
        val options = FormattingOptions(4, true)
        assertThat(EditorConfig.formatting(uri, options, FormattingConfig(indentSize = 4)).config.indentSize).isEqualTo(2)
        val off = FormattingConfig(indentSize = 4, editorConfig = false)
        assertThat(EditorConfig.formatting(uri, options, off)).isEqualTo(EditorConfig.Formatting(off, options))
    }

    @Test
    fun `the line ending is the file's end_of_line, for the editor to apply`() {
        val uri = write("App.x", "").toUri().toString()
        listOf("lf" to "lf", "CRLF" to "crlf", "cr" to "cr", "native" to null, "unset" to null).forEach { (value, ending) ->
            write(".editorconfig", "root = true\n[*.x]\nend_of_line = $value\n")
            assertThat(EditorConfig.endOfLine(uri, null)).describedAs(value).isEqualTo(ending)
        }
        write(".editorconfig", "root = true\n[*.x]\nend_of_line = crlf\n")
        assertThat(EditorConfig.endOfLine(uri, FormattingConfig(editorConfig = false))).isNull()
        assertThat(EditorConfig.endOfLine("untitled:Untitled-1", null)).isNull()
    }

    @Test
    fun `a document that is not a file keeps the editor's settings`() {
        val options = FormattingOptions(4, true)
        val editor = FormattingConfig(indentSize = 3)
        assertThat(EditorConfig.formatting("untitled:Untitled-1", options, editor))
            .isEqualTo(EditorConfig.Formatting(editor, options))
    }

    @Test
    fun `formatting a file follows its editorconfig, over the editor's settings`() {
        write(".editorconfig", "root = true\n[*.x]\nindent_size = 2\ninsert_final_newline = false\n")
        val file = write("App.x", "")
        val uri = file.toUri().toString()
        val text = "module App {\nvoid run() {\nreturn;\n}\n}"
        XdkAdapter().use { adapter ->
            adapter.editorFormattingConfig = FormattingConfig(indentSize = 4)
            val formatted = apply(text, adapter.formatDocument(uri, text, FormattingOptions(4, true)))
            assertThat(formatted).isEqualTo("module App {\n  void run() {\n    return;\n  }\n}")
            val typed = "module App {\n  void run() {\n\n  }\n}"
            adapter.compile(uri, typed)
            val indented = apply(typed, adapter.onTypeFormatting(uri, 2, 0, "\n", FormattingOptions(4, true)))
            assertThat(indented.lines()[2]).isEqualTo("    ")
        }
    }

    private fun base(
        config: FormattingConfig,
        tabSize: Int = 4,
    ) = EditorConfig.Formatting(config, FormattingOptions(tabSize, config.insertSpaces))

    private fun write(
        relative: String,
        text: String,
    ): Path = root.resolve(relative).also { Files.createDirectories(it.parent) }.also { Files.writeString(it, text) }
}
