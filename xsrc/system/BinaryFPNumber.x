interface BinaryFPNumber
        extends FPNumber
    {
    @ro Int radix
        {
        return 2;
        }

    @ro Int precision
        {
        // TODO k – round(4×log2(k)) + 13
        }

    @ro Int emax
        {
        // TODO 2^(k–p–1) –1
        }
    }
