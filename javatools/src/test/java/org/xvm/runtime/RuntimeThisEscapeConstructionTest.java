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
    /**
     * FieldAccessChain construction must validate inputs without relying on partially constructed
     * receiver state. This keeps constructor cleanup behaviorally equivalent.
     */
    @Test
    public void fieldAccessChainValidatesConstructorInput() throws IOException {
        var source = readString("org/xvm/runtime/CallChain.java");

        assertFalse(source.contains("assert isField();"),
                "FieldAccessChain must not call an instance method from its constructor");
        assertTrue(source.contains("assert CallChain.isFieldChain(aMethods);"),
                "FieldAccessChain must preserve field-chain validation without dispatching through this");
    }

    /**
     * MethodHandle validation must not use a partial handle from its own constructor. The test
     * preserves validation behavior while removing construction-time publication.
     */
    @Test
    public void methodHandleValidationDoesNotUsePartialHandle() throws IOException {
        var source = readString("org/xvm/runtime/template/_native/reflect/xRTMethod.java");

        assertFalse(source.contains("assert getMethodInfo() != null;"),
                "MethodHandle must not call getMethodInfo() on itself from its constructor");
        assertTrue(source.contains("assert resolveMethodInfo(typeTarget, method) != null;"),
                "MethodHandle must preserve validation using constructor arguments");
    }

    /**
     * Implicit-field metadata is constructor data, not mutable runtime state. This checks the
     * refactor did not change the implicit field list.
     */
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

    /**
     * Container constructors must not capture `this` in owner helpers before construction
     * completes. That was unsafe for single-threaded reentry and parallel startup alike.
     */
    @Test
    public void containerConstructorDoesNotCaptureThisInOwnerHelpers() throws IOException {
        var container = readString("org/xvm/runtime/Container.java");
        var heap      = readString("org/xvm/runtime/ConstHeap.java");
        var main      = readString("org/xvm/runtime/MainContainer.java");
        var nested    = readString("org/xvm/runtime/NestedContainer.java");

        assertFalse(container.contains("new ConstHeap(this)"),
                "Container must not pass this into ConstHeap from the base constructor");
        assertFalse(container.contains("new NativeTemplates(this)"),
                "Container must not construct owner-retaining NativeTemplates during construction");
        assertFalse(container.contains("registerContainer(this)"),
                "Container must not register itself from the base constructor");
        assertTrue(container.contains("Lazy.ofBound(NativeTemplates::new)"),
                "NativeTemplates must remain owner-local and lazy after Container construction");
        assertTrue(container.contains("private final ConstHeap f_heap;"),
                "ConstHeap should be reached through Container.getConstHeap()");
        assertFalse(heap.contains("f_container"),
                "ConstHeap must not retain the owner captured during Container construction");
        assertTrue(main.contains("runtime.registerContainer(new MainContainer"),
                "MainContainer must register only after construction returns");
        assertTrue(nested.contains("registerContainer(\n                new NestedContainer"),
                "NestedContainer must register only after construction returns");
    }

    private static String readString(String source) throws IOException {
        var path = Path.of("src/main/java", source);
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source));
    }
}
