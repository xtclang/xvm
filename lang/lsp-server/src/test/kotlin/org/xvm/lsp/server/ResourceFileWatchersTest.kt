package org.xvm.lsp.server

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

class ResourceFileWatchersTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `failed removals are retried without reusing the retired registration`() {
        val client = mock(LanguageClient::class.java)
        val removals = CopyOnWriteArrayList<UnregistrationParams>()
        doAnswer { CompletableFuture.completedFuture<Void>(null) }
            .`when`(client)
            .registerCapability(any())
        doAnswer {
                removals += it.getArgument<UnregistrationParams>(0)
                if (removals.size == 1)
                    CompletableFuture.failedFuture<Void>(IllegalStateException("not removed"))
                else CompletableFuture.completedFuture<Void>(null)
            }
            .`when`(client)
            .unregisterCapability(any())
        ResourceFileWatchers().use { watchers ->
            val root = setOf("file:///external/a/")
            val first = watchers.update(client, root).get(5, SECONDS)
            assertThat(watchers.update(client, emptySet()).get(5, SECONDS)).isEmpty()
            val next = watchers.update(client, root).get(5, SECONDS)
            assertThat(next.values).doesNotContainAnyElementsOf(first.values)
            assertThat(removals).hasSize(2)
            assertThat(removals.last().unregisterations.map { it.id })
                .containsExactlyElementsOf(first.values)
            watchers.update(client, root).get(5, SECONDS)
            assertThat(removals).hasSize(2)
        }
    }

    @Test
    fun `stalled registration releases queue and late success removes only abandoned IDs`() {
        val client = mock(LanguageClient::class.java)
        val registered = LinkedBlockingQueue<RegistrationParams>()
        val removed = LinkedBlockingQueue<UnregistrationParams>()
        val replies = LinkedBlockingQueue<CompletableFuture<Void>>()
        val deadlines = LinkedBlockingQueue<CompletableFuture<Void>>()
        doAnswer {
                registered.add(it.getArgument(0))
                CompletableFuture<Void>().also(replies::add)
            }
            .`when`(client)
            .registerCapability(any())
        doAnswer {
                removed.add(it.getArgument(0))
                CompletableFuture.completedFuture<Void>(null)
            }
            .`when`(client)
            .unregisterCapability(any())
        ResourceFileWatchers { CompletableFuture<Void>().also(deadlines::add) }
            .use { watchers ->
                val first = watchers.update(client, setOf("file:///external/a/"))
                val oldId = registered.poll(5, SECONDS).registrations.single().id
                val lateReply = replies.poll(5, SECONDS)
                deadlines.poll(5, SECONDS).complete(null)
                assertThat(first.get(5, SECONDS)).isEmpty()
                val second = watchers.update(client, setOf("file:///external/a/"))
                val newId = registered.poll(5, SECONDS).registrations.single().id
                replies.poll(5, SECONDS).complete(null)
                assertThat(second.get(5, SECONDS).values).containsExactly(newId)
                lateReply.complete(null)
                assertThat(removed.poll(5, SECONDS).unregisterations.map { it.id })
                    .containsExactly(oldId)
                assertThat(newId).isNotEqualTo(oldId)
                assertThat(
                        watchers.update(client, setOf("file:///external/a/")).get(5, SECONDS).values
                    )
                    .containsExactly(newId)
            }
    }

    @Test
    fun `stalled removal never lets subsequent roots reuse an ID being removed`() {
        val client = mock(LanguageClient::class.java)
        val deadlines = LinkedBlockingQueue<CompletableFuture<Void>>()
        val removal = CompletableFuture<Void>()
        doAnswer { CompletableFuture.completedFuture<Void>(null) }
            .`when`(client)
            .registerCapability(any())
        doAnswer { removal }.`when`(client).unregisterCapability(any())
        ResourceFileWatchers { CompletableFuture<Void>().also(deadlines::add) }
            .use { watchers ->
                val root = setOf("file:///external/a/")
                val first = watchers.update(client, root).get(5, SECONDS)
                deadlines.poll(5, SECONDS) // completed registration's cancelled timer
                val empty = watchers.update(client, emptySet())
                deadlines.poll(5, SECONDS).complete(null)
                assertThat(empty.get(5, SECONDS)).isEmpty()
                val replacing = watchers.update(client, root)
                deadlines.poll(5, SECONDS) // replacement registration's cancelled timer
                deadlines.poll(5, SECONDS).complete(null) // retire the unanswered removal retry
                val second = replacing.get(5, SECONDS)
                assertThat(second.values).doesNotContainAnyElementsOf(first.values)
                removal.complete(null)
                assertThat(watchers.update(client, root).get(5, SECONDS)).isEqualTo(second)
            }
    }

    @Test
    fun `disconnect releases outstanding and queued watcher updates`() {
        val client = mock(LanguageClient::class.java)
        val started = CompletableFuture<Void>()
        doAnswer {
                started.complete(null)
                CompletableFuture<Void>()
            }
            .`when`(client)
            .registerCapability(any())
        val watchers = ResourceFileWatchers()
        val first = watchers.update(client, setOf("file:///external/a/"))
        val second = watchers.update(client, setOf("file:///external/b/"))
        started.get(5, SECONDS)
        watchers.close()
        assertThat(first.get(5, SECONDS)).isEmpty()
        assertThat(second.get(5, SECONDS)).isEmpty()
        assertThat(watchers.update(client, setOf("file:///external/c/")).join()).isEmpty()
    }

    @Test
    fun `missing roots watch only their next child and replace plans as directories appear`() {
        val client = mock(LanguageClient::class.java)
        val registrations = CopyOnWriteArrayList<RegistrationParams>()
        val removals = CopyOnWriteArrayList<UnregistrationParams>()
        doAnswer {
                registrations += it.getArgument<RegistrationParams>(0)
                CompletableFuture.completedFuture<Void>(null)
            }
            .`when`(client)
            .registerCapability(any())
        doAnswer {
                removals += it.getArgument<UnregistrationParams>(0)
                CompletableFuture.completedFuture<Void>(null)
            }
            .`when`(client)
            .unregisterCapability(any())
        val watchers = ResourceFileWatchers()
        val root = directory.resolve("generated/assets")
        val uri = root.toUri().toString()
        watchers.update(client, setOf(uri)).get(10, SECONDS)
        fun patterns() =
            (registrations.last().registrations.single().registerOptions
                    as DidChangeWatchedFilesRegistrationOptions)
                .watchers
                .map { it.globPattern.right }
        assertThat(patterns().map { it.baseUri.right to it.pattern })
            .containsExactly(
                uri to "**/*",
                directory.toUri().toString() to "generated",
            )
        Files.createDirectory(root.parent)
        watchers.update(client, setOf(uri)).get(10, SECONDS)
        assertThat(patterns().last().baseUri.right).isEqualTo(root.parent.toUri().toString())
        assertThat(patterns().last().pattern).isEqualTo("assets")
        assertThat(removals).hasSize(1)
        Files.createDirectory(root)
        watchers.update(client, setOf(uri)).get(10, SECONDS)
        assertThat(registrations).hasSize(2)
        // Retain the parent subscription even while the root exists, for delete/recreate.
        Files.delete(root)
        watchers.update(client, setOf(uri)).get(10, SECONDS)
        assertThat(registrations).hasSize(2)
    }

    @Test
    fun `external root registrations and removals remain ordered across delayed client replies`() {
        val client = mock(LanguageClient::class.java)
        val registrations = CopyOnWriteArrayList<RegistrationParams>()
        val removals = CopyOnWriteArrayList<UnregistrationParams>()
        val first = CompletableFuture<Void>()
        doAnswer {
                registrations += it.getArgument<RegistrationParams>(0)
                if (registrations.size == 1) first
                else CompletableFuture.completedFuture<Void>(null)
            }
            .`when`(client)
            .registerCapability(any())
        doAnswer {
                removals += it.getArgument<UnregistrationParams>(0)
                CompletableFuture.completedFuture<Void>(null)
            }
            .`when`(client)
            .unregisterCapability(any())
        val watchers = ResourceFileWatchers()
        val a = watchers.update(client, setOf("file:///external/a/"))
        val b = watchers.update(client, setOf("file:///external/b/"))
        assertThat(a.isDone).isFalse()
        assertThat(b.isDone).isFalse()
        first.complete(null)
        val firstIds = a.get(10, SECONDS)
        val secondIds = b.get(10, SECONDS)
        assertThat(secondIds.keys).containsExactly("file:///external/b/")
        assertThat(removals.single().unregisterations.map { it.id })
            .containsExactly(firstIds.values.single())
        watchers.update(client, emptySet()).get(10, SECONDS)
        assertThat(registrations).hasSize(2)
        assertThat(removals.last().unregisterations.map { it.id })
            .containsExactly(secondIds.values.single())
    }
}
