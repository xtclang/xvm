package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkWorkspaceNavigationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `unopened consumers contribute implementations subtype edges and selected incoming calls`() {
        val libraryText = "module Library { class Base { Int pick(Int value)=value; String pick(String value)=value; } }"
        val library = source("Library", libraryText)
        val consumerText = "module Consumer { package lib import Library; class Child extends lib.Base { " +
            "@Override Int pick(Int value)=value+1; } Int run(lib.Base box)=box.pick(1); }"
        val consumer = source("Consumer", consumerText)
        source("Unrelated", "module Unrelated { class Base { Int pick(Int value)=value; } }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(library, libraryText).diagnostics).isEmpty()
            val type = adapter.prepareTypeHierarchy(library, 0, libraryText.indexOf("Base")).single()
            val child = adapter.getSubtypes(type).single()
            assertThat(child.uri).isEqualTo(consumer)
            assertThat(child.name).isEqualTo("Child")
            assertThat(adapter.getSupertypes(child).single().uri).isEqualTo(library)
            assertThat(adapter.findImplementation(library, 0, libraryText.indexOf("pick")).map { it.uri })
                .contains(consumer).doesNotContain(directory.resolve("Unrelated.x").toUri().toString())
            val method = adapter.prepareCallHierarchy(library, 0, libraryText.indexOf("pick")).single()
            val incoming = adapter.getIncomingCalls(method).single()
            assertThat(incoming.from.uri).isEqualTo(consumer)
            assertThat(incoming.from.name).isEqualTo("run")
            assertThat(incoming.fromRanges.single().start.column).isEqualTo(consumerText.lastIndexOf("pick"))
            assertThat(adapter.getOutgoingCalls(incoming.from).single().to.uri).isEqualTo(library)
            val other = adapter.prepareCallHierarchy(library, 0, libraryText.lastIndexOf("pick")).single()
            assertThat(adapter.getIncomingCalls(other)).isEmpty()
            assertThat(adapter.findDefinition(consumer, 0, consumerText.lastIndexOf("pick"))!!.uri).isEqualTo(library)
            assertThat(adapter.getCachedResult(consumer)).isNull()
        }
    }

    @Test
    fun `hierarchy handles reject closed file edits without relying on watcher timing`() {
        val libraryText = "module Library { class Base {} }"
        val library = source("Library", libraryText)
        source("Consumer", "module Consumer { package lib import Library; class Child extends lib.Base {} }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val item = adapter.prepareTypeHierarchy(library, 0, libraryText.indexOf("Base")).single()
            assertThat(adapter.getSubtypes(item)).hasSize(1)
            source("Consumer", "module Consumer { package lib import Library; class Child {} }")
            assertThat(adapter.getSubtypes(item)).isEmpty()
            val fresh = adapter.prepareTypeHierarchy(library, 0, libraryText.indexOf("Base")).single()
            assertThat(fresh.data).isNotEqualTo(item.data)
            assertThat(adapter.getSubtypes(fresh)).isEmpty()
        }
    }

    private fun source(name: String, text: String): String = directory.resolve("$name.x").toFile().let {
        it.writeText(text)
        it.toURI().toString()
    }
}
