interface DecimalFPNumber
        extends FPNumber
    {
    @Override
    @RO Int radix.get()
        {
        return 10;
        }

    @Override
    @RO Int precision.get()
        {
        TODO 9×k/32 – 2
        }

    @Override
    @RO Int emax.get()
        {
        TODO 3 × 2 ^ (k /16 + 3)
        }
    }
