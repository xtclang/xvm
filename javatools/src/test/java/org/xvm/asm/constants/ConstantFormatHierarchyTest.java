package org.xvm.asm.constants;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constant.Format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Pins two facts about the constant hierarchy that are easy to assume and wrong to assume.
 *
 * <p><b>1. The class hierarchy and the format hierarchy do not coincide.</b> Four constant classes
 * extend another constant class but report a DIFFERENT {@link Format}, so for those four
 * {@code x instanceof SuperConstant} and {@code x.getFormat() == Format.Super} are not the same
 * predicate - the first matches the subclass, the second does not.</p>
 *
 * <p>This is a live hazard rather than a curiosity. {@code ClassStructure}'s generic-parameter
 * visitor tests {@code getFormat() == Format.Property} and then casts to {@code PropertyConstant};
 * rewriting that as the "obviously equivalent" {@code instanceof PropertyConstant} silently widens
 * it to include {@code FormalTypeChildConstant}, and the Ecstasy library then fails to compile with
 * {@code COMPILER-145: Unresolvable type parameter(s): OuterType} - a formal type child being
 * mistaken for a generic type parameter. Nothing in the Java unit suite catches that; only
 * compiling the library does.</p>
 *
 * <p>Across the tree there are ~150 {@code instanceof}/{@code case} sites on the four superclasses
 * involved. Most intend to include the subclass. The point of this test is not that they are wrong,
 * but that the two spellings are NOT interchangeable, so no mechanical rewrite between them is
 * safe, and a NEW divergence must be a deliberate decision rather than a surprise.</p>
 *
 * <p><b>2. Every constant that can define a type is an identity or a pseudo constant.</b>
 * {@code TerminalTypeConstant}'s constructor admits exactly the formats for which
 * {@code Format.isTypeable()} is true, and every class carrying one of those formats sits in one of
 * those two trees. That is what lets {@code TypeConstant.getDefiningConstant()} be typed as their
 * union rather than as a bare {@code Constant}.</p>
 */
public class ConstantFormatHierarchyTest {
    /**
     * The four known divergences, as {@code Subclass -> Superclass}. Each entry means: the subclass
     * IS-A the superclass, but does NOT report the superclass's format.
     */
    private static final Map<String, String> KNOWN_DIVERGENCES = new LinkedHashMap<>(Map.of(
            "CastTypeConstant",        "IntersectionTypeConstant",
            "FormalTypeChildConstant", "PropertyConstant",
            "NativeRebaseConstant",    "ClassConstant",
            "RecursiveTypeConstant",   "TerminalTypeConstant"));

    @Test
    public void formatDivergencesFromTheClassHierarchyAreExactlyTheKnownFour() throws IOException {
        Map<String, ClassFacts> facts = scanConstantClasses();
        var found = new TreeSet<String>();

        facts.forEach((name, self) -> {
            if (self.format == null) {
                return;
            }
            for (String sup = self.superName; sup != null; ) {
                ClassFacts parent = facts.get(sup);
                if (parent == null) {
                    break;
                }
                if (parent.format != null) {
                    if (!parent.format.equals(self.format)) {
                        found.add(name + " -> " + sup);
                    }
                    break;
                }
                sup = parent.superName;
            }
        });

        var expected = new TreeSet<String>();
        KNOWN_DIVERGENCES.forEach((sub, sup) -> expected.add(sub + " -> " + sup));

        assertEquals(expected, found,
                "the set of constant classes whose format differs from their nearest"
                + " format-carrying superclass changed. For each entry, `x instanceof Super` and"
                + " `x.getFormat() == Format.Super` are DIFFERENT predicates, so any code switching"
                + " between those two spellings around it is a behaviour change, not a cleanup."
                + " Add the entry here only after checking the instanceof/case sites on the"
                + " superclass.");
    }

    /**
     * The invariant behind typing a defining constant as the identity/pseudo union: every constant
     * class whose format is {@link Format#isTypeable()} lives in one of those two trees.
     */
    @Test
    public void everyTypeableConstantIsAnIdentityOrPseudoConstant() throws IOException {
        var offenders = new ArrayList<String>();

        scanConstantClasses().forEach((name, self) -> {
            if (self.format == null || !isTypeable(self.format)) {
                return;
            }
            Class<?> clz;
            try {
                clz = Class.forName("org.xvm.asm.constants." + name);
            } catch (ClassNotFoundException e) {
                return;      // nested or renamed; the source scan is the looser of the two views
            }
            if (!IdentityConstant.class.isAssignableFrom(clz)
                    && !PseudoConstant.class.isAssignableFrom(clz)) {
                offenders.add(name + " (" + self.format + ")");
            }
        });

        assertTrue(offenders.isEmpty(),
                () -> "these constants can define a type but are neither an IdentityConstant nor a"
                      + " PseudoConstant: " + offenders + ". TerminalTypeConstant's constructor"
                      + " admits them (Format.isTypeable()), so getDefiningConstant() can return"
                      + " one, and every consumer that assumes the identity/pseudo union would"
                      + " break on it.");
    }

    /** Guards the scan itself: a regex that silently matches nothing would make both tests pass. */
    @Test
    public void theSourceScanActuallyFindsTheConstantClasses() throws IOException {
        Map<String, ClassFacts> facts = scanConstantClasses();

        assertTrue(facts.size() > 80,
                () -> "expected the constants package to hold many classes, scanned " + facts.size());
        assertTrue(facts.values().stream().filter(f -> f.format != null).count() > 50,
                "expected most constant classes to declare a literal format");
        assertEquals("Property", facts.get("PropertyConstant").format);
        assertEquals("FormalTypeChild", facts.get("FormalTypeChildConstant").format);
        assertEquals("PropertyConstant", facts.get("FormalTypeChildConstant").superName);
    }

    private static boolean isTypeable(String sFormat) {
        for (Format format : Format.values()) {
            if (format.name().equals(sFormat)) {
                return format.isTypeable();
            }
        }
        return false;
    }

    /** What the source tells us about one constant class. */
    private record ClassFacts(String superName, String format) {}

    private static Map<String, ClassFacts> scanConstantClasses() throws IOException {
        var map = new LinkedHashMap<String, ClassFacts>();
        try (var paths = Files.list(constantsDir())) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name   = path.getFileName().toString().replace(".java", "");
                String source = Files.readString(path);

                Matcher mExt = Pattern.compile(
                        "\\bclass\\s+" + name + "\\b[^{]*?\\bextends\\s+(\\w+)", Pattern.DOTALL)
                        .matcher(source);
                Matcher mFmt = Pattern.compile(
                        "public Format getFormat\\(\\)\\s*\\{\\s*return\\s+Format\\.(\\w+)\\s*;")
                        .matcher(source);

                map.put(name, new ClassFacts(mExt.find() ? mExt.group(1) : null,
                                             mFmt.find() ? mFmt.group(1) : null));
            }
        }
        return map;
    }

    private static Path constantsDir() {
        Path cwd     = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java/org/xvm/asm/constants");
        return Files.exists(project)
                ? project
                : cwd.resolve("javatools/src/main/java/org/xvm/asm/constants");
    }
}
