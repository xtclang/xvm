package org.xvm.asm;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.ConditionalConstant;

import org.xvm.asm.op.CatchStart;
import org.xvm.asm.op.Cmp;
import org.xvm.asm.op.GP_Add;
import org.xvm.asm.op.GP_Neg;
import org.xvm.asm.op.IIP_Inc;
import org.xvm.asm.op.IIP_PreInc;
import org.xvm.asm.op.IsNot;
import org.xvm.asm.op.IsType;
import org.xvm.asm.op.JumpCond;
import org.xvm.asm.op.JumpEq;
import org.xvm.asm.op.JumpNCond;
import org.xvm.asm.op.JumpType;
import org.xvm.asm.op.PIP_Inc;
import org.xvm.asm.op.PIP_PreInc;
import org.xvm.asm.op.Var_C;
import org.xvm.asm.op.Var_I;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Structural checks for runtime-executed op-code caches that can otherwise pin the first frame's
 * constant pool when a decoded op graph is reused.
 */
public class OpRuntimeCacheTest {
    private static final Pattern RUNTIME_COMMON_TYPE_WRITE = Pattern.compile(
            "m_typeCommon\\s*=\\s*typeCommon\\s*=\\s*frame\\.getConstant");

    @Test
    public void conditionalJumpOpsDoNotCacheFrameConditionalConstants() {
        assertNoConditionalConstantField(JumpCond.class);
        assertNoConditionalConstantField(JumpNCond.class);
    }

    @Test
    public void commonTypeCalculationDoesNotWriteFrameConstantsBackToOps()
            throws IOException {
        assertNoRuntimeCommonTypeWrite("org/xvm/asm/OpTest.java");
        assertNoRuntimeCommonTypeWrite("org/xvm/asm/OpCondJump.java");
    }

    @Test
    public void opcodeShapeConstructorsPreserveDecodedOperandLayouts()
            throws Exception {
        assertDecodedInts(new GP_Add(input(1, 2, 3), Constant.NO_CONSTS),
                "m_nTarget", 1, "m_nArgValue", 2, "m_nRetValue", 3);
        assertDecodedInts(new GP_Neg(input(4, 5), Constant.NO_CONSTS),
                "m_nTarget", 4, "m_nRetValue", 5);

        assertDecodedInts(new JumpEq(input(6, 7, 8, 9), Constant.NO_CONSTS),
                "m_nType", 6, "m_nArg", 7, "m_nArg2", 8, "m_ofJmp", 9);
        assertDecodedInts(new JumpType(input(10, 11, 12), Constant.NO_CONSTS),
                "m_nArg", 10, "m_nArg2", 11, "m_ofJmp", 12);

        assertDecodedInts(new Cmp(input(13, 14, 15, 16), Constant.NO_CONSTS),
                "m_nType", 13, "m_nValue1", 14, "m_nValue2", 15, "m_nRetValue", 16);
        assertDecodedInts(new IsType(input(17, 18, 19), Constant.NO_CONSTS),
                "m_nValue1", 17, "m_nValue2", 18, "m_nRetValue", 19);
        assertDecodedInts(new IsNot(input(20, 21), Constant.NO_CONSTS),
                "m_nValue1", 20, "m_nRetValue", 21);

        assertDecodedInts(new IIP_PreInc(input(22, 23, 24), Constant.NO_CONSTS),
                "m_nTarget", 22, "m_nIndex", 23, "m_nRetValue", 24);
        assertDecodedInts(new IIP_Inc(input(25, 26), Constant.NO_CONSTS),
                "m_nTarget", 25, "m_nIndex", 26);

        assertDecodedInts(new PIP_PreInc(input(27, 28, 29), Constant.NO_CONSTS),
                "m_nPropId", 27, "m_nTarget", 28, "m_nRetValue", 29);
        assertDecodedInts(new PIP_Inc(input(30, 31), Constant.NO_CONSTS),
                "m_nPropId", 30, "m_nTarget", 31);

        assertDecodedInts(new Var_I(input(32, 33), Constant.NO_CONSTS),
                "m_nType", 32, "m_nValueId", 33);
        assertDecodedInts(new Var_C(input(33), Constant.NO_CONSTS), "m_nArgValue", 33);
        assertDecodedInts(new CatchStart(input(), Constant.NO_CONSTS));
    }

    @Test
    public void opcodeShapeCleanupDoesNotAddHotShapeFields() {
        assertNoShapeFields(OpGeneral.class);
        assertNoShapeFields(OpTest.class);
        assertNoShapeFields(OpCondJump.class);
        assertNoShapeFields(OpInPlace.class);
        assertNoShapeFields(OpIndex.class);
        assertNoShapeFields(OpPropInPlace.class);
        assertNoShapeFields(OpVar.class);
    }

    private static void assertNoConditionalConstantField(Class<?> clazz) {
        List<String> fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> ConditionalConstant.class.isAssignableFrom(field.getType()))
                .map(Field::getName)
                .toList();

        assertEquals(List.of(), fields, clazz.getName() + " must resolve conditions from Frame");
    }

    private static void assertNoRuntimeCommonTypeWrite(String source)
            throws IOException {
        String text = Files.readString(sourcePath(source));

        assertFalse(RUNTIME_COMMON_TYPE_WRITE.matcher(text).find(),
                source + " must not cache frame constants on runtime Op instances");
    }

    private static DataInputStream input(int... values) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            for (int value : values) {
                writePackedLong(out, value);
            }
        }
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static void assertDecodedInts(Op op, Object... fields)
            throws Exception {
        assertEquals(0, fields.length % 2);
        for (int i = 0; i < fields.length; i += 2) {
            String name     = (String) fields[i];
            Object expected = fields[i + 1];
            assertEquals(expected, fieldValue(op, name),
                    () -> op.getClass().getSimpleName() + "." + name);
        }
    }

    private static int fieldValue(Op op, String name) throws Exception {
        Class<?> clazz = op.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(op);
            } catch (NoSuchFieldException _) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new AssertionError("missing field " + name + " on " + op.getClass().getName());
    }

    private static void assertNoShapeFields(Class<?> clazz) {
        var fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType().getSimpleName().endsWith("Shape")
                        || field.getName().toLowerCase().contains("shape")
                        || field.getName().equals("binary")
                        || field.getName().equals("secondArgument")
                        || field.getName().equals("assigns")
                        || field.getName().equals("typeAware"))
                .map(Field::getName)
                .toList();

        assertEquals(List.of(), fields, clazz.getName()
                + " must not add per-op shape/cache fields to the runtime hot path");
    }

    private static Path sourcePath(String source) {
        Path path = Path.of("src/main/java", source);
        if (!Files.exists(path)) {
            path = Path.of("javatools/src/main/java", source);
        }
        assertTrue(Files.exists(path), () -> "missing source file " + source);
        return path;
    }
}
