package org.xvm.asm;

import java.util.Arrays;
import java.util.List;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Return_0;
import org.xvm.asm.op.Return_1;

import org.xvm.runtime.RuntimeTypeContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMethodsTest {
    @Test
    void failedAndIncompleteAttemptsCanRetryWithoutPublishing() {
        var fixture = new Fixture();
        var context = new RuntimeTypeContext(fixture.pool);
        var pool = context.getDescriptorPool();
        var key = fixture.key(pool);
        fixture.file.ensureReadOnly();
        var failure = new IllegalStateException("injected build failure");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> pool.getRuntimeMethods().ensure(key, () -> { throw failure; })));
        assertThrows(IllegalStateException.class, () -> pool.getRuntimeMethods().ensure(key,
                () -> new RuntimeMethodStructure(key.host())));
        var method = pool.getRuntimeMethods().ensure(key, () -> assembled(key));
        assertNotNull(method.getLocalConstants());
        context.clearMetadata();
        assertSame(method, pool.getRuntimeMethods().ensure(key, () -> { throw failure; }));
    }

    @Test
    @Timeout(10)
    void concurrentAttemptsPublishOneAssembledWinner() throws Exception {
        var fixture = new Fixture();
        var pool = new RuntimeTypeContext(fixture.pool).getDescriptorPool();
        var key = fixture.key(pool);
        fixture.file.ensureReadOnly();
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 2).mapToObj(_ -> threads.submit(() ->
                    pool.getRuntimeMethods().ensure(key, () -> {
                        var method = assembled(key);
                        ready.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        return method;
                    }))).toList();
            try {
                ready.await();
            } finally {
                release.countDown();
            }
            var first = tasks.getFirst().get();
            assertSame(first, tasks.getLast().get());
            assertNotNull(first.getLocalConstants());
        }
    }

    @Test
    void contextsAndCompleteKeysKeepExecutablesIndependent() {
        var fixture = new Fixture();
        var first = new RuntimeTypeContext(fixture.pool).getDescriptorPool();
        var second = new RuntimeTypeContext(fixture.pool).getDescriptorPool();
        var constants = fixture.pool.getConstants();
        fixture.file.ensureReadOnly();
        var key = fixture.key(first);
        var method = first.getRuntimeMethods().ensure(key, () -> assembled(key));
        var secondKey = fixture.key(second);
        var other = second.getRuntimeMethods().ensure(secondKey, () -> assembled(secondKey));
        assertNotSame(method, other);
        assertNotSame(method.getOps(), other.getOps());
        assertThrows(IllegalArgumentException.class,
                () -> first.getRuntimeMethods().ensure(secondKey, () -> other));
        var host = first.register(fixture.host.getIdentityConstant());
        for (var changed : List.of(
                new RuntimeMethods.Key(key.receiver().ensureAccess(Access.PRIVATE),
                        key.host(), key.declaration(), key.delegate()),
                new RuntimeMethods.Key(key.receiver(), first.ensureMethodConstant(host, "other",
                        TypeConstant.NO_TYPES, TypeConstant.NO_TYPES), key.declaration(), key.delegate()),
                new RuntimeMethods.Key(key.receiver(), key.host(), host, key.delegate()),
                new RuntimeMethods.Key(key.receiver(), key.host(), key.declaration(),
                        first.ensurePropertyConstant(host, "other")))) {
            assertNotSame(method, first.getRuntimeMethods().ensure(changed, () -> assembled(changed)));
        }
        assertArrayEquals(constants, fixture.pool.getConstants());
        assertThrows(UnsupportedOperationException.class, fixture.pool::getRuntimeMethods);
    }

    @Test
    void generatedParametersAreIndependentOfFrozenDeclarations() {
        var fixture = new Fixture();
        var type = fixture.object.getIdentityConstant().getType();
        var source = fixture.host.createMethod(false, Access.PUBLIC, null,
                new Parameter[] {new Parameter(fixture.pool, type, null, null, true, 0, false)}, "echo",
                new Parameter[] {new Parameter(fixture.pool, type, "value", null, false, 0, false)}, true, false);
        var context = new RuntimeTypeContext(fixture.pool);
        var constants = fixture.pool.getConstants();
        context.freezeDefinitions();
        var pool = context.getDescriptorPool();
        var method = new RuntimeMethodStructure(fixture.host, pool.register(source.getIdentityConstant()),
                Access.PUBLIC, false, Annotation.NO_ANNOTATIONS, source.getReturnArray(), source.getParamArray());
        method.createCode().add(new Return_1(new Register(method.getParam(0).getType(), "value", 0)));
        method.forceAssembly(pool);
        assertSame(source, source.getParam(0).getContaining());
        assertSame(source, source.getReturn(0).getContaining());
        assertNotSame(source.getParam(0), method.getParam(0));
        assertSame(method, method.getParam(0).getContaining());
        assertSame(pool, method.getParam(0).getType().getConstantPool());
        assertSame(fixture.host, method.getContainingClass());
        assertFalse(method.isFunction());
        assertArrayEquals(constants, fixture.pool.getConstants());
    }

    @Test
    void accessorsHaveLocalConstantsWithoutInsertingAHostProperty() {
        var fixture = new Fixture();
        var type = fixture.object.getIdentityConstant().getType();
        var declared = fixture.object.createProperty(false, Access.PUBLIC, Access.PUBLIC, type, "value");
        var delegate = fixture.host.createProperty(false, Access.PUBLIC, Access.PUBLIC, type, "target");
        var context = new RuntimeTypeContext(fixture.pool);
        var constants = fixture.pool.getConstants();
        var positions = Arrays.stream(constants).map(Constant::getPosition).toList();
        context.freezeDefinitions();
        var pool = context.getDescriptorPool();
        var localType = pool.register(type);
        var getter = fixture.host.ensurePropertyDelegation(pool, declared, delegate,
                pool.ensureSignatureConstant("get", TypeConstant.NO_TYPES, new TypeConstant[] {localType}));
        var setter = fixture.host.ensurePropertyDelegation(pool, declared, delegate,
                pool.ensureSignatureConstant("set", new TypeConstant[] {localType}, TypeConstant.NO_TYPES));
        assertTrue(getter instanceof RuntimeMethodStructure);
        assertTrue(setter instanceof RuntimeMethodStructure);
        assertSame(fixture.host, getter.getContainingClass());
        assertNull(fixture.host.getChild("value"));
        assertTrue(Arrays.stream(getter.getLocalConstants()).allMatch(c -> c.getPosition() == -1));
        assertTrue(Arrays.stream(setter.getLocalConstants()).allMatch(c -> c.getPosition() == -1));
        assertArrayEquals(constants, fixture.pool.getConstants());
        assertEquals(positions, Arrays.stream(constants).map(Constant::getPosition).toList());
    }

    @Test
    void generatedReferenceInitializersDecodeAtTheirOriginalAddresses() {
        var fixture = new Fixture();
        var pool = new RuntimeTypeContext(fixture.pool).getDescriptorPool();
        var key = fixture.key(pool);
        fixture.file.ensureReadOnly();
        var method = new RuntimeMethodStructure(key.host());
        var original = new RuntimeMethodStructure.InitRef(key.delegate());
        method.createCode().add(original).add(new Return_0());
        method.forceAssembly(pool);
        var first = method.createExecutionCode();
        var second = method.createExecutionCode();
        assertEquals(2, first.ops().length);
        assertTrue(first.ops()[0] instanceof RuntimeMethodStructure.InitRef);
        assertTrue(first.ops()[1] instanceof Return_0);
        assertNotSame(original, first.ops()[0]);
        assertNotSame(first.ops()[0], second.ops()[0]);
        assertEquals(0, first.ops()[0].getAddress());
        assertEquals(1, first.ops()[1].getAddress());
        assertEquals(0, first.maxVars());
        assertEquals(1, first.maxScopes());
    }

    private static RuntimeMethodStructure assembled(RuntimeMethods.Key key) {
        var method = new RuntimeMethodStructure(key.host());
        method.createCode().add(new Return_0());
        method.forceAssembly(key.host().getConstantPool());
        return method;
    }

    private static final class Fixture {
        final FileStructure file = new FileStructure(Constants.ECSTASY_MODULE);
        final ConstantPool pool = file.getConstantPool();
        final ClassStructure object = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Object", null);
        final ClassStructure host = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Host", null);

        RuntimeMethods.Key key(ConstantPool destination) {
            var identity = destination.register(host.getIdentityConstant());
            var method = destination.ensureMethodConstant(identity, "call", TypeConstant.NO_TYPES, TypeConstant.NO_TYPES);
            return new RuntimeMethods.Key(identity.getType(), method, method,
                    destination.ensurePropertyConstant(identity, "target"));
        }
    }
}
