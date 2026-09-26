package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.XdkWorkspaceDiscovery
import java.nio.file.Path
import java.util.concurrent.CancellationException

class XdkWorkspaceDiscoveryTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `discovery reads module imports ignores generated and bundled sources and honors buffers`() {
        val base = source("Base.x", "module Base { class Box {} }")
        val app = source("App.x", "module App { package lib import Base; }")
        source("App/Member.x", "class Member {}")
        source("build/Generated.x", "module Generated {}")
        source("Xml.x", "module xml.xtclang.org {}")
        source("Comment.x", "// module Fake {}\nclass Ordinary {}")
        val modules = XdkWorkspaceDiscovery.scan(listOf(directory.toFile()), emptyMap(), emptyList()) { false }
        assertThat(modules.map { it.name }).containsExactly("App", "Base")
        assertThat(modules.first().dependencies).containsExactly("Base")
        val edited = XdkWorkspaceDiscovery.scan(
            listOf(directory.toFile()), mapOf(app to "module App {}", base to "module Base { class Box {} }"), modules,
        ) { false }
        assertThat(edited.first().dependencies).isEmpty()
        assertThatThrownBy { XdkWorkspaceDiscovery.scan(listOf(directory.toFile()), emptyMap(), modules) { true } }
            .isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `unopened graph resolves imports and indexes independent healthy modules despite a broken neighbor`() {
        val base = source("Base.x", "module Base { static Int answer()=42; }")
        val appText = "module App { package lib import Base; Int run()=lib.answer(); }"
        val app = source("App.x", appText)
        source("Broken.x", "module Broken { Missing value; }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.findWorkspaceSymbols("answer").single().location.uri).isEqualTo(base)
            assertThat(adapter.compile(app, appText).diagnostics).isEmpty()
            assertThat(adapter.findDefinition(app, 0, appText.indexOf("answer"))!!.uri).isEqualTo(base)
            val added = source("Added.x", "module Added { class AddedType {} }")
            adapter.refreshDiscoveredSources()
            assertThat(adapter.findWorkspaceSymbols("AddedType").single().location.uri).isEqualTo(added)
            directory.resolve("Added.x").toFile().delete()
            adapter.refreshDiscoveredSources()
            assertThat(adapter.findWorkspaceSymbols("AddedType")).isEmpty()
        }
    }

    @Test
    fun `explicit graphs win and invalid replacements preserve automatic discovery`() {
        val base = source("Base.x", "module Base { class Box {} }")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val module = XdkSourceModule("Base", base)
            assertThatThrownBy { adapter.replaceSourceModules(listOf(module, module)) }
                .isInstanceOf(IllegalArgumentException::class.java)
            val other = source("Other.x", "module Other { class OtherBox {} }")
            adapter.refreshDiscoveredSources()
            assertThat(adapter.findWorkspaceSymbols("OtherBox").single().location.uri).isEqualTo(other)
            adapter.replaceSourceModules(emptyList())
            adapter.refreshDiscoveredSources()
            assertThat(adapter.findWorkspaceSymbols("Box")).isEmpty()
            adapter.discoverSourceModules()
            assertThat(adapter.findWorkspaceSymbols("Box")).hasSize(2)
        }
    }

    private fun source(name: String, text: String): String = directory.resolve(name).toFile().let {
        it.parentFile.mkdirs()
        it.writeText(text)
        it.toURI().toString()
    }
}
