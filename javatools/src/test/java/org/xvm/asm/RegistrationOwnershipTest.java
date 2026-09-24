package org.xvm.asm;

import java.lang.ref.WeakReference;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;
import org.xvm.asm.constants.UnresolvedNameConstant;

import org.xvm.compiler.Token.Id;

import org.xvm.util.TransientThreadLocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RegistrationOwnershipTest {
    enum Ambient { NONE, SOURCE, DESTINATION, UNRELATED }

    @ParameterizedTest
    @EnumSource(Ambient.class)
    void registrationAndFoldingUseTheExplicitPool(Ambient binding) {
        var source = new FileStructure("Source");
        var owner = source.getConstantPool();
        for (var destination : List.of(owner, new FileStructure(source).getConstantPool())) {
            var ambient = switch (binding) {
                case NONE -> null;
                case SOURCE -> owner;
                case DESTINATION -> destination;
                case UNRELATED -> new FileStructure("Other").getConstantPool();
            };
            var signature = owner.ensureSignatureConstant("value", TypeConstant.NO_TYPES,
                    new TypeConstant[] {owner.typeString()});
            var unresolved = new UnresolvedNameConstant(owner, "Missing");
            var first = owner.ensureIntConstant(7);
            var second = owner.ensureIntConstant(3);
            try (var scope = ConstantPool.withPool(ambient)) {
                var registered = destination.register(signature);
                assertSame(destination, registered.getConstantPool());
                assertSame(destination, registered.getRawReturns()[0].getConstantPool());
                assertSame(registered, destination.register(signature));
                assertSame(registered, destination.register(registered));
                if (destination == owner) {
                    assertSame(signature, registered);
                } else {
                    assertNotSame(signature, registered);
                    assertNotSame(signature.getRawReturns(), registered.getRawReturns());
                }
                assertSame(unresolved, destination.register(unresolved));
                assertSame(owner, unresolved.getConstantPool());
                var result = first.apply(destination, Id.ADD, second);
                assertEquals(destination.ensureIntConstant(10), result);
                assertSame(destination, result.getConstantPool());
                assertSame(owner, first.getConstantPool());
                assertSame(owner, signature.getConstantPool());
                assertSame(owner, signature.getRawReturns()[0].getConstantPool());
                assertSame(ambient, ConstantPool.getCurrentPool());
            }
        }
    }

    @Test
    void comparisonCachesDoNotRetainAnotherPoolStronglyOrShareItsLock() throws Exception {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var destination = new FileStructure(source).getConstantPool();
        var first = pool.ensureSignatureConstant("first", TypeConstant.NO_TYPES, TypeConstant.NO_TYPES);
        var local = pool.ensureSignatureConstant("local", TypeConstant.NO_TYPES, TypeConstant.NO_TYPES);
        var foreign = destination.ensureSignatureConstant("foreign", TypeConstant.NO_TYPES, TypeConstant.NO_TYPES);
        var strong = SignatureConstant.class.getDeclaredField("m_sigPrev");
        var weak = SignatureConstant.class.getDeclaredField("m_refSigPrev");
        var lock = SignatureConstant.class.getDeclaredField("comparisonLock");
        strong.setAccessible(true);
        weak.setAccessible(true);
        lock.setAccessible(true);

        first.compareTo(local);
        assertSame(local, strong.get(first));
        assertNull(weak.get(first));
        first.compareTo(foreign);
        assertNull(strong.get(first));
        assertSame(foreign, ((WeakReference<?>) weak.get(first)).get());
        var copy = destination.register(first);
        assertNotSame(lock.get(first), lock.get(copy));
        assertNull(strong.get(copy));
        assertNull(weak.get(copy));
        assertSame(foreign, ((WeakReference<?>) weak.get(first)).get());
        assertEquals(0, copy.compareTo(first));
    }

    @Test
    @SuppressWarnings("unchecked")
    void adoptedFormalDoesNotInheritAnActiveComparison() throws Exception {
        var source = new FileStructure("Source");
        var pool = source.getConstantPool();
        var method = pool.ensureMethodConstant(source.getModuleId(), "generic", TypeConstant.NO_TYPES,
                TypeConstant.NO_TYPES);
        var formal = pool.register(new TypeParameterConstant(pool, method, "Element", 0));
        var field = TypeParameterConstant.class.getDeclaredField("comparisonRecursion");
        field.setAccessible(true);
        var guard = (TransientThreadLocal<Boolean>) field.get(formal);
        try (var scope = guard.push(true)) {
            var destination = new FileStructure(source).getConstantPool();
            var copy = destination.register(formal);
            var copiedGuard = (TransientThreadLocal<Boolean>) field.get(copy);
            assertNotSame(guard, copiedGuard);
            assertNull(copiedGuard.get());
            assertEquals(Boolean.TRUE, guard.get());
            assertSame(guard, field.get(pool.register(formal)));
        }
        assertNull(guard.get());
    }
}
