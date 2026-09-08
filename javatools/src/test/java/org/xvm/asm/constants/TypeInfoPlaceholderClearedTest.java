package org.xvm.asm.constants;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * {@code ensureTypeInfoInWindow} marks a type "busy building" before building it:
 *
 * <pre>{@code
 * setTypeInfo(pool.infoPlaceholder());
 * }</pre>
 *
 * and every exit after that point must undo it. Master issue 48.
 *
 * <p><b>Why this is a source-shape gate and not a behavioural test.</b> The invariant deliberately
 * has no observable behaviour: every reader of the place-holder recovers on its own, which is why
 * issue 48 was withdrawn as a defect. `isComplete(placeholder)` is false so the outer path rebuilds,
 * and the deferred loop calls {@code buildTypeInfo} directly. So a stranded marker cannot be caught
 * by asserting on results - there are no wrong results to assert on.
 *
 * <p>It is also not reachable from a test even if one wanted to force it: seeding the throw needs
 * {@code Constant.addDeferredTypeInfo}, which is protected in {@code org.xvm.asm}, while observing
 * the strand needs {@code TypeConstant.getTypeInfo}, protected in {@code org.xvm.asm.constants}, and
 * nothing public reads a cached TypeInfo without building one. A behavioural test would need test
 * hooks opened in both packages, to pin an invariant with no behaviour - which is a worse trade than
 * reading the source.
 *
 * <p><b>What the gate protects.</b> The marker's harmlessness today rests on a SECOND mechanism
 * catching the lie. If a future change makes the inner defer authoritative, or drops the direct
 * {@code buildTypeInfo} call from the deferred loop, the strand becomes live immediately - and
 * nothing else in the tree would fail first.
 */
public class TypeInfoPlaceholderClearedTest {
    private static final Pattern THROW = Pattern.compile("^\\s*throw\\b");

    @Test
    public void everyThrowAfterTheMarkerIsSetUndoesIt() throws IOException {
        List<String> body = methodBody();

        int marker = indexOf(body, "setTypeInfo(pool.infoPlaceholder())");
        int tryAt  = firstMatching(body, marker, l -> l.strip().startsWith("try {"));
        int catchAt = indexOf(body, "catch (Exception | Error e)");

        assertTrue(marker >= 0, "the place-holder assignment must be findable, or this gate is blind");
        assertTrue(tryAt > marker && catchAt > tryAt, "the guarding try/catch must be findable");

        // The catch's own `throw e` is checked by the loop below, which treats anything after the
        // catch line as needing its own clear - so there is no separate assertion for it here.

        List<String> unguarded = new ArrayList<>();
        for (int i = marker; i < body.size(); ++i) {
            if (!THROW.matcher(body.get(i)).find()) {
                continue;
            }
            // a throw inside the guarded region is covered by that catch; one outside must clear
            boolean covered = (i > tryAt && i < catchAt) || clearsBetween(body, Math.max(marker, i - 6), i);
            if (!covered) {
                unguarded.add(body.get(i).strip());
            }
        }

        assertTrue(unguarded.isEmpty(),
                () -> "these leave the type marked \"busy building\":\n  " + String.join("\n  ", unguarded));
    }

    /**
     * The gate is worthless if it cannot see the method, so prove it found a real one.
     */
    @Test
    public void theGateIsReadingTheRealMethod() throws IOException {
        List<String> body = methodBody();

        assertTrue(body.size() > 100, () -> "expected the whole method, found " + body.size() + " lines");
        assertFalse(indexOf(body, "setTypeInfo(pool.infoPlaceholder())") < 0,
                "the method must still set a place-holder; if it stopped, this gate is obsolete");
    }

    private static List<String> methodBody() throws IOException {
        List<String> lines = Files.readAllLines(source());
        int start = firstMatching(lines, 0, l -> l.contains("private TypeInfo ensureTypeInfoInWindow("));
        assertTrue(start >= 0, "ensureTypeInfoInWindow must be findable");

        int depth = 0;
        for (int i = start; i < lines.size(); ++i) {
            depth += count(lines.get(i), '{') - count(lines.get(i), '}');
            if (depth == 0 && i > start) {
                return lines.subList(start, i + 1);
            }
        }
        throw new IllegalStateException("unbalanced braces reading ensureTypeInfoInWindow");
    }

    private static Path source() {
        Path cwd = Path.of("").toAbsolutePath();
        for (Path root : List.of(cwd.resolve("src/main/java"), cwd.resolve("javatools/src/main/java"))) {
            Path file = root.resolve("org/xvm/asm/constants/TypeConstant.java");
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        throw new IllegalStateException("cannot locate TypeConstant.java from " + cwd);
    }

    private static boolean clearsBetween(List<String> body, int from, int to) {
        for (int i = Math.max(0, from); i < Math.min(to, body.size()); ++i) {
            if (body.get(i).contains("clearTypeInfoPlaceholder()")) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(List<String> body, String needle) {
        return firstMatching(body, 0, l -> l.contains(needle));
    }

    private static int firstMatching(List<String> body, int from, java.util.function.Predicate<String> p) {
        for (int i = from; i < body.size(); ++i) {
            if (p.test(body.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int count(String s, char c) {
        return (int) s.chars().filter(ch -> ch == c).count();
    }
}
