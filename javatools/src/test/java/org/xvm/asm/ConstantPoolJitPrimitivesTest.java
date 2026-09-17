package org.xvm.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Set;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link ConstantPool#getJitPrimitiveTypes}, which is held in a {@code Lazy.Bound} so
 * that {@link ConstantPool#optimize} can discard it.
 * <p/>
 * Nothing else in the build reaches this code: the set is used only by
 * {@code TypeConstant.isJitL2Specializable()}, which currently has no callers.
 * <p/>
 * These need the real compiled {@code ecstasy} module rather than a synthetic
 * {@code new FileStructure("test")} pool. Building the set asserts {@code isJitPrimitive()} on each
 * type, which reaches {@code TerminalTypeConstant.getCategory()} and therefore needs the actual
 * class components; against a synthetic pool that throws "missing class for constant: Bit".
 */
public class ConstantPoolJitPrimitivesTest {
    private static final File LIB =
            new File("lib_ecstasy/build/xtc/main/lib").isDirectory()
                    ? new File("lib_ecstasy/build/xtc/main/lib")
                    : new File("../lib_ecstasy/build/xtc/main/lib");

    /**
     * The value is computed once and handed back identically on every subsequent call.
     */
    @Test
    public void memoizesTheSet() throws IOException {
        ConstantPool pool = ecstasyPool();

        Set<TypeConstant> first = pool.getJitPrimitiveTypes();
        assertNotNull(first);
        assertFalse(first.isEmpty());
        assertSame(first, pool.getJitPrimitiveTypes(), "the set must be computed once and cached");
    }

    /**
     * Every entry really is a JIT primitive, and each declared type contributes its nullable form
     * as well, so the set holds an even number of types.
     */
    @Test
    public void holdsEachPrimitiveAndItsNullableForm() throws IOException {
        ConstantPool pool = ecstasyPool();

        Set<TypeConstant> types = pool.getJitPrimitiveTypes();
        for (TypeConstant type : types) {
            assertTrue(type.isJitPrimitive(), () -> type + " is not a JIT primitive");
        }

        assertTrue(types.contains(pool.typeInt64()));
        assertTrue(types.contains(pool.typeInt64().ensureNullable()));
        assertTrue(types.contains(pool.typeFloat8e4()));
        assertTrue(types.contains(pool.typeFloat8e4().ensureNullable()));
        assertTrue(types.contains(pool.typeFloat8e5()));
        assertTrue(types.contains(pool.typeFloat8e5().ensureNullable()));

        assertEquals(0, types.size() % 2, "each type contributes itself and its nullable form");
    }

    /**
     * The set is immutable, so it can be handed out without copying.
     */
    @Test
    public void handsOutAnImmutableSet() throws IOException {
        Set<TypeConstant> types = ecstasyPool().getJitPrimitiveTypes();

        assertThrows(UnsupportedOperationException.class, types::clear);
    }

    /**
     * The point of the holder being resettable: {@code optimize()} evicts unreferenced constants
     * and renumbers the rest, so a set derived from constants resolved beforehand has to be
     * discarded and recomputed rather than handed out stale.
     */
    @Test
    public void optimizeDiscardsTheCachedSet() throws IOException {
        FileStructure file = ecstasyFile();
        ConstantPool  pool = file.getConstantPool();

        Set<TypeConstant> before = pool.getJitPrimitiveTypes();
        assertSame(before, pool.getJitPrimitiveTypes());

        // reregisterConstants(true) is the only thing that drives optimize()
        file.reregisterConstants(true);

        Set<TypeConstant> after = pool.getJitPrimitiveTypes();
        assertNotNull(after);
        assertFalse(after.isEmpty());
        assertNotSame(before, after, "optimize() must discard the cached set, not reuse it");
        assertSame(after, pool.getJitPrimitiveTypes(), "the recomputed set must be cached again");

        for (TypeConstant type : after) {
            assertTrue(type.isJitPrimitive(), () -> type + " is not a JIT primitive after optimize");
        }
    }

    /**
     * Concurrent first access must compute once and give every caller the same set. Computing takes
     * the holder's monitor and then, by way of registering constants, the pool's; this exercises
     * that order under contention.
     */
    @Test
    public void concurrentFirstAccessComputesOnce() throws Exception {
        ConstantPool pool    = ecstasyPool();
        int          cThread = 8;

        try (var executor = Executors.newFixedThreadPool(cThread)) {
            var start = new CountDownLatch(1);
            var futures = IntStream.range(0, cThread)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return pool.getJitPrimitiveTypes();
                    }))
                    .toList();
            start.countDown();

            Set<TypeConstant> expected = futures.getFirst().get(30, TimeUnit.SECONDS);
            assertNotNull(expected);
            for (var future : futures) {
                assertSame(expected, future.get(30, TimeUnit.SECONDS),
                        "racing callers must all observe the same computed set");
            }
        }
    }

    private static ConstantPool ecstasyPool() throws IOException {
        return ecstasyFile().getConstantPool();
    }

    private static FileStructure ecstasyFile() throws IOException {
        File fileEcstasy = new File(LIB, "ecstasy.xtc");
        assumeTrue(fileEcstasy.isFile(),
                "need a compiled ecstasy.xtc; run ./gradlew xdk:installDist");

        try (var in = new FileInputStream(fileEcstasy)) {
            return new FileStructure(in);
        }
    }
}
