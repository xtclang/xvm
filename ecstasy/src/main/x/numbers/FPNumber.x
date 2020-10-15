const FPNumber
        extends Number
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * Options for rounding.
     *
     * These are the rounding directions defined by the IEEE 754 standard.
     */
    enum Rounding {TiesToEven, TiesToAway, TowardPositive, TowardZero, TowardNegative}

    /**
     * The value of _pi_ ("π"), defined as the ratio of the circumference of a circle to its
     * diameter.
     */
    static FPLiteral PI = 3.141592653589793238462643383279502884197169399375105820974944592307816406286;

    /**
     * The value of _e_ (Euler's number, aka Napier's constant), defined as the base of the natural
     * logarithm, and equal to the limit of `(1 + 1/n)^n` as `n` approaches infinity.
     */
    static FPLiteral E  = 2.718281828459045235360287471352662497757247093699959574966967627724076630353;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    protected construct(Bit[] bits)
        {
        construct Number(bits);
        }

    /**
     * Construct a floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    protected construct(Byte[] bytes)
        {
        construct Number(bytes);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The value of the explicit negative sign bit of this floating point number, if the format of
     * this floating point number has a sign bit and the sign bit is set.
     *
     * Note that the sign bit can be set, but the value will not report a `sign` of `Negative` in
     * the case of the zero value `-0`; both `-0` and `+0` have a `sign` of `Zero`. This property
     * can be used to differentiate between `+0` and `-0`.
     *
     * IEEE 754 values have a sign bit; it is the left-most bit of the value for both binary
     * and decimal floating point values. In the standard, the sign bit is referred to as _S_.
     */
    @RO Boolean signBit.get()
        {
        return toBitArray()[0].toBoolean();
        }

    /**
     * IEEE 754 defines the _significand_ as _a component of a finite floating-point number
     * containing its significant digits. The significand can be thought of as an integer, a
     * fraction, or some other fixed-point form, by choosing an appropriate exponent offset. A
     * decimal or subnormal binary significand can also contain leading zeros_.
     *
     * The value of the significand is specified as an `IntNumber` due to the potential for it
     * being quite large for certain floating point types.
     */
     @RO IntNumber significand.get()
        {
        (_, IntNumber significand, _) = split();
        return significand;
        }

    /**
     * The exponent of this floating point number.
     *
     * IEEE 754 defines the exponent as _the component of a finite floating-point
     * representation that signifies the integer power to which the radix is raised in determining
     * the value of that floating-point representation. The exponent `e` is used when the
     * significand is regarded as an integer digit and fraction field, and the exponent `q` is used
     * when the significand is regarded as an integer; `e = q + p − 1`, where `p` is the precision
     * of the format in digits_.
     *
     * The value of the exponent is specified as an `IntNumber` due to the potential for it being
     * quite large for certain floating point types.
     */
    @RO IntNumber exponent.get()
        {
        (_, _, IntNumber exponent) = split();
        return exponent;
        }

    /**
     * True iff the floating point value is a finite value, indicating that it is neither infinity
     * nor Not-a-Number (`NaN`).
     */
    @RO Boolean finite.get()
        {
        return !infinity && !NaN;
        }

    /**
     * True iff the floating point value is positive infinity or negative infinity. Floating point
     * values can be infinite as the result of math overflow, for example.
     */
    @Abstract @RO Boolean infinity;

    /**
     * True iff the floating point value is a `NaN` (_Not-a-Number_). Floating point values can be
     * `NaN` as the result of math underflow, for example.
     */
    @Abstract @RO Boolean NaN;

    /**
     * The radix of the significand.
     *
     * The IEEE 754 defines the _radix_ as _the base for the representation of binary or
     * decimal floating-point numbers, two or ten_.
     */
    @Abstract @RO Int radix;

    /**
     * The precision, in "digits" (of the `radix`) of the floating point number.
     *
     * Precision is defined by IEEE 754; in the standard, it is referred to as _p_, and defined
     * as _the maximum number p of significant digits that can be represented in a format, or the
     * number of digits to that a result is rounded_.
     */
    @Abstract @RO Int precision;

    /**
     * The maximum exponent value for the floating point format of this number.
     *
     * IEEE 754 uses a different calculation to determine `emax` for binary and decimal
     * floating point formats.
     *
     * The value of the maximum exponent is specified as an `IntNumber` due to the potential for it
     * being quite large for certain floating point types.
     */
    @Abstract @RO IntNumber emax;

    /**
     * The minimum exponent value for the floating point format of this number.
     *
     * IEEE 754 uses the same calculation to determine `emin` for both binary and decimal
     * floating point formats: `1 - emax`.
     *
     * The value of the minimum exponent is specified as an `IntNumber` due to the potential for its
     * magnitude being quite large for certain floating point types.
     */
    @Abstract @RO IntNumber emin;

    /**
     * The exponent bias for the floating point format of this number.
     *
     * IEEE 754 refers to the exponent bias as _bias_, and calculates the value differently
     * for binary and decimal floating point formats. The standard defines the _biased exponent_ as
     * _the sum of the exponent and a constant (bias) chosen to make the biased exponent’s range
     * nonnegative_.
     *
     * The value of the exponent bias is specified as an `IntNumber` due to the potential for it
     * being quite large for certain floating point types.
     */
    @Abstract @RO IntNumber bias;

    /**
     * The size, in bits, of the significand data in the floating point number.
     *
     * The "trailing significand field width in bits" is defined by IEEE 754; in the standard,
     * it is referred to as _t_. The _trailing significand field_ is defined as _the component of an
     * encoded binary or decimal floating-point format containing all the significand digits except
     * the leading digit. In these formats, the biased exponent or combination field encodes or
     * implies the leading significand digit_.
     */
    @Abstract @RO Int significandBitLength;

    /**
     * The size, in bits, of the exponent data.
     *
     * The "exponent field width in bits" is defined by IEEE 754; in the standard, it is
     * referred to as _w_.
     */
    @Abstract @RO Int exponentBitLength;


    // ----- floating point operations -------------------------------------------------------------

    /**
     * Split the floating point number into its constituent pieces: A sign bit, a significand, and
     * an exponent.
     *
     * @return signBit
     * @return significand
     * @return exponent
     */
    (Boolean signBit, IntNumber significand, IntNumber exponent) split();

    /**
     * Round a floating point value to an integer.
     *
     * @param direction  the optional rounding direction specifier
     *
     * @return the rounded value
     */
    FPNumber round(Rounding direction = TiesToAway);

    /**
     * @return the greatest integer value less than or equal to this floating point value
     */
    FPNumber floor();

    /**
     * @return the least integer value greater than or equal to this floating point value
     */
    FPNumber ceil();

    /**
     * @return Euler's number raised to the power of this floating point value
     */
    FPNumber exp();

    /**
     * Scale this value by an power of its radix; this is analogous to "shifting the decimal point"
     * in a number, and may be more efficient than using multiplication or division.
     *
     * @return this value scaled by `radix` raised to the power of `n`
     */
    FPNumber scaleByPow(Int n);

    /**
     * @return the natural logarithm (base _e_) of this floating point value
     */
    FPNumber log();

    /**
     * @return the base 2 logarithm of this floating point value
     */
    FPNumber log2();

    /**
     * @return the base 10 logarithm of this floating point value
     */
    FPNumber log10();

    /**
     * @return the square root of this floating point value
     */
    FPNumber sqrt();

    /**
     * @return the cubic root of this floating point value
     */
    FPNumber cbrt();

    /**
     * @return the sine of this floating point value
     */
    FPNumber sin();

    /**
     * @return the cosine of this floating point value
     */
    FPNumber cos();

    /**
     * @return the tangent of this floating point value
     */
    FPNumber tan();

    /**
     * @return the arc sine of this floating point value
     */
    FPNumber asin();

    /**
     * @return the arc cosine of this floating point value
     */
    FPNumber acos();

    /**
     * @return the arc tangent of this floating point value
     */
    FPNumber atan();

    /**
     * Calculate the principal value of the arc tangent of y/x, expressed in radians.
     *
     * The x coordinate is represented by this value; the y coordinate is passed as a parameter.
     *
     * To compute the value, the function takes into account the sign of both arguments in order to
     * determine the quadrant.
     *
     * @param y  represents the y coordinate
     *
     * @return the arc tangent of the coordinates specified by the x (this) and y coordinates
     */
    FPNumber atan2(FPNumber y);

    /**
     * @return the hyperbolic sine of this floating point value representing a hyperbolic angle
     */
    FPNumber sinh();

    /**
     * @return the hyperbolic cosine of this floating point value representing a hyperbolic angle
     */
    FPNumber cosh();

    /**
     * @return the hyperbolic tangent of this floating point value representing a hyperbolic angle
     */
    FPNumber tanh();

    /**
     * @return the area hyperbolic sine of this floating point value
     */
    FPNumber asinh();

    /**
     * @return the area hyperbolic cosine of this floating point value
     */
    FPNumber acosh();

    /**
     * @return the area hyperbolic tangent of this floating point value
     */
    FPNumber atanh();

    /**
     * @return the number of radians corresponding to the degrees specified by this value
     */
    FPNumber deg2rad();

    /**
     * @return the number of degrees corresponding to the radians specified by this value
     */
    FPNumber rad2deg();

    /**
     * Floating point numbers are not Sequential, in the true sense. However, they are _similar_ to
     * Sequential values in one way: There is an ordering of possible floating point numbers, and
     * there exists a means to move from each value within that order to the one immediately
     * preceding or following it.
     *
     * The IEEE 754 standard refers to this operation as _nextUp_: `nextUp(x)` is the least
     * floating-point number in the format of `x` that compares greater than `x`. If `x` is the
     * negative number of least magnitude in `x`’s format, `nextUp(x)` is `−0`. `nextUp(±0)` is the
     * positive number of least magnitude in `x`’s format. `nextUp(+∞)` is `+∞`, and `nextUp(−∞)` is
     * the finite negative number largest in magnitude. When `x` is a non-signalling `NaN`, then the
     * result is `NaN`.
     *
     * @return the next representable floating point value that follows this value, if there is one
     */
    FPNumber nextUp();

    /**
     * Floating point numbers are not Sequential, in the true sense. However, they are _similar_ to
     * Sequential values in one way: There is an ordering of possible floating point numbers, and
     * there exists a means to move from each value within that order to the one immediately
     * preceding or following it.
     *
     * The IEEE 754 standard refers to this operation as _nextDown_, defined as `−nextUp(−x)`.
     *
     * @return the next representable floating point value that precedes this value, if there is one
     */
    FPNumber nextDown();


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    IntLiteral toIntLiteral()
        {
        return round(TowardZero).toIntN().toIntLiteral();
        }

    @Override
    FPLiteral toFPLiteral()
        {
        return new FPLiteral(toString());
        }
    }
