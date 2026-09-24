package org.xvm.asm.constants;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Parameter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Diagnostic formatting and annotation inspection must also work outside compiler scopes. */
class MethodBodyAmbientPoolTest {
    @Test
    void describesAnOrdinaryMethodWithoutAnAmbientPool() {
        try (var empty = ConstantPool.withPool(null)) {
            var body = methodBody();
            assertNull(ConstantPool.getCurrentPool());
            assertNotNull(assertDoesNotThrow(body::toString));
            assertNull(ConstantPool.getCurrentPool());
        }
    }

    @Test
    void checksAnnotationsWithoutAnAmbientPool() {
        try (var empty = ConstantPool.withPool(null)) {
            var body = methodBody();
            assertNull(ConstantPool.getCurrentPool());
            assertFalse(body.isOp());
            assertFalse(body.isAuto());
            assertFalse(body.isOverride());
            assertNull(ConstantPool.getCurrentPool());
        }
    }

    private static MethodBody methodBody() {
        var file = new FileStructure("test");
        var clz = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Test", null);
        var method = clz.createMethod(false, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "go", Parameter.NO_PARAMS, true, true);
        return new MethodBody(method.getIdentityConstant(), method.getIdentityConstant().getSignature(),
                MethodBody.Implementation.Explicit);
    }
}
