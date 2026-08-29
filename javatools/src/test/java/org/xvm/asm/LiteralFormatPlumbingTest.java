package org.xvm.asm;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constant.Format;

import org.xvm.asm.constants.LiteralConstant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Ratchet for the literal-format plumbing: every {@link Format} the compiler can produce for a
 * literal must be accepted by every stage that has to carry it.
 *
 * <p>This exists because {@code Format.TimeZone} was not. The lexer accepted a {@code TimeZone:}
 * literal ({@code Lexer.java}, {@code case "TimeZone"}), the AST handed it to the pool
 * ({@code LiteralExpression}, {@code pool.ensureLiteralConstant(Format.TimeZone, ...)}), and the
 * pool threw {@code IllegalStateException: unsupported format: TimeZone} - so any source file
 * containing one died with an internal error rather than a diagnostic. The same omission was
 * repeated in FIVE places: the pool's construction switch, the pool's {@code disassemble} switch,
 * two switches in {@link LiteralConstant}, and its format-to-type mapping. Its siblings
 * {@code Date}, {@code TimeOfDay}, {@code Time} and {@code Duration} were present in all five.</p>
 *
 * <p><b>Why a test rather than a compile-time check.</b> The obvious answer - an exhaustive switch
 * expression with no {@code default}, which javac verifies - does not work here: {@code Format} has
 * 107 constants and each of these switches legitimately handles a small subset, so exhaustiveness
 * would demand 107 cases at every site. The defect was never a missing case in an otherwise
 * complete switch; it was the SAME FACT ("TimeZone is a string-backed literal") written out in five
 * places, four of which nobody thought to update. This test is the single place that fact is
 * asserted, and it fails at whichever of the five stages a future format is missed.</p>
 */
public class LiteralFormatPlumbingTest {
    /**
     * Every format {@code LiteralExpression} converts via {@code pool.ensureLiteralConstant(...)},
     * with a representative literal string. Adding a literal format to the compiler means adding a
     * row here, and the row then checks all the stages at once.
     */
    private static Map<Format, String> literalFormats() {
        var map = new LinkedHashMap<Format, String>();
        map.put(Format.IntLiteral, "42");
        map.put(Format.FPLiteral,  "1.5");
        map.put(Format.Date,       "1999-12-31");
        map.put(Format.TimeOfDay,  "23:59:59");
        map.put(Format.Time,       "1999-12-31T23:59:59Z");
        map.put(Format.TimeZone,   "Z");
        map.put(Format.Duration,   "PT1H");
        map.put(Format.Path,       "/some/path");
        return map;
    }

    /**
     * Stage 1: the pool must build a constant for the format at all. This is the stage that threw
     * for {@code TimeZone}.
     */
    @Test
    public void poolAcceptsEveryLiteralFormatTheCompilerProduces() {
        ConstantPool pool = new FileStructure("test").getConstantPool();

        literalFormats().forEach((format, sLiteral) -> {
            Constant constant = assertDoesNotThrow(
                    () -> pool.ensureLiteralConstant(format, sLiteral),
                    () -> "ConstantPool.ensureLiteralConstant rejects " + format
                          + ", which the compiler produces for a literal of that form");
            assertInstanceOf(LiteralConstant.class, constant, () -> "for " + format);
            assertEquals(format, constant.getFormat(), () -> "for " + format);
        });
    }

    /**
     * Stage 2: the constant must know its own Ecstasy type. {@code LiteralConstant.getType()} maps
     * format to type and falls through to {@code Constant.getType()}, which THROWS
     * {@code UnsupportedOperationException}, so a format missing from that mapping is a live crash
     * rather than a silent default.
     */
    @Test
    public void everyLiteralFormatMapsToAType() {
        ConstantPool pool = new FileStructure("test").getConstantPool();

        literalFormats().forEach((format, sLiteral) -> {
            Constant constant = pool.ensureLiteralConstant(format, sLiteral);
            assertDoesNotThrow(constant::getType,
                    () -> "LiteralConstant.getType() has no mapping for " + format
                          + ", so it falls through to Constant.getType(), which throws");
        });
    }

    /**
     * Stage 3: the pool's {@code disassemble} switch is a SEPARATE list from its construction
     * switch, so a format can be constructible and still unreadable - which is exactly what
     * {@code TimeZone} was. A round trip through {@link FileStructure} cannot check this, because
     * a constant nothing references is pruned before it is ever written, so this reads the two
     * case lists out of the source and compares them.
     */
    @Test
    public void diassembleReadsBackEveryLiteralFormatThePoolCanBuild() throws IOException {
        String source     = Files.readString(constantPoolSource());
        var    unreadable = new ArrayList<Format>();

        int ofDisassemble = source.indexOf("protected void disassemble(DataInput in)");
        assertTrue(ofDisassemble > 0, "ConstantPool.disassemble(DataInput) not found");
        String disassemble = source.substring(ofDisassemble);

        for (Format format : literalFormats().keySet()) {
            if (!disassemble.contains("case " + format.name() + ":")) {
                unreadable.add(format);
            }
        }

        assertTrue(unreadable.isEmpty(),
                () -> "ConstantPool.disassemble() cannot read back " + unreadable
                      + ", so a module containing one of those literals writes but does not load."
                      + " Its construction switch and its disassemble switch are separate lists;"
                      + " both need the format.");
    }

    /**
     * Formats whose runtime value is materialised by {@code xConst}'s literal switch, which calls
     * a {@code construct(String)} on the corresponding Ecstasy class. This is a SIXTH list, in a
     * different module from the other five, and it is the one that actually broke: after the pool
     * plumbing was fixed, a {@code TimeZone:} literal compiled and then died at run time with
     * {@code Unexpected op execution failure ... op=VAR_IN}, because the runtime had no case for
     * it and {@code TimeZone} had no String constructor to call even if it had.
     */
    private static final List<Format> RUNTIME_LITERAL_FORMATS = List.of(
            Format.Time, Format.Date, Format.TimeOfDay, Format.TimeZone,
            Format.Duration, Format.Version, Format.Path);

    /**
     * Stage 4: the runtime must be able to turn the constant into a handle. A format can be
     * constructible, typed, and readable, and STILL be unusable, which is exactly the state
     * {@code TimeZone} was left in after the pool-side fix alone.
     */
    @Test
    public void runtimeMaterialisesEveryLiteralFormatItIsGiven() throws IOException {
        String source   = Files.readString(xConstSource());
        var    unusable = new ArrayList<Format>();

        for (Format format : RUNTIME_LITERAL_FORMATS) {
            if (!source.contains("case " + format.name() + ":")) {
                unusable.add(format);
            }
        }

        assertTrue(unusable.isEmpty(),
                () -> "xConst's literal switch cannot materialise " + unusable
                      + ", so a literal of that form compiles and then fails at run time with"
                      + " \"Unexpected op execution failure ... op=VAR_IN\".");
    }

    /**
     * Stage 5: the runtime path calls {@code construct(String)} on the Ecstasy class, so the class
     * must declare one. {@code TimeZone} did not - it had only {@code TimeZone(Int64 picos)} and a
     * conditional {@code of(String)} - which is why the runtime case alone was not enough.
     */
    @Test
    public void everyRuntimeLiteralClassHasAStringConstructor() throws IOException {
        Map<Format, String> sources = Map.of(
                Format.Time,      "temporal/Time.x",
                Format.Date,      "temporal/Date.x",
                Format.TimeOfDay, "temporal/TimeOfDay.x",
                Format.TimeZone,  "temporal/TimeZone.x",
                Format.Duration,  "temporal/Duration.x",
                Format.Path,      "fs/Path.x");

        var missing = new ArrayList<Format>();
        for (Map.Entry<Format, String> entry : sources.entrySet()) {
            Path path = ecstasySource(entry.getValue());
            if (!Files.exists(path)) {
                continue;
            }
            String text = Files.readString(path);
            if (!text.contains("construct(String") && !text.contains("construct " + entry.getKey().name() + "(String")) {
                missing.add(entry.getKey());
            }
        }

        assertTrue(missing.isEmpty(),
                () -> "these Ecstasy classes have no construct(String), so xConst cannot build a"
                      + " literal value for them: " + missing);
    }

    /**
     * Stage 6: {@code NativeContainer.getConstType} maps a constant to the Ecstasy class that
     * implements it, and its default throws
     * {@code LauncherException("No implementation for constant: ...")}. This was the LAST of the
     * seven places {@code TimeZone} was missing, and the one that still failed after the pool and
     * the {@code xConst} switch were both fixed - which is why this ratchet has six stages rather
     * than the three it started with.
     */
    @Test
    public void nativeContainerKnowsTheTypeOfEveryRuntimeLiteral() throws IOException {
        String source    = Files.readString(nativeContainerSource());
        var    unmapped  = new ArrayList<Format>();

        for (Format format : RUNTIME_LITERAL_FORMATS) {
            // getConstType groups its cases on one line, e.g. "case Date, TimeOfDay, Time, ...:"
            if (!source.contains(format.name() + ",") && !source.contains(format.name() + ":")) {
                unmapped.add(format);
            }
        }

        assertTrue(unmapped.isEmpty(),
                () -> "NativeContainer.getConstType has no mapping for " + unmapped
                      + ", so loading such a constant fails with \"No implementation for"
                      + " constant\" even though it compiled and the runtime switch handles it.");
    }

    private static Path nativeContainerSource() {
        Path cwd     = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java/org/xvm/runtime/NativeContainer.java");
        return Files.exists(project)
                ? project
                : cwd.resolve("javatools/src/main/java/org/xvm/runtime/NativeContainer.java");
    }

    private static Path xConstSource() {
        Path cwd     = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java/org/xvm/runtime/template/xConst.java");
        return Files.exists(project)
                ? project
                : cwd.resolve("javatools/src/main/java/org/xvm/runtime/template/xConst.java");
    }

    private static Path ecstasySource(String sRelative) {
        Path cwd = Path.of("").toAbsolutePath();
        Path here = cwd.resolve("lib_ecstasy/src/main/x/ecstasy/" + sRelative);
        return Files.exists(here)
                ? here
                : cwd.getParent().resolve("lib_ecstasy/src/main/x/ecstasy/" + sRelative);
    }

    private static Path constantPoolSource() {
        Path cwd     = Path.of("").toAbsolutePath();
        Path project = cwd.resolve("src/main/java/org/xvm/asm/ConstantPool.java");
        return Files.exists(project)
                ? project
                : cwd.resolve("javatools/src/main/java/org/xvm/asm/ConstantPool.java");
    }
}
