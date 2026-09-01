package org.xvm.asm.constants;


import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.api.EmbeddingTestSupport;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.ErrorListener.ErrorInfo;
import org.xvm.asm.XvmStructure;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Building a TypeInfo is also what validates the type, and the result is memoized - so before this,
 * the diagnostics that building found went to whichever caller happened to ask FIRST and to nobody
 * afterwards. Which caller "owns" a type error was therefore decided by call ordering.
 *
 * <p>A TypeInfo now carries the diagnostics its own construction produced, and the memoized path
 * replays them. These tests pin that, and the property that makes it safe to replay from every
 * caller: {@link ErrorList} deduplicates, so being told twice records once.
 *
 * <p>See docs/errorlistener/README.md section 8.
 */
public class TypeInfoDiagnosticsReplayTest {
    @Test
    public void everyCallerHearsWhatBuildingTheTypeFound() {
        ConstantPool pool = systemPool();
        TypeConstant type = pool.typeInt64();

        // build it once, the ordinary way
        TypeInfo info = type.ensureTypeInfo(ErrorListener.BLACKHOLE);

        // stand in for a diagnostic that building it produced; doing it this way is what lets the
        // test pin the REPLAY without needing a deliberately broken module to produce a real one
        info.recordDiagnostics(List.of(diagnostic("in the build")));

        // two later callers, each with its own listener - the memoized path must tell both
        var first  = ErrorList.unlimited();
        var second = ErrorList.unlimited();

        assertEquals(info, type.ensureTypeInfo(first),  "the cached TypeInfo is what comes back");
        assertEquals(info, type.ensureTypeInfo(second), "and again for the second caller");

        assertEquals(1, first.getErrors().size(),  "the first later caller was told");
        assertEquals(1, second.getErrors().size(), "and so was the second - not just whoever asked first");
        assertEquals(Constants.VE_UNKNOWN, first.getErrors().getFirst().getCode());
    }

    /**
     * Replaying into one listener repeatedly must not multiply the diagnostic. This is the property
     * that makes "tell every caller" safe rather than noisy, and it is not new machinery:
     * {@code ErrorList.log} has always deduplicated by UID.
     */
    @Test
    public void beingToldTwiceRecordsItOnce() {
        ConstantPool pool = systemPool();
        TypeConstant type = pool.typeInt64();
        TypeInfo     info = type.ensureTypeInfo(ErrorListener.BLACKHOLE);

        info.recordDiagnostics(List.of(diagnostic("said twice")));

        var errs = ErrorList.unlimited();
        type.ensureTypeInfo(errs);
        type.ensureTypeInfo(errs);
        info.replayDiagnostics(errs);

        assertEquals(1, errs.getErrors().size(), "deduplicated by UID, however many times it is replayed");
    }

    /**
     * A type that builds cleanly records nothing - so the replay costs an empty loop and cannot
     * invent a diagnostic for a type that does not have one.
     */
    @Test
    public void aCleanBuildRecordsNothing() {
        ConstantPool pool = systemPool();
        TypeInfo     info = pool.typeInt64().ensureTypeInfo(ErrorListener.BLACKHOLE);

        assertTrue(info.diagnostics().isEmpty(), () -> "unexpected: " + info.diagnostics());

        var errs = ErrorList.unlimited();
        pool.typeInt64().ensureTypeInfo(errs);
        assertTrue(errs.getErrors().isEmpty(), "nothing to replay, so nothing reported");
    }

    /**
     * The safeguard, stated as a test: reachability must NOT depend on the
     * invalidate-on-serious-errors hatch.
     *
     * <p>That hatch declines to cache a TypeInfo whose build produced serious errors, so the next
     * caller rebuilds and hears them. It is easy to mistake that for the thing that makes serious
     * diagnostics reachable - and then to "simplify" it away. It is not: the record plus the replay
     * cover a cached TypeInfo too. This pins that, so removing or changing the hatch is a decision
     * about caching, not one that can silently make a broken type stop reporting.
     */
    @Test
    public void seriousDiagnosticsReachALaterCallerEvenFromTheCache() {
        ConstantPool pool = systemPool();
        TypeConstant type = pool.typeInt64();
        TypeInfo     info = type.ensureTypeInfo(ErrorListener.BLACKHOLE);

        info.recordDiagnostics(List.of(new ErrorInfo(Severity.ERROR, Constants.VE_UNKNOWN,
                new Object[] {"serious, and cached"}, (XvmStructure) null)));

        var errs = ErrorList.unlimited();
        type.ensureTypeInfo(errs);

        assertEquals(1, errs.getErrors().size(), "a cached TypeInfo still reports what building it found");
        assertTrue(errs.hasSeriousErrors(), "and at the severity it was recorded with");
    }

    /**
     * The other half of the safeguard: dropping the cache must not leave a stale record to be
     * served. After an invalidation the type is rebuilt, and what the REBUILD finds is what gets
     * reported - not what some earlier build happened to leave behind.
     */
    @Test
    public void invalidationDoesNotResurrectStaleDiagnostics() {
        ConstantPool pool = systemPool();
        TypeConstant type = pool.typeInt64();

        type.ensureTypeInfo(ErrorListener.BLACKHOLE)
            .recordDiagnostics(List.of(diagnostic("from a build that no longer applies")));

        type.invalidateTypeInfo();

        var errs = ErrorList.unlimited();
        type.ensureTypeInfo(errs);

        assertTrue(errs.getErrors().isEmpty(),
                () -> "a rebuild reports what it finds, not a stale record: " + errs.getErrors());
    }

    private static ErrorInfo diagnostic(String sText) {
        return new ErrorInfo(Severity.WARNING, Constants.VE_UNKNOWN, new Object[] {sText},
                (XvmStructure) null);
    }

    private static ConstantPool systemPool() {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");
        var runtime = new Runtime();
        runtime.start();
        return NativeContainer.create(runtime, EmbeddingTestSupport.systemRepository(),
                ErrorListener.RUNTIME).getConstantPool();
    }
}
