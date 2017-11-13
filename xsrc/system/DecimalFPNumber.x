interface DecimalFPNumber
        extends FPNumber
    {
    Int radix.get()
        {
        return 10;
        }

    Int precision.get()
        {
        TODO 9×k/32 – 2
        }

    Int emax.get()
        {
        TODO 3 × 2 ^ (k /16 + 3)
        }
    }
