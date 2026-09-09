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
    void assembleDoesNotPrintAndContinueAfterOpAssemblyFailure() throws Exception {
        // Reads the compiled method rather than a source region delimited by two comments - the
        // pattern 54bcea306 deleted five tests for. Those two markers are ordinary comments: an
        // editor reflowing them, or a later edit moving one, silently changes what is asserted on
        // while the test keeps passing.
        var code = codeOf("assemble");

        assertAll(
                () -> assertFalse(invokes(code, "println"),
                        "op assembly failure must not use ad hoc stderr reporting"),
                () -> assertTrue(loadsConstantContaining(code, "op assembly failed for method"),
                        "op assembly failure must be rethrown with artifact context"),
                () -> assertTrue(constructsWithCause(code, "java/lang/IllegalStateException"),
                        "op assembly failure must preserve the original cause"));
    }

    /**
     * @return the code of the named method in the compiled MethodStructure
     */
    private static java.util.List<java.lang.classfile.CodeElement> codeOf(String sMethod)
            throws Exception {
        java.nio.file.Path anchor = java.nio.file.Path.of(MethodStructure.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertTrue(java.nio.file.Files.isDirectory(anchor),
                "the tree must be scannable as exploded classes, but the code source is " + anchor);

        java.nio.file.Path file = anchor.resolve("org/xvm/asm/MethodStructure.class");
        for (var method : java.lang.classfile.ClassFile.of()
                .parse(java.nio.file.Files.readAllBytes(file)).methods()) {
            if (sMethod.equals(method.methodName().stringValue()) && method.code().isPresent()) {
                return method.code().get().elementList();
            }
        }
        throw new IllegalStateException(sMethod + " not found in the compiled MethodStructure");
    }

    private static boolean invokes(java.util.List<java.lang.classfile.CodeElement> code, String sName) {
        return code.stream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .anyMatch(i -> sName.equals(i.method().name().stringValue()));
    }

    /**
     * @return true if the method mentions the text as a constant
     *
     * <p>Two forms, because javac chooses between them: a plain literal is an {@code ldc}, but a
     * literal that is part of a concatenation becomes a {@code makeConcatWithConstants} RECIPE
     * carried in the invokedynamic's bootstrap arguments, with no {@code ldc} anywhere. Checking
     * only the first is how this test failed on a message that is plainly present in the source -
     * which is itself a small argument for reading bytecode: the source hid that distinction.
     */
    private static boolean loadsConstantContaining(
            java.util.List<java.lang.classfile.CodeElement> code, String sText) {
        boolean fLdc = code.stream()
                .filter(java.lang.classfile.instruction.ConstantInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.ConstantInstruction.class::cast)
                .anyMatch(c -> String.valueOf(c.constantValue()).contains(sText));

        boolean fRecipe = code.stream()
                .filter(java.lang.classfile.instruction.InvokeDynamicInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeDynamicInstruction.class::cast)
                .flatMap(indy -> indy.bootstrapArgs().stream())
                .anyMatch(arg -> String.valueOf(arg).contains(sText));

        return fLdc || fRecipe;
    }

    /**
     * @return true if the method constructs the named throwable with a (String, Throwable) shape,
     *         which is what preserving the cause looks like once compiled
     */
    private static boolean constructsWithCause(
            java.util.List<java.lang.classfile.CodeElement> code, String sType) {
        return code.stream()
                .filter(java.lang.classfile.instruction.InvokeInstruction.class::isInstance)
                .map(java.lang.classfile.instruction.InvokeInstruction.class::cast)
                .anyMatch(i -> "<init>".equals(i.method().name().stringValue())
                        && sType.equals(i.owner().asInternalName())
                        && i.typeSymbol().parameterList().size() == 2);
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
