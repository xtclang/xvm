package org.xvm.lsp.server

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
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
