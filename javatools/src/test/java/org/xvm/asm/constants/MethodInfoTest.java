package org.xvm.asm.constants;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;

import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.TypeInfo.Progress;

import org.xvm.util.ListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link MethodInfo} and {@link MethodBody} ownership.
 */
public class MethodInfoTest {
    /**
     * A MethodInfo and its MethodBody chain must be owner-exclusive. The old construction path
     * could mutate shared source bodies, so two TypeInfo owners could accidentally share metadata.
     */
    @Test
    public void methodInfoAndBodyHaveExclusiveOwners() {
        FileStructure  file   = new FileStructure("test");
        ConstantPool   pool   = file.getConstantPool();
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        SignatureConstant sig = pool.ensureSignatureConstant(
                "test", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        MethodConstant id = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        MethodInfo method = MethodInfo.create(new MethodBody(id, sig, Implementation.Native), 0);

        TypeInfo info1 = createTypeInfo(struct, id, sig, method);
        TypeInfo info2 = createTypeInfo(struct, id, sig, method);

        MethodInfo method1 = info1.getMethods().get(id);
        MethodInfo method2 = info2.getMethods().get(id);

        assertNull(method.getTypeInfo());
        assertSame(info1, method1.getTypeInfo());
        assertSame(info2, method2.getTypeInfo());
        assertNotSame(method1, method2);

        assertSame(method1, method1.getHead().getMethodInfo());
        assertSame(method2, method2.getHead().getMethodInfo());
        assertNotSame(method1.getHead(), method2.getHead());

        assertSame(method1, info1.getVirtMethods().get(sig));
        assertSame(method2, info2.getVirtMethods().get(sig));
    }

    /**
     * Owning a fresh body must not fabricate a self target. The owned-copy constructor rewrote
     * {@code m_target} whenever {@code body.m_target == body.m_infoMethod}; for a fresh unowned
     * body both are null, so every owned copy gained a spurious target pointing at its own
     * MethodInfo. Because body equality compares target shape, logically identical bodies from
     * independent owners stopped comparing equal, which corrupted union/difference TypeInfo
     * merges: bisect pinned the lib_json COMPILER-177 access failure on
     * {@code "ParentInput - Nullable"} (and the MethodInfo/MethodBody equality stack overflow
     * that was later worked around separately) to the commit that made MethodInfo always copy
     * bodies through this constructor.
     */
    @Test
    public void owningFreshBodyDoesNotFabricateSelfTarget() {
        FileStructure  file   = new FileStructure("test");
        ConstantPool   pool   = file.getConstantPool();
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        SignatureConstant sig = pool.ensureSignatureConstant(
                "test", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        MethodConstant id   = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        MethodBody     body = new MethodBody(id, sig, Implementation.Implicit, null);

        MethodInfo method1 = MethodInfo.create(body, 0);
        MethodInfo method2 = MethodInfo.create(body, 0);

        assertEquals(body, method1.getHead(),
                "an owned copy of a targetless body must stay equal to its source");
        assertEquals(method1.getHead(), method2.getHead(),
                "independently owned copies of one source body must stay equal");
    }

    /**
     * MethodInfo construction must not call overridable body-attachment hooks before final owner
     * state is assigned. That constructor-time callback is unsafe even in single-threaded code.
     */
    @Test
    public void methodInfoFactoryDoesNotCallOverridableBodyAttachment() {
        FileStructure  file   = new FileStructure("test");
        ConstantPool   pool   = file.getConstantPool();
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        SignatureConstant sig = pool.ensureSignatureConstant(
                "test", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        MethodConstant id = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var body = new OwnerInspectingMethodBody(id, sig, 9, 1);
        MethodInfo method = MethodInfo.create(body, 9);

        assertEquals(9, method.getRank());
        assertEquals(1, method.getChain().length);
        assertSame(method, method.getHead().getMethodInfo());
        assertNotSame(body, method.getHead());
        assertNull(body.getMethodInfo());
    }

    /**
     * Method metadata helpers must derive their pool from the receiver owner. The old helper read
     * ambient current-pool state, so direct or nested metadata queries could crash or use a wrong
     * pool.
     */
    @Test
    public void metadataPoolHelpersUseOwnerWithoutAmbientPool() throws Exception {
        FileStructure  file   = new FileStructure("test");
        ConstantPool   pool   = file.getConstantPool();
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        SignatureConstant sig = pool.ensureSignatureConstant(
                "test", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        MethodConstant id = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        MethodInfo method = MethodInfo.create(new MethodBody(id, sig, Implementation.Native), 0);

        assertSame(pool, invokePool(method));
        assertSame(pool, invokePool(method.getHead()));
        assertFalse(method.isOp());
    }

    /**
     * Parallel TypeInfo construction from the same source MethodInfo must produce independent
     * owner graphs. This proves the factory/copy path does not let the first owner claim source
     * metadata that later owners reuse.
     */
    @Test
    public void typeInfoConstructionCopiesMethodInfoInParallel() throws Exception {
        FileStructure  file   = new FileStructure("test");
        ConstantPool   pool   = file.getConstantPool();
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        SignatureConstant sig = pool.ensureSignatureConstant(
                "test", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        MethodConstant id = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        MethodInfo method = MethodInfo.create(new MethodBody(id, sig, Implementation.Native), 0);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return createTypeInfo(struct, id, sig, method).getMethods().get(id);
                    }))
                    .toList();

            start.countDown();

            var seen = Collections.newSetFromMap(new IdentityHashMap<MethodInfo, Boolean>());
            for (var future : futures) {
                MethodInfo owned = future.get(10, TimeUnit.SECONDS);

                assertSame(owned, owned.getTypeInfo().getMethods().get(id));
                assertSame(owned, owned.getHead().getMethodInfo());
                assertTrue(seen.add(owned));
            }
        }

        assertNull(method.getTypeInfo());
    }

    /**
     * MethodInfo equality must be cycle-safe. FromInto and Implicit bodies can point back into the
     * MethodInfo graph that owns them, and the old MethodBody.equals() recursively compared that
     * target with MethodInfo.equals(). That is a single-threaded StackOverflow hazard and a
     * parallel type-info stress failure when independently owned graphs are compared during map/set
     * lookup.
     */
    @Test
    public void methodInfoEqualityDoesNotRecurseThroughMethodTargets() throws Exception {
        FileStructure  file   = new FileStructure("test");
        ConstantPool   pool   = file.getConstantPool();
        ClassStructure struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        SignatureConstant sig = pool.ensureSignatureConstant(
                "test", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        MethodConstant id = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);

        MethodInfo method1 = MethodInfo.create(
                new MethodBody(id, sig, Implementation.FromInto, null), 0);
        MethodInfo method2 = MethodInfo.create(
                new MethodBody(id, sig, Implementation.FromInto, null), 0);

        setTarget(method1.getHead(), method1);
        setTarget(method2.getHead(), method2);

        assertEquals(method1.getHead().hashCode(), method2.getHead().hashCode());
        assertTrue(method1.equals(method2));

        MethodInfo implicit1 = MethodInfo.create(
                new MethodBody(id, sig, Implementation.Implicit, null), 0);
        MethodInfo implicit2 = MethodInfo.create(
                new MethodBody(id, sig, Implementation.Implicit, null), 0);

        setTarget(implicit1.getHead(), implicit1);
        setTarget(implicit2.getHead(), implicit2);

        assertEquals(implicit1.getHead().hashCode(), implicit2.getHead().hashCode());
        assertTrue(implicit1.equals(implicit2));
    }

    /**
     * Optimized method chains are runtime metadata. The old cache was a plain lazy array even
     * though building it can replace bodies and attach generated delegation methods. Parallel
     * first access must publish one fully built array through a Java memory-model edge.
     */
    @Test
    public void optimizedMethodChainCacheIsSafelyPublishedInParallel() throws Exception {
        var field = MethodInfo.class.getDeclaredField("m_aBodyResolved");
        assertTrue(Modifier.isVolatile(field.getModifiers()));
        field.setAccessible(true);

        var file = new FileStructure("test");
        var pool = file.getConstantPool();
        var struct = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);

        var sig = pool.ensureSignatureConstant(
                "test", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
        var id = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        var method = MethodInfo.create(new MethodBody(id, sig, Implementation.Implicit), 0);
        var info = createTypeInfo(struct, id, sig, method);
        var owned = info.getMethods().get(id);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return owned.ensureOptimizedMethodChain(info);
                    }))
                    .toList();

            start.countDown();

            for (var future : futures) {
                assertSame(MethodBody.NO_BODIES, future.get(10, TimeUnit.SECONDS));
            }
        }

        assertSame(MethodBody.NO_BODIES, field.get(owned));
    }

    private static ConstantPool invokePool(Object target) throws Exception {
        Method method = target.getClass().getDeclaredMethod("pool");
        method.setAccessible(true);
        return (ConstantPool) method.invoke(target);
    }

    private static void setTarget(MethodBody body, MethodInfo target) throws Exception {
        Field field = MethodBody.class.getDeclaredField("m_target");
        field.setAccessible(true);
        field.set(body, new MethodBody.Target.Origin(target));
    }

    private TypeInfo createTypeInfo(
            ClassStructure    struct,
            MethodConstant    id,
            SignatureConstant sig,
            MethodInfo        method) {
        return new TypeInfoReal(
                struct.getCanonicalType(), 0, struct, 0, false,
                Collections.emptyMap(), Annotation.NO_ANNOTATIONS, Annotation.NO_ANNOTATIONS,
                null, null, null, Collections.emptyList(), new ListMap<>(), new ListMap<>(),
                Collections.emptyMap(), Map.of(id, method), Collections.emptyMap(),
                Map.of(sig, method), new ListMap<>(), null, Progress.Complete);
    }

    private static final class OwnerInspectingMethodBody extends MethodBody {
        private final int expectedRank;
        private final int expectedBodies;

        OwnerInspectingMethodBody(
                MethodConstant    id,
                SignatureConstant sig,
                int               expectedRank,
                int               expectedBodies) {
            super(id, sig, Implementation.Native);

            this.expectedRank   = expectedRank;
            this.expectedBodies = expectedBodies;
        }

        @Override
        synchronized MethodBody forMethod(MethodInfo method) {
            if (method.getRank() != expectedRank || method.getChain().length != expectedBodies) {
                throw new IllegalStateException("method owner was observed too early");
            }
            return super.forMethod(method);
        }
    }
}
