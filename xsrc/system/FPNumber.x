interface FPNumber
        implements Number
    {
    @ro int Radix;
    @ro int Precision;
    @ro int ExpMin;
    @ro int ExpMax;

    static FPLiteral PI = 3.141592653589793238462643383279502884197169399375105820974944592307816406286;
    static FPLiteral E  = 2.718281828459045235360287471352662497757247093699959574966967627724076630353;
    }

interface FPNumber
        extends Number
    {
    /**
     * If the floating point number is a finite value, indicating that it is neither
     * infinite nor Not-a-Number.
     */
    @ro Boolean finite;
    /**
     * If the floating point number is an infinite value.
     */
    @ro Boolean infinite;
    /**
     * If the floating point number is Not-a-Number.
     */
    @ro Boolean NaN;
    /**
     * The radix. (The only values defined by IEEE 754-2008 are 2 and 10.)
     */
    @ro Int radix;
    /**
     * The precision, in "digits" of the radix of the floating point number, as specified by IEEE 754-2008.
     */
    @ro Int precision;
    /**
     * The maximum exponent, as specified by IEEE 754-2008.
     */
    @ro Int emax;
    /**
     * The minimum exponent, as specified by IEEE 754-2008.
     */
    @ro Int emin
        {
        return 1 - emax;
        }

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
