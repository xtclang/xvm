package org.xvm.javajit;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.xvm.javajit.Builder.CD_CtorCtx;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.JitFlavor.Primitive;
import static org.xvm.javajit.JitFlavor.Specific;

/**
 * Tests for JIT constructor-time this-escape removals.
 */
public class JitConstructorEscapeTest {
    @Test
    public void methodDescriptorUsesCtxImplicitParam() {
        var desc = new JitMethodDesc(null,
                returns(CD_int), params(CD_String), null, null, true);

        assertEquals(1, desc.getImplicitParamCount());
        assertEquals(MethodTypeDesc.of(CD_int, CD_Ctx, CD_String), desc.standardMD);
    }

    @Test
    public void primitiveReceiverStillPrecedesImplicitContext() {
        var desc = new JitMethodDesc(null,
                returns(CD_int),
                new JitParamDesc[] {
                    new JitParamDesc(null, Primitive, CD_long, -1, -1, false),
                    param(CD_String)
                },
                null, null, false);

        assertEquals(1, desc.getImplicitParamCount());
        assertEquals(MethodTypeDesc.of(CD_int, CD_long, CD_Ctx, CD_String), desc.standardMD);
    }

    @Test
    public void constructorDescriptorUsesExplicitImplicitParamShape() {
        var target = ClassDesc.of("xtclang.test.Target");
        var desc = new JitCtorDesc(null, target, true, true,
                JitParamDesc.NONE, params(CD_String), null, null);

        assertEquals(4, desc.getImplicitParamCount());
        assertEquals(MethodTypeDesc.of(CD_void,
                CD_Ctx, CD_CtorCtx, CD_TypeConstant, target, CD_String), desc.standardMD);
    }

    @Test
    public void descriptorComputationIsPrivateConstructorData() {
        assertFalse(hasDeclaredMethod(JitCtorDesc.class, "fillExtraClassDesc"));
        assertFalse(hasDeclaredMethod(JitCtorDesc.class, "getImplicitParamCount"));
    }

    private static boolean hasDeclaredMethod(Class<?> clz, String name) {
        return Arrays.stream(clz.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals(name));
    }

    private static JitParamDesc[] returns(ClassDesc cd) {
        return new JitParamDesc[] {new JitParamDesc(null, Specific, cd, 0, -1, false)};
    }

    private static JitParamDesc[] params(ClassDesc cd) {
        return new JitParamDesc[] {param(cd)};
    }

    private static JitParamDesc param(ClassDesc cd) {
        return new JitParamDesc(null, Specific, cd, 0, 0, false);
    }
}
