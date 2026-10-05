package org.xtclang.idea.lsp

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicatorBase
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

class CompilerImportServiceTest {
    /** Cancellation ownership is tested without installing an application-wide ProgressManager. */
    private class Indicator : EmptyProgressIndicatorBase(ModalityState.nonModal()) {
        private val cancelled = AtomicBoolean()

        override fun cancel() {
            cancelled.set(true)
        }

        override fun isCanceled(): Boolean = cancelled.get()
    }

    private fun service(): CompilerImportService {
        val project =
            Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
                error("Progress ownership must not access project.${method.name}")
            } as Project
        return CompilerImportService(project)
    }

    @Test
    fun `project disposal cancels the owned indicator and rejects later work`() {
        val service = service()
        val indicator = Indicator()
        service.run(indicator) {
            service.dispose()
            assertThat(indicator.isCanceled).isTrue()
        }
        assertThatThrownBy { service.run(Indicator()) { error("Must not start") } }
            .isInstanceOf(ProcessCanceledException::class.java)
        service.dispose()
    }

    @Test
    fun `concurrent imports are refused and a completed owner releases the progress slot`() {
        val service = service()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first =
            CompletableFuture.runAsync {
                service.run(Indicator()) {
                    started.countDown()
                    check(release.await(5, SECONDS))
                }
            }
        try {
            assertThat(started.await(5, SECONDS)).isTrue()
            assertThatThrownBy { service.run(Indicator()) { error("Overlapping import") } }
                .hasMessageContaining("already running")
        } finally {
            release.countDown()
        }
        first.get(5, SECONDS)
        assertThat(service.run(Indicator()) { "new owner" }).isEqualTo("new owner")
        service.dispose()
    }
}
