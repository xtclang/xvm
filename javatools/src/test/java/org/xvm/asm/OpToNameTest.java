package org.xvm.asm;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Op#toName} answers for every opcode rather than throwing.
 *
 * <p>It is called from {@link Op#toString()} and, more consequentially, from inside the
 * construction of the exceptions this package raises - {@code OpGeneral} and {@code OpCondJump}
 * both do {@code throw new ...Exception(toName(getOpCode()) + ...)}. When {@code toName} threw, the
 * real failure was replaced by a secondary one and lost.</p>
 */
public class OpToNameTest {
    @Test
    public void toNameAnswersForEveryDeclaredOpcode() throws IllegalAccessException {
        var listFailures = new ArrayList<String>();
        var cOpcodes     = 0;

        for (Field field : Op.class.getDeclaredFields()) {
            int nMods = field.getModifiers();
            if (!Modifier.isStatic(nMods) || !Modifier.isFinal(nMods)
                    || field.getType() != int.class || !field.getName().startsWith("OP_")) {
                continue;
            }
            cOpcodes++;
            try {
                String sName = Op.toName(field.getInt(null));
                if (sName == null || sName.isEmpty()) {
                    listFailures.add(field.getName() + " -> " + sName);
                }
            } catch (RuntimeException e) {
                listFailures.add(field.getName() + " -> " + e);
            }
        }

        assertTrue(cOpcodes > 100, "expected Op to declare the opcodes; found " + cOpcodes);
        assertEquals(List.of(), listFailures,
                "Op.toName must answer for every opcode, including reserved and unnamed ones, so"
                        + " that toString() and the exception messages built from it cannot throw");
    }
}
