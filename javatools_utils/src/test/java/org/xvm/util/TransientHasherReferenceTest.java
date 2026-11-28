package org.xvm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.Test;

import org.xvm.testing.Await;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A {@link TransientHasherReference} is held by one thread at a time, and only the thread that gave one back takes it
 * again, up to as many as the thread keeps. Two threads hand their steps to each other; nothing is timed.
 */
public class TransientHasherReferenceTest {
    @Test
    public void aReferenceGivenBackGoesOnlyToTheThreadThatGaveItBack() throws Exception {
        Hasher<String> hasher = Hasher.natural();
        try (Agent first = new Agent(); Agent second = Agent.sharingSlotWith(first)) {
            // the first thread takes a reference, and the second thread one of its own meanwhile
            TransientHasherReference<String> held = first.call(() -> TransientHasherReference.of("first", hasher));
            TransientHasherReference<String> other = second.call(() -> TransientHasherReference.of("second", hasher));
            assertNotSame(held, other);

            // the second thread gives its reference back and takes one again, while the first still holds its own
            second.call(() -> {
                other.close();
                try (var again = TransientHasherReference.of("again", hasher)) {
                    assertSame(other, again);
                }
                return null;
            });
            assertEquals("first", held.get());
            assertEquals(hasher.hash("first"), held.hashCode());

            // a reference that the first thread gives back is the first thread's own spare, never the second's
            first.call(() -> {
                held.close();
                return null;
            });
            TransientHasherReference<String> taken = second.call(() -> TransientHasherReference.of("second", hasher));
            assertNotSame(held, taken);
            assertSame(other, taken);
            assertSame(held, first.call(() -> TransientHasherReference.of("first", hasher)));
        }
    }

    @Test
    public void aThreadReusesItsSparesForLookupsNestedAsDeepAsItKeepsThem() throws Exception {
        var hasher = Hasher.<String>natural();
        try (var agent = new Agent()) {
            agent.call(() -> {
                // one more than the thread keeps, held at once as nested lookups hold them, and given back as they
                // end, the innermost first: the outermost, given back last, finds the thread's spares full
                var held = new ArrayList<TransientHasherReference<String>>();
                for (int i = 0; i <= TransientHasherReference.SPARES; ++i) {
                    held.add(TransientHasherReference.of("level " + i, hasher));
                }
                held.reversed().forEach(TransientHasherReference::close);

                // lookups nested as deep again take the spares, the last given back first, and the deepest a new one
                var again = new ArrayList<TransientHasherReference<String>>();
                for (int i = 0; i <= TransientHasherReference.SPARES; ++i) {
                    again.add(TransientHasherReference.of("again " + i, hasher));
                }
                for (int i = 0; i < TransientHasherReference.SPARES; ++i) {
                    assertSame(held.get(i + 1), again.get(i));
                    assertEquals("again " + i, again.get(i).get());
                }
                // by identity: a reference given back has no hasher to compare with
                assertFalse(held.stream().anyMatch(ref -> ref == again.getLast()));
                assertFalse(again.stream().anyMatch(ref -> ref == held.getFirst()));
                return null;
            });
        }
    }

    /**
     * A thread that runs what it is given, one call at a time, and returns each call's result.
     */
    private static final class Agent implements AutoCloseable {
        /**
         * A thread that is not started yet, for its identity hash. The pool that threads once shared took the slot of
         * its lock-free stacks from that hash, so the second thread is one that would have shared the first's slot.
         */
        static Agent sharingSlotWith(Agent agent) {
            int mask = (Integer.highestOneBit(Runtime.getRuntime().availableProcessors()) << 1) - 1;
            while (true) {
                Agent candidate = new Agent();
                if ((candidate.thread.hashCode() & mask) == (agent.thread.hashCode() & mask)) {
                    candidate.thread.start();
                    return candidate;
                }
            }
        }

        Agent() {
            thread = new Thread(this::serve, "TransientHasherReferenceTest");
            thread.setDaemon(true);
        }

        <T> T call(Callable<T> task) throws Exception {
            if (thread.getState() == Thread.State.NEW) {
                thread.start();
            }
            FutureTask<T> call = new FutureTask<>(task);
            calls.add(call);
            return Await.get(call);
        }

        @Override
        public void close() throws InterruptedException {
            if (thread.getState() != Thread.State.NEW) {
                calls.add(STOP);
                Await.join(thread);
            }
        }

        private void serve() {
            try {
                for (FutureTask<?> call; (call = calls.take()) != STOP; ) {
                    call.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * The call that ends the thread.
         */
        private static final FutureTask<?> STOP = new FutureTask<>(() -> null);

        private final Thread                       thread;
        private final BlockingQueue<FutureTask<?>> calls = new LinkedBlockingQueue<>();
    }
}
