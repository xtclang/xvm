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
 * Guards the reflection invoke argument boundary (must-fix row 151, graduated from the array
 * element exposure audit). {@code xRTMethod.invokeInvoke} used to pass the caller tuple's
 * {@code m_ahValue} storage straight into {@code CallChain.invokeT}; because
 * {@code Utils.ensureSize} hands the array through unchanged whenever the callee needs no extra
 * registers, the tuple's storage became the callee frame's register file ({@code f_ahVar}), and
 * any parameter reassignment inside the invoked method wrote into the caller's - possibly
 * immutable, possibly const-heap-cached - tuple. {@code xRTFunction.invokeInvoke} already cloned
 * in exactly this case, proving the hazard was known.
 *
 * A full red/green execution test (invoke a method reflectively, reassign a parameter, assert the
 * caller's tuple is unchanged) needs the fiber execution harness tracked by the same-JVM stress
 * plan; until that lands, these tests pin the two halves of the invariant at source level, the
 * same way {@code FreezeViewSharingTest} pins the freeze-cell write discipline.
 */
public class MethodInvokeArgumentAliasingTest {
    /**
     * The reason the boundary must copy: {@code ensureSize} is a grow-only operation and
     * deliberately returns the caller's array when it is already big enough. If this contract
     * ever changes to always copy, the defensive clones at the reflection boundaries become
     * redundant rather than wrong; while it holds, they are load-bearing.
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
     * The reflection invoke path must clone the tuple's storage before handing it to the call
     * chain. Red on master: {@code invokeInvoke} passed {@code hTuple.m_ahValue} through raw
     * (with an in-code {@code TODO GG+CP do we need to check these?} asking exactly this
     * question).
     */
    @Test
    public void reflectionInvokePathClonesTupleStorage() throws IOException {
        var source = Files.readString(sourceFor("org/xvm/runtime/template/_native/reflect/xRTMethod.java"));
        var body   = methodBody(source, "public int invokeInvoke");

        assertTrue(Pattern.compile("m_ahValue\\s*\\.\\s*clone\\s*\\(\\)").matcher(body).find(),
                "xRTMethod.invokeInvoke must clone the tuple storage before it becomes the"
                        + " callee register file");
        assertFalse(Pattern.compile("=\\s*hTuple\\s*\\.\\s*m_ahValue\\s*;").matcher(body).find(),
                "xRTMethod.invokeInvoke must not hand the caller tuple's own array to the"
                        + " call chain");
    }

    /**
     * The function invoke path keeps its existing defensive copy; this pins the precedent the
     * method path now mirrors, so neither path can silently regress to sharing storage.
     */
    @Test
    public void functionInvokePathKeepsItsDefensiveCopy() throws IOException {
        var source = Files.readString(sourceFor("org/xvm/runtime/template/_native/reflect/xRTFunction.java"));
        var body   = methodBody(source, "public int invokeInvoke");

        assertTrue(Pattern.compile("\\.\\s*clone\\s*\\(\\)").matcher(body).find(),
                "xRTFunction.invokeInvoke must keep cloning the tuple storage when the callee"
                        + " needs no extra registers");
    }

    /**
     * The ISA tuple-argument ops (the Call_T, Invoke_T, Construct_T, New_T, NewG_T families) share
     * the reflection path's exact hazard: extracting {@code TupleHandle.m_ahValue} and handing it
     * toward a callee frame aliases the caller's tuple storage as the register file. Today's
     * compiler never emits the tuple-arg forms, but the opcodes decode from any {@code .xtc}, so
     * the latent path is reachable from hostile or future-compiler modules. Decision (board row):
     * every extraction goes through {@code TupleHandle.valuesCopy()}, mirroring the
     * {@code xRTMethod}/{@code xRTFunction} clones. Red on the pre-decision shape, where all the
     * ops read {@code .m_ahValue} raw.
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
