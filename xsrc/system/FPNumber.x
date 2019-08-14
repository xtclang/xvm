const FPNumber
        extends Number
    {
    // ----- constants -----------------------------------------------------------------------------

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
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
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
     * If the floating point number is a finite value, indicating that it is neither
     * infinite nor Not-a-Number.
     */
    @Abstract @RO Boolean finite;
    /**
     * If the floating point number is an infinite value.
     */
    @Abstract @RO Boolean infinite;
    /**
     * If the floating point number is Not-a-Number.
     */
    @Abstract @RO Boolean NaN;
    /**
     * The radix. (The only values defined by IEEE 754-2008 are 2 and 10.)
     */
    @Abstract @RO Int radix;
    /**
     * The precision, in "digits" of the radix of the floating point number, as specified by IEEE 754-2008.
     */
    @Abstract @RO Int precision;
    /**
     * The maximum exponent, as specified by IEEE 754-2008.
     */
    @Abstract @RO Int emax;
    /**
     * The minimum exponent, as specified by IEEE 754-2008.
     */
    @RO Int emin.get()
        {
        return 1 - emax;
        }


    // ----- floating point operations -------------------------------------------------------------

    FPNumber round();
    FPNumber floor();
    FPNumber ceil();

    FPNumber exp();
    FPNumber log();
    FPNumber log10();
    FPNumber sqrt();
    FPNumber cbrt();

    FPNumber sin();
    FPNumber cos();
    FPNumber tan();
    FPNumber asin();
    FPNumber acos();
    FPNumber atan();
    FPNumber deg2rad();
    FPNumber rad2deg();
    }
