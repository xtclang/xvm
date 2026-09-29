package org.xvm.lsp.server

import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import org.eclipse.lsp4j.FileEvent

/**
 * Coalesce small-file duplicates by content. Directory and large-file events always propagate:
 * fingerprinting an entire moved workspace on the notification thread would stall the server.
 */
internal class FileChangeSnapshots(private val limit: Int = 512) {
    private val observed = linkedMapOf<Path, List<Byte>>()

    @Synchronized
    fun changed(events: List<FileEvent>): List<FileEvent> =
        events
            .associateBy { it.uri }
            .values
            .filter { event ->
                val path =
                    runCatching { Path.of(URI(event.uri)).toAbsolutePath().normalize() }.getOrNull()
                        ?: return@filter true
                val fingerprint =
                    runCatching { fingerprint(path) }.getOrNull() ?: return@filter true
                val previous = observed.put(path, fingerprint)
                while (observed.size > limit) observed.remove(observed.keys.first())
                previous != fingerprint
            }

    private fun fingerprint(path: Path): List<Byte>? {
        if (!Files.exists(path, NOFOLLOW_LINKS)) return emptyList()
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.size(path) > 1_000_000) return null
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).toList()
    }
}
