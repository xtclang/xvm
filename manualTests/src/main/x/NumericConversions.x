module NumericConversions {
    import ecstasy.numbers.IntConvertible;

    Random rnd = new ecstasy.numbers.PseudoRandom(123);

    static void show(Object o=Null) {
        @Inject Console console;
        console.print(o);
    }

    void run() {
        check(new IntLiteral("0"));

        for (Int testNum : 1..10000) {
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

            if (testNum % 1000 == 0) {
                show($"{testNum=}");
            }
            check(new IntLiteral(buf));
        }
    }

    Type<IntNumber>[] IntTypes =
            [ Int8,  Int16,  Int32,  Int64,  Int128,  IntN,
             UInt8, UInt16, UInt32, UInt64, UInt128, UIntN,
            ];

    Method<Number, <>, <IntNumber>>[] NumConvs =
            [Number .toInt8, Number .toInt16, Number .toInt32, Number .toInt64, Number .toInt128, Number .toIntN,
             Number.toUInt8, Number.toUInt16, Number.toUInt32, Number.toUInt64, Number.toUInt128, Number.toUIntN,
            ];

    Method<IntLiteral, <>, <IntNumber>>[] LitConvs =
            [IntLiteral .toInt8, IntLiteral .toInt16, IntLiteral .toInt32, IntLiteral .toInt64, IntLiteral .toInt128, IntLiteral .toIntN,
             IntLiteral.toUInt8, IntLiteral.toUInt16, IntLiteral.toUInt32, IntLiteral.toUInt64, IntLiteral.toUInt128, IntLiteral.toUIntN,
            ];

    void check(IntLiteral lit) {
        // should be able to hold any int value in an IntN
        IntN         bigInt    = lit.toIntN();
        Int          typeCount = IntTypes.size;
        IntNumber?[] eachInt   = new IntNumber?[typeCount];
        for (Int i : 0..<typeCount) {
            val type    = IntTypes[i];
            val convLit = LitConvs[i];
            val convNum = NumConvs[i];
            try {
                eachInt[i] = convLit.bindTarget(lit)();
            } catch (OutOfBounds e) {
                // this is expected, but double-check that the failure to convert is the same as
                // we'd get from IntN
                try {
                    type.DataType n = convNum.is(Method<IntConvertible, <Boolean>, <IntNumber>>)
                            ? convNum.bindTarget(bigInt)(True)
                            : convNum.bindTarget(bigInt)();
                    throw new Assertion($"literal conversion failed, but IntN {bigInt} yielded {type} {n}");
                } catch (OutOfBounds expected) {
                }
            } catch (Exception e) {
                show($"converting {lit} to {type} produced: {e}");
            }
        }

        // do conversions to all other types
        for (Int from : 0..<typeCount) {
            if (IntNumber fromN ?= eachInt[from]) {
                val fromType = IntTypes[from];
                for (Int to : 0..<typeCount) {
                    val toType = IntTypes[to];
                    val convTo = NumConvs[to];
                    toType.DataType? toN = eachInt[to].as(toType.DataType?);

                    // we're going to try to convert from `fromN` to `identicalTo`, by using
                    // "checkBounds=True", and compare the result to the previous conversion from
                    // the IntLiteral (`toN`); the result must always be the same:
                    // 1) either both convert successfully or both should fail
                    // 2) if both convert successfully, then the integer value must be identical
                    toType.DataType? identicalTo = Null;
                    try {
                        identicalTo = convTo.is(Method<IntConvertible, <Boolean>, <IntNumber>>)
                                ? convTo.bindTarget(fromN)(True)
                                : convTo.bindTarget(fromN)();
                    } catch (OutOfBounds _) {
                    } catch (Exception e) {
                        show($"converting {fromType} {fromN} to {toType} produced: {e}");
                        throw e;
                    }

                    // the result of the conversion must be identical (whether null or integer value)
                    assert toN == identicalTo as $"converting {fromType} {fromN} to {toType} produced {identicalTo} but the literal {lit} produced {toN}";

                    // second, we'll try with the "checkBounds=False", which should always succeed,
                    // and if the first succeeds then the second must be the identical value; there
                    // is one weird exception to this rule, which is trying to convert a negative
                    // value to a UIntN, which should always fail (because there is no fixed number
                    // of bits for a UIntN, and thus no result that makes sense)
                    toType.DataType? forcedTo = Null;
                    try {
                        forcedTo = convTo.bindTarget(fromN)();
                    } catch (OutOfBounds _) {
                        assert toType == UIntN && fromN.negative;
                    } catch (Exception e) {
                        assert as $"converting {fromType} {fromN} to {toType} produced: {e}";
                    }

                    assert identicalTo? == forcedTo;

                    // verify that the bits in forcedTo are the least significant bits from `fromN`
                    if (forcedTo != Null) {
                        Bit[] fromBits  = fromN.bits;
                        Bit[] truncBits = forcedTo.bits;
                        Int   fromSize  = fromBits.size;
                        Int   truncSize = truncBits.size;
                        Int   overlap   = fromSize.notGreaterThan(truncSize);
                        assert fromBits[fromSize-overlap..<fromSize] == truncBits[truncSize-overlap..<truncSize];
                    }
                }
            }
        }
    }
}

