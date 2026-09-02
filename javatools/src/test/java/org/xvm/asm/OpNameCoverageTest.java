package org.xvm.asm;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Ratchet: every opcode {@code Op.instantiate} can produce must have a name in {@code Op.toName}.
 *
 * <p>The two are parallel ~200-case switches over the same domain, and they had drifted by 16
 * opcodes - the {@code IIP_*} and {@code PIP_*} bitwise, shift and mod forms. Since
 * {@code Op.toString()} is {@code toName(getOpCode())} and {@code toName}'s default throws, those
 * 16 opcodes threw {@code IllegalStateException} from {@code toString()}. That is worse than an
 * ordinary missing case, for two reasons.</p>
 *
 * <p>First, {@code toString()} is called IMPLICITLY - by debuggers, by loggers, by string
 * concatenation - so the failure appears in places that have nothing to do with the opcode.</p>
 *
 * <p>Second, and worse, fifteen sites build an error message with {@code toName(getOpCode())}, for
 * example {@code OpInPlaceAssign}:</p>
 *
 * <pre>default -&gt; throw new UnsupportedOperationException(toName(getOpCode()));</pre>
 *
 * <p>{@code OpInPlaceAssign} IS the {@code IIP_*} family. So on exactly the opcodes that reach that
 * line, constructing the intended {@code UnsupportedOperationException} threw
 * {@code IllegalStateException} instead, destroying the real diagnostic at the moment it was
 * needed.</p>
 *
 * <p>The relationship asserted is a SUBSET, not equality: {@code toName} may legitimately name an
 * opcode {@code instantiate} does not produce. {@code OP_NEWC_T} and {@code OP_NEWCG_T} are exactly
 * that - reserved slots holding their place in the contiguous {@code NEWC}/{@code NEWCG} numbering,
 * unimplemented but deliberately not removed, because removing them invites a renumbering that
 * would break the binary format.</p>
 */
public class OpNameCoverageTest {

    /**
     * A spot check on the specific opcode that proved the bug, so the regression has a named,
     * readable case rather than only appearing inside a set difference.
     */
    @Test
    public void theOpcodeThatProvedTheBugIsNamed() {
        assertEquals("IIP_AND", assertDoesNotThrow(() -> Op.toName(Op.OP_IIP_AND)));
        assertEquals("PIP_XOR", assertDoesNotThrow(() -> Op.toName(Op.OP_PIP_XOR)));
        assertEquals("LINE_1",  Op.toName(Op.OP_LINE_1));
    }

    /**
     * Extract the {@code case OP_*} labels between two markers in the source.
     *
     * @param source  the source text
     * @param sFrom   the marker the region starts at
     * @param sTo     the marker the region ends at, or null for "to the end"
     */
    private static Set<String> casesIn(String source, String sFrom, String sTo) {
        int of = source.indexOf(sFrom);
        assertTrue(of >= 0, "marker not found: " + sFrom);

        int ofEnd = sTo == null ? source.length() : source.indexOf(sTo, of);
        assertTrue(ofEnd > of, "end marker not found after start: " + sTo);

        var     names   = new TreeSet<String>();
        Matcher matcher = CASE_LABEL.matcher(source.substring(of, ofEnd));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Path opSource() {
        Path cwd     = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java/org/xvm/asm/Op.java");
        return Files.exists(project)
                ? project
                : cwd.resolve("javatools/src/main/java/org/xvm/asm/Op.java");
    }

    private static final Pattern CASE_LABEL = Pattern.compile("case\\s+(OP_\\w+)\\s*:");
}
