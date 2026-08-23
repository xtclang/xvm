package org.xvm.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the compiler code-generation failure boundary. Master printed unchecked codegen defects
 * and continued the phase loop, which could hide corrupted compiler/module state.
 */
class CompilerCodegenFailureTest {
    /**
     * Code generation mutates the module graph and constant pools, so unchecked defects are
     * terminal. This test fails on master because master caught Throwable, printed to stderr, and
     * kept compiling.
     */
    @Test
    void codeGenerationFailuresAreTerminalAndPreserveCause() throws IOException {
        var source = readString("org/xvm/tool/Compiler.java");
        var method = sourceBetween(source, "protected void generateCode", "    @SuppressWarnings");

        assertAll(
                () -> assertFalse(method.contains("catch (Throwable"),
                        "codegen must not catch Throwable and continue"),
                () -> assertFalse(method.contains("System.err.println(\"Failed to generate code"),
                        "codegen failures must not use ad hoc stderr reporting"),
                () -> assertFalse(method.contains("printStackTrace(System.err)"),
                        "codegen failures must preserve cause through the launcher failure path"),
                () -> assertTrue(method.contains("catch (Error e)"),
                        "fatal VM errors must be rethrown directly"),
                () -> assertTrue(method.contains("catch (RuntimeException e)"),
                        "unchecked compiler defects must be treated as terminal"),
                () -> assertTrue(method.contains("log(FATAL, e, \"Failed to generate code for {}\", compiler)"),
                        "terminal compiler failure must keep the original cause"));
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
