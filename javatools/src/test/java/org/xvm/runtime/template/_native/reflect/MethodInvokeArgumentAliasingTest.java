package org.xvm.runtime.template._native.reflect;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the reflection invoke argument boundary. {@code xRTMethod.invokeInvoke} used to pass the
 * caller tuple's {@code m_ahValue} storage straight into {@code CallChain.invokeT}; because
 * {@code Utils.ensureSize} hands the array through unchanged whenever the callee needs no extra
 * registers, the tuple's storage became the callee frame's register file ({@code f_ahVar}), and
 * any parameter reassignment inside the invoked method wrote into the caller's - possibly
 * immutable, possibly const-heap-cached - tuple. {@code xRTFunction.invokeInvoke} already copied
 * in exactly this case, proving the hazard was known.
 *
 * A full execution test (invoke a method reflectively, reassign a parameter, then assert the
 * caller's tuple is unchanged) needs a fiber execution harness these unit tests do not have, so
 * the tests below pin the two halves of the invariant instead: the {@code ensureSize} aliasing
 * contract that makes the copy load-bearing, and the copy itself at both reflection boundaries.
 */
public class MethodInvokeArgumentAliasingTest {
    /**
     * The reason the boundary must copy: {@code ensureSize} is a grow-only operation and
     * deliberately returns the caller's array when it is already big enough. If this contract ever
     * changes to always copy, the defensive copies at the reflection boundaries become redundant
     * rather than wrong; while it holds, they are load-bearing.
     */
    @Test
    public void ensureSizeAliasesWhenNoGrowthIsNeeded() {
        var ahArg = new ObjectHandle[3];

        assertSame(ahArg, Utils.ensureSize(ahArg, 3),
                "ensureSize hands the same array through when no growth is needed;"
                        + " callers that must not share storage have to copy first");
        assertSame(ahArg, Utils.ensureSize(ahArg, 2));
    }

    /**
     * The reflection invoke path must copy the tuple's storage before handing it to the call
     * chain. Red before the fix: {@code invokeInvoke} passed {@code hTuple.m_ahValue} through raw,
     * with an in-code {@code TODO GG+CP do we need to check these?} asking exactly this question.
     */
    @Test
    public void reflectionInvokePathClonesTupleStorage() throws IOException {
        var source = Files.readString(
                sourceFor("org/xvm/runtime/template/_native/reflect/xRTMethod.java"));
        var body   = methodBody(source, "public int invokeInvoke");

        assertTrue(Pattern.compile("valuesCopy\\s*\\(\\s*\\)"
                        + "|copyOf\\s*\\(\\s*hTuple\\s*\\.\\s*m_ahValue").matcher(body).find(),
                "xRTMethod.invokeInvoke must copy the tuple storage before it becomes the"
                        + " callee register file");
        assertFalse(body.contains("m_ahValue"),
                "xRTMethod.invokeInvoke must not hand the caller tuple's own array to the"
                        + " call chain");
    }

    /**
     * The function invoke path keeps its existing defensive copy; this pins the precedent the
     * method path now mirrors, so neither path can silently regress to sharing storage.
     */
    @Test
    public void functionInvokePathKeepsItsDefensiveCopy() throws IOException {
        var source = Files.readString(
                sourceFor("org/xvm/runtime/template/_native/reflect/xRTFunction.java"));
        var body   = methodBody(source, "public int invokeInvoke");

        assertTrue(Pattern.compile("copyOf\\s*\\(|\\.\\s*clone\\s*\\(\\)").matcher(body).find(),
                "xRTFunction.invokeInvoke must keep cloning the tuple storage when the callee"
                        + " needs no extra registers");
    }


    /**
     * The ISA tuple-argument ops share the reflection path's exact hazard: extracting
     * {@code TupleHandle.m_ahValue} and handing it toward a callee frame aliases the caller's
     * tuple storage as that frame's register file. None of these ops is emitted by today's
     * compiler - the {@code InvocationExpression}/{@code NewExpression} branches that would build
     * them are gated on an {@code m_fTupleArg} flag that is never assigned, and Construct_T is an
     * outright {@code UnsupportedOperationException} - so the hazard here is latent rather than
     * live. It is still reachable, because the opcodes decode from any {@code .xtc}, and the ops
     * will go live the moment that flag is wired up. Every extraction goes through
     * {@code TupleHandle.valuesCopy()} so the fix is in place before that happens.
     */
    @Test
    public void tupleArgumentOpsCopyTupleStorage() throws IOException {
        String[] asOps = {"Call_T0", "Call_T1", "Call_TN", "Call_TT", "Invoke_T0", "Invoke_T1",
                          "Invoke_TN", "Invoke_TT", "Construct_T", "New_T", "NewG_T"};
        for (String sOp : asOps) {
            var source = Files.readString(sourceFor("org/xvm/asm/op/" + sOp + ".java"));
            assertFalse(source.contains(".m_ahValue"),
                    sOp + " must not hand the caller tuple's own storage toward a callee frame");
            assertTrue(source.contains(".valuesCopy()"),
                    sOp + " must extract tuple arguments through TupleHandle.valuesCopy()");
        }
    }

    /**
     * {@code valuesCopy()} must actually copy, and must not use the {@code clone()} idiom.
     */
    @Test
    public void tupleValuesCopyReturnsAnIndependentArray() throws IOException {
        var source  = Files.readString(
                sourceFor("org/xvm/runtime/template/collections/xTuple.java"));
        int ofStart = source.indexOf("public ObjectHandle[] valuesCopy()");

        assertTrue(ofStart >= 0,
                "TupleHandle must expose valuesCopy() as the one sanctioned way to take the"
                        + " tuple's storage toward a callee frame");
        assertTrue(source.substring(ofStart, source.indexOf('}', ofStart)).contains("Arrays.copyOf("),
                "TupleHandle.valuesCopy() must copy the element storage with Arrays.copyOf");
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return the text from the given method declaration to the next method-level declaration,
     *         which is enough context for the shape assertions above
     */
    private static String methodBody(String source, String declaration) {
        int ofStart = source.indexOf(declaration);
        assertTrue(ofStart >= 0, "declaration not found: " + declaration);

        int ofEnd = source.indexOf("\n    public ", ofStart + declaration.length());
        if (ofEnd < 0) {
            ofEnd = source.length();
        }
        return source.substring(ofStart, ofEnd);
    }

    private static Path sourceFor(String relativePath) {
        var local = Path.of("src/main/java").resolve(relativePath);
        if (Files.isRegularFile(local)) {
            return local;
        }

        var dir = Path.of(".").toAbsolutePath().normalize();
        while (dir != null) {
            var candidate = dir.resolve("javatools/src/main/java").resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate main source file: " + relativePath);
    }
}
