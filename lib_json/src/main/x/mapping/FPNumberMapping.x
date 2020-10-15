import ecstasy.numbers;

/**
 * A JSON [Mapping] implementation for Ecstasy floating point types.
 */
const FPNumberMapping<Serializable extends FPNumber>
        implements Mapping<Serializable>
    {
    construct()
        {
        assert convert := CONVERSION.get(Serializable);
        }

    /**
     * The function that converts an FPLiteral to the desired floating point type.
     */
    function FPNumber(FPLiteral) convert;

    @Override
    String typeName.get()
        {
        return Serializable.toString();
        }

    @Override
    Serializable read(ElementInput in)
        {
        return convert(in.readFPLiteral()).as(Serializable);
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toFPLiteral());
        }

    static Map<Type, function FPNumber(FPLiteral)> CONVERSION =
        Map:[
            numbers.FPNumber        = (lit) -> lit.toDecN(),
            numbers.DecimalFPNumber = (lit) -> lit.toDecN(),
            numbers.Dec32           = (lit) -> lit.toDec32(),
            numbers.Dec64           = (lit) -> lit.toDec64(),
            numbers.Dec128          = (lit) -> lit.toDec128(),
            numbers.DecN            = (lit) -> lit.toDecN(),
            numbers.BinaryFPNumber  = (lit) -> lit.toFloatN(),
            numbers.BFloat16        = (lit) -> lit.toBFloat16(),
            numbers.Float16         = (lit) -> lit.toFloat16(),
            numbers.Float32         = (lit) -> lit.toFloat32(),
            numbers.Float64         = (lit) -> lit.toFloat64(),
            numbers.Float128        = (lit) -> lit.toFloat128(),
            numbers.FloatN          = (lit) -> lit.toFloatN(),
            ];
    }
