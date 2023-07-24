module test {
    import ecstasy.numbers.IntConvertible;

//    @Inject Random rnd;
    Random rnd = new ecstasy.numbers.PseudoRandom(123);

    static void show(Object o=Null) {
        @Inject Console console;
        console.print(o);
    }

    void run() {
        check(new IntLiteral("0"));

        for (Int testNum : 1..1000) {
            StringBuffer buf = new StringBuffer();
            if (rnd.int(5) == 0) {
                buf.add('-');
            }

            // 128 bits is up to 38 digits
            Int digitCount = rnd.int(rnd.int(38) + 1) + 1;
            buf.add('1' + rnd.int(9).toUInt32());           // make first digit not a zero
            for (Int i : 2..digitCount) {
                buf.add('0' + rnd.int(10).toUInt32());
            }

            check(new IntLiteral(buf));
        }
    }

    Type<IntNumber>[] IntTypes =
            [ Int8,  Int16,  Int32,  Int64,  Int128,  IntN,
             UInt8, UInt16, UInt32, UInt64, UInt128, UIntN,
            ];

    Method<IntConvertible, <>, <IntNumber>>[] IntConvs =
            [IntConvertible .toInt8, IntConvertible .toInt16, IntConvertible .toInt32, IntConvertible .toInt64, IntConvertible .toInt128, IntConvertible .toIntN,
             IntConvertible.toUInt8, IntConvertible.toUInt16, IntConvertible.toUInt32, IntConvertible.toUInt64, IntConvertible.toUInt128, IntConvertible.toUIntN,
            ];

    void check(IntLiteral lit) {
        // should be able to hold any int value in an IntN
        IntN         bigInt    = lit.toIntN();
        Int          typeCount = IntTypes.size;
        IntNumber?[] eachInt   = new IntNumber?[typeCount];
        for (Int i : 0..<typeCount) {
            val           type = IntTypes[i];
            val           conv = IntConvs[i];
            try {
                eachInt[i] = conv.bindTarget(lit)();
            } catch (OutOfBounds e) {
                // this is expected, but double-check that the failure to convert is the same as
                // we'd get from IntN
                try {
                    type.DataType n = conv.is(Method<IntConvertible, <Boolean>, <IntNumber>>)
                            ? conv.bindTarget(bigInt)(True)
                            : conv.bindTarget(bigInt)();
                    throw new Assertion($"literal conversion failed, but IntN {bigInt} yielded {type} {n}");
                } catch (OutOfBounds expected) {
                }
            } catch (Exception e) {
                show($"converting {lit} to {type} produced: {e}");
            }
        }
    }
}