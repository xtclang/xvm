package org.xtclang.idea.lsp

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Editor-owned, authenticated loopback control for the CLI. The endpoint outlives server restarts,
 * accepts only status/restart operations, and removes its discovery file when the project closes.
 * Project paths are queried live; the token never appears in responses or logs.
 */
internal class ServerControlEndpoint(
    directory: Path,
    private val projects: () -> List<String>,
    private val restart: () -> CompletableFuture<*>,
) : AutoCloseable {
    private val id = UUID.randomUUID().toString()
    private val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    private val busy = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val gson = Gson()
    private val executor = Executors.newFixedThreadPool(2) { Thread(it, "xtc-editor-control").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
    private val descriptor = directory.resolve("$id.json")

    init {
        try {
            Files.createDirectories(directory)
            require(!Files.isSymbolicLink(directory)) { "Control directory must not be a symbolic link" }
            val posix = Files.getFileStore(directory).supportsFileAttributeView("posix")
            if (posix) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
            server.executor = executor
            server.createContext("/") { exchange -> exchange.use { handle(it) } }
            server.start()
            val attributes =
                if (posix) {
                    arrayOf(
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
                    )
                } else {
                    emptyArray()
                }
            Files.createFile(descriptor, *attributes)
            Files.writeString(descriptor, gson.toJson(status() + mapOf("port" to server.address.port, "token" to token)))
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    private fun status(): Map<String, Any> =
        mapOf("version" to 1, "id" to id, "ide" to "intellij", "pid" to ProcessHandle.current().pid(), "projects" to projects())

    private fun handle(exchange: HttpExchange) {
        val authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
        if (exchange.requestHeaders.containsKey("Origin") ||
            !MessageDigest.isEqual(authorization.toByteArray(), "Bearer $token".toByteArray())
        ) {
            reply(exchange, 403, mapOf("error" to "Unauthorized"))
            return
        }
        when (exchange.requestMethod to exchange.requestURI.toString()) {
            "GET" to "/status" -> reply(exchange, 200, status())

            "POST" to "/restart" -> {
                if (!busy.compareAndSet(false, true)) {
                    reply(exchange, 409, mapOf("error" to "Restart already in progress"))
                    return
                }
                var pending: CompletableFuture<*>? = null
                try {
                    check(!closed.get()) { "Project is closing" }
                    pending = restart()
                    pending.whenComplete { _, _ -> busy.set(false) }
                    pending.get(60, SECONDS)
                    reply(exchange, 200, status() + ("message" to "Server restarted; the editor is resynchronizing open buffers."))
                } catch (failure: Exception) {
                    reply(exchange, 500, mapOf("error" to (failure.cause?.message ?: failure.message ?: "Restart failed")))
                } finally {
                    if (pending == null) busy.set(false)
                }
            }

            else -> reply(exchange, 404, mapOf("error" to "Unknown operation"))
        }
    }

    private fun reply(
        exchange: HttpExchange,
        code: Int,
        value: Map<String, Any>,
    ) {
        val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        server.stop(0)
        executor.shutdownNow()
        Files.deleteIfExists(descriptor)
    }
}
