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
 * immutable, possibly const-heap-cached - tuple. {@code xRTFunction.invokeInvoke} already copied
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
     * ever changes to always copy, the defensive copies at the reflection boundaries become
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
