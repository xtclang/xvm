package org.xtclang.idea.lsp

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Registration
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Checks shared watch leases, wire registration decoding and exact cleanup. Prevents root replacement or
 * disconnect from leaking watches or subscribing to unintended global paths.
 */
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
    fun `a walk lists each directory in a read of its own, below recursive roots only`() {
        val tree =
            mapOf(
                "root" to listOf("root/a", "root/b"),
                "root/a" to listOf("root/a/c"),
                "root/a/c" to emptyList(),
                "root/b" to emptyList(),
                "parent" to listOf("parent/unrelated"),
            )
        val reads = mutableListOf<List<String>>()
        var inRead = false
        CompilerRootWatches.walk(
            listOf("root" to true, "parent" to false),
            read = { listing ->
                check(!inRead) { "reads must not nest" }
                inRead = true
                listing().also {
                    inRead = false
                    reads += it
                }
            },
            subdirectories = { tree.getValue(it) },
        )
        // One read per directory, each holding the lock for a single listing. A missing root's
        // nearest existing parent is listed but never walked.
        assertThat(reads).containsExactly(
            listOf("root/a", "root/b"),
            listOf("parent/unrelated"),
            listOf("root/a/c"),
            emptyList(),
            emptyList(),
        )
    }

    @Test
    fun `decode wire registrations including relative patterns without watching global globs`() {
        fun registration(
            id: String,
            glob: String,
        ) = Registration(
            id,
            "workspace/didChangeWatchedFiles",
            JsonParser.parseString("""{"watchers":[{"globPattern":$glob}]}"""),
        )
        assertThat(
            CompilerRootWatches.roots(
                registration("xtc-resources-a", "\"/outside/assets/**/*\""),
            ),
        ).containsExactly(Path.of("/outside/assets"))
        assertThat(
            CompilerRootWatches.roots(
                registration(
                    "xtc-resources-b",
                    """{"baseUri":"file:///outside/source/","pattern":"**/*"}""",
                ),
            ),
        ).containsExactly(Path.of("/outside/source"))
        assertThat(
            CompilerRootWatches.roots(
                registration(
                    "xtc-resources-flat",
                    """{"baseUri":"file:///","pattern":"generated"}""",
                ),
            ),
        ).isEmpty()
        assertThat(CompilerRootWatches.roots(registration("xtc-file-watcher", "\"**/*.x\"")))
            .isEmpty()
        assertThat(
            CompilerRootWatches.roots(
                registration(
                    "xtc-resources-b",
                    """{"baseUri":"https://example.com/","pattern":"**/*"}""",
                ),
            ),
        ).isEmpty()
    }
}
