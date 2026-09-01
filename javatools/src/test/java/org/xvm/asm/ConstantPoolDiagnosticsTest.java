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
     * The implicit-identity cache is lazily written from concurrent service threads at runtime
     * (e.g. {@code xService} resolving "Timeout" through the owner pool while sibling services run
     * on the shared executor; TypeInfo builds resolving "Object"/"String"). All ServiceContexts of
     * one container share one pool, so no parallel containers are needed to reach this.
     *
     * <p>Master's shape was a plain {@code HashMap}, so a {@code put} resize racing another
     * thread's {@code get} could structurally corrupt the map - lost unrelated entries, broken
     * bins - not merely duplicate work. The cached values themselves converge (identities are
     * interned), so the map implementation is the entire defect.</p>
     *
     * <p><b>Proven red on master</b> {@code fd7eb58f7}: the instance-type pin below fails
     * deterministically against the {@code new HashMap<>()} field initializer. The parallel
     * exercise is the behavioral companion - it cannot be relied on to fail deterministically,
     * because {@code HashMap} corruption under a race is scheduling-dependent.</p>
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
