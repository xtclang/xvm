package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/**
 * Checks rename across nested generic delegation and module boundaries. Written contracts and implementations
 * must remain one proven family after serialization and source edits.
 */
class XdkDelegatedRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["read", "value"])
    fun `nested generic delegates join written contracts and implementations across modules`(member: String) {
        val library = "module Library { interface Api<T> { T value; T read(); } }"
        val consumer =
            "module Consumer { package lib import Library; " +
                "class Engine implements lib.Api<String> { @Override String value = \"text\"; @Override String read() = value; } " +
                "class Forward(Engine target) delegates lib.Api<String>(target) {} " +
                "class Outer(Forward target) delegates lib.Api<String>(target) {} " +
                "String use(Outer box) = box.read() + box.value; }"
        directory.libraryAndConsumer(library, consumer) { adapter, libraryUri, consumerUri ->
            assertThat(adapter.compile(consumerUri, consumer).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(consumerUri, 0, consumer.lastIndexOf(member), "renamed"))
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(libraryUri, consumerUri)
            assertThat(apply(library, edit, libraryUri)).isEqualTo(library.replace(member, "renamed"))
            assertThat(apply(consumer, edit, consumerUri)).isEqualTo(consumer.replace(member, "renamed"))
        }
    }
}
