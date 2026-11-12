package org.xvm.lsp.server

import com.sun.management.GarbageCollectionNotificationInfo
import com.sun.management.HotSpotDiagnosticMXBean
import com.sun.management.VMOption
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.management.Notification
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import javax.management.openmbean.CompositeData

/**
 * What the server notices about its own health, for `xtc/languageServiceStatus` and the server log: the heap that the
 * garbage collections leave in use, and a compile queue whose jobs wait or run too long. Each threshold that the server
 * crosses is logged once, as a warning, and its recovery once, and the latest of these lines stay in [events].
 *
 * The JVM tells it of each collection, so nothing polls the heap; the queue it checks every [QUEUE_CHECK_SECONDS].
 */
internal class ServerHealth(
    /** The compile queue's snapshot, as `xtc/languageServiceStatus` reports it; null when there is no compiler. */
    private val queue: () -> Map<String, Any>?,
) : AutoCloseable {
    private val events = HealthEvents()
    private val heap = HeapWatch(events)
    private val jobs = QueueWatch(events)
    private val heapPools =
        ManagementFactory
            .getMemoryPoolMXBeans()
            .filter { it.type == MemoryType.HEAP }
            .map { it.name }
            .toSet()
    private val collectors = ManagementFactory.getGarbageCollectorMXBeans()
    private val listener = NotificationListener { notification, _ -> collected(notification) }
    private val checks: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(
            Thread
                .ofPlatform()
                .daemon()
                .name("xtc-health")
                .factory(),
        )

    init {
        collectors.filterIsInstance<NotificationEmitter>().forEach { it.addNotificationListener(listener, null, null) }
        checks.scheduleWithFixedDelay(::checkQueue, QUEUE_CHECK_SECONDS, QUEUE_CHECK_SECONDS, TimeUnit.SECONDS)
    }

    /** The heap's figures for `xtc/languageServiceStatus`, with what the collections have left in use. */
    fun heapStatus(): Map<String, Any?> {
        val usage = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        return mapOf(
            "usedBytes" to usage.used,
            "committedBytes" to usage.committed,
            "maxBytes" to usage.max,
            // the heap's target below its maximum, which only some collectors keep to; null when none is set
            "softMaxBytes" to softMax(),
            "collectors" to collectors.map { it.name },
            "collections" to collectors.sumOf { it.collectionCount.coerceAtLeast(0) },
            "collectionMillis" to collectors.sumOf { it.collectionTime.coerceAtLeast(0) },
        ) + heap.status()
    }

    /** The latest lines the server logged about its health, oldest first. */
    fun events(): List<Map<String, Any>> = events.recent()

    override fun close() {
        checks.shutdownNow()
        collectors.filterIsInstance<NotificationEmitter>().forEach { runCatching { it.removeNotificationListener(listener) } }
    }

    private fun collected(notification: Notification) {
        if (notification.type != GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION) return
        val info = GarbageCollectionNotificationInfo.from(notification.userData as CompositeData).gcInfo
        val after =
            info.memoryUsageAfterGc
                .filterKeys(heapPools::contains)
                .values
                .sumOf { it.used }
        // ZGC's pauses report no usage; only its cycles do
        if (after > 0) heap.collected(System.currentTimeMillis(), after, ManagementFactory.getMemoryMXBean().heapMemoryUsage.max)
    }

    private fun checkQueue() {
        runCatching {
            val snapshot = queue() ?: return
            jobs.check(
                System.currentTimeMillis(),
                (snapshot["queueSize"] as? Int) ?: 0,
                (snapshot["oldestQueuedMs"] as? Long) ?: 0,
                (snapshot["longestRunningMs"] as? Long) ?: 0,
                (snapshot["runningJobs"] as? List<*>)?.firstOrNull()?.toString(),
            )
        }.onFailure { logger.warn("Could not check the compile queue's health", it) }
    }

    private fun softMax(): Long? =
        runCatching {
            ManagementFactory
                .getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
                .getVMOption("SoftMaxHeapSize")
                .takeIf { it.origin != VMOption.Origin.DEFAULT && it.origin != VMOption.Origin.ERGONOMIC }
                ?.value
                ?.toLong()
        }.getOrNull()

    companion object {
        /** How often the compile queue is checked. */
        const val QUEUE_CHECK_SECONDS = 5L

        private val logger = LoggerFactory.getLogger(ServerHealth::class.java)
    }
}

/** The latest [CAPACITY] lines that the server logged about its health, which [warn] and [recovered] log too. */
internal class HealthEvents(
    private val log: (warning: Boolean, text: String) -> Unit = { warning, text ->
        if (warning) logger.warn(text) else logger.info(text)
    },
) {
    private val recent = ArrayDeque<Map<String, Any>>()

    fun warn(
        at: Long,
        text: String,
    ) = add(at, "warning", text)

    fun recovered(
        at: Long,
        text: String,
    ) = add(at, "recovered", text)

    @Synchronized
    fun recent(): List<Map<String, Any>> = recent.toList()

    @Synchronized
    private fun add(
        at: Long,
        kind: String,
        text: String,
    ) {
        log(kind == "warning", text)
        recent.addLast(mapOf("at" to Instant.ofEpochMilli(at).toString(), "kind" to kind, "text" to text))
        while (recent.size > CAPACITY) recent.removeFirst()
    }

    private companion object {
        const val CAPACITY = 20
        val logger = LoggerFactory.getLogger(ServerHealth::class.java)
    }
}

/**
 * The heap that the collections leave in use, of which the lowest in the last [WINDOW_MILLIS] estimates the live data:
 * one collection's figure also counts garbage that a later one frees, G1's young collections the old garbage up to its
 * next marking, by up to several times the live data. It warns once when that estimate reaches a share of the maximum
 * in [SHARES], and once more on each higher one, and says once that it is back when it falls [RECOVERY] points below
 * the share it reached; and it warns when the estimate has doubled, by at least [GROWTH_BYTES], since the last warning
 * about growth, or since the server started.
 */
internal class HeapWatch(
    private val events: HealthEvents,
) {
    private val window = ArrayDeque<Pair<Long, Long>>()
    private var share = 0
    private var last = 0L
    private var peak = 0L
    private var grownFrom = 0L

    /** Called after each collection, at [at] milliseconds, with the heap it left in use and the heap's maximum. */
    @Synchronized
    fun collected(
        at: Long,
        after: Long,
        max: Long,
    ) {
        last = after
        peak = maxOf(peak, after)
        window.addLast(at to after)
        while (window.first().first < at - WINDOW_MILLIS) window.removeFirst()
        val live = window.minOf { it.second }
        val percent = (live * 100 / max).toInt()
        val reached = SHARES.lastOrNull { percent >= it } ?: 0
        if (reached > share) {
            share = reached
            events.warn(at, "The heap the garbage collections leave in use is at $percent% of its maximum: ${mb(live)} of ${mb(max)}")
        } else if (share > 0 && percent < share - RECOVERY) {
            events.recovered(at, "The heap the garbage collections leave in use is back at $percent% of its maximum, below $share%")
            share = SHARES.lastOrNull { percent >= it } ?: 0
        }
        if (grownFrom == 0L || live < grownFrom) {
            grownFrom = live
        } else if (live >= 2 * grownFrom && live - grownFrom >= GROWTH_BYTES) {
            events.warn(at, "The heap the garbage collections leave in use grew from ${mb(grownFrom)} to ${mb(live)}")
            grownFrom = live
        }
    }

    /** What the collections have left in use: after the last, the lowest in the last [WINDOW_MILLIS], and the most. */
    @Synchronized
    fun status(): Map<String, Any?> =
        mapOf(
            "afterLastCollectionBytes" to last.takeIf { window.isNotEmpty() },
            "lowestAfterCollectionBytes" to window.minOfOrNull { it.second },
            "peakAfterCollectionBytes" to peak.takeIf { window.isNotEmpty() },
        )

    companion object {
        const val WINDOW_MILLIS = 60_000L
        val SHARES = listOf(50, 75, 90)
        const val RECOVERY = 10
        const val GROWTH_BYTES = 256L shl 20
    }
}

/**
 * The compile queue's health: it warns once when the queue holds [QUEUED_JOBS] jobs or more, or its oldest job has
 * waited [WAIT_MILLIS], and says once that it is back when the queue is empty; and it warns once of each job that runs
 * [RUN_MILLIS] or longer.
 */
internal class QueueWatch(
    private val events: HealthEvents,
) {
    private var stalled = false
    private var longRunning: String? = null

    /**
     * Called at [at] milliseconds with the number of queued jobs, how long the oldest has waited and the longest running
     * job has run, in milliseconds, and the running job's label.
     */
    @Synchronized
    fun check(
        at: Long,
        queued: Int,
        oldestWait: Long,
        longestRun: Long,
        running: String?,
    ) {
        if (!stalled && (queued >= QUEUED_JOBS || oldestWait >= WAIT_MILLIS)) {
            stalled = true
            events.warn(
                at,
                "The compile queue is backing up: $queued jobs queued, the oldest for ${oldestWait / 1000} s; running: ${running ?: "none"}",
            )
        } else if (stalled && queued == 0) {
            stalled = false
            events.recovered(at, "The compile queue has caught up")
        }
        if (running != null && longestRun >= RUN_MILLIS && running != longRunning) {
            longRunning = running
            events.warn(at, "A compile job has run for ${longestRun / 1000} s: $running")
        }
    }

    companion object {
        const val QUEUED_JOBS = 10
        const val WAIT_MILLIS = 30_000L
        const val RUN_MILLIS = 120_000L
    }
}

/** [bytes] in MB, as the health lines give it. */
private fun mb(bytes: Long): String = "${bytes shr 20} MB"
