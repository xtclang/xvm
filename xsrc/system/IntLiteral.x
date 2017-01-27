/**
 * An IntNumber is a Number that represents an integer value.
 */
const IntLiteral
        implements IntNumber
    {
    String text;
    IntNumber preferredForm;

    construct IntLiteral(String text)
        {
        // TODO validate

        this.text = text;

            construct String(Char[] chars)
        {
        assert:always chars.length > 0;

        Int     of   = 0;
        Boolean fNeg = false;

        // optional leading sign
        switch (chars[of])
            {
            case '-':
                {
                fNeg = true;
                ++of;
                }

            case '+':
                {
                ++of;
                }
            }

        // optional leading format
        if (chars.length - of >= 2 && chars[of] == '0')
            {
            switch (chars[of+1])
                {
                case 'X':
                case 'x':
                    {
                    of += 2;
                    }
                case 'B':
                case 'b':
            }
        }

        }

    /**
     * Integer increment.
     */
    @op IntNumber inc()
        {
        return this + 1;
        }

    /**
     * Integer decrement.
     */
    @op IntNumber dec()
        {
        return this - 1;
        }

    /**
     * Shift bits left. This is both a logical left shift and arithmetic left shift, for
     * both signed and unsigned integer values.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are used.
     */
    @op IntNumber shl(Int count);
    /**
     * Shift bits right. For signed integer values, this is an arithmetic right shift. For
     * unsigned integer values, this is both a logical right shift and arithmetic right
     * shift.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are used.
     */
    @op IntNumber shr(Int count);
    /**
     * "Unsigned" Shift bits right. For signed integer values, this is an logical right
     * shift. For unsigned integer values, this is both a logical right shift and arithmetic
     * right shift.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are used.
     */
    @op IntNumber ushr(Int count);

    /**
     * Rotate bits left.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are guaranteed to be used.
     */
    IntNumber rol(Int count);
    /**
     * Rotate bits right.
     * <p/>
     * Note: For an integer of size n bits, only the least significant log2(n) bits of the
     *       count value are guaranteed to be used.
     */
    IntNumber ror(Int count);
    }
