/**
 * An FPLiteral is a constant type that is able to convert any text string containing a
 * legal representation of an FPNumber into any of the built-in FPNumber implementations.
 */
const FPLiteral(String text)
    {
    /**
     * Convert the number to a variable-length binary radix floating point number.
     */
    @auto VarFloat to<VarFloat>();

    /**
     * Convert the number to a 16-bit radix-2 (binary) floating point number.
     */
    @auto Float16 to<Float16>()
        {
        return to<VarFloat>().to<Float16>();
        }

    /**
     * Convert the number to a 32-bit radix-2 (binary) floating point number.
     */
    @auto Float32 to<Float32>()
        {
        return to<VarFloat>().to<Float32>();
        }

    /**
     * Convert the number to a 64-bit radix-2 (binary) floating point number.
     */
    @auto Float64 to<Float64>()
        {
        return to<VarFloat>().to<Float64>();
        }

    /**
     * Convert the number to a 128-bit radix-2 (binary) floating point number.
     */
    @auto Float128 to<Float128>()
        {
        return to<VarFloat>().to<Float128>();
        }

    /**
     * Convert the number to a variable-length decimal radix floating point number.
     */
    @auto VarDec to<VarDec>();

    /**
     * Convert the number to a 32-bit radix-10 (decimal) floating point number.
     */
    @auto Dec32 to<Dec32>()
        {
        return to<VarDec>().to<Dec32>();
        }

    /**
     * Convert the number to a 64-bit radix-10 (decimal) floating point number.
     */
    @auto Dec64 to<Dec64>()
        {
        return to<VarDec>().to<Dec64>();
        }

    /**
     * Convert the number to a 128-bit radix-10 (decimal) floating point number.
     */
    @auto Dec128 to<Dec128>()
        {
        return to<VarDec>().to<Dec128>();
        }

    @Override
    String to<String>()
        {
        return text;
        }
    }
