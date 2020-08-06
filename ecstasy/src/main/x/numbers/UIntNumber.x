const UIntNumber
        extends IntNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    protected construct(Bit[] bits)
        {
        construct IntNumber(bits);
        }

    /**
     * Construct an unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    protected construct(Byte[] bytes)
        {
        construct IntNumber(bytes);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Boolean signed.get()
        {
        return False;
        }

    @Override
    UIntNumber magnitude.get()
        {
        return this;
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    UIntNumber abs()
        {
        return this;
        }

    @Override
    @Op("-#")
    UIntNumber neg()
        {
        throw new UnsupportedOperation();
        }
    }
