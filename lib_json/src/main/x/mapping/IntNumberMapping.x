import ecstasy.numbers;

/**
 * A JSON [Mapping] implementation for Ecstasy integer types.
 */
const IntNumberMapping<Serializable extends IntNumber>
        implements Mapping<Serializable>
    {
    construct()
        {
        assert convert := CONVERSION.get(Serializable);
        }

    /**
     * The function that converts an IntLiteral to the desired integer type.
     */
    function IntNumber(IntLiteral) convert;

    @Override
    String typeName.get()
        {
        return Serializable.toString();
        }

    @Override
    Serializable read(ElementInput in)
        {
        return convert(in.readIntLiteral()).as(Serializable);
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toIntLiteral());
        }

    static Map<Type, function IntNumber(IntLiteral)> CONVERSION =
        Map:[
                       numbers.IntNumber = (lit) -> lit.toIntN()                 ,
            @Unchecked numbers.IntNumber = (lit) -> lit.toIntN()   .toUnchecked(),
                       numbers.Int8      = (lit) -> lit.toInt8()                 ,
            @Unchecked numbers.Int8      = (lit) -> lit.toInt8()   .toUnchecked(),
                       numbers.Int16     = (lit) -> lit.toInt16()                ,
            @Unchecked numbers.Int16     = (lit) -> lit.toInt16()  .toUnchecked(),
                       numbers.Int32     = (lit) -> lit.toInt32()                ,
            @Unchecked numbers.Int32     = (lit) -> lit.toInt32()  .toUnchecked(),
                       numbers.Int64     = (lit) -> lit.toInt()                  ,
            @Unchecked numbers.Int64     = (lit) -> lit.toInt()    .toUnchecked(),
                       numbers.Int128    = (lit) -> lit.toInt128()               ,
            @Unchecked numbers.Int128    = (lit) -> lit.toInt128() .toUnchecked(),
                       numbers.UInt8     = (lit) -> lit.toByte()                 ,
            @Unchecked numbers.UInt8     = (lit) -> lit.toByte()   .toUnchecked(),
                       numbers.UInt16    = (lit) -> lit.toUInt16()               ,
            @Unchecked numbers.UInt16    = (lit) -> lit.toUInt16() .toUnchecked(),
                       numbers.UInt32    = (lit) -> lit.toUInt32()               ,
            @Unchecked numbers.UInt32    = (lit) -> lit.toUInt32() .toUnchecked(),
                       numbers.UInt64    = (lit) -> lit.toUInt()                 ,
            @Unchecked numbers.UInt64    = (lit) -> lit.toUInt()   .toUnchecked(),
                       numbers.UInt128   = (lit) -> lit.toUInt128()              ,
            @Unchecked numbers.UInt128   = (lit) -> lit.toUInt128().toUnchecked(),
                       numbers.IntN      = (lit) -> lit.toIntN()                 ,
            @Unchecked numbers.IntN      = (lit) -> lit.toIntN() .toUnchecked()  ,
                       numbers.UIntN     = (lit) -> lit.toUIntN()                ,
            @Unchecked numbers.UIntN     = (lit) -> lit.toUIntN().toUnchecked()  ,
            ];
    }
