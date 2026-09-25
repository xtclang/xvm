package org.xvm.runtime;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;

import org.xvm.runtime.template.maps.xListMap;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ListMapOwnershipTest {
    @Test
    void constructorCacheBelongsToTheTemplateDefinitions() {
        var first = fixture();
        first.template.initNative();
        assertSame(first.constructor, first.template.ensureConstructor());

        var second = fixture();
        second.template.initNative();
        assertNotSame(first.constructor, second.constructor);
        assertSame(second.constructor, second.template.ensureConstructor());
        assertSame(first.constructor, first.template.ensureConstructor());
    }

    private static Fixture fixture() {
        var file = new FileStructure("App");
        var pool = file.getConstantPool();
        var declaration = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "ListMap", null);
        var type = declaration.getIdentityConstant().getType();
        var parameters = IntStream.range(0, 3)
                .mapToObj(index -> new Parameter(pool, type, "arg" + index, null, false, index, false))
                .toArray(Parameter[]::new);
        var constructor = declaration.createMethod(false, Access.PUBLIC, null, Parameter.NO_PARAMS,
                "construct", parameters, true, false);
        // Constructor selection needs only declarations, not a running VM or installed XDK.
        return new Fixture(new xListMap(null, declaration, false), constructor);
    }

    private record Fixture(xListMap template, MethodStructure constructor) {}
}
