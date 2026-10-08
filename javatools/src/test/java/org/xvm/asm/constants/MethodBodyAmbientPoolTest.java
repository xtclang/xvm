package org.xvm.asm.constants;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Method inspection uses the host's explicit working-pool scope, as compilation does.
 */
public class MethodBodyAmbientPoolTest {
    @Test
    public void aHostCanInspectAMethodWithinItsPoolScope() {
        assertNull(ConstantPool.getCurrentPool());
        MethodBody body = body();

        try (var _ = ConstantPool.withPool(body.getIdentity().getConstantPool())) {
            assertFalse(body.isOp());
            assertTrue(body.toString().contains("go"));
        }

        assertNull(ConstantPool.getCurrentPool());
    }

    @Test
    public void inspectionPreservesAnExplicitAlternateWorkingPool() {
        MethodBody body = body();
        ConstantPool worker = new FileStructure("worker").getConstantPool();

        try (var _ = ConstantPool.withPool(worker)) {
            assertFalse(body.isOp());
            assertTrue(body.toString().contains("go"));
            assertSame(worker, ConstantPool.getCurrentPool());
        }

        assertNull(ConstantPool.getCurrentPool());
    }

    private static MethodBody body() {
        FileStructure file = new FileStructure("test");
        ClassStructure clz = file.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "Test", null);
        MethodStructure method = clz.createMethod(false, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "go", Parameter.NO_PARAMS, true, true);

        return new MethodBody(method.getIdentityConstant(), method.getIdentityConstant().getSignature(),
                MethodBody.Implementation.Explicit);
    }
}
