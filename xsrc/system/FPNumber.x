interface FPNumber
        implements Number
    {
    @ro int Radix;
    @ro int Precision;
    @ro int ExpMin;
    @ro int ExpMax;

    static FPNumber PI = 3.141592653589793238462643383279502884197169399375105820974944592307816406286;
    static FPNumber E  = 2.718281828459045235360287471352662497757247093699959574966967627724076630353;
    }
