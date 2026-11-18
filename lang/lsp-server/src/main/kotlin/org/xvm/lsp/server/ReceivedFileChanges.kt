package org.xvm.lsp.server

/**
 * When the server received the latest watched-file event of each of the latest [limit] files, by URI, in milliseconds
 * since the epoch. A client that changed a file on disk can tell from it whether the server has heard of the change:
 * it has once it received an event of the file later than the change.
 */
internal class ReceivedFileChanges(
    private val limit: Int = 256,
) {
    private val received = linkedMapOf<String, Long>()

    /** Records that the events of the files [uris] arrived at [time], the latest last. */
    @Synchronized
    fun received(
        uris: Collection<String>,
        time: Long,
    ) {
        uris.forEach {
            received.remove(it)
            received[it] = time
        }
        while (received.size > limit) received.remove(received.keys.first())
    }

    @Synchronized
    fun snapshot(): Map<String, Long> = LinkedHashMap(received)
}
