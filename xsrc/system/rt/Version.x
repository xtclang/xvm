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
 *   in the actual version, or if the first different version indicator in the actual version is
 *   greater than the corresponding version indicator in the requested version; in other words,
 *   version "1.2", "1.2.1", and "1.2.1.7", and "1.3" are all substitutable for version "1.2",
 *   but "2.0" and "2.1" are not.
 *
 * * In the previous example, to use only one of the versions that begins with "1.2", the
 *   requested version `v<sub>R</sub>` should be specified as "1.2.0"; versions "1.2",
 *   "1.2.1", and "1.2.1.7" are subsitutes for 1.2.0, but versions "1.3", "2.0", and "2.1" are
 *   not.
 *
 * * Pre-release version numbers are encoded specially. The version "1.2beta3" is encoded as
 *   "1.2.-2.3", which means that it is explicitly **not** substitutable for version "1.2";
 *   however, version "1.2" _is_ substitutable for version "1.2beta3".
 *
 * The Version design supports [Semantic Versioning 2.0.0] (https://semver.org/), and adherence
 * to that specification is encouraged. However, the use of Semantic Versioning is a choice, and
 * while this Version class does _support_ Semantic Versioning, it does not _enforce_ it.
 * Additionally, this design constrains the use of alpha-numeric forms in two different ways:
 *
 * * Only six alpha-numeric forms are supported ("CI", "Dev", "QC", "alpha", "beta", and "rc"),
 *   with the first three representing internal versions and the remainder representing published
 *   versions. (The use of uppercase names for the first three is not accidental; ASCII uppercase
 *   precedes ASCII lowercase, which achieves compliance with the ASCII ordering specified by the
 *   Semantic Versioning specification.)
 *
 * * A Version can only contain **one** of the alpha-numeric pre-release specifiers, and it must
 *   occur at the end of the Version (no subsequent "dot" indicators). For example, "2.0.0-alpha"
 *   and "2.0.0-beta3" are both legal, but neither "2-alpha-beta3" nor "2.0.0-beta3.5" are legal.
 *
 * As specified by Semantic Versioning, build metadata is appended to the end of the version string
 * after a plus sign. Build metadata is retained by, but ignored by the Version class. The examples
 * from the Semantic Versioning 2.0.0 specification are all legal Version examples:
 * "1.0.0-alpha+001", "1.0.0+20130313144700", "1.0.0-beta+exp.sha.5114f85".
 */
const Version
        implements Sequence<Version>
        implements Sequential
    {
    /**
     * Each part of a version is called a version indicator, and that version indicator has a Form.
     * The overwhelmingly prevalent form of version indicator is the Num (number) form.
     */
    enum Form(String text, Int equiv)
        {
        /**
         * Continuous Integration (CI) version. This is an "internal" version, i.e. not intended
         * for distribution. Automated build tools are expected to use a CI version with a counter
         * to identify builds; for example, a the 47th CI build for version 2.7.1 should be labeled
         * as "2.7.1-CI47"
         */
        CI   ("CI",    -6),
        /**
         * Development (or "engineering") version. This is an "internal" version, i.e. not intended
         * for distribution. In the absence of a Version being specified, the Ecstasy compiler will
         * simply use "Dev" as the default version when it emits a module.
         */
        Dev  ("Dev"  , -5),
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
        QC   ("QC"   , -4),
        /**
         * Alpha (α) version. An alpha version usually indicates an extremely early pre-release
         * version that is selectively published to obtain engineering feedback. An alpha is an
         * external version, but it is not a generally available (GA) version.
         */
        Alpha("alpha", -3),
        /**
         * Beta (β) version. A beta version usually indicates a pre-release version that is
         * feature complete (or nearly so), and approaching a GA release. A beta version is
         * selectively published to obtain engineering, quality control, and end user feedback.
         * A beta is an external version, but it is not a generally available (GA) version.
         */
        Beta ("beta" , -2),
        /**
         * Release Candidate (RC) version. A release candidate version indicates a pre-release
         * version that is being evaluated as a potential version to publish as the generally
         * available (GA) release version. A release candidate is an external version, but it is
         * not a generally available (GA) version.
         */
        RC   ("rc"   , -1),
        /**
         * An integer version indicator.
         */
        Num  ("#"    ,  0)
        }

    /**
     * Construct a Version from a String representation.
     *
     * @param version  a legal version string, such as one emitted by `Version.to<String>()`
     */
    construct(String version)
        {
        // check for "+" (start of build string)
        Int     end   = version.size-1;
        String? build = null;
        if (Int buildStart : version.indexOf('+'))
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
        Boolean  fAnyChars = false;
        Boolean  fAnyNums  = false;
        Int      start     = 0;
        Version? parent    = null;
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
                    parent = new Version(version[0..of-1]);
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

                    fAnyChars = true;
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

                    fAnyNums = true;
                    break;
                }
            }

        if (fAnyChars)
            {
            String name = version[start..end];
            // if (Form form : Form.byName.get(name))
            if (Form form : Form_byName_get(name))
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
            Int num = new IntLiteral(version[start..end]).to<Int>();
            construct Version(parent, num, build);
            }
        else
            {
            throw new IllegalArgument("Invalid version string: \"" + version + "\"");
            }
        }

    static conditional Form Form_byName_get(String name)
        {
        return switch (name)
            {
            case "CI"   : (true, CI);
            case "Dev"  : (true, Dev);
            case "QC"   : (true, QC);
            case "alpha": (true, Alpha);
            case "beta" : (true, Beta);
            case "rc"   : (true, RC);
            default     : false;
            };
        }

    /**
     * Construct a Version that is a revision of another Version.
     *
     * @param parent  the version that this is a revision of
     * @param number  the version indicator for the revision
     * @param build   an optional build metadata string, comprised only of ASCII alphanumerics and
     *                hyphen `[0-9A-Za-z-]`
     */
    construct(Version? parent, Int number, String? build = null)
        {
        construct Version(parent, Num, number, build);
        }

    /**
     * Construct a pre-release Version.
     *
     * @param parent  the version that this is a pre-release version of
     * @param form    the pre-release version indicator
     * @param build   an optional build metadata string, comprised only of ASCII alphanumerics and
     *                hyphen `[0-9A-Za-z-]`
     */
    construct(Version? parent, Form form, String? build = null)
        {
        assert form != Num;
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
    private construct(Version? parent, Form form, Int number, String? build = null)
        {
        // only one pre-release designation can be used in a Version
        assert form == Num || parent?.GA;

        // version indicators are >= 0 except for the pre-defined forms
        assert form == Num ? number >= 0 : number == form.equiv;

        // for a non-GA parent, only a version number indicator can be added, and only if the last
        // indicator of the parent is the pre-release indicator
        assert parent?.GA || parent?.form != Num;  // TODO CP: the second parent? should be just "parent"

        for (Char ch : build?)
            {
            switch (ch)
                {
                case '0'..'9':
                case 'A'..'Z':
                case 'a'..'z':
                case '-':
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
            default: Num;
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
    @Override
    Int size;

    /**
     * True iff the version information denotes a "generally available" version.
     */
    Boolean GA.get()
        {
        return form == Num && (parent?.form == Num : true);
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

        return build == null
                ? this
                : new Version(parent, form, number, null);
        }

    /**
     * Determine if this version satisfies the specified required version.
     *
     * The actual version `v<sub>A</sub>` is **substitutable for** the requested version
     * `v<sub>R</sub>` iff each version indicator of the requested version from the most
     * significant to the least significant is identical to the corresponding version indicator
     * in the actual version, or if the first different version indicator in the actual version is
     * greater than the corresponding version indicator in the requested version; in other words,
     * version "1.2", "1.2.1", and "1.2.1.7", and "1.3" are all substitutable for version "1.2",
     * but "2.0" and "2.1" are not.
     *
     * @param that  the requested version
     *
     * @return true iff this version can be used as a match for the requested version based on the
     *         rules of version substitutability
     */
    Boolean satisfies(Version that)
        {
        TODO
//        Int tailSize = this.size - that.size;
//        if (tailSize < 0 || this.absolute != that.absolute)
//            {
//            return false;
//            }
//
//        Version parent = this;
//        while (tailSize-- > 0)
//            {
//            parent = parent.parent ?: assert;
//            }
//
//        return parent == that;
        }


    // ----- Sequential methods --------------------------------------------------------------------

    @Override
    conditional Version prev()
        {
        return form == Num && number > 0
                ? (true, new Version(parent, form, number-1, null))
                : false;
        }

    @Override
    conditional Version next()
        {
        return true, form == Num
                ? new Version(parent, form, number+1, null)
                : new Version(this, Num, 1, null);
        }

    @Override
    Int stepsTo(Version that)
        {
        // common scenario: two versions with different ending number but common root
        // e.g. "1.2.beta3" to "1.2.beta5"
        Int sizeDiff = this.size - that.size;
        if (sizeDiff == 0 && this.form == that.form && (this.parent? == that.parent? : true))
            {
            return this.form == Num
                    ? that.number - this.number
                    : 0;
            }

        // compare a numbered version to a non-numbered version
        // e.g. "1.2.beta3" to "1.2.beta"
        if (sizeDiff == 1 && that.form != Num && this.parent? == that)
            {
            return -this.number;
            }

        // compare a non-numbered version to a numbered version
        // e.g. "1.2.beta" to "1.2.beta3"
        else if (sizeDiff == -1 && this.form != Num && this == that.parent?)
            {
            return that.number;
            }

        throw new OutOfBounds("no sequential steps from \"" + this + "\" to \"" + that + "\"");
        }


    // ----- Sequence methods ----------------------------------------------------------------------

    @Override
    @Op("[]")
    Version getElement(Int index)
        {
        if (index < 0)
            {
            throw new OutOfBounds(index.to<String>() + " < 0");
            }
        if (index >= size)
            {
            throw new OutOfBounds(index.to<String>() + " >= " + size);
            }

        Version version = this;
        Int     steps   = size - index - 1;
        while (steps-- > 0)
            {
            version = version.parent ?: assert;
            }
        return version;
        }

    @Override
    @Op("[..]")
    Version slice(Range<Int> range)
        {
        Int lower = range.lowerBound;
        Int upper = range.upperBound;
        if (lower < 0)
            {
            throw new OutOfBounds(lower.to<String>() + " < 0");
            }
        if (upper >= size)
            {
            throw new OutOfBounds(upper.to<String>() + " >= " + size);
            }
        assert lower <= upper;
        assert !range.reversed;

        if (lower == 0)
            {
            return this[upper];
            }

        Version? slice = null;
        for (Int index : range)
            {
            Version part = this[index];
            slice = new Version(slice, part.form, part.number, part.build);
            }
        return slice ?: assert;
        }


    // ----- Hashable ------------------------------------------------------------------------------

    @Override
    @RO Int hash.get()
        {
        return (parent?.hash.rotateLeft(1) : 0) ^ number;
        }

    /**
     * Two entries are equal iff they contain equal keys and equal values.
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
        each: while (version != null)
            {
            // check if we accidentally assumed a separator character between the current
            // pre-release version indicator and the following version indicator (e.g. "beta.2"
            // should be "beta2")
            if (version.form != Num && !each.first)
                {
                --length;
                }

            length += 1 + (form == Num ? number.estimateStringLength() : form.text.size);

            version = version.parent;
            }

        // the first version indicator does not have a "." separator preceding it
        --length;

        // append the build metadata (if any)
        length += 1 + build?.size;

        return length;
        }

    @Override
    void appendTo(Appender<Char> appender, Boolean suppressBuild=false)
        {
        parent?.appendTo(appender, true);

        if (parent != null && parent.form == Num)
            {
            appender.add(this.form == Num ? '.' : '-');
            }

        if (this.form == Num)
            {
            number.appendTo(appender);
            }
        else
            {
            appender.add(form.text);
            }

        if (build != null && !suppressBuild)
            {
            appender.add('+').add(build);
            }
        }
    }