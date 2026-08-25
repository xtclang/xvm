package org.xvm.asm;


import java.util.List;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards runtime-lazy delegation synthesis (must-fix graduated 2026-08-25 from the frozen-code
 * lifecycle audit). Two defects in the old shape:
 *
 * <p>First, {@code ensureMethodDelegation}/{@code ensurePropertyDelegation} attached the
 * synthetic method as a findable child BEFORE building and assembling its code, so a concurrent
 * dispatcher (a second MethodInfo/PropertyInfo owner for the same host class and signature)
 * could find the half-built method, capture a partial unlinked op array, and race the winner's
 * in-place {@code forceAssembly}. The fix builds the method detached, assembles it completely,
 * and publishes under the host class's synthesis lock with a find-or-discard check.
 *
 * <p>Second, the branch's always-on pool publication marker turned LEGITIMATE lazy delegation
 * into a loud IllegalStateException in the main container: synthesis must intern genuinely new
 * constants (the mirrored property, the accessor's MethodConstant) after the pool is
 * runtime-published. The fix is an explicit per-thread, reference-counted synthesis window
 * ({@code ConstantPool.openRuntimeSynthesisWindow}) that only relaxes the registration guard -
 * never the destructive-mutation guard - and only for the synthesizing thread. Red on the
 * pre-fix branch shape: {@code publishedPoolPermitsDelegationSynthesis} dies on the guard ISE
 * (verified by stashing the fix).
 */
public class DelegationSynthesisTest {
    @Test
    public void publishedPoolPermitsDelegationSynthesis() {
        var fixture = new Fixture();
        fixture.pool.markRuntimePublishedForDiagnostics("unit-test");

        MethodStructure accessor = fixture.host.ensurePropertyDelegation(
                fixture.prop, fixture.propTarget, fixture.sigGet);

        assertNotNull(accessor);
        assertTrue(accessor.isSynthetic());
        assertNotNull(accessor.getOps(), "the published accessor must be fully assembled");

        // the window is closed: unrelated late registration must still fail loudly
        var error = assertThrows(IllegalStateException.class,
                () -> fixture.pool.ensureStringConstant("late"));
        assertTrue(error.getMessage().contains("after runtime publication"));
    }

    @Test
    public void synthesizedAccessorIsFindableAndStable() {
        var fixture = new Fixture();

        MethodStructure accessor = fixture.host.ensurePropertyDelegation(
                fixture.prop, fixture.propTarget, fixture.sigGet);

        var propHost = (PropertyStructure) fixture.host.getChild(fixture.prop.getName());
        assertNotNull(propHost);
        assertSame(accessor, propHost.findMethod(fixture.sigGet),
                "the published accessor must be the same instance the synthesis returned");
        assertSame(accessor, fixture.host.ensurePropertyDelegation(
                        fixture.prop, fixture.propTarget, fixture.sigGet),
                "repeated synthesis must return the published accessor, not rebuild it");
    }

    /**
     * Concurrency guard: every thread must receive the same fully assembled accessor. On the old
     * shape the losers could find the half-built method and race the winner's assembly; this
     * exercise is the probabilistic companion to the deterministic marker test above.
     */
    @Test
    public void concurrentSynthesisYieldsOneAssembledAccessor() throws Exception {
        var fixture = new Fixture();
        var start   = new CountDownLatch(1);

        List<MethodStructure> results;
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return fixture.host.ensurePropertyDelegation(
                                fixture.prop, fixture.propTarget, fixture.sigGet);
                    }))
                    .toList();
            start.countDown();

            results = futures.stream().map(future -> {
                try {
                    return future.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("synthesis failed concurrently", e);
                }
            }).toList();
        }

        MethodStructure accessor = results.get(0);
        for (MethodStructure result : results) {
            assertSame(accessor, result, "all threads must observe one published accessor");
        }
        assertNotNull(accessor.getOps());
    }

    private static final class Fixture {
        final ConstantPool      pool;
        final ClassStructure    host;
        final PropertyStructure prop;
        final PropertyStructure propTarget;
        final SignatureConstant sigGet;

        Fixture() {
            var file      = new FileStructure("test");
            pool          = file.getConstantPool();
            var delegatee = file.getModule().createClass(
                    Access.PUBLIC, Format.INTERFACE, "Delegatee", null);
            host          = file.getModule().createClass(
                    Access.PUBLIC, Format.CLASS, "Host", null);
            prop          = delegatee.createProperty(
                    false, Access.PUBLIC, Access.PUBLIC, pool.typeString(), "value");
            propTarget    = host.createProperty(
                    false, Access.PUBLIC, Access.PUBLIC, pool.typeString(), "target");
            sigGet        = pool.ensureSignatureConstant("get",
                    ConstantPool.NO_TYPES, new TypeConstant[] {pool.typeString()});
        }
    }
}
