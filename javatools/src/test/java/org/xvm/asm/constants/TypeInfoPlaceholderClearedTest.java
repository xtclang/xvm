package org.xvm.asm.constants;


import java.io.IOException;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ThrowInstruction;

import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * {@code ensureTypeInfoInWindow} marks a type "busy building" before building it, and both exits
 * that throw must undo the mark. Master issue 48.
 *
 * <p><b>Reads the compiled class, not the source.</b> The first version scanned
 * {@code TypeConstant.java} as text, which is the pattern `54bcea306` deleted five tests for: it
 * passes when the code is spelled the expected way and breaks on reformatting. That commit's remedy
 * is to read the compiled classes, and this follows it and {@code FreezeViewSharingTest}'s scan.
 *
 * <p><b>What it checks, stated honestly.</b> It counts invocations of
 * {@code clearTypeInfoPlaceholder} in the method and requires both. It does NOT prove that every
 * throwing path passes through one - that needs control-flow analysis, and two attempts at it were
 * wrong in opposite directions: the first accepted any earlier clear in the method, so it passed
 * with the fix removed; the second demanded a clear in the throw's own basic block, and rejected
 * paths that are correctly covered by the handler.
 *
 * <p>Counting is the weaker claim and the honest one. It catches the regression that actually
 * threatens this - someone deleting one of the two calls - and it is immune to reformatting, which
 * is what the source version was not. The javadoc on the two call sites carries the reasoning that
 * a count cannot.
 *
 * <p><b>Why a gate at all.</b> The invariant has no observable behaviour: every reader of the
 * place-holder recovers on its own, which is why issue 48 was withdrawn as a defect. There are no
 * wrong results to assert on, and its harmlessness rests on a second mechanism catching the lie.
 */
public class TypeInfoPlaceholderClearedTest {
    private static final String METHOD = "ensureTypeInfoInWindow";
    private static final String CLEAR  = "clearTypeInfoPlaceholder";

    /**
     * Two throwing exits, two clears: the {@code IllegalStateException} raised before the guarded
     * region, and the {@code catch (Exception | Error)} that rethrows.
     */
    private static final int EXPECTED_CLEARS = 2;

    @Test
    public void bothThrowingExitsClearThePlaceHolder() throws IOException, URISyntaxException {
        List<CodeElement> code = codeOf(METHOD);

        assertEquals(EXPECTED_CLEARS, countInvocations(code, CLEAR),
                METHOD + " must clear the place-holder on both throwing exits; a missing one leaves"
                        + " the type marked \"busy building\" on a shared, interned TypeConstant");
    }

    /**
     * The gate is worth nothing if it cannot find the method, so prove it read a real one.
     */
    @Test
    public void theGateIsReadingRealBytecode() throws IOException, URISyntaxException {
        List<CodeElement> code = codeOf(METHOD);

        assertTrue(code.size() > 200, () -> "expected a substantial method body, found " + code.size());
        assertTrue(code.stream().anyMatch(ThrowInstruction.class::isInstance),
                "and one that can throw - otherwise this gate guards nothing");
    }

    private static int countInvocations(List<CodeElement> code, String sMethod) {
        return (int) code.stream()
                .filter(InvokeInstruction.class::isInstance)
                .map(InvokeInstruction.class::cast)
                .filter(invoke -> sMethod.equals(invoke.method().name().stringValue()))
                .count();
    }

    private static List<CodeElement> codeOf(String sMethod) throws IOException, URISyntaxException {
        Path anchor = Path.of(TypeConstant.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(anchor),
                "the compiler must be scannable as exploded classes, but the code source is " + anchor);

        Path file = anchor.resolve("org/xvm/asm/constants/TypeConstant.class");
        assertTrue(Files.isRegularFile(file), () -> "compiled TypeConstant not found at " + file);

        for (var method : ClassFile.of().parse(Files.readAllBytes(file)).methods()) {
            if (sMethod.equals(method.methodName().stringValue()) && method.code().isPresent()) {
                return method.code().get().elementList();
            }
        }
        throw new IllegalStateException(sMethod + " not found in the compiled TypeConstant");
    }
}
