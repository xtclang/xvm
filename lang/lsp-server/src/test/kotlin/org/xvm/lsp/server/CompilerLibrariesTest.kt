package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.CompilerTestSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuildModel
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.adapter.xdk.toDependency
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class CompilerLibrariesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `paths preserve order distinguish inheritance and empty and reject aliases or ambiguous relative owners`() {
        val base = directory.toUri().toString()

        fun read(paths: Any?) = CompilerLibraries.read(mapOf("libraries" to mapOf("modulePath" to paths)), listOf(base))!!
        assertThat(CompilerLibraries.read(emptyMap<String, Any>(), listOf(base))).isNull()
        assertThat(read(null).modulePath).isNull()
        assertThat(read(emptyList<String>()).modulePath).isEmpty()
        assertThat(
            read(listOf("second", "first")).modulePath,
        ).containsExactly(directory.resolve("second").toFile().canonicalFile, directory.resolve("first").toFile().canonicalFile)
        assertThatThrownBy { read(listOf("first", "./first")) }.hasMessageContaining("Duplicate")
        assertThatThrownBy { read(listOf("https://example.org/library.xtc")) }.hasMessageContaining("local file")
        assertThatThrownBy {
            CompilerLibraries.read(mapOf("libraries" to mapOf("modulePath" to listOf("relative"))), listOf(base, "file:///other/"))
        }.hasMessageContaining("exactly one")
        assertThatThrownBy { read(listOf("missing")).resolve(emptyList()) }.hasMessageContaining("does not exist")
    }

    @Test
    fun `ordered external binaries and attached navigation stay read only and expire on removal`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Attached {
                static Int value() = 7;
            }
            """.trimIndent()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(text, "Attached.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val binary = directory.resolve("Attached.xtc")
        Files.write(binary, compilation.toDependency().bytes())
        val sources = Files.createDirectories(directory.resolve("sources"))
        Files.writeString(sources.resolve("Attached.x"), text)
        val raw =
            mapOf(
                "libraries" to
                    mapOf(
                        "modulePath" to listOf(binary.toUri().toString()),
                        "sourceAttachments" to listOf(mapOf("module" to "Attached", "roots" to listOf(sources.toUri().toString()))),
                    ),
            )
        val libraries = CompilerLibraries.read(raw, emptyList())!!
        val artifacts = libraries.resolve(emptyList())
        val uri = directory.resolve("Consumer.x").toUri().toString()
        val consumer = "module Consumer { package lib import Attached; Int run() = lib.value(); }"
        XdkAdapter().use { adapter ->
            adapter.replaceBuildInputs(XdkBuildModel.Inputs(emptyList(), artifacts))
            assertThat(adapter.compile(uri, consumer).success).isTrue()
            val target = requireNotNull(adapter.findDefinition(uri, 0, consumer.indexOf("value")))
            assertThat(target.startLine).isEqualTo(1)
            assertThat(target.uri).isNotEqualTo(sources.resolve("Attached.x").toUri().toString())
            assertThat(adapter.readOnlyDocument(target.uri)?.text).isEqualTo(text)
            assertThat(adapter.effectiveSourceModules()).isEmpty()
            adapter.replaceDependencies(emptyList())
            assertThat(adapter.readOnlyDocument(target.uri)).isNull()
            assertThat(adapter.compile(uri, consumer).success).isFalse()
        }
        Files.writeString(sources.resolve("Attached.x"), text.replace("7", "8"))
        assertThatThrownBy { libraries.resolve(emptyList()) }.hasMessageContaining("does not match")
    }

    @Test
    fun `earlier module path wins while unknown attachments and bundled overrides fail`() {
        CompilerTestSupport.configure()
        val artifacts =
            listOf(1, 2).map { number ->
                val errors = ErrorList()
                val compiled =
                    EmbeddingSupport.instance().compileModule(
                        Source("module Duplicate { static Int value() = $number; }"),
                        null,
                        errors,
                    )
                assertThat(compiled.succeeded()).isTrue()
                directory.resolve("$number.xtc").toFile().apply { writeBytes(compiled.toDependency().bytes()) }
            }
        val settings = CompilerLibraries(artifacts, emptyMap())
        assertThat(settings.resolve(emptyList()).single().bytes()).isEqualTo(artifacts.first().readBytes())
        assertThatThrownBy { settings.copy(sourceAttachments = mapOf("Unknown" to listOf(directory.toFile()))).resolve(emptyList()) }
            .hasMessageContaining("effective external binary")
        val bundled = XdkLibraries.module("ecstasy.xtclang.org")!!
        val bytes = ByteArrayOutputStream().also { bundled.fileStructure.writeTo(it) }.toByteArray()
        val file = directory.resolve("bundled.xtc").toFile().apply { writeBytes(bytes) }
        assertThatThrownBy { CompilerLibraries(listOf(file), emptyMap()).resolve(emptyList()) }.hasMessageContaining("bundled XDK")
    }
}
