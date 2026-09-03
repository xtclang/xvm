module TestNumbers {
    @Inject ecstasy.io.Console console;

    void run() {
        testUInt64();
        testByte();
        testInt128();
        testUInt128();
        testFloat64();
        testFloat32();
        testFloat16();
        testDec64();
        testInfinity();
        testConverter();
        testAggregator();
        testDec28();
        // testIntParsing();
    }

    void testUInt64() {
        console.print("\n** testUInt()");

        UInt64 n1 = 42;
        console.print("n1=" + n1);

        Bit[] bits = n1.toUInt64().toBitArray();
        UInt64 n11  = new UInt64(bits);
        assert n11 == n1;

        Byte[] bytes = n1.toByteArray();
        UInt64 n12   = new UInt64(bytes);
        assert n12 == n1;

        UInt64 n2 = 0xFFFF_FFFF_FFFF_FFFF;
        console.print("n2=" + n2);
        console.print("-1=" + (--n2));
        console.print("+1=" + (++n2));
        console.print("+1=" + (++n2));

        UInt64 d3 = n2 / 1000;
        console.print("d3=" + d3);
        console.print("n3=" + (d3*1000 + n2 % 1000));

        Int64 un1 = MaxValue;
        Int64 un2 = un1 + 1;
        assert un2 == Int64.MinValue; // wraps around w/out exception

        UInt64 un3 = MaxValue;
        UInt64 un4 = ++un3;
        assert un4 == 0;
    }

    void testByte() {
        console.print("\n** testByte()");

        Byte n1 = 42;
        console.print("n1=" + n1);

        Byte n2 = 0xFF;
        console.print("n2=" + n2);
        console.print("-1=" + (--n2));
        console.print("+1=" + (++n2));
        console.print("+1=" + (++n2));

        Byte d3 = n2 / 10;
        console.print("d3=" + d3);
        console.print("n3=" + (d3*10 + n2 % 10));

        // Byte == UInt8
        Byte un1 = MaxValue;
        Byte un2 = un1 + 1;

        assert un2 == 0; // wraps around w/out exception

        Int8 un3 = MaxValue;
        Int8 un4 = ++un3;
        assert un4 == Int8.MinValue;
    }

    void testInt128() {
        console.print("\n** testInt128()");

        Int128 n1 = 42;
        console.print("n1=" + n1);

        Int128 n2 = 0x7FFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;
        console.print("n2=" + n2);
        console.print("-1=" + (--n2));
        console.print("+1=" + (++n2));

        Int128 d3 = n2 / 1000;
        console.print("d3=" + d3);
        console.print("n3=" + (d3*1000 + n2 % 1000));

        console.print("-------");

        Int128 n4 = -n2 - 1;
        console.print("n4=" + n4);
        console.print("+1=" + (++n4));
        console.print("-1=" + (--n4));

        Int128 d4 = n4 / 1000;
        console.print("d4=" + d4);
        console.print("n4=" + (d4*1000 - (1000 - n4 % 1000))); // mod is not a remainder

        try {
            n2++;
            assert;
        } catch (Exception e) {}
    }

    void testUInt128() {
        console.print("\n** testUInt128()");

        UInt128 n1 = 42;
        console.print("n1=" + n1);

        UInt128 n2 = 0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;
        console.print("n2=" + n2);
        console.print("-1=" + (--n2));
        console.print("+1=" + (++n2));

        UInt128 d3 = n2 / 1000;
        console.print("d3=" + d3);
        console.print("n3=" + (d3*1000 + n2 % 1000));

        try {
            n2++;
            assert;
        } catch (Exception e) {}
    }

    void testFloat64() {
        console.print("\n** testFloat64()");

        Float64 n1 = 4.2;
        console.print("n1=" + n1);

        Byte[]  bytes = n1.toByteArray();
        Float64 n11   = new Float64(bytes);
        assert n11 == n1;

        Bit[]   bits = n1.toBitArray();
        Float64 n12  = new Float64(bits);
        assert n12 == n1;

        Float64 n2 = n1 + 1;
        console.print("-1=" + n2);
        console.print("+1=" + (n2 - 1));

        Float64 n3 = n1*10;
        console.print("*10=" + n3);
        console.print("/10=" + (n3 / 10));

        console.print("PI=" + FPNumber.PI);
        Float64 pi64 = FPNumber.PI;
        console.print("pi64=" + pi64);

        // see http://www.cplusplus.com/reference/cmath/round/
        Float64[] floats = [2.3, 3.8, 5.5, -2.3, -3.8, -5.5];

        console.print();
        console.print("value\tround\tfloor\tceil\ttoZero");
        console.print("-----\t-----\t-----\t----\t-----");
        for (Float64 f : floats) {
            console.print($"{f},\t{f.round()},\t{f.floor()},\t{f.ceil()},\t{f.round(TowardZero)}");
        }
    }

    void testFloat32() {
        console.print("\n** testFloat32()");

        Float32 n1 = 4.2;
        console.print("n1=" + n1);

        Byte[]  bytes = n1.toByteArray();
        Float32 n11   = new Float32(bytes);
        assert n11 == n1;

        Bit[]   bits = n1.toBitArray();
        Float32 n12  = new Float32(bits);
        assert n12 == n1;

        Float32 pi32 = FPNumber.PI;
        console.print("pi32=" + pi32);
    }

    void testFloat16() {
        console.print("\n** testFloat16()");

        Float16 n1 = 4.2;
        console.print("n1=" + n1);

        Byte[]  bytes = n1.toByteArray();
        Float16 n11   = new Float16(bytes);
        assert n11 == n1;

        Bit[]   bits = n1.toBitArray();
        Float16 n12  = new Float16(bits);
        assert n12 == n1;

        Float16 pi16 = FPNumber.PI;
        console.print("pi16=" + pi16);
    }

    void testDec64() {
        console.print("\n** testDec64()");

        Dec64 n1 = 4.2;
        console.print("n1=" + n1);

        Byte[] bytes = n1.toByteArray();
        Dec64  n11   = new Dec64(bytes);
        assert n11 == n1.toDec64();

        Bit[]  bits = n1.toBitArray();
        Dec64  n12  = new Dec64(bits);
        assert n12 == n1;

        Dec64 n2 = n1 + 1;
        console.print("-1=" + n2);
        console.print("+1=" + (n2 - 1));

        Dec64 n3 = n1*10;
        console.print("*10=" + n3);
        console.print("/10=" + (n3 / 10));

        console.print("PI=" + FPNumber.PI);
        Dec64 pi64 = FPNumber.PI;
        console.print("pi64=" + pi64);

        // see http://www.cplusplus.com/reference/cmath/round/
        Dec64[] numbers = [2.3, 3.8, 5.5, -2.3, -3.8, -5.5];

        console.print();
        console.print("value\tround\tfloor\tceil\ttoZero");
        console.print("-----\t-----\t-----\t----\t-----");
        for (Dec64 d : numbers) {
            console.print($"{d},\t{d.round()},\t{d.floor()},\t{d.ceil()},\t{d.round(TowardZero)}");
        }
    }

    void testInfinity() {
        console.print("\n** testInfinity()");

        Float64 f = -123456789.987654321;
        Dec64   d = f.toDec64();
        while (True) {
            console.print($"f={f} d={d}");
            if (f.infinity) {
                console.print($"++: {f + f}\t{d + d}");
                console.print($"--: {f - f}\t{d - d}");
                console.print($"**: {f * f}\t{d * d}");
                console.print($"//: {f / f}\t{d / d}");
                console.print($"+1: {f + 1}\t{d + 1}");
                console.print($"-1: {f - 1}\t{d - 1}");
                console.print($"1/: {1 / f}\t{1 / d}");

                console.print($"ln: {f.log()}\t{d.log()}");
                break;
            }

            d = f.toDec64();
            f = -f*f;
            d = -d*d;
        }
    }

    void testConverter() {
        function Byte(Int) convert = Number.converterFor(Int, Byte);

        assert convert(3) == Byte:3;
        assert convert(45) == Byte:45;

        Int     n = 42;
        Float64 f = n.toFloat64();
        console.print($"int={n}, float64={f}");
        function Float64(Int) convert2 = Number.converterFor(Int, Float64);
        console.print($"using converter: int={n}, float64={convert2(n)}");

        Int64[] ints  = [1, 2, 3];
        Bit[]   bits  = ints.asBitArray();
        Byte[]  bytes = ints.asByteArray();

        assert bits.toByteArray().toInt64Array() == ints;
        assert bits.reify(Mutable).toByteArray().toInt64Array() == ints;
        assert bytes.toInt64Array() == ints;
        assert bytes.reify(Fixed).toInt64Array() == ints;

        Int64[] slice = ints[1..2];
        assert slice.asByteArray().asInt64Array() == slice;
        assert slice.asByteArray().reify().asInt64Array() == slice;

        ints = ints.reify(Mutable);
        bits = ints.asBitArray();
        bits[63] = 0;
        assert ints[0] == 0;
        bytes = ints.asByteArray();
        bytes[7] = 1;
        assert ints[0] == 1;

        bytes[0] = 255;
        assert bytes.asInt8Array()[0] == -1;
    }

    package agg import aggregate.xtclang.org;

    void testAggregator() {
        import agg.*;

        console.print("\n** testAggregator()");

        Sum<Int>             sum = new Sum();
        Average<Int, Double> avg = new Average();
        Min<Int>             min = new Min();
        Max<Int>             max = new Max();
        MinMax<Int>          mmx = new MinMax();

        Int[] empty = [];
        assert empty.reduce(sum) == 0;
        assert empty.reduce(min) == Null;
        assert empty.reduce(avg) == Null;

        Partition[] partitions = new Partition[10](i -> new Partition(i));

        @Volatile val finishSum = sum.finalAggregator.init();
        @Volatile Int remainSum = partitions.size;
        @Volatile val finishAvg = avg.finalAggregator.init();
        @Volatile Int remainAvg = partitions.size;
        @Volatile val finishMin = min.finalAggregator.init();
        @Volatile Int remainMin = partitions.size;
        @Volatile val finishMax = max.finalAggregator.init();
        @Volatile Int remainMax = partitions.size;
        @Volatile val finishMMx = mmx.finalAggregator.init();
        @Volatile Int remainMMx = partitions.size;

        Loop: for (Partition partition : partitions) {
            @Future sum.Partial pendingSum = partition.exec(sum);
            @Future avg.Partial pendingAvg = partition.exec(avg);
            @Future min.Partial pendingMin = partition.exec(min);
            @Future max.Partial pendingMax = partition.exec(max);
            @Future mmx.Partial pendingMMx = partition.exec(mmx);

            &pendingSum.handle(e -> {
                    console.print($"exception during partition {partition.id} processing: {e}");
                    return 0;
            })
                .passTo(partial -> {
                    finishSum.add(partial);
                    if (--remainSum <= 0) {
                        console.print($"sum result={sum.finalAggregator.reduce(finishSum)}");
                }
            });

            &pendingAvg.handle(e -> {
                    console.print($"exception during partition {partition.id} processing: {e}");
                    return avg.elementAggregator.reduce(avg.elementAggregator.init());
            })
                .passTo(partial -> {
                    finishAvg.add(partial);
                    if (--remainAvg <= 0) {
                        console.print($"avg result={avg.finalAggregator.reduce(finishAvg)}");
                }
            });

            &pendingMin.handle(e -> {
                    console.print($"exception during partition {partition.id} processing: {e}");
                    return min.elementAggregator.reduce(min.elementAggregator.init());
            })
                .passTo(partial -> {
                    finishMin.add(partial);
                    if (--remainMin <= 0) {
                        console.print($"min result={min.finalAggregator.reduce(finishMin)}");
                }
            });

            &pendingMax.handle(e -> {
                    console.print($"exception during partition {partition.id} processing: {e}");
                    return max.elementAggregator.reduce(max.elementAggregator.init());
            })
                .passTo(partial -> {
                    finishMax.add(partial);
                    if (--remainMax <= 0) {
                        console.print($"max result={max.finalAggregator.reduce(finishMax)}");
                }
            });

            &pendingMMx.handle(e -> {
                    console.print($"exception during partition {partition.id} processing: {e}");
                    return mmx.elementAggregator.reduce(mmx.elementAggregator.init());
            })
                .passTo(partial -> {
                    finishMMx.add(partial);
                    if (--remainMMx <= 0) {
                        console.print($"min/max result={mmx.finalAggregator.reduce(finishMMx)}");
                }
            });
        }
    }

    service Partition(Int id) {
        import ecstasy.collections.ParallelAggregator;

        construct(Int id) {
            this.id = id;

            Random rnd = new ecstasy.numbers.PseudoRandom(id.toUInt64()+1);
            data = new Int[10](_ -> rnd.int(0..100));
        }

        public/private Int id;
        public/private Int[] data;

        <Partial> Partial exec(ParallelAggregator<Int, Partial> parallel) {
            return data.reduce(parallel.elementAggregator);
        }
    }

    // this test is not currently running as a part of the functional test suite
    void testDec28() {
        console.print("\n** testDec28()");

        String[] literals = ["0", "1", "123", "123.45", "1.234567", "0.0001234567",
                             ".00001234500", "1234000"];
        String[] expected = ["0", "1", "123", "123.45", "1.234567", "0.0001234567",
                             "0.000012345", "1234000"];
        assert literals.size == expected.size;

        for (Int i : 0 ..< literals.size) {
            String literal = literals[i];
            String actual  = new Dec28(literal).toString();
            assert actual == expected[i] as $|Dec28("{literal}") rendered "{actual}", \
                                             |expected "{expected[i]}"
                                            ;
        }
    }

    void testIntParsing() {
        assert Int x := Int.parse("1k");

        String[] nums = ["", "0", "-0", "+0", "_0", "0_", "0.", ".0", "+0_", "1", "123", "0x123",
                         "0o123", "0b123", "1k", "1_1k", "1_1kb", "1_2kib", "0m", "1m",
                         "63k", "64k", "65k", "63ki", "64ki", "65535", "6__5__5__3__5", "65536", "6_5_5_3_6"];

        console.print($|{"string"   .center(12)} \
                       |{"parse"    .center(12)} \
                       |{"parse r2" .center(12)} \
                       |{"parse r8" .center(12)} \
                       |{"parse r10".center(12)} \
                       |{"parse r16".center(12)} \
                       |{"parse r36".center(12)}
                     );
        console.print(('-'.dup(12) + ' ').dup(7));

        Int?[] TestRadixes = [Null, 2, 8, 10, 16, 36];
        for (String num : nums) {
            StringBuffer buf = new StringBuffer();
            buf.append(num.quoted().leftJustify(12));
            for (Int? radix : TestRadixes) {
                if (UInt16 n := UInt16.parse(num, radix)) {
                    buf.add(' ')
                       .append(n.toString().rightJustify(12));
                } else {
                    buf.add(' ').append("err".center(12));
                }
            }
            console.print(buf);

            buf.clear();
            buf.append("Int64".center(12));
            for (Int? radix : TestRadixes) {
                if (Int n := Int.parse(num, radix)) {
                    buf.add(' ')
                       .append(n.toString().rightJustify(12));
                } else {
                    buf.add(' ').append("err".center(12));
                }
            }
            console.print(buf);
        }
    }


/**
 * Implementation details:
 *
 *     S --G (combo)-- -------T (trailing significand)--------
 *       ---w+5 bits-- ----------J*10 bits--------------------
 *       G0         G6
 *     0 1 2 3 4 5 6 7 8                                    27
 *
 *     2 2 2 2 2 2 2 2 1 1 1 1 1 1 1 1 1 1 0 0 0 0 0 0 0 0 0 0
 *     7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0 9 8 7 6 5 4 3 2 1 0
 *
 *     J=2
 *     k=28    1+5+w+t     =32×ceiling((p+2)/9)    storage width, in bits
 *     bias    E−q         =emax+p–2
 *     w=2     k–t−6       =k/16+4
 *     w+5=7               =k/16+9                 combination field width in bits
 *     t=20    k–w−6       =15×k/16−10             trailing significand field width in bits
 *     p=7     3×t/10+1    =9×k/32−2               precision, in digits
 *     emax=6  3×2^(w−1)
 *     emin=-5 1 − emax
 *     bias=   emax+p−2
 *
 *     i) If G0 and G1 together are one of 00, 01, or 10, then the biased exponent E
 *        is formed from G0 through Gw+1 (G3) and the significand is formed from bits
 *        Gw+2 (G4) through the end of the encoding (including T).
 *        -> 4 bits of exponent
 *     ii) If G0 and G1 together are 11 and G2 and G3 together are one of 00, 01, or 10,
 *         then the biased exponent E is formed from G2 through Gw+3 (G5) and the
 *         significand is formed by prefixing the 4 bits (8+G(w+4)) (8+G6) to T.
 *         -> 4 bits of exponent
 *
 *     ?11110...   ∞
 *     011110...   +∞
 *     111110...   -∞
 *     ?11111...   NaN
 *     011111...   +NaN
 *     111111...   -NaN
 *     0111111...  +sNaN
 *     1111111...  -sNaN
 *     0111110...  +qNaN
 *     1111110...  -qNaN
 */
const Dec28(Bit[] bits) {
    construct(Bit[] bits) {
        assert:arg bits.size == 28;
        this.bits = bits.freeze();
        }

    construct(String lit) {
        Boolean neg = False;
        UInt32  sig = 0;
        Boolean dot = False;
        Int     ldc = 0;        // leading digit count
        Int     lzc = 0;        // leading zero count (after the dot, before any non-zero digits)
        Int     tdc = 0;        // trailing digit count
        Boolean any = False;
        Loop: for (Char ch : lit) {
            switch (ch) {
                case '+':
                    assert Loop.count == 0;
                    break;

                case '-':
                    assert Loop.count == 0;
                    neg = True;
                    break;

                case '.':
                    assert !dot;
                    dot=True;
                    break;

                case '0'..'9':
                    any = True;
                    sig = sig * 10 + (ch - '0');
                    if (dot) {
                        if (sig > 0) {
                            ++tdc;
                        } else {
                            ++lzc;
                        }
                    } else if (sig > 0) { // ignore leading zeros
                        ++ldc;
                    }
                    break;

                default:
                    assert as $"unexpected character: {ch.quoted()}";
                }
            }

        assert ldc <= 7            as $"too many digits ({ldc})";
        assert any                 as "no digits";
        assert !dot || lzc+tdc > 0 as "no digits encountered after decimal point";

        Int digits = ldc + lzc + tdc;
        switch (digits) {
            case 0:
                construct Dec28(neg ? NegZero.bits : PosZero.bits);
                return;

            case 1..6:
                while (digits++ < 7) {
                    // add a trailing zero
                    sig *= 10;
                }
                continue;
            case 7:
                construct Dec28(neg, sig, (ldc - 1 - lzc).toInt8());
                return;
            }

        // 8 or more digits; test if some were trailing zeros
        while (True) {
            (val newSig, val digit) = sig /% 10;
            if (digit != 0) {
                break;
                }

            sig = newSig;
            if (--digits == 7) {
                construct Dec28(neg, sig, (ldc - 1 - lzc).toInt8());
                }
            assert --tdc >= 0;
            }

        // only other thing allowed is if it's a small fraction with leading zeros
        assert ldc == 0 && tdc <= 7 as $"too many significant digits ({digits})";
        assert lzc + tdc <= 11      as $|too many digits after the decimal point ({lzc} leading \
                                        |zeros followed by {tdc} non-zero digits)
                                       ;
        construct Dec28(neg, sig, (6 - tdc - lzc).toInt8());
        }

    construct(Boolean negative, UInt32 significand, Int8 exponent) {
        Bit[] bits = new Bit[28];

        assert:arg significand < 10_000_000;
        assert:arg -5 <= exponent <= 6;

        if (negative) {
            bits[0] = 1;
            }

        if (significand != 0) {
            // adjust exponent such that the minimum exponent is stored as a 0
            exponent += 5;

            // separate out the first digit, and copy the remaining 20 bits of data
            UInt32 highBits = significand >> 20;          // <4 bits = ~1 digit
            significand    &= significand & 0x0FFFFF;     // 20 bits = 6+ digits
            bits.replaceAll(8, significand.toBitArray()[12..<32]);

            if (highBits >= 8) {
                bits[1] = 1;
                bits[2] = 1;
                bits.replaceAll(3, exponent.toBitArray()[4..<8]);
                bits[7] = highBits.toBitArray()[31];
                } else {
                bits.replaceAll(1, exponent.toBitArray()[4..<8]);
                bits.replaceAll(5, highBits.toBitArray()[29..<32]);
                }
            }

        construct Dec28(bits);
        }

    // ----- internal -----

    static Dec28 PosZero     = new Dec28([0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 NegZero     = new Dec28([1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 PosNaN      = new Dec28([0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 NegNaN      = new Dec28([1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 PosInfinity = new Dec28([0,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    static Dec28 NegInfinity = new Dec28([1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);

    Dec28 normalize() {
        if (!finite) {
            if (infinity) {
                return negative ? NegInfinity : PosInfinity;
                }

            assert NaN;
            return negative ? NegNaN : PosNaN;
            }

        if (significand == 0) {
            return negative ? NegZero : PosZero;
            }

        // TODO?
        return this;
        }

    Boolean S.get() {
        return bits[0].toBoolean();
        }

    Boolean G0.get() {
        return bits[1].toBoolean();
        }

    Boolean G1.get() {
        return bits[2].toBoolean();
        }

    Boolean G2.get() {
        return bits[3].toBoolean();
        }

    Boolean G3.get() {
        return bits[4].toBoolean();
        }

    Boolean G4.get() {
        return bits[5].toBoolean();
        }

    Boolean G5.get() {
        return bits[6].toBoolean();
        }

    Boolean G6.get() {
        return bits[7].toBoolean();
        }

    Int emax.get() {
        return 6;
        }

    Int emin.get() {
        return 1-emax;
        }

    Int bias.get() {
        // emax+p−2 == 11
        return emax+5;
        }

    // -----

    Boolean finite.get() {
        return !(G1 && G2 && G3 && G4);
        }

    Boolean infinity.get() {
        return G1 && G2 && G3 && G4 && !G5;
        }

    Boolean NaN.get() {
        return G1 && G2 && G3 && G4 && G5;
        }

    Signum sign.get() {
        if (finite && significand == 0) {
            return Zero;
            }

        return negative ? Negative : Positive;
        }

    Boolean negative.get() {
        return S;
        }

    (Boolean negative, UInt32 significand, Int8 exponent) split() {
        return negative, significand, exponent;
        }

    UInt32 significand.get() {
        // if G0 and G1 together are one of 00, 01, or 10, then the significand is formed from bits
        // Gw+2 (G4) through the end of the encoding (including T).
        if (!(G0 & G1)) {
            return bits[5..<28].toUInt32();
            }

        // otherwise, if G2 and G3 together are one of 00, 01, or 10, then the significand is formed
        // by prefixing the 4 bits (8+G(w+4)) (8+G6) to T.
        if (!(G2 & G3)) {
            return 0x800000 | bits[7..<28].toUInt32();
            }

        return 0;
        }

    Int8 exponent.get() {
        // if G0 and G1 together are one of 00, 01, or 10, then the biased exponent E is formed from
        // G0 through Gw+1 (G3)
        if (!(G0 & G1)) {
            return bits[1..4].toUInt8().toInt8() + emin.toInt8();
            }

        // otherwise, if G2 and G3 together are one of 00, 01, or 10, then the biased exponent E is
        // formed from G2 through Gw+3 (G5)
        if (!(G2 & G3)) {
            return bits[3..6].toUInt8().toInt8() + emin.toInt8();
            }

        return 0;
        }

    // ----- operators -----------------------------------------------------------------------------

    @Op("-#")
    Dec28 neg() {
        return new Dec28(bits.replace(0, ~bits[0]));
        }

    @Op("+")
    Dec28 add(Dec28 n) {
        if (!(finite && n.finite)) {
            if (NaN) {
                return this;
                }

            if (n.NaN) {
                return n;
                }

            if (infinity) {
                return this;
                }

            assert n.infinity;
            return n;
            }

        (Boolean neg1, UInt32 sig1, Int8 exp1) = this.split();
        if (sig1 == 0) {
            return n;
            }

        (Boolean neg2, UInt32 sig2, Int8 exp2) = n.split();
        if (sig2 == 0) {
            return this;
            }

        if (exp1 != exp2) {
            // TODO scale
            }

        if (neg1 == neg2) {
            sig1 += sig2;
            } else if (sig2 > sig1) {
            sig1 = sig2 - sig1;
            neg1 = !neg1;
            } else {
            sig1 -= sig2;
            }

        if (sig1 > 9999999) {
            if (++exp1 > emax) {
                return neg1 ? NegInfinity : PosInfinity;
                }
            sig1 /= 10;
            }

        return new Dec28(neg1, sig1, exp1);
        }

    @Op("-")
    Dec28 sub(Dec28 n) {
        TODO return new Dec28();
        }

    @Op("*")
    Dec28 mul(Dec28 n) {
        TODO return new Dec28();
        }

    @Op("/")
    Dec28 div(Dec28 n) {
        TODO return new Dec28();
        }

    @Op("%")
    Dec28 mod(Dec28 n) {
        TODO return new Dec28();
        }

    @Override
    String toString() {
        if (!finite) {
            return switch (negative, NaN) {
                    case (False, False): "+∞";
                    case (False, True ): "+NaN";
                    case (True , False): "-∞";
                    case (True , True ): "-NaN";
                };
            }

        (Boolean neg, UInt32 sig, Int8 exp) = this.split();
        if (sig == 0) {
            return neg ? "-0" : "0";
            }

        StringBuffer buf = new StringBuffer(14);
        if (neg) {
            buf.append('-');
            }

        // render up to the first digit of the significand
        Int pow = exp;
        switch (pow <=> -1) {
            case Lesser:
                // for a significand of 1234567, the value is e.g. 0.00001234567
                buf.append('0')
                   .append('.');

                while(pow < -1) {
                    buf.append('0');
                    ++pow;
                }
               break;

            case Equal:
                // for a significand of 1234567, the value is 0.1234567
                buf.append('0');
                break;

            case Greater:
                // begin rendering at the first digit of the significand
                pow = -1;
                break;
            }
        assert pow == -1;     // TODO delete this line eventually

        // render the significand
        static UInt32[] pows = [1000000, 100000, 10000, 1000, 100, 10, 1];
        while (sig != 0 && pow < 6) {
            if (pow == exp) {
                buf.append('.');
                }

            UInt32 digit;
            if (++pow < 6) {
                (digit, sig) = sig /% pows[pow];
                } else {
                digit = sig;
                }

            buf.append(digit);
            }

        // finish any trailing zeros to the left of the decimal point
        while (pow++ < exp) {
            buf.append('0');
            }

        return buf.toString();
        }

    // TODO equals hashcode compare
    }

}
