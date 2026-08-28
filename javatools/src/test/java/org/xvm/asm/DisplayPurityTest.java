package org.xvm.asm;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * The enforcement ratchet for the side-effect-free display contract
 * (docs/reentrancy/plans/side-effect-free-tostring.md).
 *
 * <p>A display method - {@code toString()}, any {@code toString(...)} overload, {@code
 * getValueString()}, {@code getDescription()}, {@code getPathString()} - must be PURE: Java (and an
 * IDE debugger) calls these implicitly, so rendering a value must not force lazy caches, intern
 * constants into a {@code ConstantPool}, resolve types with write-back, read ambient thread-local
 * context, or allocate owner-bearing runtime objects. Merely inspecting a value in the Variables view
 * must not mutate the very state being inspected.</p>
 *
 * <p>This test greps the {@linkplain #BANNED banned-callee tokens} inside display-method bodies and
 * compares the result against {@link #BASELINE} - the set of violations that still exist today (the
 * plan's slices 2-7). It therefore:</p>
 * <ul>
 *   <li><b>fails on a NEW violation</b> - a display method that gained an impure callee; and</li>
 *   <li><b>fails on a STALE baseline entry</b> - a violation that was fixed but not removed from the
 *       baseline, so the gate ratchets tighter with every slice instead of silently rotting.</li>
 * </ul>
 *
 * <p>Scope note: this scans the text of each display body, not transitively through its callees. A
 * safe wrapper (e.g. {@code GenericHandle.peekField}, which checks {@code isFieldLayoutComputed()}
 * before reading) is therefore intentionally not flagged - that is the sanctioned "peek, never force"
 * pattern.</p>
 */
public class DisplayPurityTest {
    /** Display methods whose bodies must satisfy the purity contract. */
    private static final Pattern DISPLAY_METHOD = Pattern.compile(
            "(?:@Override\\s+)?p(?:ublic|rotected)\\s+(?:static\\s+)?(?:final\\s+)?String\\s+"
            + "(toString|getValueString|getDescription|getPathString)\\s*\\([^)]*\\)\\s*\\{");

    /** Banned callees, per the plan's banned-callee list. */
    private static final List<Banned> BANNED = List.of(
            // pool interning / registration
            new Banned("ensure*Constant",   "\\.ensure[A-Z]\\w*Constant\\("),
            new Banned("implicitIdentity",  "getImplicitlyImportedIdentity\\("),
            new Banned("canonicalPoolType", "\\b(?:typeFunction|typeObject|clzOp|clzRO|clzOverride|clzInject)\\(\\)"),
            // lazy forcing
            new Banned("ensureTypeInfo",    "ensureTypeInfo\\("),
            new Banned("ensureChain",       "ensureOptimizedMethodChain\\("),
            new Banned("ensureCode/getOps", "ensureCode\\(|\\bgetOps\\("),
            new Banned("fieldLayout",       "\\bfieldLayout\\(\\)|getFieldInfo\\(|\\bgetField\\("),
            new Banned("enumInfo",          "\\benumInfo\\(\\)|getNameByOrdinal\\("),
            new Banned("annotationArrays",  "getPropertyAnnotations\\(|buildAnnotationArrays\\("),
            new Banned("sourceNormalize",   "\\bnormalize\\(|getLineCount\\("),
            // resolution with write-back
            new Banned("ensureResolved",    "ensureResolvedConstant\\("),
            new Banned("getAnnotationClass","getAnnotationClass\\("),
            new Banned("resolve*",          "resolveTypedefs\\(|resolveGenerics\\("),
            new Banned("isA/relation",      "\\.isA\\(|calculateRelation\\("),
            // ambient context
            new Banned("ambientContext",    "ServiceContext\\.getCurrentContext\\("),
            new Banned("ambientPool",       "ConstantPool\\.(?:getCurrentPool|withPool)\\("),
            // runtime allocation
            new Banned("makeHandle",        "xException\\.makeHandle\\(|Utils\\.translate\\("),
            // known-impure HELPERS named by the inventory. The scan is textual, not transitive, so a
            // display body that delegates its impurity to one of these named helpers is flagged by
            // the helper's own name.
            new Banned("toSafeString",      "toSafeString\\("),
            new Banned("annotationFlags",   "isExplicitAbstract\\(|isExplicitOverride\\(|isExplicitReadOnly\\(|\\bisInjected\\("),
            new Banned("containsAnnotation","containsAnnotation\\("),
            new Banned("reportUnimplemented","reportUnimplemented\\("),
            new Banned("handleDataType",    "getDataType\\("));

    /**
     * Known violations that still exist (plan slices 2-7). Format: {@code path#method:banned}.
     * Fixing a site means DELETING its line here - the test fails if a baseline entry no longer
     * violates, which is what makes this a ratchet rather than a snapshot.
     */
    private static final Set<String> BASELINE = Set.of(
            "org/xvm/asm/Annotation.java#getDescription:getAnnotationClass",
            "org/xvm/asm/Annotation.java#getValueString:getAnnotationClass",
            "org/xvm/asm/Component.java#toString:resolve*",
            "org/xvm/asm/MethodStructure.java#getDescription:sourceNormalize",
            "org/xvm/asm/ast/BinaryAST.java#toString:reportUnimplemented",
            "org/xvm/runtime/template/_native/reflect/xRTType.java#toString:handleDataType",
            "org/xvm/runtime/template/annotations/xFuture.java#toString:toSafeString");

    // Known coverage gap (deliberate): xEnum.EnumHandle.toString forces the lazy EnumInfo through a
    // plain getName() call. "getName(" is far too common a token to ban globally, so that site is not
    // machine-detectable here; it is tracked in the inventory doc and closes with plan slice 5.

    @Test
    public void displayMethodsDoNotRegressIntoImpurity() throws IOException {
        Set<String> found = scan();

        var added = new TreeSet<>(found);
        added.removeAll(BASELINE);
        assertTrue(added.isEmpty(),
                "display method(s) gained an impure callee - a toString()/getValueString() must not "
                + "force, intern, resolve, or read ambient state (see "
                + "docs/reentrancy/plans/side-effect-free-tostring.md):\n  "
                + String.join("\n  ", added));

        var stale = new TreeSet<>(BASELINE);
        stale.removeAll(found);
        assertTrue(stale.isEmpty(),
                "baseline entr(ies) no longer violate - DELETE them from BASELINE so the gate "
                + "ratchets tighter:\n  " + String.join("\n  ", stale));
    }

    // ----- scanner -------------------------------------------------------------------------------

    private static Set<String> scan() throws IOException {
        Path root = sourceRoot();
        var  hits = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel  = root.relativize(file).toString();
                String text = sanitize(Files.readString(file));

                Matcher m = DISPLAY_METHOD.matcher(text);
                while (m.find()) {
                    String body = body(text, m.end() - 1);
                    for (Banned banned : BANNED) {
                        if (banned.pattern().matcher(body).find()) {
                            hits.add(rel + '#' + m.group(1) + ':' + banned.name());
                        }
                    }
                }
            }
        }
        return hits;
    }

    /**
     * Blank out comments and string/char literals (preserving length and newlines) so braces and
     * banned tokens appearing inside them neither confuse brace matching nor raise false positives.
     */
    private static String sanitize(String src) {
        var sb = new StringBuilder(src);
        for (int i = 0, c = sb.length(); i < c; i++) {
            char ch = sb.charAt(i);
            int  end;
            if (ch == '/' && i + 1 < c && sb.charAt(i + 1) == '/') {
                end = sb.indexOf("\n", i);
                end = end < 0 ? c : end;
            } else if (ch == '/' && i + 1 < c && sb.charAt(i + 1) == '*') {
                end = sb.indexOf("*/", i);
                end = end < 0 ? c : end + 2;
            } else if (ch == '"' || ch == '\'') {
                end = i + 1;
                while (end < c && sb.charAt(end) != ch) {
                    end += sb.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(end + 1, c);
            } else {
                continue;
            }
            for (int j = i; j < end; j++) {
                if (sb.charAt(j) != '\n') {
                    sb.setCharAt(j, ' ');
                }
            }
            i = end - 1;
        }
        return sb.toString();
    }

    /** @param ofOpen  index of the method body's opening brace
     *  @return the body text, brace-matched */
    private static String body(String text, int ofOpen) {
        int depth = 0;
        for (int i = ofOpen, c = text.length(); i < c; i++) {
            char ch = text.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}' && --depth == 0) {
                return text.substring(ofOpen, i + 1);
            }
        }
        return text.substring(ofOpen);
    }

    private static Path sourceRoot() {
        var path = Path.of("src/main/java");
        return Files.isDirectory(path) ? path : Path.of("javatools/src/main/java");
    }

    private record Banned(String name, String regex) {
        Pattern pattern() {
            return Pattern.compile(regex);
        }
    }

    /** Helper for seeding {@link #BASELINE}: prints the current findings as pasteable lines. */
    public static void main(String[] args) throws IOException {
        var lines = new ArrayList<String>();
        for (String hit : scan()) {
            lines.add("            \"" + hit + "\",");
        }
        System.out.println(String.join("\n", lines));
        System.out.println("// total: " + lines.size());
    }
}
