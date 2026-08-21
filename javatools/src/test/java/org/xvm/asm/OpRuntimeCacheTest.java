package org.xvm.asm;


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

import org.xvm.asm.op.JumpCond;
import org.xvm.asm.op.JumpNCond;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


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

    private static Path sourcePath(String source) {
        Path path = Path.of("src/main/java", source);
        if (!Files.exists(path)) {
            path = Path.of("javatools/src/main/java", source);
        }
        assertTrue(Files.exists(path), () -> "missing source file " + source);
        return path;
    }
}
