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
