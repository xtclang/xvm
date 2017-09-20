interface BinaryFPNumber
        extends FPNumber
    {
    @RO Int radix.get()
        {
        return 2;
        }

    @RO Int precision.get()
        {
        // TODO k – round(4×log2(k)) + 13
        return 0;
        }

    @RO Int emax.get()
        {
        // TODO 2^(k–p–1) –1
        return 0;
        }
    }
