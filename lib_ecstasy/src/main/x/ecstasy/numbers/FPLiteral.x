/**
 * An FPLiteral is a constant type that is able to convert any text string containing a
 * legal representation of an FPNumber into any of the built-in FPNumber implementations.
 */
const FPLiteral(String text)
        implements Orderable
    {
    /**
     * The literal text.
     */
    private String text;

    /**
     * Convert the number to a variable-length binary radix floating point number.
     */
    @Auto FloatN toFloatN()
        {
        TODO
        }

    /**
     * Convert the number to a "brain" 16-bit radix-2 (binary) floating point number.
     */
    @Auto BFloat16 toBFloat16()
        {
        return toFloatN().toBFloat16();
        }

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     */
    @Auto Float16 toFloat16()
        {
        return toFloatN().toFloat16();
        }

    /**
     * Convert the number to a 32-bit radix-2 (binary) floating point number.
     */
    @Auto Float32 toFloat32()
        {
        return toFloatN().toFloat32();
        }

    /**
     * Convert the number to a 64-bit radix-2 (binary) floating point number.
     */
    @Auto Float64 toFloat64()
        {
        return toFloatN().toFloat64();
        }

    /**
     * Convert the number to a 128-bit radix-2 (binary) floating point number.
     */
    @Auto Float128 toFloat128()
        {
        return toFloatN().toFloat128();
        }

    /**
     * Convert the number to a variable-length decimal radix floating point number.
     */
    @Auto DecN toDecN()
        {
        TODO
        }

    /**
     * Convert the number to a 32-bit radix-10 (decimal) floating point number.
     */
    @Auto Dec32 toDec32()
        {
        return toDecN().toDec32();
        }

    /**
     * Convert the number to a 64-bit radix-10 (decimal) floating point number.
     */
    @Auto Dec64 toDec64()
        {
        return toDecN().toDec64();
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     */
    @Auto Dec128 toDec128()
        {
        return toDecN().toDec128();
        }

    @Override
    String toString()
        {
        return text;
        }


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return text.size;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        return text.appendTo(buf);
        }
    }
