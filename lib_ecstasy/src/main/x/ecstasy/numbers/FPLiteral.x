/**
 * An FPLiteral is a constant type that is able to convert any text string containing a
 * legal representation of an FPNumber into any of the built-in FPNumber implementations.
 */
const FPLiteral(String text)
        implements Orderable
        implements FPConvertible
        implements Destringable
        default(0.0)
    {
    /**
     * The literal text.
     */
    private String text;

    @Auto
    @Override
    FloatN toFloatN()
        {
        TODO
        }

    @Auto
    @Override
    Float8e4 toFloat8e4()
        {
        return toFloatN().toFloat8e4();
        }

    @Auto
    @Override
    Float8e5 toFloat8e5()
        {
        return toFloatN().toFloat8e5();
        }

    @Auto
    @Override
    BFloat16 toBFloat16()
        {
        return toFloatN().toBFloat16();
        }

    @Auto
    @Override
    Float16 toFloat16()
        {
        return toFloatN().toFloat16();
        }

    @Auto
    @Override
    Float32 toFloat32()
        {
        return toFloatN().toFloat32();
        }

    @Auto
    @Override
    Float64 toFloat64()
        {
        return toFloatN().toFloat64();
        }

    @Auto
    @Override
    Float128 toFloat128()
        {
        return toFloatN().toFloat128();
        }

    @Auto
    @Override
    DecN toDecN()
        {
        TODO
        }

    @Auto
    @Override
    Dec32 toDec32()
        {
        return toDecN().toDec32();
        }

    @Auto
    @Override
    Dec64 toDec64()
        {
        return toDecN().toDec64();
        }

    @Auto
    @Override
    Dec128 toDec128()
        {
        return toDecN().toDec128();
        }

    @Override
    FPLiteral toFPLiteral()
        {
        return this;
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
