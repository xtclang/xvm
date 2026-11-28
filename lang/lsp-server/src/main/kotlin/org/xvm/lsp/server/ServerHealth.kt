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
import java.util.concurrent.atomic.AtomicLong
import javax.management.Notification
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import javax.management.openmbean.CompositeData
import com.sun.management.OperatingSystemMXBean as ProcessCpu

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
    private val requests = AtomicLong()
    private val activity = ActivityWatch(sample())
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
        checks.scheduleWithFixedDelay(::checkActivity, ACTIVITY_SECONDS, ACTIVITY_SECONDS, TimeUnit.SECONDS)
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

    /** The latest minute in which the server worked, as its activity line logged it; null before one has. */
    fun activity(): Map<String, Any>? = activity.latest()

    /** Counts a request that the server answered, for its activity lines. */
    fun answered() {
        requests.incrementAndGet()
    }

    override fun close() {
        runCatching { activity.session(sample(), heap.peak(), maxHeap()) }
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

    private fun checkActivity() {
        runCatching { activity.window(sample(), heap.range(), maxHeap()) }
    }

    private fun sample() =
        ActivityWatch.Sample(
            System.currentTimeMillis(),
            (ManagementFactory.getOperatingSystemMXBean() as? ProcessCpu)?.processCpuTime?.coerceAtLeast(0) ?: 0,
            collectors.sumOf { it.collectionCount.coerceAtLeast(0) },
            collectors.sumOf { it.collectionTime.coerceAtLeast(0) },
            requests.get(),
        )

    private fun maxHeap() = ManagementFactory.getMemoryMXBean().heapMemoryUsage.max

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

        /** How often the server logs what it did, if it worked; see [ActivityWatch]. */
        const val ACTIVITY_SECONDS = 60L

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

    /** The lowest and the most that the collections of the last [WINDOW_MILLIS] left in use; null without one. */
    @Synchronized
    fun range(): LongRange? = window.takeIf { it.isNotEmpty() }?.let { it.minOf { e -> e.second }..it.maxOf { e -> e.second } }

    /** The most that a collection has left in use since the server started; null before one. */
    @Synchronized
    fun peak(): Long? = peak.takeIf { window.isNotEmpty() }

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

/**
 * What the server did in each minute in which it worked, and in its whole session, for the server log: the CPU it used,
 * its collections and their time, the range of heap they left in use, and the requests it answered. A minute in which
 * it used less than [BUSY_CPU_NANOS] of CPU logs nothing, so an idle server's log stays quiet; the session's line comes
 * when the server stops.
 */
internal class ActivityWatch(
    private val start: Sample,
    private val log: (String) -> Unit = { logger.info(it) },
) {
    /** The server's totals at [at] milliseconds, of which a line gives the differences. */
    data class Sample(
        val at: Long,
        val cpuNanos: Long,
        val collections: Long,
        val collectionMillis: Long,
        val requests: Long,
    )

    private var last = start
    private var latest: Map<String, Any>? = null

    /** Logs the minute up to [now] if the server worked in it, with the heap that its collections left in use. */
    @Synchronized
    fun window(
        now: Sample,
        heap: LongRange?,
        max: Long,
    ) {
        val previous = last
        last = now
        val cpu = now.cpuNanos - previous.cpuNanos
        if (cpu < BUSY_CPU_NANOS) return
        val seconds = (now.at - previous.at) / 1000
        val share = cpu * 100 / maxOf(1, (now.at - previous.at) * 1_000_000)
        log(
            "activity in the last $seconds s: CPU ${seconds(cpu)} ($share% of a core), " +
                "${now.collections - previous.collections} collections in ${(now.collectionMillis - previous.collectionMillis)} ms, " +
                "heap left in use ${heap?.let { "${mb(it.first)} to ${mb(it.last)}" } ?: "unknown"} of ${mb(max)}, " +
                "${now.requests - previous.requests} requests answered",
        )
        latest =
            mapOf(
                "at" to Instant.ofEpochMilli(now.at).toString(),
                "seconds" to seconds,
                "cpuMillis" to cpu / 1_000_000,
                "collections" to now.collections - previous.collections,
                "collectionMillis" to now.collectionMillis - previous.collectionMillis,
                "requests" to now.requests - previous.requests,
            ) + (heap?.let { mapOf("lowestAfterCollectionBytes" to it.first, "mostAfterCollectionBytes" to it.last) } ?: emptyMap())
    }

    /** Logs the whole session up to [now], with the most heap that a collection left in use. */
    @Synchronized
    fun session(
        now: Sample,
        peak: Long?,
        max: Long,
    ) {
        val minutes = (now.at - start.at) / 60_000
        log(
            "session of ${minutes / 60} h ${minutes % 60} min: CPU ${seconds(now.cpuNanos - start.cpuNanos)}, " +
                "${now.collections - start.collections} collections in ${(now.collectionMillis - start.collectionMillis) / 1000} s, " +
                "heap left in use at most ${peak?.let(::mb) ?: "unknown"} of ${mb(max)}, " +
                "${now.requests - start.requests} requests answered",
        )
    }

    @Synchronized
    fun latest(): Map<String, Any>? = latest

    companion object {
        /** The CPU in a minute below which the server is idle: under 2% of a core. */
        const val BUSY_CPU_NANOS = 1_000_000_000L
        private val logger = LoggerFactory.getLogger(ServerHealth::class.java)

        private fun seconds(nanos: Long) = "%.1f s".format(nanos / 1e9)
    }
}

/** [bytes] in MB, as the health lines give it. */
private fun mb(bytes: Long): String = "${bytes shr 20} MB"
