package org.xvm.asm;


import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Compiler;
import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * An {@link ErrorList} is the sink a host hands to {@code XtcEngine.compile(errsCaller, ...)}, and
 * the engine then gives that one object to every compile thread it runs. The shared terminal sink is
 * therefore concurrent by construction - not by a caller's choice - so it has to survive being
 * logged to from many threads at once.
 *
 * <p>It did not. {@code log} did an unguarded {@code HashSet.add}, a read-modify-write on the worst
 * severity, an {@code ArrayList.add} and a {@code ++}, with no lock anywhere in the class.
 *
 * <p>The lock is on {@code log} and {@code clear} only. The polled readers - {@code isAbortDesired},
 * {@code hasSeriousErrors} - take none, because {@code StageMgr} calls them per node and the scalars
 * they read are {@code volatile} instead. This test pins the write path, which is the one that
 * needed guarding.
 *
 * <p>The first test is the one that catches it, and it is not marginal: with the lock removed it
 * kept 2125 of 4000 diagnostics. The second pins deduplication under the same contention and passed
 * even unlocked - a {@code HashSet} race is real but far less likely to be observed - so it is here
 * to state the invariant, not as a second detector. Keep the first if only one survives.
 */
public class ErrorListConcurrencyTest {
    private static final int THREADS    = 8;
    private static final int PER_THREAD = 500;

    /**
     * Distinct diagnostics from many threads: every one must survive.
     */
    @Test
    public void aSharedSinkKeepsEveryDiagnostic() throws Exception {
        ErrorList errs = ErrorList.unlimited();

        inParallel(thread -> {
            for (int i = 0; i < PER_THREAD; ++i) {
                errs.log(distinct(thread * PER_THREAD + i));
            }
        });

        assertEquals(THREADS * PER_THREAD, errs.getErrors().size(),
                "a missing diagnostic means one thread's add was lost inside the list");
        assertEquals(THREADS * PER_THREAD, errs.getSeriousErrorCount(),
                "the serious-error count is a ++ on the same unguarded path");
    }

    /**
     * The SAME diagnostic from many threads: deduplication must still leave exactly one.
     */
    @Test
    public void deduplicationSurvivesTheRace() throws Exception {
        ErrorList errs = ErrorList.unlimited();

        inParallel(thread -> {
            for (int i = 0; i < PER_THREAD; ++i) {
                errs.log(distinct(0));
            }
        });

        assertEquals(1, errs.getErrors().size(),
                "one UID, so one diagnostic, however many threads reported it at once");
    }

    /**
     * Run {@code body} on {@link #THREADS} threads and rethrow whatever any of them threw.
     */
    private static void inParallel(IntConsumer body) throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, THREADS)
                    .<Callable<Void>>mapToObj(thread -> () -> {
                        body.accept(thread);
                        return null;
                    })
                    .toList();
            for (var future : pool.invokeAll(tasks)) {
                future.get();
            }
        }
    }

    /**
     * @param n  a value unique to this diagnostic
     *
     * @return an ErrorInfo whose UID is unique to {@code n}. {@code genUID} keys on the parameter
     *         VALUES, so a single distinct int is enough. It used to key on
     *         {@code Arrays.hashCode}, where a single int also avoided collisions - by luck rather
     *         than by contract, which is the defect master issue 47 records.
     */
    private static ErrorListener.ErrorInfo distinct(int n) {
        return new ErrorListener.ErrorInfo(
                Severity.ERROR, Compiler.FATAL_ERROR, new Object[]{n}, (XvmStructure) null);
    }

    @FunctionalInterface
    private interface IntConsumer {
        void accept(int value);
    }
}
