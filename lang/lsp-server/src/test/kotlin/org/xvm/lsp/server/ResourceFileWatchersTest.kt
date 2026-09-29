package org.xvm.lsp.server

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

class ResourceFileWatchersTest {
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
