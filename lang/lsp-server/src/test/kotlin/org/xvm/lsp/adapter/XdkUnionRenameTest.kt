package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

/**
 * Checks rename across all possible union-call targets and closed consumers. Reordered union legs or shared
 * spellings must not hide a changed binding.
 */
class XdkUnionRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `either declaration or the union call renames every possible contract`(entry: Int) {
        val text =
            """
            module App {
                class First { Int read() = 1; }
                class Second { Int read() = 2; }
                Int use(First | Second target) = target.read();
                class Other { Int read() = 3; }
            }
            """.trimIndent()
        directory.workspace(text) { adapter, uri ->
            val offset =
                Regex("read")
                    .findAll(text)
                    .elementAt(entry)
                    .range.first
            val changed = apply(text, requireNotNull(adapter.renameAt(uri, text, offset, "fetch")), uri)
            assertThat(changed).isEqualTo(text.replace("read", "fetch").replace("fetch() = 3", "read() = 3"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            val undo = requireNotNull(adapter.renameAt(uri, changed, changed.indexOf("fetch"), "read"))
            assertThat(apply(changed, undo, uri)).isEqualTo(text)
        }
    }

    @Test
    fun `nested union legs and direct consumers retain the same targets`() {
        val text =
            """
            module App {
                class First { Int read() = 1; }
                class Second { Int read() = 2; }
                class Third { Int read() = 3; }
                Int use(First | Second | Third target) = target.read();
                Int direct(Second target) = target.read();
            }
            """.trimIndent()
        directory.assertReadRenamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `a closed cross-module union consumer joins otherwise independent source families`() {
        val first =
            """
            module First {
                class Box { Int read() = 1; }
            }
            """.trimIndent()
        val second =
            """
            module Second {
                class Box { Int read() = 2; }
            }
            """.trimIndent()
        val consumer =
            """
            module Consumer {
                package one import First;
                package two import Second;
                Int use(one.Box | two.Box target) = target.read();
            }
            """.trimIndent()
        val sources = mapOf("First" to first, "Second" to second, "Consumer" to consumer)
        val uris =
            sources.mapValues { (name, text) ->
                directory
                    .resolve("$name.x")
                    .toFile()
                    .apply { writeText(text) }
                    .canonicalFile
                    .toURI()
                    .toString()
            }
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(
                sources.keys.map { name ->
                    XdkSourceModule(name, uris.getValue(name), if (name == "Consumer") setOf("First", "Second") else emptySet())
                },
            )
            assertThat(adapter.compile(uris.getValue("First"), first).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.renameAt(uris.getValue("First"), first, first.indexOf("read"), "fetch"))
            assertThat(edit.changes.keys).containsExactlyInAnyOrderElementsOf(uris.values)
            sources.forEach { (name, text) ->
                assertThat(apply(text, edit.changes.getValue(uris.getValue(name)))).isEqualTo(text.replace("read", "fetch"))
            }
        }
    }

    @Test
    fun `renaming a receiver class may reorder union legs without rebinding calls`() {
        val text =
            """
            module App {
                class First { Int read() = 1; }
                class Second { Int read() = 2; }
                Int use(First | Second target) = target.read();
            }
            """.trimIndent()
        directory.workspace(text) { adapter, uri ->
            val edit = requireNotNull(adapter.renameAt(uri, text, text.indexOf("First"), "Zed"))
            val changed = apply(text, edit, uri)
            assertThat(changed).isEqualTo(text.replace("First", "Zed"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `collisions on one leg refuse the entire edit`() {
        val text =
            """
            module App {
                class First {
                    Int read() = 1;
                    Int fetch() = 3;
                }
                class Second { Int read() = 2; }
                Int use(First | Second target) = target.read();
            }
            """.trimIndent()
        directory.workspace(text) { adapter, uri ->
            assertThat(adapter.renameAt(uri, text, text.lastIndexOf("read"), "fetch")).isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int", "String", "List<String>", "Map<String, Int>", "List<Int | String>"])
    fun `generic union receivers preserve nested substitutions during rename`(argument: String) {
        val text =
            """
            module App {
                class First<T>(T value) { T read() = value; }
                class Second<T>(T value) { T read() = value; }
                $argument use(First<$argument> | Second<$argument> target) = target.read();
            }
            """.trimIndent()
        directory.assertReadRenamed(text, text.lastIndexOf("read"), reversible = true)
    }

    @ParameterizedTest
    @MethodSource("annotatedReceivers")
    fun `annotated union receivers preserve annotation values and composed methods`(text: String) {
        directory.assertReadRenamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `virtual child union receivers retain their enclosing source ownership`() {
        val text =
            """
            module App {
                class Owner {
                    class First { Int read() = 1; }
                    class Second { Int read() = 2; }
                    Int use(First | Second target) = target.read();
                }
            }
            """.trimIndent()
        directory.assertReadRenamed(text, text.lastIndexOf("read"))
    }

    @ParameterizedTest
    @MethodSource("annotationArguments")
    fun `compound and numeric annotation arguments permit proven union rename`(argument: Pair<String, String>) {
        val (type, value) = argument
        val text =
            """
            module App {
                class First { Int read() = 1; }
                class Second { Int read() = 2; }
                annotation Mark($type value) into Object {}
                Int use(Boolean choose) {
                    (@Mark($value) First) | Second target = choose ? new @Mark($value) First() : new Second();
                    return target.read();
                }
            }
            """.trimIndent()
        directory.assertReadRenamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `renaming a substituted source type translates identities inside receiver type arguments`() {
        val text =
            """
            module App {
                class Payload {}
                class First<T>(T value) { T read() = value; }
                class Second<T>(T value) { T read() = value; }
                Payload use(First<Payload> | Second<Payload> target) = target.read();
            }
            """.trimIndent()
        directory.workspace(text) { adapter, uri ->
            val changed = apply(text, requireNotNull(adapter.renameAt(uri, text, text.indexOf("Payload"), "Content")), uri)
            assertThat(changed).isEqualTo(text.replace("Payload", "Content"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `source formal arguments retain their declaration identity`() {
        val text =
            """
            module App {
                class First<T>(T value) { T read() = value; }
                class Second<T>(T value) { T read() = value; }
                class Consumer<T>(First<T> | Second<T> target) {
                    T use() = target.read();
                }
            }
            """.trimIndent()
        directory.assertReadRenamed(text, text.indexOf("read"))
    }

    private companion object {
        @JvmStatic
        fun annotationArguments() =
            listOf(
                "Byte" to "Byte:7",
                "Float64" to "Float64:1.5",
                "Dec64" to "Dec64:1.5",
                "Int[]" to "Int[]:[1, 2]",
                "Map<String, Int>" to "Map<String, Int>:[\"one\" = 1]",
                "Range<Int>" to "Int:1..<Int:3",
                "Tuple<Int, String>" to "Tuple:(Int:1, \"two\")",
            )

        @JvmStatic
        fun annotatedReceivers() =
            listOf(
                """
                module App {
                    class First { Int read() = 1; }
                    class Second { Int read() = 2; }
                    annotation Mark(String label) into Object {}
                    Int use((@Mark("one") First) | (@Mark("two") Second) target) = target.read();
                }
                """.trimIndent(),
                """
                module App {
                    class First { Int read() = 1; }
                    class Second { Int read() = 2; }
                    annotation Loud into First { @Override Int read() = super() + 1; }
                    Int use((@Loud First) | Second target) = target.read();
                }
                """.trimIndent(),
            )
    }
}
