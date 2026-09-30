package org.xtclang.idea.lsp

import com.google.gson.JsonParser
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Registration
import org.junit.jupiter.api.Test

class CompilerRootWatchesTest {
    @Test
    fun `watch leases share roots replace settings and close exactly once`() {
        val acquired = mutableListOf<Set<Path>>()
        val released = mutableListOf<Set<Path>>()
        val refreshed = mutableListOf<Set<Path>>()
        val pending = CompletableFuture<Void>()
        val watches =
            CompilerRootWatches(
                { paths ->
                    acquired.add(paths)
                    AutoCloseable { released.add(paths) }
                },
                { paths ->
                    refreshed.add(paths)
                    pending
                },
            )
        val a = setOf(Path.of("/external/missing"))
        val b = setOf(Path.of("/other/source"))
        watches.replace(mapOf("a" to a, "shared" to a))
        watches.replace(emptyMap(), setOf("shared"))
        assertThat(acquired).containsExactly(a)
        watches.refresh()
        watches.refresh()
        assertThat(refreshed).containsExactly(a)
        watches.replace(mapOf("b" to b), setOf("a"))
        assertThat(released).containsExactly(a)
        pending.complete(null)
        watches.refresh()
        assertThat(refreshed).containsExactly(a, b)
        watches.close()
        watches.close()
        watches.replace(mapOf("late" to a))
        watches.refresh()
        assertThat(acquired).containsExactly(a, b)
        assertThat(released).containsExactly(a, b)
        assertThat(refreshed).containsExactly(a, b)
    }

    @Test
    fun `decode wire registrations including relative patterns without watching global globs`() {
        fun registration(id: String, glob: String) =
            Registration(
                id,
                "workspace/didChangeWatchedFiles",
                JsonParser.parseString("""{"watchers":[{"globPattern":$glob}]}"""),
            )
        assertThat(
                CompilerRootWatches.roots(
                    registration("xtc-resources-a", "\"/outside/assets/**/*\"")
                )
            )
            .containsExactly(Path.of("/outside/assets"))
        assertThat(
                CompilerRootWatches.roots(
                    registration(
                        "xtc-resources-b",
                        """{"baseUri":"file:///outside/source/","pattern":"**/*"}""",
                    )
                )
            )
            .containsExactly(Path.of("/outside/source"))
        assertThat(
                CompilerRootWatches.roots(
                    registration(
                        "xtc-resources-flat",
                        """{"baseUri":"file:///","pattern":"generated"}""",
                    )
                )
            )
            .isEmpty()
        assertThat(CompilerRootWatches.roots(registration("xtc-file-watcher", "\"**/*.x\"")))
            .isEmpty()
        assertThat(
                CompilerRootWatches.roots(
                    registration(
                        "xtc-resources-b",
                        """{"baseUri":"https://example.com/","pattern":"**/*"}""",
                    )
                )
            )
            .isEmpty()
    }
}
