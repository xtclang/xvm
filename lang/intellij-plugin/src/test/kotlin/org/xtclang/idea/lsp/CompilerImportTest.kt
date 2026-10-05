package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class CompilerImportTest {
    private val disk = AtomicReference<String?>("original")
    private val imports = CompilerImport(disk::get) { require(it != "invalid") { "Invalid model" } }

    @Test
    fun `watchers retain accepted inputs until a successful import finishes`() {
        assertThat(imports.current()).isEqualTo("original")
        val operation = imports.begin(false)
        disk.set("replacement")
        assertThat(imports.current()).isEqualTo("original")
        assertThat(imports.description()).contains("Refreshing")
        assertThatThrownBy { imports.begin(true) }.hasMessageContaining("already running")
        assertThat(imports.finish(operation, CompilerImport.Outcome.SUCCEEDED).outcome).isEqualTo(CompilerImport.Outcome.SUCCEEDED)
        assertThat(imports.current()).isEqualTo("replacement")
        assertThat(imports.description()).contains("Last import:")
    }

    @Test
    fun `cancelled and failed builds cannot publish even a valid newly written report`() {
        listOf(CompilerImport.Outcome.CANCELLED, CompilerImport.Outcome.FAILED).forEach { outcome ->
            disk.set("original")
            imports.current()
            val operation = imports.begin(true)
            disk.set("unaccepted")
            imports.finish(operation, outcome)
            assertThat(imports.current()).isEqualTo("original")
            assertThat(imports.current()).isEqualTo("original")
            val retry = imports.begin(false)
            imports.finish(retry, CompilerImport.Outcome.SUCCEEDED)
            assertThat(imports.current()).isEqualTo("unaccepted")
        }
    }

    @Test
    fun `invalid and absent successful reports retain the previous model and allow repair`() {
        imports.current()
        listOf("invalid", null).forEach { text ->
            val operation = imports.begin(false)
            disk.set(text)
            val result = imports.finish(operation, CompilerImport.Outcome.SUCCEEDED)
            assertThat(result.outcome).isEqualTo(CompilerImport.Outcome.FAILED)
            assertThat(imports.current()).isEqualTo("original")
        }
        disk.set("externally repaired")
        assertThat(imports.current()).isEqualTo("externally repaired")
        disk.set(null)
        assertThat(imports.current()).isNull()
    }

    @Test
    fun `late completion cannot retire a newer import or replace its model`() {
        imports.current()
        val first = imports.begin(false)
        imports.finish(first, CompilerImport.Outcome.CANCELLED)
        val next = imports.begin(true)
        disk.set("new")
        assertThatThrownBy { imports.finish(first, CompilerImport.Outcome.SUCCEEDED) }.hasMessageContaining("no longer owns")
        assertThat(imports.current()).isEqualTo("original")
        imports.finish(next, CompilerImport.Outcome.SUCCEEDED)
        assertThat(imports.current()).isEqualTo("new")
    }

    @Test
    fun `cancellation during final report validation retains the accepted inputs`() {
        imports.current()
        val operation = imports.begin(false)
        disk.set("new")
        assertThat(imports.finish(operation, CompilerImport.Outcome.SUCCEEDED, cancelled = { true }).outcome)
            .isEqualTo(CompilerImport.Outcome.CANCELLED)
        assertThat(imports.current()).isEqualTo("original")
    }

    @Test
    fun `a slow watcher cannot overwrite a newer accepted import`() {
        val blocked = AtomicBoolean()
        val captured = CountDownLatch(1)
        val release = CountDownLatch(1)
        val owner =
            CompilerImport({
                val text = disk.get()
                if (blocked.compareAndSet(true, false)) {
                    captured.countDown()
                    check(release.await(5, SECONDS))
                }
                text
            }) {}
        owner.current()
        disk.set("stale watcher")
        blocked.set(true)
        val watcher = CompletableFuture.supplyAsync(owner::current)
        try {
            assertThat(captured.await(5, SECONDS)).isTrue()
            val operation = owner.begin(false)
            disk.set("fresh import")
            owner.finish(operation, CompilerImport.Outcome.SUCCEEDED)
        } finally {
            release.countDown()
        }
        assertThat(watcher.get(5, SECONDS)).isEqualTo("fresh import")
        assertThat(owner.current()).isEqualTo("fresh import")
    }
}
