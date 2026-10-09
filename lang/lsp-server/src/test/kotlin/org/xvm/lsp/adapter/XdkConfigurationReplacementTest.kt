package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkBuildModel
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

class XdkConfigurationReplacementTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `source and build replacements retain independent successful analyses`() {
        CompilerTestSupport.configure()
        val library = source("Library", "module Library { static Int value() = 1; }")
        val consumer =
            source(
                "Consumer",
                """
                module Consumer {
                    package lib import Library;
                    Int read() = lib.value();
                }
                """.trimIndent(),
                setOf("Library"),
            )
        val independent = source("Independent", "module Independent { Int value = 42; }")
        val graph = listOf(library, consumer, independent)
        listOf(false, true).forEach { buildModel ->
            XdkAdapter().use { adapter ->
                fun replace(modules: List<XdkSourceModule>) =
                    if (buildModel) {
                        adapter.replaceBuildInputs(XdkBuildModel.Inputs(modules, emptyList()))
                    } else {
                        adapter.replaceSourceModules(modules)
                    }
                replace(graph)
                assertThat(adapter.compile(independent.uri, text(independent)).success).isTrue()
                assertThat(adapter.compile(consumer.uri, text(consumer)).success).isTrue()
                val retained = adapter.getCachedResult(independent.uri)
                val replacement = XdkSourceModule("Library", library.uri, resourceRoots = emptyList())
                assertThat(replace(listOf(replacement, consumer, independent))).containsExactly(consumer.uri)
                assertThat(adapter.getCachedResult(independent.uri)).isEqualTo(retained)
                assertThat(adapter.getCachedResult(consumer.uri)).isNull()
                assertThat(adapter.compile(consumer.uri, text(consumer)).success).isTrue()
                assertThat(replace(listOf(consumer, independent))).containsExactly(consumer.uri)
                assertThat(adapter.getCachedResult(independent.uri)).isEqualTo(retained)
                assertThat(adapter.compile(consumer.uri, text(consumer)).success).isFalse()
                // Failed import sets are incomplete: adding the missing source must retry them.
                assertThat(replace(graph)).containsExactly(consumer.uri)
                assertThat(adapter.compile(consumer.uri, text(consumer)).success).isTrue()
            }
        }
    }

    @Test
    fun `changing a transitive dependency or source location retires its consumers`() {
        CompilerTestSupport.configure()
        val base = source("Base", "module Base { static Int value() = 1; }")
        val middle =
            source(
                "Middle",
                """
                module Middle {
                    package base import Base;
                    static Int value() = base.value();
                }
                """.trimIndent(),
                setOf("Base"),
            )
        val app =
            source(
                "App",
                """
                module App {
                    package middle import Middle;
                    Int read() = middle.value();
                }
                """.trimIndent(),
                setOf("Middle"),
            )
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(listOf(base, middle, app))
            assertThat(adapter.compile(app.uri, text(app)).success).isTrue()
            val missingEdge = XdkSourceModule("Middle", middle.uri)
            assertThat(adapter.replaceSourceModules(listOf(base, missingEdge, app))).containsExactly(app.uri)
            assertThat(adapter.compile(app.uri, text(app)).success).isFalse()
            adapter.replaceSourceModules(listOf(base, middle, app))
            assertThat(adapter.compile(app.uri, text(app)).success).isTrue()
            val moved = source("moved/Base", "module Base { static String value() = \"changed\"; }")
            assertThat(adapter.replaceSourceModules(listOf(moved, middle, app))).containsExactly(app.uri)
            assertThat(adapter.compile(app.uri, text(app)).success).isFalse()
        }
    }

    @Test
    fun `binary replacement and source shadowing retire consumers but preserve unrelated buffers`() {
        CompilerTestSupport.configure()

        fun library(
            type: String,
            value: String,
        ) = EmbeddingSupport
            .instance()
            .compileModule(
                Source("module Library { static $type value() = $value; }", "Library.x"),
                null,
                ErrorList(),
            ).also { assertThat(it.succeeded()).isTrue() }
            .toDependency()
        val number = library("Int", "1")
        val string = library("String", "\"changed\"")
        val consumer =
            source(
                "Consumer",
                """
                module Consumer {
                    package lib import Library;
                    Int read() = lib.value();
                }
                """.trimIndent(),
                setOf("Library"),
            )
        val independent = source("Independent", "module Independent { Int value = 42; }")
        val sourceLibrary = source("Library", "module Library { static String value() = \"source\"; }")
        XdkAdapter().use { adapter ->
            val graph = listOf(consumer, independent)
            adapter.replaceBuildInputs(XdkBuildModel.Inputs(graph, listOf(number)))
            assertThat(adapter.compile(independent.uri, text(independent)).success).isTrue()
            assertThat(adapter.compile(consumer.uri, text(consumer)).success).isTrue()
            val retained = adapter.getCachedResult(independent.uri)
            assertThat(adapter.replaceBuildInputs(XdkBuildModel.Inputs(graph, listOf(string))))
                .containsExactly(consumer.uri)
            assertThat(adapter.getCachedResult(independent.uri)).isEqualTo(retained)
            assertThat(adapter.compile(consumer.uri, text(consumer)).success).isFalse()
            adapter.replaceBuildInputs(XdkBuildModel.Inputs(graph, listOf(number)))
            assertThat(adapter.compile(consumer.uri, text(consumer)).success).isTrue()
            assertThat(adapter.replaceBuildInputs(XdkBuildModel.Inputs(graph + sourceLibrary, listOf(number))))
                .containsExactly(consumer.uri)
            assertThat(adapter.getCachedResult(independent.uri)).isEqualTo(retained)
            assertThat(adapter.compile(consumer.uri, text(consumer)).success).isFalse()
        }
    }

    @Test
    fun `a real graph replacement cancels pending work even for unchanged inputs`() {
        CompilerTestSupport.configure()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val support = EmbeddingSupport.instance()
        val added = source("Added", "module Added {}")
        XdkAdapter { source, errors ->
            entered.countDown()
            check(release.await(10, SECONDS))
            support.compileModule(source, null, errors)
        }.use { adapter ->
            val pending = adapter.compileAsync("untitled:Pending.x", "module Pending {}")
            try {
                check(entered.await(10, SECONDS))
                assertThat(adapter.replaceSourceModules(listOf(added))).containsExactly("untitled:Pending.x")
                assertThat(pending.isCancelled).isTrue()
                release.countDown()
                assertThat(adapter.compile(added.uri, text(added)).success).isTrue()
                assertThat(adapter.getCachedResult("untitled:Pending.x")).isNull()
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `batched ownership follows unsaved roots canonical aliases and later filesystem changes`() {
        val root = directory.resolve("Outer.x").toFile().canonicalFile
        val member = directory.resolve("Outer/Member.x").toFile().canonicalFile
        member.parentFile.mkdirs()
        member.writeText("class Member {}")
        val rootUri = root.toURI().toString()
        val memberUri = member.toURI().toString()
        val alias =
            member.parentFile
                .resolve("../Outer/Member.x")
                .toURI()
                .toString()
        XdkAdapter().use { adapter ->
            adapter.updateDocument(rootUri, "module Outer {}")
            assertThat(adapter.analysisScopes(listOf(memberUri, alias, "untitled:Other.x")))
                .containsEntry(memberUri, rootUri)
                .containsEntry(alias, rootUri)
                .containsEntry("untitled:Other.x", "untitled:Other.x")
            adapter.closeDocument(rootUri)
            assertThat(adapter.analysisScopes(listOf(memberUri))).containsEntry(memberUri, memberUri)
            root.writeText("module Outer {}")
            assertThat(adapter.analysisScopes(listOf(memberUri))).containsEntry(memberUri, rootUri)
        }
    }

    private fun source(
        path: String,
        text: String,
        dependencies: Set<String> = emptySet(),
    ): XdkSourceModule {
        val file = directory.resolve("$path.x").toFile().canonicalFile
        file.parentFile.mkdirs()
        file.writeText(text)
        return XdkSourceModule(file.nameWithoutExtension, file.toURI().toString(), dependencies)
    }

    private fun text(module: XdkSourceModule): String = module.root.readText()
}
