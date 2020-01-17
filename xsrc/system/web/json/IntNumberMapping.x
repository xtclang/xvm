/**
 * A JSON [Mapping] implementation for Ecstasy integer types.
 */
const IntNumberMapping<Serializable extends IntNumber>
        implements Mapping<Serializable>
    {
    assert()
        {
        assert CONVERSION.contains(Serializable);
        }

    @Override
    <ObjectType extends Serializable> ObjectType read<ObjectType>(ElementInput in)
        {
        if (function IntNumber(IntLiteral) convert := CONVERSION.get(ObjectType))
            {
            return convert(in.readIntLiteral()).as(ObjectType);
            }

        throw new MissingMapping(type=ObjectType);
        }

    @Override
    <ObjectType extends Serializable> void write(ElementOutput out, ObjectType value)
        {
        out.add(value.toIntLiteral());
        }

    // TODO GG - it did NOT like this without the "Type<>" around the keys
    static Map<Type, function IntNumber(IntLiteral)> CONVERSION =
        Map<Type, function IntNumber(IntLiteral)>:[
        Type<           numbers.IntNumber> = (lit) -> lit.toVarInt()               ,
        Type<@Unchecked numbers.IntNumber> = (lit) -> lit.toVarInt() .toUnchecked(),
        Type<           numbers.Int8     > = (lit) -> lit.toInt8()                 ,
        Type<@Unchecked numbers.Int8     > = (lit) -> lit.toInt8()   .toUnchecked(),
        Type<           numbers.Int16    > = (lit) -> lit.toInt16()                ,
        Type<@Unchecked numbers.Int16    > = (lit) -> lit.toInt16()  .toUnchecked(),
        Type<           numbers.Int32    > = (lit) -> lit.toInt32()                ,
        Type<@Unchecked numbers.Int32    > = (lit) -> lit.toInt32()  .toUnchecked(),
        Type<           numbers.Int64    > = (lit) -> lit.toInt()                  ,
        Type<@Unchecked numbers.Int64    > = (lit) -> lit.toInt()    .toUnchecked(),
        Type<           numbers.Int128   > = (lit) -> lit.toInt128()               ,
        Type<@Unchecked numbers.Int128   > = (lit) -> lit.toInt128() .toUnchecked(),
        Type<           numbers.UInt8    > = (lit) -> lit.toByte()                 ,
        Type<@Unchecked numbers.UInt8    > = (lit) -> lit.toByte()   .toUnchecked(),
        Type<           numbers.UInt16   > = (lit) -> lit.toUInt16()               ,
        Type<@Unchecked numbers.UInt16   > = (lit) -> lit.toUInt16() .toUnchecked(),
        Type<           numbers.UInt32   > = (lit) -> lit.toUInt32()               ,
        Type<@Unchecked numbers.UInt32   > = (lit) -> lit.toUInt32() .toUnchecked(),
        Type<           numbers.UInt64   > = (lit) -> lit.toUInt()                 ,
        Type<@Unchecked numbers.UInt64   > = (lit) -> lit.toUInt()   .toUnchecked(),
        Type<           numbers.UInt128  > = (lit) -> lit.toUInt128()              ,
        Type<@Unchecked numbers.UInt128  > = (lit) -> lit.toUInt128().toUnchecked(),
        Type<           numbers.VarInt   > = (lit) -> lit.toVarInt()               ,
        Type<@Unchecked numbers.VarInt   > = (lit) -> lit.toVarInt() .toUnchecked(),
        Type<           numbers.VarUInt  > = (lit) -> lit.toVarUInt()              ,
        Type<@Unchecked numbers.VarUInt  > = (lit) -> lit.toVarUInt().toUnchecked(),
        ];
    }
