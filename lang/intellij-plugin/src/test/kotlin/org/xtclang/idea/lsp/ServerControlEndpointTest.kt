package org.xtclang.idea.lsp

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/** Verify authenticated routing, restart acknowledgement, isolation and cleanup without launching an IDE. */
class ServerControlEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `only an authenticated restart invokes the callback and waits for completion`() {
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val pending = CompletableFuture<Void>()
        ServerControlEndpoint(directory, { listOf("/project") }) {
            calls.incrementAndGet()
            entered.countDown()
            pending
        }.use {
            val file = Files.list(directory).use { it.findFirst().orElseThrow() }
            val descriptor = JsonParser.parseString(Files.readString(file)).asJsonObject
            val client = HttpClient.newHttpClient()
            val base = "http://127.0.0.1:${descriptor["port"].asInt}"
            val token = descriptor["token"].asString

            fun request(
                route: String,
                method: String = "GET",
                credential: String = token,
            ): HttpRequest =
                HttpRequest
                    .newBuilder(URI.create(base + route))
                    .header("Authorization", "Bearer $credential")
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build()
            assertThat(client.send(request("/restart", "POST", "wrong"), BodyHandlers.ofString()).statusCode()).isEqualTo(403)
            assertThat(client.send(request("/restart"), BodyHandlers.ofString()).statusCode()).isEqualTo(404)
            val status = client.send(request("/status"), BodyHandlers.ofString())
            assertThat(status.statusCode()).isEqualTo(200)
            assertThat(status.body()).contains("/project", "intellij").doesNotContain(token)
            assertThat(calls.get()).isZero()
            val restart = client.sendAsync(request("/restart", "POST"), BodyHandlers.ofString())
            assertThat(entered.await(5, SECONDS)).isTrue()
            assertThat(restart).isNotDone()
            assertThat(client.send(request("/restart", "POST"), BodyHandlers.ofString()).statusCode()).isEqualTo(409)
            pending.complete(null)
            assertThat(restart.get(5, SECONDS).statusCode()).isEqualTo(200)
            assertThat(calls.get()).isEqualTo(1)
        }
        assertThat(Files.list(directory).use { it.count() }).isZero()
    }

    @Test
    fun `failed restart reports failure and does not stop the control endpoint`() {
        ServerControlEndpoint(directory, { listOf("/project") }) {
            CompletableFuture.failedFuture<Void>(IllegalStateException("Launch failed"))
        }.use {
            val descriptor =
                Files
                    .list(
                        directory,
                    ).use { JsonParser.parseString(Files.readString(it.findFirst().orElseThrow())).asJsonObject }
            val client = HttpClient.newHttpClient()
            val builder =
                HttpRequest
                    .newBuilder(URI.create("http://127.0.0.1:${descriptor["port"].asInt}/restart"))
                    .header("Authorization", "Bearer ${descriptor["token"].asString}")
                    .POST(HttpRequest.BodyPublishers.noBody())
            repeat(2) {
                val response = client.send(builder.build(), BodyHandlers.ofString())
                assertThat(response.statusCode()).isEqualTo(500)
                assertThat(response.body()).contains("Launch failed")
            }
        }
    }
}
