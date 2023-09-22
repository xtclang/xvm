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
                console.print($"++: {f + f)}\t{d + d}");
                console.print($"--: {f - f)}\t{d - d}");
                console.print($"**: {f * f)}\t{d * d}");
                console.print($"//: {f / f)}\t{d / d}");
                console.print($"+1: {f + 1)}\t{d + 1}");
                console.print($"-1: {f - 1)}\t{d - 1}");
                console.print($"1/: {1 / f)}\t{1 / d}");

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
}