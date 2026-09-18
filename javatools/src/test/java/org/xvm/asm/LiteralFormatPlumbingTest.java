package org.xvm.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    /**
     * Stage 3: a literal of every format the pool can build must survive being written and read
     * back. The pool's construction switch and its {@code disassemble} switch are separate lists,
     * so a format can be constructible and still unreadable - which is exactly what
     * {@code TimeZone} was, and what makes a module containing one write fine and then fail to
     * load.
     *
     * <p>{@link FileStructure#writeTo} cannot be used here: it calls
     * {@code reregisterConstants(true)}, whose {@code optimize()} prunes every constant nothing
     * references, which is all of these. {@code reregisterConstants(false)} keeps them, and
     * {@code assemble} is the same writer {@code writeTo} delegates to.
     */
    @Test
    public void everyLiteralFormatSurvivesAWriteAndReadBack() throws IOException {
        Map<Format, String> literals = literalFormats();

        FileStructure file = new FileStructure("test");
        literals.forEach(file.getConstantPool()::ensureLiteralConstant);

        var bytes = new ByteArrayOutputStream();
        file.reregisterConstants(false);
        file.assemble(new DataOutputStream(bytes));

        ConstantPool reread = assertDoesNotThrow(
                () -> new FileStructure(new ByteArrayInputStream(bytes.toByteArray()))
                        .getConstantPool(),
                "ConstantPool.disassemble() could not read back a literal it had just written;"
                + " a format is missing from its disassemble switch");

        var found = new LinkedHashMap<Format, String>();
        for (Constant constant : reread.getConstants()) {
            if (constant instanceof LiteralConstant literal
                    && literals.containsKey(literal.getFormat())) {
                found.put(literal.getFormat(), literal.getValue());
            }
        }

        assertEquals(literals, found,
                "every literal written must come back with its format and value intact");
    }

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
     * Stage 1b: the two lists behind stage 1 must be the SAME list.
     * {@code ConstantPool.ensureLiteralConstant} decides which formats it hands to
     * {@link LiteralConstant}, and {@link LiteralConstant}'s constructor decides which formats it
     * will hold. Those are separate switches, so a format can sit in one and not the other, in
     * either direction, and neither javac nor stage 1 notices.
     *
     * <p>A format in the pool's list but not LiteralConstant's is an arm that can only ever throw
     * {@code IllegalStateException: unsupported format}. {@code Format.RegEx} was exactly that: the
     * pool advertised a RegEx literal that {@link LiteralConstant} has never had a case for, because
     * a regular expression is not a LiteralConstant at all - {@code RegExConstant} extends
     * {@code ValueConstant} and is built by {@code ensureRegExConstant(String, int)}, which takes
     * flags the literal path has no way to express.</p>
     *
     * <p>Neither set is written down here. Both are measured by driving every {@link Format} through
     * the two entry points, so this also fails for the next format added to one switch and forgotten
     * in the other - which is precisely what {@code Format.TimeZone} was.</p>
     *
     * <p>{@code Format.Version} is in neither set, and that is correct: {@link LiteralConstant}
     * refuses to hold one unless it is a {@code VersionConstant}, and the pool routes versions to
     * {@code ensureVersionConstant} instead.</p>
     */
    @Test
    public void poolOffersExactlyTheFormatsLiteralConstantAccepts() {
        ConstantPool pool = new FileStructure("test").getConstantPool();

        var offered  = new LinkedHashSet<Format>();
        var accepted = new LinkedHashSet<Format>();
        for (Format format : Format.values()) {
            if (poolRoutesToLiteralConstant(pool, format)) {
                offered.add(format);
            }
            if (literalConstantAccepts(pool, format)) {
                accepted.add(format);
            }
        }

        var deadArms = new LinkedHashSet<>(offered);
        deadArms.removeAll(accepted);
        var unreachable = new LinkedHashSet<>(accepted);
        unreachable.removeAll(offered);

        assertEquals(accepted, offered,
                () -> "ConstantPool.ensureLiteralConstant and LiteralConstant keep separate lists of"
                      + " the formats a LiteralConstant can hold, and they have drifted."
                      + " ensureLiteralConstant hands " + deadArms + " to LiteralConstant, whose"
                      + " constructor rejects it, so those arms can only ever throw;"
                      + " LiteralConstant accepts " + unreachable + " that ensureLiteralConstant"
                      + " has no arm for.");
    }

    /**
     * @return true iff {@code ensureLiteralConstant} routes the format into {@link LiteralConstant},
     *         whether or not LiteralConstant then accepts it - a rejection thrown from
     *         LiteralConstant's own constructor still counts as offered, because the pool did
     *         advertise the format and only the callee refused
     */
    private static boolean poolRoutesToLiteralConstant(ConstantPool pool, Format format) {
        try {
            return pool.ensureLiteralConstant(format, sampleLiteral(format), null)
                    instanceof LiteralConstant literal && literal.getFormat() == format;
        } catch (RuntimeException | AssertionError e) {
            return reachedLiteralConstantConstructor(e);
        }
    }

    /**
     * Both switches throw {@code IllegalStateException("unsupported format: " + format)} with the
     * same text, so the message cannot say which of them refused. The stack can.
     */
    private static boolean reachedLiteralConstantConstructor(Throwable e) {
        return Arrays.stream(e.getStackTrace())
                .anyMatch(frame -> LiteralConstant.class.getName().equals(frame.getClassName())
                                   && "<init>".equals(frame.getMethodName()));
    }

    /**
     * @return true iff {@link LiteralConstant} will hold a value of this format
     */
    private static boolean literalConstantAccepts(ConstantPool pool, Format format) {
        try {
            return new LiteralConstant(pool, format, sampleLiteral(format), null)
                    .getFormat() == format;
        } catch (RuntimeException | AssertionError e) {
            return false;
        }
    }

    /**
     * A plausible literal for a format, so that a rejection is about the format and not about the
     * string. Formats with no sample of their own get a value the numeric paths can parse; no
     * literal format validates its string yet, so any of them accepts it.
     */
    private static String sampleLiteral(Format format) {
        return literalFormats().getOrDefault(format, "1");
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

}
