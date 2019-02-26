const BinaryFPNumber
        extends FPNumber
    {
    @Override
    @RO Int radix.get()
        {
        return 2;
        }

    @Override
    @RO Int precision.get()
        {
        // TODO k – round(4×log2(k)) + 13
        return 0;
        }

    @Override
    @RO Int emax.get()
        {
        // TODO 2^(k–p–1) –1
        return 0;
        }
    }
