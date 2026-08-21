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
    public void fieldAccessChainValidatesConstructorInput()
            throws IOException {
        var source = readString("org/xvm/runtime/CallChain.java");

        assertFalse(source.contains("assert isField();"),
                "FieldAccessChain must not call an instance method from its constructor");
        assertTrue(source.contains("assert CallChain.isFieldChain(aMethods);"),
                "FieldAccessChain must preserve field-chain validation without dispatching through this");
    }

    @Test
    public void methodHandleValidationDoesNotUsePartialHandle()
            throws IOException {
        var source = readString("org/xvm/runtime/template/_native/reflect/xRTMethod.java");

        assertFalse(source.contains("assert getMethodInfo() != null;"),
                "MethodHandle must not call getMethodInfo() on itself from its constructor");
        assertTrue(source.contains("assert resolveMethodInfo(typeTarget, method) != null;"),
                "MethodHandle must preserve validation using constructor arguments");
    }

    private static String readString(String source)
            throws IOException {
        var path = Path.of("src/main/java", source);
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source));
    }
}
