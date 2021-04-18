module TestNumbers
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        testUInt();
        testByte();
        testInt128();
        testUInt128();
        testFloat64();
        testFloat32();
        testFloat16();
        testDec64();
        testInfinity();
        testAggregator();
        }

    void testUInt()
        {
        console.println("\n** testUInt()");

        UInt n1 = 42;
        console.println("n1=" + n1);

        Bit[] bits = n1.toBitArray();
        UInt  n11  = new UInt(bits);
        assert n11 == n1;

        Byte[] bytes = n1.toByteArray();
        UInt   n12   = new UInt(bytes);
        assert n12 == n1;

        UInt n2 = 0xFFFF_FFFF_FFFF_FFFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        UInt d3 = n2 / 1000;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*1000 + n2 % 1000));

        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            }

        Int un1 = Int.maxvalue.toInt64().toUnchecked();
        Int un2 = un1 + 1;

        assert un2 == Int.minvalue; // wraps around w/out exception
        assert un2.is(@Unchecked Int);

        UInt un3 = UInt.maxvalue.toUInt64().toUnchecked();
        UInt un4 = ++un3;
        assert un4 == 0;

        assert un4.is(@Unchecked UInt);
        }

    void testByte()
        {
        console.println("\n** testByte()");

        Byte n1 = 42;
        console.println("n1=" + n1);

        Byte n2 = 0xFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        Byte d3 = n2 / 10;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*10 + n2 % 10));

        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            }

        // Byte == UInt8
        Byte un1 = Byte.maxvalue.toUInt8().toUnchecked();
        Byte un2 = un1 + 1;

        assert un2 == 0; // wraps around w/out exception
        assert un2.is(@Unchecked Byte);

        Int8 un3 = Int8.maxvalue.toInt8().toUnchecked();
        Int8 un4 = ++un3;
        assert un4 == Int8.minvalue;

        assert un4.is(@Unchecked Int8);
        }

    void testInt128()
        {
        console.println("\n** testInt128()");

        Int128 n1 = 42;
        console.println("n1=" + n1);

        Int128 n2 = 0x7FFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        Int128 d3 = n2 / 1000;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*1000 + n2 % 1000));

        console.println("-------");

        Int128 n4 = -n2 - 1;
        console.println("n4=" + n4);
        console.println("+1=" + (++n4));
        console.println("-1=" + (--n4));

        Int128 d4 = n4 / 1000;
        console.println("d4=" + d4);
        console.println("n4=" + (d4*1000 + n4 % 1000));

        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            }
        }

    void testUInt128()
        {
        console.println("\n** testUInt128()");

        UInt128 n1 = 42;
        console.println("n1=" + n1);

        UInt128 n2 = 0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;
        console.println("n2=" + n2);
        console.println("-1=" + (--n2));
        console.println("+1=" + (++n2));

        UInt128 d3 = n2 / 1000;
        console.println("d3=" + d3);
        console.println("n3=" + (d3*1000 + n2 % 1000));

        try
            {
            n2++;
            assert;
            }
        catch (Exception e)
            {
            }
        }

    void testFloat64()
        {
        console.println("\n** testFloat64()");

        Float n1 = 4.2;
        console.println("n1=" + n1);

        Byte[] bytes = n1.toByteArray();
        Float  n11   = new Float(bytes);
        assert n11 == n1;

        Bit[]  bits = n1.toBitArray();
        Float  n12  = new Float(bits);
        assert n12 == n1;

        Float n2 = n1 + 1;
        console.println("-1=" + n2);
        console.println("+1=" + (n2 - 1));

        Float n3 = n1*10;
        console.println("*10=" + n3);
        console.println("/10=" + (n3 / 10));

        console.println("PI=" + FPNumber.PI);
        Float pi64 = FPNumber.PI;
        console.println("pi64=" + pi64);

        // see http://www.cplusplus.com/reference/cmath/round/
        Float[] floats = [2.3, 3.8, 5.5, -2.3, -3.8, -5.5];

        console.println();
        console.println("value\tround\tfloor\tceil\ttoZero");
        console.println("-----\t-----\t-----\t----\t-----");
        for (Float f : floats)
            {
            console.println($"{f},\t{f.round()},\t{f.floor()},\t{f.ceil()},\t{f.round(TowardZero)}");
            }
        }

    void testFloat32()
        {
        console.println("\n** testFloat32()");

        Float32 n1 = 4.2;
        console.println("n1=" + n1);

        Byte[]  bytes = n1.toByteArray();
        Float32 n11   = new Float32(bytes);
        assert n11 == n1;

        Bit[]   bits = n1.toBitArray();
        Float32 n12  = new Float32(bits);
        assert n12 == n1;

        Float32 pi32 = FPNumber.PI;
        console.println("pi32=" + pi32);
        }

    void testFloat16()
        {
        console.println("\n** testFloat16()");

        Float16 n1 = 4.2;
        console.println("n1=" + n1);

        Byte[]  bytes = n1.toByteArray();
        Float16 n11   = new Float16(bytes);
        assert n11 == n1;

        Bit[]   bits = n1.toBitArray();
        Float16 n12  = new Float16(bits);
        assert n12 == n1;

        Float16 pi16 = FPNumber.PI;
        console.println("pi16=" + pi16);
        }

    void testDec64()
        {
        console.println("\n** testDec64()");

        Dec n1 = 4.2;
        console.println("n1=" + n1);

        Byte[] bytes = n1.toByteArray();
        Dec  n11   = new Dec(bytes);
        assert n11 == n1;

        Bit[]  bits = n1.toBitArray();
        Dec  n12  = new Dec(bits);
        assert n12 == n1;

        Dec n2 = n1 + 1;
        console.println("-1=" + n2);
        console.println("+1=" + (n2 - 1));

        Dec n3 = n1*10;
        console.println("*10=" + n3);
        console.println("/10=" + (n3 / 10));

        console.println("PI=" + FPNumber.PI);
        Dec pi64 = FPNumber.PI;
        console.println("pi64=" + pi64);

        // see http://www.cplusplus.com/reference/cmath/round/
        Dec[] numbers = [2.3, 3.8, 5.5, -2.3, -3.8, -5.5];

        console.println();
        console.println("value\tround\tfloor\tceil\ttoZero");
        console.println("-----\t-----\t-----\t----\t-----");
        for (Dec d : numbers)
            {
            console.println($"{d},\t{d.round()},\t{d.floor()},\t{d.ceil()},\t{d.round(TowardZero)}");
            }
        }

    void testInfinity()
        {
        console.println("\n** testInfinity()");

        Float f = -123456789.987654321;
        Dec   d = f.toDec64();
        while (True)
            {
            console.println($"f={f} d={d}");
            if (f.infinity)
                {
                console.println($"++: {f + f)}\t{d + d}");
                console.println($"--: {f - f)}\t{d - d}");
                console.println($"**: {f * f)}\t{d * d}");
                console.println($"//: {f / f)}\t{d / d}");
                console.println($"+1: {f + 1)}\t{d + 1}");
                console.println($"-1: {f - 1)}\t{d - 1}");
                console.println($"1/: {1 / f)}\t{1 / d}");

                console.println($"ln: {f.log()}\t{d.log()}");
                break;
                }

            d = f.toDec64();
            f = -f*f;
            d = -d*d;
            }
        }

    package agg import aggregate.xtclang.org;

    void testAggregator()
        {
        import agg.Sum;

        console.println("\n** testAggregator()");

        Partition[] partitions = new Partition[10](i -> new Partition(i));

        Sum<Int> sum = new Sum();
        val finalAccumulator = sum.finalAggregator.init();
        Int remain = partitions.size;
        Loop: for (Partition partition : partitions)
            {
            @Future Int pendingPartial = partition.exec(sum);

// TODO GG
//2021-04-17 23:25:25.344 Service "TestSimple.test.org" (id=0) contended @at <TestSimple.test.org>, fiber 35: Unhandled exception: IllegalState: Un-initialized property "Property{property=notify}"
//	at annotations.FutureVar.chain(Function<Tuple<FutureVar.Completion, Nullable | FutureVar.Referent, Nullable | Exception>, Tuple<>>) (line=452, op=JumpNotNull)
//	at annotations.FutureVar.chain(Type<Object>, this:class(FutureVar).DependentFuture<chain(?)#NewType>) (line=439, op=Invoke_10)
//	at annotations.FutureVar.handle(Function<Tuple<Exception>, Tuple<FutureVar.Referent>>) (line=222, op=Invoke_N1)
//	at run() (line=43, op=Invoke_11)
//	at <TestSimple.test.org> (iPC=0, op=)
//            &pendingPartial.handle(e ->
//                {
//                console.println($"exception during partition {partition.id} processing: {e}");
//                return 0;
//                })
//            .passTo(partial ->
//                {
//                finalAccumulator.add(partial);
//                if (--remain <= 0)
//                    {
//                    console.println($"final result=${sum.finalAggregator.reduce(finalAccumulator)}");
//                    }
//                });
            &pendingPartial.whenComplete((partial, e) ->
                {
                if (e == null)
                    {
                    assert partial != null;
                    }
                else
                    {
                    console.println($"exception during partition {partition.id} processing: {e}");
                    partial = 0;
                    }

                finalAccumulator.add(partial);
                --remain;
                console.println($"remaining partitions: {remain}");
                if (remain <= 0)
                    {
                    console.println($"final result={sum.finalAggregator.reduce(finalAccumulator)}");
                    }
                });
            }
        }

    service Partition(Int id)
        {
        import ecstasy.collections.ParallelAggregator;

        construct(Int id)
            {
            this.id = id;

            Random rnd = new ecstasy.numbers.PseudoRandom(id.toUInt64()+1);
            data = new Int[10](_ -> rnd.int(0..100));
            }

        public/private Int id;
        public/private Int[] data;

        Int exec(ParallelAggregator<Int, Int, Int> parallel)
            {
            return data.reduce(parallel.elementAggregator);
            }
        }

    void testConverter()
        {
        function Byte(Int) convert = Number.converterFor(Int, Byte);

        assert convert(3) == Byte:3;
        assert convert(45) == Byte:45;
        }
    }