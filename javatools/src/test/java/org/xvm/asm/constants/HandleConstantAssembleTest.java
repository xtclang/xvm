package org.xvm.asm.constants;


import java.io.DataOutputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.ObjectHandle;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@link HandleConstant} serialization. The inherited {@code assemble()} wrote only the
 * borrowed Register format byte, so a runtime-only handle constant referenced at serialization
 * time would silently produce a structurally corrupt module. It must fail loudly instead.
 */
public class HandleConstantAssembleTest {
    @Test
    public void handleConstantRefusesSerialization() {
        var constant = new HandleConstant(ObjectHandle.DEFAULT);
        var error    = assertThrows(IllegalStateException.class,
                () -> constant.assemble(new DataOutputStream(OutputStream.nullOutputStream())));
        assertTrue(error.getMessage().contains("runtime-only"), error.getMessage());
    }
}
