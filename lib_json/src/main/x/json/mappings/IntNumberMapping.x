import ecstasy.numbers;

/**
 * A JSON [Mapping] implementation for Ecstasy integer types.
 */
const IntNumberMapping<Serializable extends IntNumber>
        implements Mapping<Serializable> {

    construct() {
        assert convert := CONVERSION.get(Serializable);
    }

    /**
     * The function that converts an IntLiteral to the desired integer type.
     */
    function IntNumber(IntLiteral) convert;

    @Override
    String typeName.get() {
        return Serializable.toString();
    }

    @Override
    Serializable read(ElementInput in) {
        return convert(in.readIntLiteral()).as(Serializable);
    }

    @Override
    void write(ElementOutput out, Serializable value) {
        out.add(value.toIntLiteral());
    }

    static Map<Type, function IntNumber(IntLiteral)> CONVERSION =
        Map:
            [
            numbers.IntNumber  = (lit) -> lit.toIntN(),
            numbers.UIntNumber = (lit) -> lit.toUIntN(),
            numbers.Int        = (lit) -> lit.toInt(),
            numbers.Int8       = (lit) -> lit.toInt8(),
            numbers.Int16      = (lit) -> lit.toInt16(),
            numbers.Int32      = (lit) -> lit.toInt32(),
            numbers.Int64      = (lit) -> lit.toInt64(),
            numbers.Int128     = (lit) -> lit.toInt128(),
            numbers.UInt       = (lit) -> lit.toUInt(),
            numbers.UInt8      = (lit) -> lit.toUInt8(),
            numbers.UInt16     = (lit) -> lit.toUInt16(),
            numbers.UInt32     = (lit) -> lit.toUInt32(),
            numbers.UInt64     = (lit) -> lit.toUInt64(),
            numbers.UInt128    = (lit) -> lit.toUInt128(),
            numbers.IntN       = (lit) -> lit.toIntN(),
            numbers.UIntN      = (lit) -> lit.toUIntN(),
            ];
}