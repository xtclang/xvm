package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/**
 * Checks rename through unions nested in generic and recursive delegation. Every possible written contract
 * and receiver remains part of the proof, including alternatives hidden behind generated dispatch.
 */
class XdkUnionDelegationRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 3])
    fun `a union inside two delegate layers retains every contract and receiver`(entry: Int) {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class First implements Api { @Override Int read() = 1; }
                class Second implements Api { @Override Int read() = 2; }
                class Forward(First | Second target) delegates Api(target) {}
                class Outer(Forward target) delegates Api(target) {}
                Int use(Outer value) = value.read();
            }
            """.trimIndent()
        directory.assertReadRenamed(
            text,
            Regex("read")
                .findAll(text)
                .elementAt(entry)
                .range.first,
            reversible = true,
        )
    }

    @Test
    fun `generic union delegate branches preserve concrete substitutions`() {
        val text =
            """
            module App {
                interface Api<T> { T read(); }
                class First<T>(T item) implements Api<T> { @Override T read() = item; }
                class Second<T>(T item) implements Api<T> { @Override T read() = item; }
                class Forward(First<Int> | Second<Int> target) delegates Api<Int>(target) {}
                Int use(Forward value) = value.read();
            }
            """.trimIndent()
        directory.assertReadRenamed(text, text.lastIndexOf("read"), reversible = true)
    }

    @Test
    fun `an implicit call to a generated union delegate joins the written family`() {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class First implements Api { @Override Int read() = 1; }
                class Second implements Api { @Override Int read() = 2; }
                class Forward(First | Second target) delegates Api(target) {
                    Int use() = read();
                }
            }
            """.trimIndent()
        directory.assertReadRenamed(text, text.lastIndexOf("read"), reversible = true)
    }

    @Test
    fun `one recursive alternative does not hide the other branch`() {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class Forward(Left | Right target) delegates Api(target) {}
                class Left(Forward target) delegates Api(target) {}
                class Right implements Api { @Override Int read() = 1; }
                Int use(Forward value) = value.read();
            }
            """.trimIndent()
        directory.assertReadRenamed(text, text.lastIndexOf("read"), reversible = true)
    }

    @Test
    fun `a collision on one delegate branch refuses all edits`() {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class First implements Api {
                    @Override Int read() = 1;
                    Int fetch() = 3;
                }
                class Second implements Api { @Override Int read() = 2; }
                class Forward(First | Second target) delegates Api(target) {}
                Int use(Forward value) = value.read();
            }
            """.trimIndent()
        directory.workspace(text) { adapter, uri ->
            assertThat(adapter.renameAt(uri, text, text.lastIndexOf("read"), "fetch")).isNull()
        }
    }
}
