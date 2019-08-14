const UIntNumber
        extends IntNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    protected construct(Bit[] bits)
        {
        construct IntNumber(bits);
        }

    protected construct(Byte[] bytes)
        {
        construct IntNumber(bytes);
        }


    @Override
    Boolean signed.get()
        {
        return false;
        }

    @Override
    UIntNumber abs()
        {
        return this;
        }

    @Override
    UIntNumber neg()
        {
        throw new UnsupportedOperation();
        }
    }
