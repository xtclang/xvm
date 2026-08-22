package org.xvm.runtime;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Guards runtime construction paths that previously invoked instance methods before construction
 * completed.
 */
public class RuntimeThisEscapeConstructionTest {
    @Test
    public void fieldAccessChainValidatesConstructorInput() throws IOException {
        var source = readString("org/xvm/runtime/CallChain.java");

        assertFalse(source.contains("assert isField();"),
                "FieldAccessChain must not call an instance method from its constructor");
        assertTrue(source.contains("assert CallChain.isFieldChain(aMethods);"),
                "FieldAccessChain must preserve field-chain validation without dispatching through this");
    }

    @Test
    public void methodHandleValidationDoesNotUsePartialHandle() throws IOException {
        var source = readString("org/xvm/runtime/template/_native/reflect/xRTMethod.java");

        assertFalse(source.contains("assert getMethodInfo() != null;"),
                "MethodHandle must not call getMethodInfo() on itself from its constructor");
        assertTrue(source.contains("assert resolveMethodInfo(typeTarget, method) != null;"),
                "MethodHandle must preserve validation using constructor arguments");
    }

    @Test
    public void implicitFieldsAreConstructorMetadata() throws IOException {
        var classTemplate = readString("org/xvm/runtime/ClassTemplate.java");
        var constTemplate = readString("org/xvm/runtime/template/xConst.java");
        var refTemplate   = readString("org/xvm/runtime/template/reflect/xRef.java");

        assertFalse(classTemplate.contains("registerImplicitFields("),
                "ClassTemplate must not call an overridable implicit-field hook from its constructor");
        assertTrue(constTemplate.contains("super(container, structure, PROP_HASH);"),
                "xConst must preserve the synthetic hash field as constructor metadata");
        assertTrue(refTemplate.contains("super(container, structure, RefHandle.REFERENT, GenericHandle.OUTER);"),
                "xRef must preserve referent and outer fields as constructor metadata");
    }

    private static String readString(String source) throws IOException {
        var path = Path.of("src/main/java", source);
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source));
    }
}
