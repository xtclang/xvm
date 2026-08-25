package org.xvm.asm.constants;


import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.GenericTypeResolver;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * Tests for owner-explicit nested identity generic resolution.
 */
public class NestedIdentityOwnerTest {
    /**
     * Resolver-backed nested identities already receive an explicit output pool. The old design
     * discarded it and resolved generic method signatures through ambient state, so a nested
     * single-threaded call or a parallel owner could intern the signature in the wrong pool.
     */
    @Test
    public void nestedIdentityResolutionUsesExplicitPool() throws Exception {
        var fileOwner = new FileStructure("owner");
        var poolOwner = fileOwner.getConstantPool();
        var poolOut   = new FileStructure("output").getConstantPool();
        var poolWrong = new FileStructure("wrong").getConstantPool();
        var box       = fileOwner.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Box", null);
        var actual    = fileOwner.getModule().createClass(
                Component.Access.PUBLIC, Component.Format.CLASS, "Actual", null);
        var formal    = box.addTypeParam("T", actual.getCanonicalType())
                .getIdentityConstant().getFormalType();
        var signature = poolOwner.ensureSignatureConstant(
                "value", ConstantPool.NO_TYPES, new TypeConstant[] {formal});
        var property  = poolOwner.ensurePropertyConstant(box.getIdentityConstant(), "slot");
        var method    = poolOwner.ensureMethodConstant(
                poolOwner.ensureMultiMethodConstant(property, "value"), signature);

        GenericTypeResolver resolver = ignored -> poolOut.typeString();

        var nested  = method.resolveNestedIdentity(poolOut, resolver);
        var resolved = (SignatureConstant) resolve(nested, signature);

        assertNotSame(signature, resolved);
        assertSame(poolOut, resolved.getConstantPool());
    }

    private static Object resolve(Object nested, SignatureConstant signature) throws Exception {
        Method method = findMethod(nested.getClass());
        method.setAccessible(true);
        return method.invoke(nested, signature);
    }

    private static Method findMethod(Class<?> clz) throws NoSuchMethodException {
        // Subclasses such as MethodConstant instantiate the inherited NestedIdentity inner class.
        // The private resolver can therefore live on a superclass of the concrete runtime class.
        for (Class<?> current = clz; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod("resolve", Object.class);
            } catch (NoSuchMethodException _) {
            }
        }

        throw new NoSuchMethodException("resolve");
    }
}
