package org.xvm.tool;

import java.io.IOException;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.InvokeInstruction;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the compiler code-generation failure boundary. Master printed unchecked codegen defects
 * and continued the phase loop, which could hide corrupted compiler/module state.
 *
 * <p><b>Reads the compiled method, not its source text.</b> The previous version searched
 * `Compiler.java` between two string markers and asserted on substrings - the pattern `54bcea306`
 * deleted five tests for. It was not hypothetical here: it broke earlier in this branch's history on
 * a rename that had nothing to do with what it guards, because one of its markers was a method
 * signature.
 *
 * <p>Bytecode states these facts directly. A caught type is an entry in the exception table, not a
 * spelling of `catch (`; and the reporting call is an invocation, not a substring that a reformat
 * across two lines would hide.
 */
class CompilerCodegenFailureTest {
    private static final String METHOD = "generateCode";

    /**
     * Code generation mutates the module graph and constant pools, so unchecked defects are
     * terminal. This fails on master, which caught Throwable, printed to stderr, and kept compiling.
     */
    @Test
    void codeGenerationFailuresAreTerminalAndPreserveCause() throws IOException, URISyntaxException {
        List<CodeElement> code   = codeOf(METHOD);
        List<String>      caught = caughtTypes(code);

        assertAll(
                () -> assertFalse(caught.contains("java/lang/Throwable"),
                        "codegen must not catch Throwable and continue; caught: " + caught),
                () -> assertFalse(caught.contains(null),
                        "codegen must not use a catch-all handler; caught: " + caught),
                () -> assertFalse(invokes(code, "println"),
                        "codegen failures must not use ad hoc stderr reporting"),
                () -> assertFalse(invokes(code, "printStackTrace"),
                        "codegen failures must preserve cause through the launcher failure path"),
                () -> assertTrue(caught.contains("java/lang/Error"),
                        "fatal VM errors must be rethrown directly; caught: " + caught),
                () -> assertTrue(caught.contains("java/lang/RuntimeException"),
                        "unchecked compiler defects must be treated as terminal; caught: " + caught),
                () -> assertTrue(invokes(code, "report"),
                        "terminal compiler failure must be reported"),
                () -> assertTrue(loadsConstant(code, "Failed to generate code for {}"),
                        "terminal compiler failure must keep the original cause and message"));
    }

    /**
     * The guard is worthless if it cannot find the method, so prove it read a real one.
     */
    @Test
    void theGuardIsReadingRealBytecode() throws IOException, URISyntaxException {
        List<CodeElement> code = codeOf(METHOD);

        assertFalse(code.isEmpty(), METHOD + " must have a body");
        assertFalse(caughtTypes(code).isEmpty(),
                METHOD + " must catch something, or this guard is checking an empty table");
    }

    /**
     * @return the caught types of every handler in the method; a null entry is a catch-all
     */
    private static List<String> caughtTypes(List<CodeElement> code) {
        return code.stream()
                .filter(ExceptionCatch.class::isInstance)
                .map(ExceptionCatch.class::cast)
                .map(handler -> handler.catchType()
                        .map(type -> type.asInternalName())
                        .orElse(null))
                .toList();
    }

    private static boolean invokes(List<CodeElement> code, String sMethod) {
        return code.stream()
                .filter(InvokeInstruction.class::isInstance)
                .map(InvokeInstruction.class::cast)
                .anyMatch(invoke -> sMethod.equals(invoke.method().name().stringValue()));
    }

    private static boolean loadsConstant(List<CodeElement> code, String sText) {
        return code.stream()
                .filter(ConstantInstruction.class::isInstance)
                .map(ConstantInstruction.class::cast)
                .anyMatch(load -> sText.equals(String.valueOf(load.constantValue())));
    }

    private static List<CodeElement> codeOf(String sMethod) throws IOException, URISyntaxException {
        Path anchor = Path.of(Compiler.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(anchor),
                "the tool must be scannable as exploded classes, but the code source is " + anchor);

        Path file = anchor.resolve("org/xvm/tool/Compiler.class");
        assertTrue(Files.isRegularFile(file), () -> "compiled Compiler not found at " + file);

        Optional<List<CodeElement>> found = ClassFile.of().parse(Files.readAllBytes(file)).methods()
                .stream()
                .filter(m -> sMethod.equals(m.methodName().stringValue()) && m.code().isPresent())
                .map(m -> m.code().get().elementList())
                .findFirst();
        assertTrue(found.isPresent(), () -> sMethod + " not found in the compiled Compiler");
        return found.get();
    }
}
