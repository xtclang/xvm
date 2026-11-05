package org.xtclang.idea

import org.xvm.testing.Await
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

// The waits of the plugin's tests, each until what it waits for happens: the watchdog of org.xvm.testing fails one
// when nothing has used CPU for a minute, at once on a deadlock, and after LIVELOCK on a livelock, with a dump of every
// thread. None has a limit of time that a slow machine reaches. Each takes, optionally, what it waits for, for the
// failure's message, which otherwise names the object waited on and the line that waits.

/** The livelock guard, for a limit that the code under test takes, such as a worker's startup: only a livelock reaches it. */
val LIVELOCK: Duration = Await.LIVELOCK.toKotlinDuration()

/** [LIVELOCK] in seconds, for annotations such as JUnit's `@Timeout`. */
const val LIVELOCK_SECONDS = Await.LIVELOCK_SECONDS

/** What a wait waits for, said by [text] only when a hang fails the wait, for a text that is costly to make. */
fun what(text: () -> String): Any =
    object {
        override fun toString() = text()
    }

/** The future's value, once it completes. */
fun <T> Future<T>.awaited(what: Any? = null) = Await.get(this, what)

/** True, once the latch counts down to zero. */
fun CountDownLatch.awaited(what: Any? = null) = Await.await(this, what)

/** The arrival index, once every party arrives. */
fun CyclicBarrier.awaited(what: Any? = null) = Await.await(this, what)

/** The head of the queue, once it has one. */
fun <T> BlockingQueue<T>.awaited(what: Any? = null) = Await.poll(this, what)

/** True, once the semaphore gives the permits. */
fun Semaphore.awaited(
    permits: Int = 1,
    what: Any? = null,
) = Await.tryAcquire(this, permits, what)

/** True, once the process ends. */
fun Process.awaited(what: Any? = null) = Await.waitFor(this, what)

/** Once the thread ends. */
fun Thread.awaited(what: Any? = null) = Await.join(this, what)

/** True, once the executor has shut down and its tasks have ended. */
fun ExecutorService.awaitedTermination(what: Any? = null) = Await.awaitTermination(this, what)

/** Poll the condition until it holds. */
fun awaitUntil(
    what: Any? = null,
    condition: () -> Boolean,
) = Await.until({ condition() }, what)

/** Run the assertion until it passes; a hang fails with its last failure as the cause. */
fun awaitAsserted(
    what: Any? = null,
    assertion: () -> Unit,
) = Await.untilAsserted({ assertion() }, what)

/** Run the block on a thread of its own and wait for it, as JUnit's `assertTimeoutPreemptively` does. */
fun <T> awaitCall(
    what: Any? = null,
    block: () -> T,
): T = Await.call(what) { block() }
