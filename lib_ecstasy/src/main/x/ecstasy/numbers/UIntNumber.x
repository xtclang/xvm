/**
 * A UIntNumber is a Number that represents an unsigned integer value.
 */
@Abstract const UIntNumber
        extends IntNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        super(bits);
        }

    /**
     * Construct an unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        super(bytes);
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


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        TODO($"Implementations of UIntNumber must override appendTo(); class={this:class}");
        }
    }
