package org.xvm.asm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.Frame;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the method op-assembly failure boundary in {@link MethodStructure#assemble}. Master
 * caught {@code UnsupportedOperationException} from op encoding, printed one line to stderr, and
 * kept serializing: the method was then persisted with zero op bytes, so a compiler defect (an op
 * that cannot be encoded, or a body that was never compiled) produced a corrupt module that still
 * looked like a successful build.
 */
class MethodStructureAssemblyFailureTest {
    /**
     * Serializing a method whose ops cannot be encoded must fail module assembly with the method
     * and module identity and the original cause. On master this test fails because
     * {@code FileStructure.writeTo} completed without an exception and quietly wrote the method
     * body as zero op bytes.
     */
    @Test
    void opAssemblyFailureIsTerminalWithArtifactContext() {
        var file   = new FileStructure("test");
        var clz    = file.getModule().createClass(
                Constants.Access.PUBLIC, Component.Format.CLASS, "Broken", null);
        var method = clz.createMethod(false, Constants.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "foo", Parameter.NO_PARAMS, true, false);

        // an op with no encoding: Op.write() delegates to the base getOpCode(), which throws
        // UnsupportedOperationException, modeling a pseudo-op that survived into final assembly
        method.createCode().add(new Op() {
            @Override
            public int process(Frame frame, int iPC) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean advances() {
                return false;
            }
        });

        var failure = assertThrows(IllegalStateException.class,
                () -> file.writeTo(new ByteArrayOutputStream()),
                "module serialization must fail when a method body cannot be assembled");

        assertAll(
                () -> assertTrue(failure.getMessage().contains("foo"),
                        "failure must identify the method: " + failure.getMessage()),
                () -> assertTrue(failure.getMessage().contains("test"),
                        "failure must identify the module: " + failure.getMessage()),
                () -> assertInstanceOf(UnsupportedOperationException.class, failure.getCause(),
                        "failure must preserve the original op-encoding cause"));
    }

    /**
     * Master's shape was catch/print/continue around {@code ensureAssembled}. This test fails on
     * master because the catch block wrote to stderr and fell through to serialize the method with
     * whatever op bytes were present (none).
     */
    @Test
    void assembleDoesNotPrintAndContinueAfterOpAssemblyFailure() throws IOException {
        var source = readString("org/xvm/asm/MethodStructure.java");
        var region = sourceBetween(source,
                "// produce the op bytes if necessary",
                "// write out the op bytes");

        assertAll(
                () -> assertFalse(region.contains("System.err"),
                        "op assembly failure must not use ad hoc stderr reporting"),
                () -> assertTrue(region.contains("throw new IllegalStateException(\"op assembly failed for method"),
                        "op assembly failure must be rethrown with artifact context"),
                () -> assertTrue(region.contains(", e)"),
                        "op assembly failure must preserve the original cause"));
    }

    private static String readString(String source) throws IOException {
        var path = Path.of("src/main/java", source);
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source));
    }

    private static String sourceBetween(String source, String start, String end) {
        var ofStart = source.indexOf(start);
        var ofEnd   = source.indexOf(end, ofStart);

        assertTrue(ofStart >= 0, "missing source start marker: " + start);
        assertTrue(ofEnd > ofStart, "missing source end marker: " + end);
        return source.substring(ofStart, ofEnd);
    }
}
