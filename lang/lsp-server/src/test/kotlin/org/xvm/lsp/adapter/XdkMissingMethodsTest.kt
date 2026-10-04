package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import java.nio.file.Path

class XdkMissingMethodsTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @CsvSource(value = ["Int;Int64", "String;String", "List<Int>;List<Int64>", "(Int | String);(Int64 | String)"], delimiter = ';')
    fun `resolved parameter and return types produce a private method`(
        type: String,
        rendered: String,
    ) {
        query(
            """
            module Missing {
                $type read($type value) {
                    return §missing(value);
                }
            }
            """.trimIndent(),
        ) { changed ->
            assertThat(changed).contains("private $rendered missing($rendered arg1)", "TODO();", "return missing(value);")
        }
    }

    @Test
    fun `statement call creates void method with no arguments`() {
        query(
            """
            module Missing {
                void run() {
                    §missing();
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private void missing()") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["this", "this:private", "peer"])
    fun `same owner receiver creates an instance method`(receiver: String) {
        query(
            """
            module Missing {
                class Box {
                    Int read(Box peer, Int value) {
                        return $receiver.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing(Int64 arg1)").doesNotContain("private static") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "static "])
    fun `class qualifier creates a static method regardless of the caller`(modifier: String) {
        query(
            """
            module Missing {
                class Box {
                    ${modifier}Int read(Int value) {
                        return Box.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private static Int64 missing(Int64 arg1)", "Box.missing(value)") }
    }

    @Test
    fun `fully qualified owner preserves typed initializer and local argument evidence`() {
        query(
            """
            module Missing {
                class Box {
                    void read(List<Int> values) {
                        val other = values;
                        List<Int> result = Missing.Box.§missing(other);
                    }
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private static List<Int64> missing(List<Int64> arg1)", "Missing.Box.missing(other)") }
    }

    @Test
    fun `class qualifier supports a void call and multiple compatible sites`() {
        query(
            """
            module Missing {
                class Box {
                    void run() {
                        Box.§missing();
                        Box.missing();
                    }
                }
            }
            """.trimIndent(),
            wholeFile = true,
        ) { assertThat(it).contains("private static void missing()") }
    }

    @Test
    fun `parameter shadowing the class name remains an instance receiver`() {
        query(
            """
            module Missing {
                class Box {
                    static Int read(Missing.Box Box, Int value) {
                        return Box.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing(Int64 arg1)").doesNotContain("private static Int64 missing") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Other", "Outer.Box", "type", "clz"])
    fun `other owner and runtime type qualifiers remain refusals`(qualifier: String) {
        refused(
            """
            module Missing {
                class Other {}
                class Outer { class Box {} }
                class Box {
                    Int read(Type<Box> type, Class<Box> clz, Int value) {
                        return $qualifier.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `a class spelling shadowed by a different instance type does not establish ownership`() {
        refused(
            """
            module Missing {
                class Box {
                    Int read(String Box, Int value) {
                        return Box.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `module singleton qualifier retains instance dispatch`() {
        query(
            """
            module Missing {
                static Int read(Int value) {
                    return Missing.§missing(value);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing(Int64 arg1)").doesNotContain("private static") }
    }

    @Test
    fun `explicit type arguments do not acquire a guessed static signature`() {
        refused(
            """
            module Missing {
                class Box<Element> {
                    Int read(Int value) {
                        return Box<Int>.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `existing static overload is not mistaken for a missing declaration`() {
        refused(
            """
            module Missing {
                class Box {
                    static Int missing(Int value) = value;
                    Int read(String value) {
                        return Box.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `module this receiver supports a void statement call`() {
        query(
            """
            module Missing {
                void run() {
                    this.§missing();
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private void missing()") }
    }

    @Test
    fun `selecting only the receiver does not offer creation for the member`() {
        refused(
            """
            module Missing {
                class Box {
                    Int read(Box peer, Int value) {
                        return §peer.missing(value);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Box other = peer;", "var other = peer;", "val other = peer;"])
    fun `same owner local receiver supports typed initializer result in a static caller`(local: String) {
        query(
            """
            module Missing {
                class Box {
                    static Int read(Box peer, Int value) {
                        $local
                        Int result = other.§missing(value);
                        return result;
                    }
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing(Int64 arg1)").doesNotContain("private static Int64 missing") }
    }

    @Test
    fun `explicit receiver bypasses unrelated local name shadowing`() {
        query(
            """
            module Missing {
                Int read(Int value) {
                    Int missing = value;
                    return this.§missing(missing);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing(Int64 arg1)", "this.missing(missing)") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["other", "otherPeer", "base", "super", "this:public", "this:protected", "this:struct"])
    fun `unproven receiver owners and access views remain refusals`(receiver: String) {
        refused(
            """
            module Missing {
                class Base {}
                class Other {}
                class Box extends Base {
                    Box make() = this;
                    Int read(Other other, Base base, String otherPeer, Int value) {
                        return $receiver.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `a class with the same short name is not the same receiver owner`() {
        refused(
            """
            module Missing {
                class Outer { class Box {} }
                class Box {
                    Int read(Outer.Box peer, Int value) {
                        return peer.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `this in a static caller does not invent instance access`() {
        refused(
            """
            module Missing {
                class Box {
                    static Int read(Int value) {
                        return this.§missing(value);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `qualified existing overload remains an overload error`() {
        refused(
            """
            module Missing {
                Int missing(Int value) = value;
                Int read(String value) {
                    return this.§missing(value);
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `static typed return preserves CRLF and unicode`() {
        query(
            """
            module Missing {
                // 😀 keep the call's UTF-16 position
                static Int read(Int value) {
                    return §missing(value);
                }
            }
            """.trimIndent().replace("\n", "\r\n"),
        ) { assertThat(it).contains("private static Int64 missing(Int64 arg1) {\r\n        TODO();\r\n") }
    }

    @Test
    fun `class owner and multiple returns preserve their written contract`() {
        query(
            """
            module Missing {
                class Box {
                    (Int, String) read(Int value, String label) {
                        return §missing(value, label, value);
                    }
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("        private (Int64, String) missing(Int64 arg1, String arg2, Int64 arg3)") }
    }

    @Test
    fun `literal types come from the compiler without exposing numeric literal internals`() {
        query(
            """
            module Missing {
                void run() {
                    §missing("hello", 'x', Int8:7);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private void missing(String arg1, Char arg2, Int8 arg3)") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int other = value;", "var other = value;", "val other = value;", "Int other; other = value;"])
    fun `compiler typed local arguments retain their declaration binding`(local: String) {
        query(
            """
            module Missing {
                Int read(Int value) {
                    $local
                    return §missing(other);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing(Int64 arg1)") }
    }

    @ParameterizedTest
    @CsvSource(value = ["Int;Int64", "String;String", "List<Int>;List<Int64>", "(Int | String);(Int64 | String)"], delimiter = ';')
    fun `explicit initializer result supplies the method return type`(
        type: String,
        rendered: String,
    ) {
        query(
            """
            module Missing {
                void read($type value) {
                    $type result = §missing(value);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private $rendered missing($rendered arg1)") }
    }

    @Test
    fun `typed initializer and inferred local arguments compose in a static class method`() {
        query(
            """
            module Missing {
                class Box {
                    static String read(Int value) {
                        var other = value;
                        String result = §missing(other, other, "hello");
                        return result;
                    }
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private static String missing(Int64 arg1, Int64 arg2, String arg3)") }
    }

    @Test
    fun `local arguments in an enclosing block keep their compiler type`() {
        query(
            """
            module Missing {
                Int read(Int value) {
                    List<Int> values = [value];
                    if (value > 0) {
                        return §missing(values);
                    }
                    return value;
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing(List<Int64> arg1)") }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "return §missing(other); Int other = value;",
            "if (value > 0) { Int other = value; } return §missing(other);",
            "Int other; return §missing(other);",
            "var other = unknown(); return §missing(other);",
            "var other = §missing(other); return other;",
            "var result = §missing(value); return result;",
            "val result = §missing(value); return result;",
            "Int result = 1 + §missing(value); return result;",
            "Int result = value; result = §missing(value); return result;",
        ],
    )
    fun `unproven local scopes and initializer types remain refusals`(body: String) {
        refused(
            """
            module Missing {
                Int read(Int value) {
                    $body
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `a broad selection offers one repair for repeated calls with matching signatures`() {
        query(
            """
            module Missing {
                Int read(Int value) {
                    return §missing(value);
                }
                Int other(Int value) {
                    return missing(value);
                }
            }
            """.trimIndent(),
            wholeFile = true,
        ) { assertThat(it).contains("private Int64 missing(Int64 arg1)") }
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "True;True arg1", "Null;Null arg1", "value + 1;Int64 arg1", "value = value;Int64 value",
            "() -> value;Function<Tuple<>, Tuple<Int64>> arg1", "[value];immutable Array<Int64> arg1",
        ],
        delimiter = ';',
    )
    fun `compiler established argument types replace earlier conservative refusals`(
        argument: String,
        parameter: String,
    ) {
        query(
            """
            module Missing {
                Int read(Int value) {
                    return §missing($argument);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private Int64 missing($parameter)", "return missing($argument);") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1", "1.5"])
    fun `unproven argument types do not invent a signature`(argument: String) {
        refused(
            """
            module Missing {
                Int read(Int value) {
                    return §missing($argument);
                }
            }
            """.trimIndent(),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "return 1 + §missing(value);",
            "return §missing<Int>(value);",
        ],
    )
    fun `unsupported receiver argument and expected type contexts remain refusals`(body: String) {
        refused(
            """
            module Missing {
                Int read(Int value) {
                    $body
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `existing overload is not mistaken for an absent method`() {
        refused(
            """
            module Missing {
                Int missing(Int value) = value;
                Int read(String text) {
                    return §missing(text);
                }
            }
            """.trimIndent(),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Object", "Const", "Hashable"])
    fun `method formals and constraints are declared by the generated sibling`(bound: String) {
        query(
            """
            module Missing {
                <Value extends $bound> Value read(Value value) {
                    return §missing(value);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private <Value extends $bound> Value missing(Value arg1)") }
    }

    @Test
    fun `conditional return context preserves the conditional signature`() {
        query(
            """
            module Missing {
                conditional Int read(Int value) {
                    return §missing(value);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("private conditional Int64 missing(Int64 arg1)") }
    }

    @Test
    fun `lambda cannot acquire a method with its enclosing method return type`() {
        refused(
            """
            module Missing {
                Int read(Int value) {
                    function Int () compute = () -> { return §missing(value); };
                    return compute();
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `a partial repair cannot hide an unrelated broken source graph`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Unknown value; }")
        refused(
            """
            module Missing {
                void run() {
                    §missing();
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `existing calls and overload selection survive the addition`() {
        query(
            """
            module Missing {
                Int keep(Int value) = value;
                Int keep(String value) = value.size;
                Int read(Int value) {
                    Int previous = keep(value);
                    return §missing(value);
                }
            }
            """.trimIndent(),
        ) { assertThat(it).contains("Int previous = keep(value);", "private Int64 missing(Int64 arg1)") }
    }

    private fun refused(marked: String) = query(marked, expected = false) { error("Unexpected method creation") }

    private fun query(
        marked: String,
        expected: Boolean = true,
        wholeFile: Boolean = false,
        check: (String) -> Unit,
    ) {
        CompilerTestSupport.configure()
        val offset = marked.indexOf('§')
        val text = marked.replace("§", "")
        val file =
            directory
                .resolve("Missing.x")
                .toFile()
                .apply { writeText(text) }
                .canonicalFile
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val diagnostics = adapter.compile(uri, text).diagnostics
            assertThat(diagnostics).isNotEmpty()
            assertThat(diagnostics).noneMatch { it.code == "EMB-5" }
            val at = XdkRename.position(text, offset)
            val end = if (wholeFile) XdkRename.position(text, text.length) else at
            val action =
                adapter.getCodeActions(uri, Range(at, end), diagnostics).singleOrNull {
                    it.title ==
                        "Create private method 'missing'"
                }
            if (!expected) {
                assertThat(action).isNull()
                return
            }
            assertThat(action).describedAs("Missing method action for %s%nCompiler diagnostics: %s", marked, diagnostics).isNotNull()
            assertThat(action!!.kind).isEqualTo(CodeAction.CodeActionKind.QUICKFIX)
            val edit = requireNotNull(action.edit)
            assertThat(edit.versioned).isTrue()
            assertThat(edit.changes.keys).containsExactly(uri)
            val changed =
                edit.changes
                    .getValue(uri)
                    .sortedByDescending { it.range.start.line * text.length + it.range.start.column }
                    .fold(text) { value, change ->
                        fun at(position: Position) =
                            requireNotNull(XdkRename.offset(text, SemanticModel.Position(position.line, position.column)))
                        value.replaceRange(at(change.range.start), at(change.range.end), change.newText)
                    }
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).filter { it.title == action.title }).isEmpty()
            assertThat(file.readText()).isEqualTo(text)
            check(changed)
        }
    }
}
