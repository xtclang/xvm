package org.xvm.xdk;

import java.io.File;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.api.ModuleCompiler;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.PropertyStructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile real comparison chains and inspect their emitted property values. A fully constant
 * chain must fold, while a runtime operand must retain its initializer even after a false prefix.
 */
class ComparisonChainConstantTest {
    private static ModuleCompiler compiler;

    @BeforeAll
    static void createCompiler() {
        File xdk = new File(System.getProperty("user.dir"), "build/install/xdk");
        File lib = new File(xdk, "lib");
        assertTrue(new File(lib, "ecstasy.xtc").isFile(), "The test requires the installed XDK");
        compiler = new ModuleCompiler(new LinkedRepository(
                new DirRepository(lib, true), new DirRepository(new File(xdk, "javatools"), true)));
    }

    @ParameterizedTest
    @MethodSource("constantChains")
    void foldsEveryLinkInAConstantChain(String expression, boolean expected) {
        var property = compileProperty(expression);
        var value    = property.getInitialValue();
        assertNotNull(value, "Constant chain was left as a runtime initializer: " + expression);
        assertEquals(expected ? property.getConstantPool().valTrue() : property.getConstantPool().valFalse(), value);
    }

    static Stream<Arguments> constantChains() {
        return Stream.of(
                Arguments.of("1 < 2 < 3", true),
                Arguments.of("1 < 2 <= 2", true),
                Arguments.of("3 > 2 > 1", true),
                Arguments.of("3 >= 3 > 1", true),
                Arguments.of("1 == 1 == 1", true),
                Arguments.of("1 != 2 != 3", true),
                Arguments.of("1 < 0 < 2", false),
                Arguments.of("1 < 2 < 0", false),
                Arguments.of("1 == 1 == 2", false),
                Arguments.of("1 == 2 == 2", false),
                Arguments.of("1 != 1 != 2", false),
                Arguments.of("1 != 2 != 2", false),
                Arguments.of("'a' < 'b' <= 'c'", true),
                Arguments.of("'z' < 'b' < 'c'", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1 < 2 < runtimeValue()", "2 < 1 < runtimeValue()"})
    void retainsRuntimeOperandsAfterAConstantPrefix(String expression) {
        var property = compileProperty(expression);
        assertNull(property.getInitialValue(), "A runtime operand must not be folded away");
        assertNotNull(property.getInitializer(), "The runtime operand must still be evaluated");
    }

    private static PropertyStructure compileProperty(String expression) {
        var errors = new ErrorList(20);
        var module = compiler.compile("""
                module ComparisonChain {
                    static Boolean result = %s;
                    static Int runtimeValue() = 3;
                }
                """.formatted(expression), null, errors);
        assertNotNull(module, () -> expression + ": " + errors.getErrors());
        assertTrue(errors.getErrors().isEmpty(), () -> errors.getErrors().toString());
        return assertInstanceOf(PropertyStructure.class, module.getChild("result"));
    }
}
