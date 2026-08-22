package org.xvm.asm.constants;


import java.util.Collections;
import java.util.Map;

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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * Tests for {@link MethodInfo} and {@link MethodBody} ownership.
 */
public class MethodInfoTest {
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

        assertSame(info1, method1.getTypeInfo());
        assertSame(info2, method2.getTypeInfo());
        assertNotSame(method1, method2);

        assertSame(method1, method1.getHead().getMethodInfo());
        assertSame(method2, method2.getHead().getMethodInfo());
        assertNotSame(method1.getHead(), method2.getHead());

        assertSame(method1, info1.getVirtMethods().get(sig));
        assertSame(method2, info2.getVirtMethods().get(sig));
    }

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
