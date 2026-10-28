package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkFileChanges
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.XdkSources
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Checks whether late filesystem notifications still describe the inputs already compiled. Identical events
 * can retain current work; real source, membership or resource changes must retire stale results.
 */
class XdkFileChangesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `late creation events match compiled inputs but content and membership changes do not`() {
        directory = directory.toRealPath()
        val root =
            directory.resolve("App.x").toFile().apply {
                writeText("module App { String resource = \$./data.txt; }")
            }
        val member =
            directory.resolve("App/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        val resource = directory.resolve("data.txt").toFile().apply { writeText("original") }
        val inputs = XdkSources.capture(root, emptyMap()) { false }.inputs
        listOf(root, member, member.parentFile, resource, root.parentFile).forEach {
            assertThat(XdkFileChanges.unchanged(it, root, inputs)).describedAs(it.path).isTrue()
        }
        val stamp = Files.getLastModifiedTime(resource.toPath())
        resource.writeText("modified")
        Files.setLastModifiedTime(resource.toPath(), stamp)
        assertThat(XdkFileChanges.unchanged(resource, root, inputs)).isFalse()
        assertThat(XdkFileChanges.unchanged(root.parentFile, root, inputs)).isFalse()
        member.writeText("class Other {}")
        assertThat(XdkFileChanges.unchanged(member, root, inputs)).isFalse()
        member.writeText("class Box {}")
        val added = member.parentFile.resolve("New.x").apply { writeText("class New {}") }
        assertThat(XdkFileChanges.unchanged(added, root, inputs)).isFalse()
        assertThat(XdkFileChanges.unchanged(member.parentFile, root, inputs)).isFalse()
        Files.delete(member.toPath())
        assertThat(XdkFileChanges.unchanged(member, root, inputs)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `incomplete overlay owns no-op proofs while real closed source and resource changes retire them`(resourceChange: Boolean) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val root =
            directory.resolve("App.x").toFile().apply {
                writeText("module App { String resource = \$./data.txt; void run() {} }")
            }
        val member =
            directory.resolve("App/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        val resource = directory.resolve("data.txt").toFile().apply { writeText("first") }
        val overlay =
            root.readText().replace("void run() {}", "void run() { Int size = resource.si; }")
        XdkAdapter().use { adapter ->
            val uri = root.toURI().toString()
            assertThat(adapter.compile(uri, overlay).success).isFalse()
            listOf(root, root.parentFile, member).forEach {
                assertThat(adapter.changedFileScopes(it.toURI().toString()))
                    .describedAs(it.path)
                    .isEmpty()
            }
            if (resourceChange) {
                resource.writeText("second")
            } else {
                member.writeText("class Box { Int changed = 1; }")
            }
            assertThat(adapter.changedFileScopes(root.parentFile.toURI().toString())).contains(uri)
        }
    }

    @Test
    fun `no-op source events preserve consumers and real changes include the dependency closure`() {
        val library =
            directory.resolve("Library.x").toFile().apply {
                writeText("module Library { class Box {} }")
            }
        val consumer =
            directory.resolve("Consumer.x").toFile().apply {
                writeText(
                    "module Consumer { package lib import Library; lib.Box create() = new lib.Box(); }",
                )
            }
        val modules =
            listOf(
                XdkSourceModule("Library", library.toURI().toString()),
                XdkSourceModule("Consumer", consumer.toURI().toString(), setOf("Library")),
            )
        val adapter = XdkAdapter()
        try {
            adapter.replaceSourceModules(modules)
            assertThat(adapter.compile(consumer.toURI().toString(), consumer.readText()).success)
                .isTrue()
            assertThat(adapter.changedFileScopes(library.toURI().toString())).isEmpty()
            val original = library.readText()
            Files.delete(library.toPath())
            assertThat(adapter.compile(consumer.toURI().toString(), consumer.readText()).success)
                .isFalse()
            library.writeText(original)
            assertThat(adapter.changedFileScopes(library.toURI().toString()))
                .containsExactlyInAnyOrderElementsOf(modules.map { it.uri })
            assertThat(adapter.compile(consumer.toURI().toString(), consumer.readText()).success)
                .isTrue()
            assertThat(
                adapter.changedFileScopes(directory.resolve("settings.json").toUri().toString()),
            ).isEmpty()
            val members = Files.createDirectory(directory.resolve("Library"))
            Files.writeString(members.resolve("New.x"), "class New {}")
            assertThat(adapter.changedFileScopes(members.toUri().toString()))
                .containsExactlyInAnyOrderElementsOf(modules.map { it.uri })
            library.writeText("module Library { class Changed {} }")
            assertThat(adapter.changedFileScopes(library.toURI().toString()))
                .containsExactlyInAnyOrderElementsOf(modules.map { it.uri })
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `a file event during a running analysis is judged against the sources it read`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val root = directory.resolve("App.x").toFile().apply { writeText("module App { Box make() = new Box(); }") }
        val member =
            directory.resolve("App/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        withHeldCompile("App.x") { adapter, hold ->
            val uri = root.toURI().toString()
            val analysis = adapter.compileAsync(uri, root.readText())
            assertThat(hold.entered.await(20, SECONDS)).isTrue()

            // the watcher reports files the running analysis already read: nothing to recompile
            listOf(root.parentFile, member.parentFile, member).forEach {
                assertThat(adapter.changedFileScopes(it.toURI().toString())).describedAs(it.path).isEmpty()
            }
            // a change it did not read is found at once
            member.writeText("class Box { Int changed = 1; }")
            assertThat(adapter.changedFileScopes(member.toURI().toString())).contains(uri)
            hold.release.countDown()
            assertThat(analysis.get(30, SECONDS).success).isTrue()
        }
    }

    @Test
    fun `a file event before a queued analysis reads its sources needs no new analysis`() {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        val busy = directory.resolve("Busy.x").toFile().apply { writeText("module Busy {}") }
        val root = directory.resolve("App.x").toFile().apply { writeText("module App { Box make() = new Box(); }") }
        val member =
            directory.resolve("App/Box.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Box {}")
            }
        withHeldCompile("Busy.x") { adapter, hold ->
            val first = adapter.compileAsync(busy.toURI().toString(), busy.readText())
            assertThat(hold.entered.await(20, SECONDS)).isTrue()
            val uri = root.toURI().toString()
            val analysis = adapter.compileAsync(uri, root.readText())

            // App waits behind Busy for the only compiler thread, so it reads its sources after this change
            member.writeText("class Box { Int changed = 1; }")
            listOf(member, member.parentFile).forEach {
                assertThat(adapter.changedFileScopes(it.toURI().toString())).describedAs(it.path).isEmpty()
            }
            hold.release.countDown()
            first.get(30, SECONDS)
            assertThat(analysis.get(30, SECONDS).success).isTrue()
            // it did read the change
            assertThat(adapter.changedFileScopes(member.toURI().toString())).isEmpty()
        }
    }

    private class Hold {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
    }

    /** The first compile of the module whose root file is [rootName] waits in the compiler until released. */
    private fun withHeldCompile(
        rootName: String,
        test: (XdkAdapter, Hold) -> Unit,
    ) {
        val hold = Hold()
        val support = EmbeddingSupport.instance()
        XdkAdapter(
            { source, repository, errors -> support.compileModule(source, repository, errors) },
            { sources, repository, errors ->
                if (sources.sourceFile.name == rootName && hold.entered.count > 0) {
                    hold.entered.countDown()
                    check(hold.release.await(20, SECONDS))
                }
                support.compileModule(sources, repository, errors)
            },
            { source, _, cursor, repository, errors -> support.analyzeIncomplete(source, cursor, repository, errors) },
        ).use { adapter ->
            try {
                test(adapter, hold)
            } finally {
                hold.release.countDown()
            }
        }
    }
}
