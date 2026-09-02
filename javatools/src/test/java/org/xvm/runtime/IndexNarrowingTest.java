package org.xvm.runtime;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * An array index arrives as a {@code long} and must be range-checked as one.
 *
 * <p>{@code extractArrayValue(Frame, ObjectHandle, long lIndex, int)} took the index as a
 * {@code long}, and two implementations narrowed it before checking it:</p>
 *
 * <pre>
 * int nIx = (int) lIndex;
 * return nIx &lt; 0 || nIx &gt;= ach.size() ? raise(..., lIndex, ...) : get(nIx);
 * </pre>
 *
 * <p>{@code (int)} keeps only the low 32 bits, so an index of 2<sup>32</sup>&nbsp;+&nbsp;4 became 4,
 * passed the check, and returned <b>the character at index 4</b> instead of raising. On a
 * String of length 8, {@code s[4294967300]} answered {@code 'e'}. The same expression on an
 * {@code Int[]} raised correctly, because {@code xArray} checks the un-narrowed value - so the two
 * disagreed about the same out-of-range index.</p>
 *
 * <p>The exception each site builds already reported {@code lIndex}, not the narrowed variable,
 * which is the tell: the check was always meant to be against the full value.</p>
 *
 * <p><b>Why this is a source scan.</b> Reaching {@code extractArrayValue} needs a live
 * {@code Frame}, a container and a type composition, none of which exist in a unit test. The
 * defect is textual and local - a narrowed variable used in the guard - so it can be detected
 * where it is written. This fails on the unfixed source and passes on the fixed source, and it
 * catches a third implementation acquiring the same shape.</p>
 */
public class IndexNarrowingTest {
    /** {@code int <name> = (int) <longParam>;} */
    private static final Pattern NARROWING =
            Pattern.compile("int\\s+(\\w+)\\s*=\\s*\\(int\\)\\s*(\\w+)\\s*;");

    /** Methods that receive an index as a long and must range-check it as one. */
    private static final Pattern INDEXED_METHOD = Pattern.compile(
            "public int (extractArrayValue|assignArrayValue)\\([^)]*long (\\w+)[^)]*\\)");

    @Test
    public void noIndexedMethodRangeChecksANarrowedIndex() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path path : runtimeSources()) {
            String  source  = Files.readString(path);
            Matcher method  = INDEXED_METHOD.matcher(source);
            while (method.find()) {
                String longParam = method.group(2);
                String body      = bodyOf(source, method.end());

                Matcher narrowing = NARROWING.matcher(body);
                while (narrowing.find()) {
                    if (!narrowing.group(2).equals(longParam)) {
                        continue;
                    }
                    String narrowed = narrowing.group(1);
                    // the guard is the comparison; using the narrowed name there is the defect
                    if (Pattern.compile("\\b" + narrowed + "\\b\\s*(<|>=|>|<=)").matcher(body).find()) {
                        offenders.add(path.getFileName() + "." + method.group(1)
                                      + ": narrows " + longParam + " to " + narrowed
                                      + " and range-checks " + narrowed);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                () -> "an index arrives as a long and must be range-checked before it is narrowed;"
                      + " (int) keeps only the low 32 bits, so an index of 2^32 + n passes a check"
                      + " written against the narrowed value and reads element n: " + offenders);
    }



    /** And that the narrowing pattern itself matches the shape the defect had. */
    @Test
    public void theNarrowingPatternMatchesTheOriginalShape() {
        Matcher m = NARROWING.matcher("        int    nIx = (int) lIndex;\n");

        assertTrue(m.find(), "the pattern must match the shape this test exists to forbid");
        assertEquals("nIx", m.group(1));
        assertEquals("lIndex", m.group(2));
    }

    private static List<Path> runtimeSources() throws IOException {
        Path cwd  = Path.of("").toAbsolutePath();
        Path root = cwd.resolve("src/main/java/org/xvm/runtime");
        if (!Files.exists(root)) {
            root = cwd.resolve("javatools/src/main/java/org/xvm/runtime");
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String bodyOf(String source, int from) {
        int depth = 0;
        int i     = source.indexOf('{', from);
        for (int k = i; k < source.length(); k++) {
            char c = source.charAt(k);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(i, k + 1);
            }
        }
        return source.substring(i);
    }
}
