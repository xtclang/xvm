package org.xvm.asm;


import java.util.List;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * Tests for {@link ConstantPool} thread-safety diagnostics.
 */
public class ConstantPoolDiagnosticsTest {
    /**
     * {@code f_implicits} backs the pool's memoized {@code clzXxx()} accessors, each of whose
     * first call does a {@code get} then a {@code put}. That is reachable from service threads -
     * {@code clzObject()} via {@code TypeConstant.isRootObject()} on the lazy TypeInfo path - and
     * all ServiceContexts of a container share one pool. The values converge, so the map itself is
     * the whole defect: a resizing {@code put} racing a {@code get} can lose unrelated keys.
     *
     * <p>The type pin is the assertion and is deterministic. The parallel exercise is a companion
     * and cannot be relied on to fail, as {@code HashMap} corruption is scheduling-dependent.</p>
     */
    @Test
    public void implicitIdentityCacheIsConcurrentSafe() throws Exception {
        var pool = new FileStructure("test").getConstantPool();

        var field = ConstantPool.class.getDeclaredField("f_implicits");
        field.setAccessible(true);
        assertInstanceOf(ConcurrentMap.class, field.get(pool),
                "f_implicits is written from service threads and must be a concurrent map");

        var names = List.of("String", "Boolean", "Object", "Exception",
                "Map", "Array", "Int64", "Char");
        try (var executor = Executors.newFixedThreadPool(8)) {
            var start   = new CountDownLatch(1);
            var futures = names.stream().map(name -> executor.submit(() -> {
                start.await();
                return pool.getImplicitlyImportedIdentity(name);
            })).toList();
            start.countDown();

            for (int i = 0; i < names.size(); i++) {
                assertSame(pool.getImplicitlyImportedIdentity(names.get(i)),
                        futures.get(i).get(10, TimeUnit.SECONDS),
                        "racing implicit lookups must resolve to the interned identity");
            }
        }
    }
}
