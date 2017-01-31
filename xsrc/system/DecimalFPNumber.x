interface DecimalFPNumber
        extends FPNumber
    {
    @ro Int radix
        {
        return 10;
        }

    @ro Int precision
        {
        // TODO 9×k/32 – 2
        }

    @ro Int emax
        {
        // TODO 3 × 2 ^ (k /16 + 3)
        }

    }
