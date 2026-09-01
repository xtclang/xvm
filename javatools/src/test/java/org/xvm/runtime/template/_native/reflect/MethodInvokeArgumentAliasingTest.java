package org.xvm.runtime.template._native.reflect;


import java.io.IOException;
import java.io.InputStream;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.FieldInstruction;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Guards the tuple-argument call boundary.
 *
 * The end-to-end proof of the defect these tests pin is an Ecstasy program, not a Java test:
 * {@code manualTests/src/main/x/reflect.x testInvokeTupleAliasing} invokes a method reflectively,
 * has it reassign its parameters, and asserts the caller's tuple is unchanged. On master that
 * program turns a tuple built as {@code ("origB", 7)} into {@code ("MUTATED", -1)}.
 *
 * What follows are the two Java-level facts that explain why it happened, and that would let it
 * happen again.
 */
public class MethodInvokeArgumentAliasingTest {
    /**
     * The classes that must not touch a tuple's storage directly, because everything they extract
     * from a tuple is on its way into a callee frame's register file.
     */
    private static final List<String> TUPLE_ARGUMENT_SITES = List.of(
            "org/xvm/asm/op/Call_T0",     "org/xvm/asm/op/Call_T1",
            "org/xvm/asm/op/Call_TN",     "org/xvm/asm/op/Call_TT",
            "org/xvm/asm/op/Invoke_T0",   "org/xvm/asm/op/Invoke_T1",
            "org/xvm/asm/op/Invoke_TN",   "org/xvm/asm/op/Invoke_TT",
            "org/xvm/asm/op/Construct_T", "org/xvm/asm/op/New_T",
            "org/xvm/asm/op/NewG_T",
            "org/xvm/runtime/template/_native/reflect/xRTMethod");

    /**
     * The reason the boundary must copy: {@code ensureSize} is a grow-only operation and
     * deliberately returns the caller's array when it is already big enough. That is what turns a
     * tuple's element storage into the callee frame's register file. If this contract ever changes
     * to always copy, the defensive copies become redundant rather than wrong; while it holds,
     * they are load-bearing.
     */
    @Test
    public void ensureSizeAliasesWhenNoGrowthIsNeeded() {
        var ahArg = new ObjectHandle[3];

        assertSame(ahArg, Utils.ensureSize(ahArg, 3),
                "ensureSize hands the same array through when no growth is needed;"
                        + " callers that must not share storage have to copy first");
        assertSame(ahArg, Utils.ensureSize(ahArg, 2));
    }

    /**
     * Each of those sites has to reach a tuple's elements through {@code valuesCopy()}, never by
     * reading {@code m_ahValue}. Read the compiled classes rather than their source: a
     * {@code getfield m_ahValue} is the defect whatever it looks like in text, and the field stays
     * public for the many uses that are not headed for a frame.
     *
     * {@code xRTFunction} is deliberately absent. It reads the field and then copies on the very
     * next line, which is correct and predates this fix.
     */
    @Test
    public void noTupleArgumentSiteReadsTupleStorageDirectly() throws IOException {
        var listOffenders = new ArrayList<String>();
        var listMissing   = new ArrayList<String>();

        for (String sSite : TUPLE_ARGUMENT_SITES) {
            try (InputStream in = getClass().getResourceAsStream("/" + sSite + ".class")) {
                if (in == null) {
                    listMissing.add(sSite);
                    continue;
                }
                ClassFile.of().parse(in.readAllBytes()).methods().forEach(method ->
                    method.code().ifPresent(code -> code.elementList().stream()
                        .filter(FieldInstruction.class::isInstance)
                        .map(FieldInstruction.class::cast)
                        .filter(field -> "m_ahValue".equals(field.name().stringValue()))
                        .forEach(field -> listOffenders.add(
                                sSite + "." + method.methodName().stringValue()))));
            }
        }

        assertEquals(List.of(), listMissing,
                "these classes were not found on the test classpath, so the scan below proved"
                        + " nothing about them");
        assertEquals(List.of(), listOffenders,
                "these tuple-argument sites read a tuple's own storage instead of valuesCopy(),"
                        + " so the callee's register file aliases the caller's tuple");
    }
}
