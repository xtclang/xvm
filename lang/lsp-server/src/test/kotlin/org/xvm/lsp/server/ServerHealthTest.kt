package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The health rules, fed with figures and times, so that no test depends on a collection or the clock. */
class ServerHealthTest {
    private val logged = mutableListOf<Pair<Boolean, String>>()
    private val events = HealthEvents { warning, text -> logged += warning to text }

    @Test
    fun `a minute in which the server worked is logged with its figures, an idle one is not, and the session at its end`() {
        val lines = mutableListOf<String>()
        val activity = ActivityWatch(ActivityWatch.Sample(0, 0, 0, 0, 0)) { lines += it }
        // an idle minute: half a second of CPU
        activity.window(ActivityWatch.Sample(60_000, 500_000_000, 2, 10, 1), 100 * MB..120 * MB, 2048 * MB)
        assertThat(lines).isEmpty()
        assertThat(activity.latest()).isNull()
        // a busy one: 30 seconds of CPU, half a core
        activity.window(ActivityWatch.Sample(120_000, 30_500_000_000, 14, 810, 15), 610 * MB..1850 * MB, 2048 * MB)
        assertThat(lines).containsExactly(
            "activity in the last 60 s: CPU 30.0 s (50% of a core), 12 collections in 800 ms, " +
                "heap left in use 610 MB to 1850 MB of 2048 MB, 14 requests answered",
        )
        assertThat(activity.latest()).containsEntry("cpuMillis", 30_000L).containsEntry("requests", 14L)
        activity.session(ActivityWatch.Sample(3_720_000, 31_000_000_000, 20, 2_400, 16), 1850 * MB, 2048 * MB)
        assertThat(lines.last()).isEqualTo(
            "session of 1 h 2 min: CPU 31.0 s, 20 collections in 2 s, heap left in use at most 1850 MB of 2048 MB, " +
                "16 requests answered",
        )
    }

    @Test
    fun `the heap warns once on each share of its maximum that the lowest collection of the last minute reaches`() {
        val heap = HeapWatch(events)
        heap.collected(0, 400 * MB, 1000 * MB)
        assertThat(logged).isEmpty()
        heap.collected(1_000, 520 * MB, 1000 * MB)
        // the lowest of the last minute, 400 MB, is below half: one collection's figure is not the live data
        assertThat(logged).isEmpty()
        heap.collected(61_500, 530 * MB, 1000 * MB)
        assertThat(
            logged,
        ).containsExactly(true to "The heap the garbage collections leave in use is at 53% of its maximum: 530 MB of 1000 MB")
        heap.collected(62_000, 560 * MB, 1000 * MB)
        assertThat(logged).hasSize(1)
        heap.collected(130_000, 760 * MB, 1000 * MB)
        assertThat(
            logged.last(),
        ).isEqualTo(true to "The heap the garbage collections leave in use is at 76% of its maximum: 760 MB of 1000 MB")
        assertThat(heap.status())
            .containsEntry("afterLastCollectionBytes", 760 * MB)
            .containsEntry("lowestAfterCollectionBytes", 760 * MB)
            .containsEntry("peakAfterCollectionBytes", 760 * MB)
    }

    @Test
    fun `the heap says once that it is back, ten points below the share it reached`() {
        val heap = HeapWatch(events)
        heap.collected(0, 600 * MB, 1000 * MB)
        heap.collected(61_000, 410 * MB, 1000 * MB)
        assertThat(logged).hasSize(1)
        heap.collected(62_000, 390 * MB, 1000 * MB)
        assertThat(
            logged.last(),
        ).isEqualTo(false to "The heap the garbage collections leave in use is back at 39% of its maximum, below 50%")
        heap.collected(63_000, 380 * MB, 1000 * MB)
        assertThat(logged).hasSize(2)
    }

    @Test
    fun `the heap warns when what the collections leave has doubled, by 256 MB or more`() {
        val heap = HeapWatch(events)
        val max = 16_384 * MB
        heap.collected(0, 200 * MB, max)
        heap.collected(61_000, 390 * MB, max)
        assertThat(logged).isEmpty()
        heap.collected(122_000, 460 * MB, max)
        assertThat(logged).containsExactly(true to "The heap the garbage collections leave in use grew from 200 MB to 460 MB")
        // from the level it warned at, not again until that has doubled
        heap.collected(183_000, 900 * MB, max)
        assertThat(logged).hasSize(1)
        heap.collected(244_000, 920 * MB, max)
        assertThat(logged.last()).isEqualTo(true to "The heap the garbage collections leave in use grew from 460 MB to 920 MB")
    }

    @Test
    fun `the queue warns once when it backs up, says once that it caught up, and warns of each long job once`() {
        val queue = QueueWatch(events)
        queue.check(0, 3, 29_000, 1_000, "#7 compile file:///A.x")
        assertThat(logged).isEmpty()
        queue.check(5_000, 3, 34_000, 6_000, "#7 compile file:///A.x")
        assertThat(logged).containsExactly(
            true to "The compile queue is backing up: 3 jobs queued, the oldest for 34 s; running: #7 compile file:///A.x",
        )
        queue.check(10_000, 12, 39_000, 11_000, "#7 compile file:///A.x")
        assertThat(logged).hasSize(1)
        queue.check(15_000, 0, 0, 0, null)
        assertThat(logged.last()).isEqualTo(false to "The compile queue has caught up")
        queue.check(20_000, 0, 0, 120_000, "#9 compile ecstasy.x")
        queue.check(25_000, 0, 0, 125_000, "#9 compile ecstasy.x")
        assertThat(logged.last()).isEqualTo(true to "A compile job has run for 120 s: #9 compile ecstasy.x")
        assertThat(logged).hasSize(3)
    }

    @Test
    fun `the status keeps the latest twenty lines, oldest first`() {
        repeat(25) { events.warn(it * 1_000L, "line $it") }
        val recent = events.recent()
        assertThat(recent).hasSize(20)
        assertThat(
            recent.first(),
        ).containsEntry("text", "line 5").containsEntry("kind", "warning").containsEntry("at", "1970-01-01T00:00:05Z")
        assertThat(recent.last()).containsEntry("text", "line 24")
    }

    private companion object {
        const val MB = 1L shl 20
    }
}
