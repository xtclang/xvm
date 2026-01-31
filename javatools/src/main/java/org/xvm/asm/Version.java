package org.xvm.asm;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.xvm.util.Handy.quotedChar;
import static org.xvm.util.Handy.quotedString;


/**
 * Represents an Ecstasy module version.
 */
public class Version
        implements Comparable<Version> {

    // ----- ReleaseCategory enum -----------------------------------------------------------------

    /**
     * Release category indicators, ordered from least stable (CI) to most stable (GA).
     * Pre-release categories are encoded as negative integers in version part lists.
     */
    public enum ReleaseCategory {
        CI(-6, "ci", "CI"), DEV(-5, "dev", "Dev"), QA(-4, "qa", "QA"),
        ALPHA(-3, "alpha"), BETA(-2, "beta"), RC(-1, "rc"), GA(0, "ga", "GA");

        public final int    code;    // negative for pre-release, 0 for GA
        public final String word;    // lowercase form for parsing
        public final String display; // display form

        ReleaseCategory(int code, String word)                 { this(code, word, word); }
        ReleaseCategory(int code, String word, String display) { this.code = code; this.word = word; this.display = display; }

        public boolean isPreRelease()                       { return code < 0; }
        public boolean isMoreStableThan(ReleaseCategory o)  { return compareTo(o) > 0; }

        private static final ReleaseCategory[] BY_CODE = new ReleaseCategory[7]; // codes -6 to 0
        static { for (var rc : values()) BY_CODE[rc.code + 6] = rc; }

        public static ReleaseCategory fromCode(int code) {
            return code >= -6 && code <= 0 ? BY_CODE[code + 6] : null;
        }

        public static ReleaseCategory fromChar(char ch) {
            return switch (Character.toLowerCase(ch)) {
                case 'a' -> ALPHA; case 'b' -> BETA; case 'c' -> CI;
                case 'd' -> DEV;   case 'q' -> QA;   case 'r' -> RC;
                default  -> null;
            };
        }
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * Default version used for error recovery or when no version is specified.
     */
    public static final Version DEFAULT = new Version("0");

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a Version from a version string.
     *
     * @param literal  the version string
     */
    public Version(@NotNull String literal) {
        var parts    = new ArrayList<Integer>();
        this.build   = parse(Objects.requireNonNull(literal), parts);
        this.literal = literal;
        this.parts   = List.copyOf(parts);
    }

    /**
     * Construct a Version from a list of version indicators.
     *
     * @param parts  the list of version indicators
     */
    public Version(@NotNull List<Integer> parts) {
        this(parts, null);
    }

    /**
     * Construct a Version from a list of version indicators and an optional build description.
     *
     * @param parts  the list of version indicators
     * @param build  optional build metadata
     */
    public Version(@NotNull List<Integer> parts, @Nullable String build) {
        Objects.requireNonNull(parts);
        this.literal = buildLiteral(parts, build);
        this.parts   = List.copyOf(parts);
        this.build   = build;
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the version number indicates a generally available release; false if the
     *         version number indicates a continuous integration build, a dev build, an alpha or
     *         beta release, or a release candidate
     */
    public boolean isGARelease() {
        return parts.stream().allMatch(p -> p >= 0);
    }

    /**
     * @return the release category of this version
     */
    public ReleaseCategory getReleaseCategory() {
        int code = parts.stream().filter(p -> p < 0).findFirst().orElse(0);
        return ReleaseCategory.fromCode(code);
    }

    /**
     * @return one of "CI", "Dev", "QA", "alpha", "beta", "rc", or "GA"
     */
    public String getReleaseCategoryString() {
        return getReleaseCategory().display;
    }

    /**
     * Determine if another version is the same version as this, or derives from this version.
     * <p/>
     * A version is either a base version, the subsequent version of another version, or a revision
     * of another version. A version number is represented as a dot-delimited string of integer
     * values; for example, version "1" is a potential base version number, version "2" is a
     * subsequent version of version "1", and version "1.1" is a revision of version 1.
     * <p/>
     * For each integer in the version string, the first integer is considered the most significant
     * version indicator, and each following integer is less significant, with the last integer
     * being the least significant version indicator. If the least significant version indicator is
     * zero, then the version is identical to a version that does not include that least significant
     * version indicator; in other words, version "1", version "1.0", and version "1.0.0" (etc.) all
     * refer to the same identical version. For purposes of comparison:
     *
     * <ul><li>The actual versions <tt>v<sub>A</sub></tt> is <b>identical to</b> the requested
     * version <tt>v<sub>R</sub></tt> iff after removing every trailing (least significant) "0"
     * indicator, each version indicator from the most significant to the least significant is
     * identical; in other words, version "1.2.1" is identical only to version "1.2.1" (which is
     * identical to version "1.2.1.0").</li>
     * <li>The actual versions <tt>v<sub>A</sub></tt> is <b>substitutable for</b> the requested
     * version <tt>v<sub>R</sub></tt> iff each version indicator of the requested version from the
     * most significant to the least significant is identical to the corresponding version indicator
     * in the actual version, or if the first different version indicator in the actual version is
     * greater than the corresponding version indicator in the requested version; in other words,
     * version "1.2", "1.2.1", and "1.2.1.7", and "1.3" are all substitutable for version "1.2", but
     *  "2.0" and "2.1" are not.</li>
     * <li>In the previous example, to use only one of the versions that begins with "1.2", the
     * requested version <tt>v<sub>R</sub></tt> should be specified as "1.2.0"; versions "1.2",
     * "1.2.1", and "1.2.1.7" are substitutes for 1.2.0, but versions "1.3", "2.0", and "2.1" are
     * not.</li>
     * </ul>
     *
     * @param that  another version
     *
     * @return true iff the specified Version is the same as or is derived from this Version
     */
    public boolean isSubstitutableFor(Version that) {
        if (this.equals(that)) {
            return true;
        }

        // check all the shared version parts (except for the last shared version part) to make
        // sure that they are identical; for example, when comparing "1.2.3" and "1.2.4", this would
        // compare both the "1" and the "2" parts, but when comparing "1.2.3" and "1.2", this would
        // only check the "1" part.
        int cThis   = this.size();
        int cThisGA = gaSize(this.parts);
        int cThat   = that.size();
        int cThatGA = gaSize(that.parts);
        int iLastGA = Math.min(cThisGA, cThatGA) - 1;
        if (iLastGA > 0 && !this.parts.subList(0, iLastGA).equals(that.parts.subList(0, iLastGA))) {
            return false;
        }

        // if this was a smaller version than that, then this cannot substitute for that
        int verDiff = iLastGA >= 0 ? this.parts.get(iLastGA) - that.parts.get(iLastGA) : 0;
        if (verDiff < 0) {
            return false;
        }

        // if this was a larger version than that, then this can sub for that if we're comparing
        // the last digit of that
        if (verDiff > 0) {
            return cThisGA >= cThatGA;
        }

        // all the shared GA digits are identical; check the non-shared digits
        if (cThisGA > cThatGA) {
            // any remaining version part number in this version higher than zero indicates this
            // could sub for that
            if (this.parts.subList(cThatGA, cThisGA).stream().anyMatch(p -> p > 0)) {
                return true;
            }
            // this could still be substitutable for that, because the GA versions are the same
        } else if (cThisGA < cThatGA) {
            // any remaining version part number in that version higher than zero indicates this
            // can NOT sub for that; the number of version parts in this is fewer than the number of
            // version parts in that, so the only way that this is substitutable for that is if all
            // subsequent version parts of that are "0"; for example, "1.2" can sub for "1.2.0.0.0"
            if (that.parts.subList(cThisGA, cThatGA).stream().anyMatch(p -> p > 0)) {
                return false;
            }
            // this could still be substitutable for that, because the GA versions are the same
        }

        // the two GA versions are identical; the only thing left to check is the non-GA information
        boolean fThisGA = cThis == cThisGA;
        boolean fThatGA = cThat == cThatGA;
        if (!fThisGA || !fThatGA) {
            // at least one is a non-GA
            // if this is GA and that is a non-GA, then this will sub for that
            // if this is non-GA and that is GA, then this can not sub for that
            if (fThisGA ^ fThatGA) {
                return fThisGA;
            }

            // they're both pre-release versions; need to compare the pre-release version parts
            int cThisNonGA   = cThis - cThisGA;
            int cThatNonGA   = cThat - cThatGA;
            int cSharedNonGA = Math.min(cThisNonGA, cThatNonGA);
            var preReleaseDiff = comparePreReleaseParts(this.parts, cThisGA, that.parts, cThatGA, cSharedNonGA);
            if (preReleaseDiff.isPresent()) {
                return preReleaseDiff.getAsInt() > 0;
            }

            // all the shared digits of the pre-release matched; check for non-shared digits of
            // one of the pre-release versions
            if (cThisNonGA != cThatNonGA) {
                // one of the pre-release versions has a sub-version
                // if this has a sub-version, then this is newer (and thus substitutable)
                // if that has a sub-version, then that is newer (and this is NOT substitutable)
                return cThisNonGA > cThatNonGA;
            }
        }

        return true;
    }

    /**
     * Compare two versions to determine if they are the same version. This is a different test than
     * the {@link #equals} method, in that two version objects are considered equal iff their
     * version strings are identical, while two versions are considered to be the same version iff
     * they are equal or the only difference between them is an addition of version parts that are
     * all zeros. For example, version "1.2" is the same version as "1.2.0" and "1.2.0.0.0.0" and
     * so on.
     *
     * @param that  another version
     *
     * @return true iff <i>this</i> Version refers to the same exact version as <i>that</i> Version
     */
    public boolean isSameAs(Version that) {
        if (this.equals(that)) {
            return true;
        }

        // check all the shared version parts to make sure that they are identical
        int cShared = Math.min(this.size(), that.size());
        if (!this.parts.subList(0, cShared).equals(that.parts.subList(0, cShared))) {
            return false;
        }

        // all remaining parts need to be "0"
        var remaining = this.size() > that.size() ? this.parts : that.parts;
        return remaining.subList(cShared, remaining.size()).stream().allMatch(p -> p == 0);
    }

    /**
     * If the version ends with ".0", return a version that does not end with ".0" but represents
     * the same version.
     *
     * @return a normalized version
     */
    public Version normalize() {
        int lastNonZero = IntStream.iterate(parts.size() - 1, i -> i > 0, i -> i - 1)
                .filter(i -> parts.get(i) != 0)
                .findFirst()
                .orElse(0);
        return lastNonZero == parts.size() - 1 ? this : new Version(parts.subList(0, lastNonZero + 1), build);
    }

    /**
     * @return the number of parts of the version
     */
    public int size() {
        return parts.size();
    }

    /**
     * @param i  specifies which part of the version to obtain
     *
     * @return the i-th part of the version
     */
    public int getPart(int i) {
        return parts.get(i);
    }

    /**
     * @return the build string if present
     */
    @SuppressWarnings("unused")
    public Optional<String> getBuildString() {
        return Optional.ofNullable(build);
    }

    /**
     * @return this Version but without build metadata
     */
    public Version withoutBuildString() {
        return build == null ? this : new Version(parts);
    }

    /**
     * @return the parts as an immutable list
     */
    @NotNull
    public List<Integer> asList() {
        return parts;
    }

    // ----- Comparable methods --------------------------------------------------------------------

    @Override
    public int compareTo(@NotNull Version that) {
        Objects.requireNonNull(that);
        return IntStream.range(0, Math.min(this.size(), that.size()))
                .map(i -> this.parts.get(i) - that.parts.get(i))
                .filter(diff -> diff != 0)
                .findFirst()
                .orElseGet(() -> this.size() - that.size());
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString() {
        return literal;
    }

    @Override
    public int hashCode() {
        return literal.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Version v && literal.equals(v.literal);
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Build the literal string representation from parts.
     */
    private static String buildLiteral(List<Integer> parts, @Nullable String build) {
        var sb   = new StringBuilder();
        int size = parts.size();

        for (int i = 0; i < size; i++) {
            int part = parts.get(i);
            if (part < 0) {
                // Pre-release indicator
                var category = ReleaseCategory.fromCode(part);
                if (i > 0) {
                    sb.append('-');
                }
                sb.append(category != null ? category.word : "?");
                if (i + 1 < size) {
                    sb.append(parts.get(++i));
                }
            } else {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append(part);
            }
        }

        if (build != null && !build.isEmpty()) {
            sb.append('+').append(build);
        }
        return sb.toString();
    }

    // ----- parsing -------------------------------------------------------------------------------
    // TODO: IllegalStateException is wrong for parse errors - should be IllegalArgumentException or
    //       a custom ParseException. Also, parsing logic should be delegated to a separate parser
    //       class rather than embedded in Version itself.

    /** Shared parsing state to avoid returning multiple values. */
    private static int parseIndex;

    @Nullable
    private static String parse(String literal, List<Integer> parts) {
        int len = literal.length();
        parseIndex = 0;

        // Parse numeric version parts (e.g., "1.2.3")
        boolean needNumber = false;
        while (parseIndex < len) {
            char ch = literal.charAt(parseIndex);
            if (Character.isDigit(ch)) {
                parts.add(parseNumber(literal));
                needNumber = false;
            } else if (ch == '.' || ch == '-') {
                if (needNumber || parseIndex == 0) {
                    throw error(literal, "number expected; '%c' found".formatted(ch));
                }
                parseIndex++;
                needNumber = true;
            } else if (Character.isLetter(ch) || ch == '+') {
                needNumber = false;
                break;
            } else {
                throw error(literal, "unknown version component " + quotedString(literal.substring(parseIndex)));
            }
        }

        if (needNumber) {
            throw error(literal, "number expected; " + quotedString(literal.substring(parseIndex) + " found"));
        }

        // Parse pre-release indicator (alpha, beta, rc, etc.)
        if (parseIndex < len && literal.charAt(parseIndex) != '+') {
            var category = ReleaseCategory.fromChar(literal.charAt(parseIndex));
            if (category == null) {
                throw error(literal, "illegal character " + quotedChar(literal.charAt(parseIndex)));
            }

            // Extract the word and validate
            int wordStart = parseIndex;
            while (parseIndex < len && Character.isLetter(literal.charAt(parseIndex))) {
                parseIndex++;
            }
            var word = literal.substring(wordStart, parseIndex);
            if (word.length() > 1 && !word.equalsIgnoreCase(category.word)) {
                throw error(literal, "expected \"%s\" but encountered \"%s\"".formatted(category.word, word));
            }
            parts.add(category.code);

            // Optional trailing number (e.g., "beta2")
            if (parseIndex < len && (literal.charAt(parseIndex) == '.' || literal.charAt(parseIndex) == '-')) {
                parseIndex++;
                if (parseIndex >= len || !Character.isDigit(literal.charAt(parseIndex))) {
                    throw error(literal, "expected trailing version; " +
                            (parseIndex < len ? quotedChar(literal.charAt(parseIndex)) : "end of string") + " found");
                }
            }
            if (parseIndex < len && Character.isDigit(literal.charAt(parseIndex))) {
                parts.add(parseNumber(literal));
            }
        }

        // Parse build string (e.g., "+build.123")
        String build = null;
        if (parseIndex < len) {
            if (literal.charAt(parseIndex) == '+') {
                build = literal.substring(parseIndex + 1);
                if (!build.matches("[A-Za-z0-9\\-.]*")) {
                    throw error(literal, "illegal build string \"%s\"; only A-Z, a-z, 0-9, '-', and '.' are permitted".formatted(build));
                }
            } else {
                throw error(literal, "invalid trailing string: " + quotedString(literal.substring(parseIndex)));
            }
        }

        if (parts.isEmpty()) {
            throw error(literal, "no version number");
        }

        return build;
    }

    private static int parseNumber(String literal) {
        int n = 0;
        while (parseIndex < literal.length() && Character.isDigit(literal.charAt(parseIndex))) {
            if (n > MAX_VERSION_NUMBER) {
                throw error(literal, "version number overflow");
            }
            n = n * 10 + (literal.charAt(parseIndex++) - '0');
        }
        return n;
    }

    private static IllegalStateException error(String literal, String message) {
        return new IllegalStateException("illegal version string \"%s\": %s".formatted(literal, message));
    }

    /**
     * Calculate the number of GA (generally available) version parts. Non-GA indicators like
     * alpha, beta, rc are represented by negative numbers and appear at the end of the parts list,
     * possibly followed by a numeric sub-version (e.g., "beta2" is [-2, 2]).
     *
     * @param parts  the version parts list
     *
     * @return the number of GA parts (excluding any trailing pre-release indicator and its version)
     */
    private static int gaSize(List<Integer> parts) {
        for (int i = 0, size = parts.size(); i < size; i++) {
            if (parts.get(i) < 0) {
                return i;
            }
        }
        return parts.size();
    }

    /**
     * Compare pre-release parts between two version part lists.
     *
     * @param thisParts   the first version's parts list
     * @param thisOffset  the starting index in thisParts
     * @param thatParts   the second version's parts list
     * @param thatOffset  the starting index in thatParts
     * @param count       the number of parts to compare
     *
     * @return the first non-zero difference, or empty if all compared parts are equal
     */
    private static OptionalInt comparePreReleaseParts(List<Integer> thisParts, int thisOffset,
                                                      List<Integer> thatParts, int thatOffset, int count) {
        for (int i = 0; i < count; i++) {
            int diff = thisParts.get(thisOffset + i) - thatParts.get(thatOffset + i);
            if (diff != 0) {
                return OptionalInt.of(diff);
            }
        }
        return OptionalInt.empty();
    }

    // ----- fields --------------------------------------------------------------------------------

    private static final int MAX_VERSION_NUMBER = 200_000_000;

    /**
     * The original version string literal as provided to the constructor or reconstructed from parts.
     */
    @NotNull
    private final String literal;

    /**
     * The version parts as an immutable list of integers. Positive values are version numbers,
     * negative values are pre-release indicators (see PREFIX).
     */
    @NotNull
    private final List<Integer> parts;

    /**
     * The build string (the part after '+' in semver), or null if none.
     */
    @Nullable
    private final String build;
}
