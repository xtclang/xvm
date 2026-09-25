package org.xvm.runtime;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.Parameter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeDeclarationPreparationTest {
    @Test
    void preparationMarksBodylessMembersAndAccessorsButNotNestedClasses() {
        var file = new FileStructure("NativePreparation");
        var pool = file.getConstantPool();
        var declaration = file.getModule().createClass(Access.PUBLIC, Format.INTERFACE, "Rebased", null);
        var type = declaration.getIdentityConstant().getType();
        var method = declaration.createMethod(false, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "nativeMethod", Parameter.NO_PARAMS, false, false);
        var explicit = declaration.createMethod(false, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "defaultMethod", Parameter.NO_PARAMS, true, false);
        var property = declaration.createProperty(false, Access.PUBLIC, Access.PUBLIC, type, "value");
        var getter = property.createMethod(false, Access.PUBLIC, null,
                new Parameter[] {new Parameter(pool, type, null, null, true, 0, false)},
                "get", Parameter.NO_PARAMS, false, false);
        var setter = property.createMethod(false, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "set",
                new Parameter[] {new Parameter(pool, type, "value", null, false, 0, false)}, false, false);
        var nested = declaration.createClass(Access.PUBLIC, Format.INTERFACE, "Nested", null);
        var nestedMethod = nested.createMethod(false, Access.PUBLIC, null,
                Parameter.NO_PARAMS, "unimplemented", Parameter.NO_PARAMS, false, false);

        NativeContainer.prepareRebaseMethods(declaration, pool);
        assertTrue(method.isNative());
        assertTrue(getter.isNative());
        assertTrue(setter.isNative());
        assertFalse(explicit.isNative());
        assertFalse(nestedMethod.isNative());

        file.ensureReadOnly();
        // A second preparation does not rewrite already prepared declarations or their parameters.
        NativeContainer.prepareRebaseMethods(declaration, pool);
        assertSame(getter, getter.getReturn(0).getContaining());
        assertSame(setter, setter.getParam(0).getContaining());
    }
}
