interface BinaryFPNumber
        extends FPNumber
    {
    @ro Int radix.get()
        {
        return 2;
        }

    @ro Int precision.get()
        {
        // TODO k – round(4×log2(k)) + 13
        return 0;
        }

    @ro Int emax.get()
        {
        // TODO 2^(k–p–1) –1
        return 0;
        }
    }
