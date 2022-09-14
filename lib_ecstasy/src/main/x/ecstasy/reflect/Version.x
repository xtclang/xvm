/**
 * The Version class is used to specify a module version. A version is a sequence of version
 * indicators (typically numbers), arranged from most "major" to most "minor". For example, for
 * the prototypical version number "1.0", the major version is "1" and the minor version is "0".
 *
 * A version number indicates one of: a _base_ version, a _subsequent_ version of a version, or
 * a _revision_ of a version. A version number is represented as a dot-delimited string of integer
 * values; for example, version "1" is a potential base version number, version "2" is a subsequent
 * version of version "1", and version "1.1" is a revision of version "1".
 *
 * For each integer in a version number, the first integer is considered the most significant
 * version indicator, and each following integer is less significant, with the last integer
 * being the least significant version indicator. If the least significant version indicator is
 * zero, then the version is identical to a version that does not include that least significant
 * version indicator; in other words, version "1", version "1.0", and version "1.0.0" (etc.) all
 * refer to the same identical version. For purposes of comparison:
 *
 * * The actual version `v<sub>A</sub>` is **identical to** the requested version `v<sub>R</sub>`
 *   iff after removing every trailing (least significant) "0" indicator, each version indicator
 *   from the most significant to the least significant is identical; in other words, version
 *   "1.2.1" is identical only to version "1.2.1" (which is identical to version "1.2.1.0").
 *
 * * The actual version `v<sub>A</sub>` is **substitutable for** the requested version
 *   `v<sub>R</sub>` iff each version indicator of the requested version from the most
 *   significant to the least significant is identical to the corresponding version indicator
 *   in the actual version, or if the first difference encountered is on the least significant
 *   version indicator of the requested version, and the version indicator in the actual version is
 *   greater than the version indicator in the requested version; in other words, version "1.2",
 *   "1.2.1", "1.2.1.7", "1.3", and "1.7.5" are substitutable for version "1.2", but "2.0", "2.1",
 *   and "3" are **not** substitutable for version "1.2".
 *
 * * In the previous example, to use only one of the versions that begins with "1.2", the
 *   requested version `v<sub>R</sub>` should be specified as "1.2.0"; versions "1.2",
 *   "1.2.1", and "1.2.1.7" are substitutes for 1.2.0, but **not** versions "1.3", "2.0", and "2.1".
 *
 * * Pre-release version numbers are encoded specially, and impact the substitutability rules
 *   significantly. The version "1.2beta3" is encoded as "1.2.-2.3"; it is substitutable for version
 *   "1.1", but it is explicitly **not** substitutable for version "1.2" -- because it is a
 *   pre-release of version "1.2". However, version "1.2" _is_ substitutable for version "1.2beta3".
 *   The pre-release indicator means that the immediately preceding version indicator is _less than_
 *   its value indicates; for example, version "1.2beta3" is less than (precedes) version "1.2", but
 *   it is greater than version "1.2alpha", "1.2alpha7", "1.2beta" and version "1.2beta2", and it is
 *   less than version "1.2beta4" and version "1.2rc".
 *
 * The Version design supports [Semantic Versioning 2.0.0] (https://semver.org/), and adherence
 * to that specification is encouraged. However, the use of Semantic Versioning is a choice, and
 * while this Version class does _support_ Semantic Versioning, it does not _enforce_ it.
 * Additionally, this design constrains the use of alpha-numeric forms in two different ways:
 *
 * * Only six alpha-numeric forms are supported ("CI", "Dev", "QC", "alpha", "beta", and "rc"),
 *   with the first three representing internal versions and the remainder representing publishable
 *   versions. (The use of uppercase names for the first three is not accidental; ASCII uppercase
 *   precedes ASCII lowercase, which achieves compliance with the ASCII ordering rules specified by
 *   the Semantic Versioning specification.)
 *
 * * A Version can only contain **one** of the alpha-numeric pre-release specifiers, and it must
 *   occur at the end of the Version with an optional numeric counter appended; i.e. there can be
 *   no subsequent "dot" indicators after the alpha-numeric pre-release specifier. For example,
 *   "2.0.0-alpha" and "2.0.0-beta3" are both legal, but neither "2-alpha-beta3" nor "2.0.0-beta3.5"
 *   are legal.
 *
 * As specified by Semantic Versioning, build metadata is appended to the end of the version string
 * after a plus sign. Build metadata is retained by, but ignored by the Version class. The examples
 * from the Semantic Versioning 2.0.0 specification are all legal Version examples:
 * "1.0.0-alpha+001", "1.0.0+20130313144700", "1.0.0-beta+exp.sha.5114f85". The Semantic Versioning
 * specification states that only A..Z, a..z, 0..9, and '-' may occur in the build metadata, but the
 * examples given also include '.', so Ecstasy considers the '.' to be legal in build metadata.
 */
const Version
        implements UniformIndexed<Int, Version>
        implements Sliceable<Int>
        implements Sequential
        implements Destringable
    {
    // ----- Version Form --------------------------------------------------------------------------

    /**
     * Each part of a version is called a version indicator, and that version indicator has a Form.
     * The overwhelmingly prevalent form of version indicator is the `Number` form.
     */
    enum Form(String text, Int equiv)
        {
        /**
         * Continuous Integration (CI) version. This is an "internal" version, i.e. not intended
         * for distribution. Automated build tools are expected to use a CI version with a counter
         * to identify builds; for example, a the 47th CI build for version 2.7.1 should be labeled
         * as "2.7.1-CI47"
         */
        CI    ("CI",    -6),
        /**
         * Development (or "engineering") version. This is an "internal" version, i.e. not intended
         * for distribution. In the absence of a Version being specified, the Ecstasy compiler will
         * simply use "Dev" as the default version when it emits a module.
         */
        Dev   ("Dev"  , -5),
        /**
         * Quality Control (QC) version. This is an "internal" version, i.e. not intended for
         * distribution. It is expected that when a Dev or CI version is selected for testing,
         * it will be stamped with a QC version. Development and integration builds are extremely
         * frequent, and tend to be discarded with a high degree of frequency; to ensure the
         * reproducibility of logged errors, a QC version is expected to be archived at least
         * until the errors are confirmed fixed or reproducible in a later QC build, or when an
         * external release occurs in which those errors are reproducible.
         *
         * (The use of "QC" instead of "QA" is purposeful, and aligns with CMM and ISO-9000.)
         */
        QC    ("QC"   , -4),
        /**
         * Alpha (α) version. An alpha version usually indicates an extremely early pre-release
         * version that is selectively published to obtain engineering feedback. An alpha is an
         * external version, but it is not a generally available (GA) version.
         */
        Alpha ("alpha", -3),
        /**
         * Beta (β) version. A beta version usually indicates a pre-release version that is
         * feature complete (or nearly so), and approaching a GA release. A beta version is
         * selectively published to obtain engineering, quality control, and end user feedback.
         * A beta is an external version, but it is not a generally available (GA) version.
         */
        Beta  ("beta" , -2),
        /**
         * Release Candidate (RC) version. A release candidate version indicates a pre-release
         * version that is being evaluated as a potential version to publish as the generally
         * available (GA) release version. A release candidate is an external version, but it is
         * not a generally available (GA) version.
         */
        RC    ("rc"   , -1),
        /**
         * An integer version indicator.
         */
        Number("#"    ,  0)
        }

    /**
     * Return the Form for a specified name.
     *
     * Note: this implementation is purposefully permissive, allowing "1.2-a" instead of "1.2-alpha".
     */
    static conditional Form byText(String name)
        {
        if (name.size == 0)
            {
            return False;
            }

        return switch (name[0])
            {
            case 'c', 'C': (True, CI);
            case 'd', 'D': (True, Dev);
            case 'q', 'Q': (True, QC);
            case 'a', 'A': (True, Alpha);
            case 'b', 'B': (True, Beta);
            case 'r', 'R': (True, RC);
            default      : False;
            };
        }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a Version from a String representation.
     *
     * @param version  a legal version string, such as one emitted by `Version.toString()`
     */
    @Override
    construct(String version)
        {
        // check for "+" (start of build string)
        Int     end   = version.size-1;
        String? build = Null;
        if (Int buildStart := version.indexOf('+'))
            {
            if (buildStart < end)
                {
                build = version[buildStart+1..end];
                }
            end = buildStart-1;
            }

        // start at the end, walking backwards until we encounter:
        //   "." (version delimiter)
        //   "-" (precedes non-numeric version indicator)
        //   a shift from numbers to letters, or letters to numbers
        //   the beginning of the string
        Boolean  fAnyChars = False;
        Boolean  fAnyNums  = False;
        Int      start     = 0;
        Version? parent    = Null;
        scan: for (Int of = end; of >= 0; --of)
            {
            switch (Char ch = version[of])
                {
                case '.':
                case '-':
                    if (of == 0)
                        {
                        throw new IllegalArgument("version (" + version
                                + ") must not begin with a version delimiter (" + ch + ")");
                        }
                    parent = new Version(version[0 ..< of]);
                    start  = of+1;
                    break scan;

                case 'A'..'Z':
                case 'a'..'z':
                    if (fAnyNums)
                        {
                        // something like "rc2" needs to stop rewinding after the "2"
                        parent = new Version(version[0..of]);
                        start  = of+1;
                        break scan;
                        }

                    fAnyChars = True;
                    break;

                case '0'..'9':
                    if (fAnyChars)
                        {
                        // there should be a "-" before the alphabetic characters, but it's not
                        // strictly required
                        parent = new Version(version[0..of]);
                        start  = of+1;
                        break scan;
                        }

                    fAnyNums = True;
                    break;
                }
            }

        if (fAnyChars)
            {
            String name = version[start..end];
            if (Form form := byText(name))
                {
                construct Version(parent, form, build);
                }
            else
                {
                throw new IllegalArgument("Invalid version indicator: \"" + name + "\"");
                }
            }
        else if (fAnyNums)
            {
            Int num = new IntLiteral(version[start..end]);
            construct Version(parent, num, build);
            }
        else
            {
            throw new IllegalArgument("Invalid version string: \"" + version + "\"");
            }
        }

    /**
     * Construct a Version that is a revision of another Version.
     *
     * @param parent  the version that this is a revision of
     * @param number  the version indicator for the revision
     * @param build   an optional build metadata string, comprised only of ASCII alphanumerics and
     *                hyphen `[0-9A-Za-z-]`
     */
    construct(Version? parent, Int number, String? build = Null)
        {
        construct Version(parent, Number, number, build);
        }

    /**
     * Construct a pre-release Version.
     *
     * @param parent  the version that this is a pre-release version of
     * @param form    the pre-release version indicator
     * @param build   an optional build metadata string, comprised only of ASCII alphanumerics and
     *                hyphen `[0-9A-Za-z-]`
     */
    construct(Version? parent, Form form, String? build = Null)
        {
        assert form != Number;
        construct Version(parent, form, form.equiv, build);
        }

    /**
     * Internal: Construct a Version.
     *
     * @param parent  the version that this is a pre-release version of
     * @param form    the pre-release version indicator
     * @param number  the version indicator for the revision
     * @param build   an optional build metadata string, comprised only of ASCII alphanumerics and
     *                hyphen `[0-9A-Za-z-]`
     */
    private construct(Version? parent, Form form, Int number, String? build = Null)
        {
        // only one pre-release designation can be used in a Version
        assert form == Number || parent?.GA;

        // version indicators are >= 0 except for the pre-defined forms
        assert form == Number ? number >= 0 : number == form.equiv;

        // for a non-GA parent, only a version number indicator can be added, and only if the last
        // indicator of the parent is the pre-release indicator
        assert parent?.GA || parent.form != Number;

        for (Char ch : build?)
            {
            switch (ch)
                {
                case '0'..'9':
                case 'A'..'Z':
                case 'a'..'z':
                case '-':
                case '.':
                    break;
                default:
                    throw new IllegalArgument("build metadata contains illegal character: " + ch);
                }
            }

        this.parent = parent;
        this.number = number;
        this.build  = build;
        this.size   = 1 + (parent?.size : 0);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The Version that this Version is a revision of.
     */
    Version? parent;

    /**
     * The form of this Version element.
     */
    @RO Form form.get()
        {
        return switch (number)
            {
            case -6: CI;
            case -5: Dev;
            case -4: QC;
            case -3: Alpha;
            case -2: Beta;
            case -1: RC;
            default: Number;
            };
        }

    /**
     * The version number of this Version element.
     */
    Int number;

    /**
     * Optional build information, which is retained but ignored by the version logic.
     */
    String? build;

    /**
     * The number of Version elements that make up this Version.
     */
    Int size;

    /**
     * True iff the version information denotes a "generally available" version.
     */
    Boolean GA.get()
        {
        return form == Number && (parent?.form == Number : True);
        }


    // ----- operations ----------------------------------------------------------------------------

    /**
     * Determine if this is a pre-release, and if it is, determine the version that it is a
     * pre-release of. Since it is possible that the version number (that this is a pre-release of)
     * is not specified, the result may be `Null`.
     *
     * @return if this is a pre-release version
     * @return (conditional) the `Version` that this is a pre-release of, or `Null` if this
     *         pre-release does not have a specific version number that it is a pre-release of
     *         (e.g. `v:1.2` for "1.2-beta3")
     * @return (conditional) the form of the pre-release (e.g. `Beta` for "1.2-beta3")
     * @return (conditional) the number of the pre-release (e.g. `3` for "1.2-beta3")
     */
    conditional (Version?, Form, Int) prereleaseOf()
        {
        if (form != Number)
            {
            return True, parent, form, 0;
            }

        if (Version parent ?= this.parent, parent.form != Number)
            {
            return True, parent.parent, parent.form, this.number;
            }

        return False;
        }

    /**
     * Simplify the version, if possible, by removing any redundant or ignored information without
     * changing the meaning of the version.
     *
     * @return  the resulting normalized version
     */
    Version normalize()
        {
        if (number == 0)
            {
            return parent?.normalize();
            }

        return build == Null
                ? this
                : new Version(parent, form, number, Null);
        }

    /**
     * Determine if this version satisfies the specified required version.
     *
     * For two GA versions, the one is substitutable for the other iff each version indicator of the
     * requested version from the most significant to the least significant is identical to the
     * corresponding version indicator in the actual version, or if the first difference encountered
     * is on the least significant version indicator of the requested version, and the corresponding
     * version indicator in the actual version is greater than the version indicator in the
     * requested version; in other words, version "1.2", "1.2.1", "1.2.1.7", "1.3", and "1.7.5" are
     * all substitutable for version "1.2", but "2.0", "2.1", and "3" are not. To use only one of
     * the versions that begins with "1.2", the requested version should be specified as "1.2.0";
     * versions "1.2", "1.2.1", and "1.2.1.7" are substitutes for 1.2.0, but **not** versions "1.3",
     * "2.0", and "2.1".
     *
     * Pre-release version numbers are encoded specially, and impact the substitutability rules
     * significantly. The version "1.2beta3" is encoded as "1.2.-2.3"; it is substitutable for
     * version "1.1", but it is explicitly **not** substitutable for version "1.2" -- because it is
     * a pre-release of version "1.2". However, version "1.2" _is_ substitutable for version
     * "1.2beta3". The pre-release indicator means that the immediately preceding version indicator
     * is _less than_ its value indicates; for example, version "1.2beta3" is less than (precedes)
     * version "1.2", but it is greater than version "1.2alpha", "1.2alpha7", "1.2beta", and version
     * "1.2beta2", and it is less than version "1.2beta4" and version "1.2rc".
     *
     * @param that  the requested version
     *
     * @return True iff this version can be used as a match for the requested version based on the
     *         rules of version substitutability
     */
    Boolean satisfies(Version that)
        {
        // if the versions are equal, then the answer is obvious
        if (this == that)
            {
            return True;
            }

        switch (this.GA, that.GA)
            {
            case (False, False):
                // "this" is a pre-release, and "that" is a pre-release, so "this" satisfies "that"
                // if "this" satisfies the GA release of "that", or if the GA release of "this" and
                // "that" are the same, and the pre-release of "this" is greater than the
                // pre-release of "that"
                assert (Version? thisGA, Form thisForm, Int thisNum) := this.prereleaseOf();
                assert (Version? thatGA, Form thatForm, Int thatNum) := that.prereleaseOf();

                // for example, 1.3-beta2 satisfies 1.2-rc7
                if (thisGA != Null && thatGA == Null || this.satisfies(thatGA?))
                    {
                    return True;
                    }

                // for example, 1.3-beta2 does NOT satisfy 1.3-rc7
                if (thisGA == thatGA)
                    {
                    return thisForm > thatForm || thisForm == thatForm && thisNum >= thatNum;
                    }

                return False;

            case (False, True):
                // "this" is a pre-release, and "that" is GA, so strip off the pre-release version
                // info (to pretend it's a GA), and if what is left is equal to "that", then "this"
                // is a pre-release of "that" (and thus does NOT satisfy "that"); otherwise, if the
                // remainder (GA) of "this" satisfies "that", then the pre-release "this" also
                // satisfies "that"
                assert Version? thisGA := this.prereleaseOf();
                return thisGA != Null && thisGA != that && thisGA.satisfies(that);

            case (True, False):
                // "this" is a GA, and "that" is a pre-release, so "this" satisfies "that" iff
                // "this" satisfies the GA version of "that"; for example, both "1.3" and "1.4"
                // satisfy the request for "1.3-beta"
                assert Version? thatGA := that.prereleaseOf();
                return thatGA == Null || this.satisfies(thatGA);

            case (True, True):
                // if this version number is shorter than that version number, then this version
                // cannot satisfy that version; consider these examples:
                //
                // this    that   result
                // -----   -----  -------------
                // 2       2.6    nope, version 2 is older
                // 3       2.6    nope, version 3 is not related to version 2.6
                // 1.2     1.2.1  nope
                // 1.3     1.2.1  nope
                // 1.2     1.2.0  YES! -- except we would have already found this above (this==that)
                if (this.size < that.size)
                    {
                    return False;
                    }

                // the digits left-to-right from "this" need to match the digits from "that", except
                // that the last digit of "this" may be greater than the last digit of "that"
                //
                // thisP    thatP  result
                // ------   -----  -------------
                // 2.5...   2.6    No
                // 1.6...   2.6    No
                // 2.6...   2.6    Yes
                // 3.6...   2.6    No
                // 2.7...   2.6    Yes
                Version thisParent = this;
                Version thatParent = that;
                for (Int i = 0, Int c = this.size - that.size; i < c; ++i)
                    {
                    thisParent = thisParent.parent ?: assert;
                    }
                assert thisParent.size == thatParent.size;

                // now that we got rid of the "..." (the two versions are now of equal number of
                // digits), the rightmost digit of this must be equal or greater than the rightmost
                // digit of that
                if (thisParent.number < thatParent.number)
                    {
                    return False;
                    }

                // all other digits must be equal
                while (thisParent ?= thisParent.parent, thatParent ?= thatParent.parent)
                    {
                    if (thisParent.number != thatParent.number)
                        {
                        return False;
                        }
                    }
                return True;
            }
        }


    // ----- Sequential methods --------------------------------------------------------------------

    @Override
    conditional Version prev()
        {
        return form == Number && number > 0
                ? (True, new Version(parent, form, number-1, Null))
                : False;
        }

    @Override
    conditional Version next()
        {
        return True, form == Number
                ? new Version(parent, form, number+1, Null)
                : new Version(this, Number, 1, Null);
        }

    @Override
    Int stepsTo(Version that)
        {
        // common scenario: two versions with different ending number but common root
        // e.g. "1.2.beta3" to "1.2.beta5"
        Int sizeDiff = this.size - that.size;
        if (sizeDiff == 0 && this.form == that.form && (this.parent? == that.parent? : True))
            {
            return this.form == Number
                    ? that.number - this.number
                    : 0;
            }

        // compare a numbered version to a non-numbered version
        // e.g. "1.2.beta3" to "1.2.beta"
        if (sizeDiff == 1 && that.form != Number && this.parent? == that)
            {
            return -this.number;
            }

        // compare a non-numbered version to a numbered version
        // e.g. "1.2.beta" to "1.2.beta3"
        else if (sizeDiff == -1 && this.form != Number && this == that.parent?)
            {
            return that.number;
            }

        throw new OutOfBounds("no sequential steps from \"" + this + "\" to \"" + that + "\"");
        }

    @Override
    Version skip(Int steps)
        {
        switch (steps.sign)
            {
            case Negative:
                steps = steps.abs();
                return form == Number && number > steps
                        ? new Version(parent, form, number-steps, Null)
                        : throw new OutOfBounds($"Version={this}, steps={steps}");

            case Zero:
                return this;

            case Positive:
                return form == Number
                        ? new Version(parent, form, number+steps, Null)
                        : new Version(this, Number, steps, Null);
            }
        }


    // ----- UniformIndexed methods ----------------------------------------------------------------

    @Override
    @Op("[]")
    Version getElement(Int index)
        {
        assert:bounds index >= 0 && index < size;

        Version version = this;
        Int     steps   = size - index - 1;
        while (steps-- > 0)
            {
            version = version.parent ?: assert;
            }
        return version.parent == Null
                ? version
                : new Version(Null, version.form, version.number, version.build);
        }


    // ----- Sliceable methods ---------------------------------------------------------------------

    @Override
    @Op("[..]") Version slice(Range<Int> indexes)
        {
        Int lower = indexes.effectiveLowerBound;
        Int upper = indexes.effectiveUpperBound;
        if (lower < 0)
            {
            throw new OutOfBounds(lower.toString() + " < 0");
            }
        if (upper >= size)
            {
            throw new OutOfBounds(upper.toString() + " >= " + size);
            }
        assert:bounds lower <= upper;
        assert !indexes.descending;

        if (lower == 0)
            {
            Version version = this;
            Int     steps   = size - upper - 1;
            while (steps-- > 0)
                {
                version = version.parent ?: assert;
                }
            return version;
            }

        Version? slice = Null;
        for (Int index : indexes)
            {
            Version part = this[index];
            slice = new Version(slice, part.form, part.number, part.build);
            }
        return slice ?: assert;
        }


    // ----- Hashable and Comparable ---------------------------------------------------------------

    static <CompileType extends Version> Int hashCode(CompileType version)
        {
        Version? parent = version.parent;
        return parent == Null
                ? version.number
                : Version.hashCode(parent.as(Version)).rotateLeft(1) ^ version.number;
        }

    /**
     * Two versions are equal iff after removing every trailing (least significant) "0" indicator,
     * each version indicator from the most significant to the least significant is identical; in
     * other words, version "1.2.1" is identical only to version "1.2.1" (which is identical to
     * version "1.2.1.0").
     */
    static <CompileType extends Version> Boolean equals(CompileType version1, CompileType version2)
        {
        switch (version1.size <=> version2.size)
            {
            case Lesser:
                return version2.number == 0 && version1 == version2.parent;

            case Equal:
                return version1.number == version2.number
                    && version1.parent == version2.parent;

            case Greater:
                return version1.number == 0 && version1.parent == version2;
            }
        }

    /**
     * The existence of a real implementation of comparison for Orderable instances will be checked
     * by the run-time.
     */
    static <CompileType extends Version> Ordered compare(CompileType version1, CompileType version2)
        {
        switch (version1.size <=> version2.size)
            {
            case Lesser:
                Ordered parentOrder = version1 <=> version2.parent? : assert;
                return parentOrder == Equal
                        ? 0 <=> version2.number
                        : parentOrder;

            case Equal:
                Ordered parentOrder = version1.parent? <=> version2.parent? : Equal;
                return parentOrder == Equal
                        ? version1.number <=> version2.number
                        : parentOrder;

            case Greater:
                Ordered parentOrder = version1.parent? <=> version2 : assert;
                return parentOrder == Equal
                        ? version1.number <=> 0
                        : parentOrder;
            }
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int      length  = 0;
        Version? version = this;
        each: while (version != Null)
            {
            // check if we accidentally assumed a separator character between the current
            // pre-release version indicator and the following version indicator (e.g. "beta.2"
            // should be "beta2")
            if (version.form != Number && !each.first)
                {
                --length;
                }

            length += 1 + (form == Number ? number.estimateStringLength() : form.text.size);

            version = version.parent;
            }

        // the first version indicator does not have a "." separator preceding it
        --length;

        // append the build metadata (if any)
        length += 1 + build?.size;

        return length;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf, Boolean suppressBuild=False)
        {
        parent?.appendTo(buf, True);

        if (parent != Null && parent.form == Number)
            {
            buf.add(this.form == Number ? '.' : '-');
            }

        if (this.form == Number)
            {
            number.appendTo(buf);
            }
        else
            {
            form.text.appendTo(buf);
            }

        if (build != Null && !suppressBuild)
            {
            buf.add('+').addAll(build);
            }

        return buf;
        }
    }