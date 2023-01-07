/**
 * A test for ConstOrdinalList.
 */
module ConstOrdinalListTest
    {
    import ecstasy.collections.ConstOrdinalList;
    import ecstasy.numbers.PseudoRandom;

    @Inject Console console;

    void run(String[] args=[])
//    void run(String[] args=["dump", "[30159670, 30159670, 182002775, 182002775, 182002775]"])
//    void run(String[] args=["dump", "[30159670, 30159670, 182002775, 182002775, 182002775, 298647733, 298647733, 298647733, 298647733, 298647733, 298647733, 298647733, 298647733]"])
//    void run(String[] args=["dump", "[30159670, 30159670, 182002775, 182002775, 182002775, 298647733, 298647733, 298647733, 298647733, 298647733, 298647733, 298647733, 298647733, 30159670, 30159670]"])
        {
        if (args.empty)
            {
            for (UInt64 i : 0 ..< 1000)
                {
                Random rnd = new PseudoRandom(i+1);

                Int max = 1+rnd.int(1+rnd.int(Int32.MaxValue));
                Int dft = rnd.int(max);
                test(i, max, dft, rnd);
                }
            }
        else
            {
            // for test reproducers:
            // e.g. "[4, 27, 27, 27, 27, 13, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 4, 4, 4]"
            String  s    = args[0];
            Boolean dump = False;
            if (s.startsWith("d") || s.startsWith("D"))
                {
                dump = True;
                s    = args[1];
                }

            if (s.startsWith('['))
                {
                s = s.substring(1);
                }
            if (s.endsWith(']'))
                {
                s = s[0 ..< s.size-1];
                }

            Int[] list = s.trim() == ""
                    ? []
                    : s.split(',').map(s -> new Int(s.trim()), new Int[]).as(Int[]);

            assert:debug;
            validate(list, 0, dump);
            }
        }

    void test(Int testNum, Int max, Int dft, Random rnd)
        {
        Int[] list = new Int[];
        for (Int i : 0 ..< 10)
            {
            try
                {
                validate(list, testNum*10+i, False);

                if (rnd.boolean())
                    {
                    for (Int add : 0 .. rnd.int(1+rnd.int(12)))
                        {
                        list.add(dft);
                        }
                    }
                else if (rnd.boolean())
                    {
                    Int val = rnd.int(max);
                    for (Int add : 0 .. rnd.int(1+rnd.int(25)))
                        {
                        list.add(val);
                        }
                    }
                else
                    {
                    for (Int add : 0 .. rnd.int(1+rnd.int(20)))
                        {
                        list.add(rnd.int(max));
                        }
                    }
                }
            catch (Exception e)
                {
                console.println($"Exception with list: {list}");
                throw e;
                }
            }
        }

    void validate(Int[] list, Int testNum, Boolean dump)
        {
        console.println($"Test #{testNum}: {list}");

        ConstOrdinalList col = new ConstOrdinalList(list);
        validate(list, col, testNum, "", dump);

        for (Int fast = 5; fast < 100; fast *= 2)
            {
            if (fast > list.size)
                {
                break;
                }

            col = new ConstOrdinalList(col.toArray(), fast);
            validate(list, col, testNum, $"fast={fast}", dump);
            }
        }

    void validate(Int[] list, ConstOrdinalList col, Int testNum, String desc, Boolean dump)
        {
        Int len = col.contents.size;

        console.println($|Test #{testNum}{desc == "" ? desc : $" ({desc})"}, size={col.size}, \
                         |bytes={len}, compression={calcPct(list.size * 4 + 4, len)}
                       );
        if (dump)
            {
            console.println(col.contents.toHexDump(40));
            }

        assert list.empty == col.empty;
        assert list.size == col.size;
        // TODO GG: assert List<Int>.equals(list, col);
        assert list.as(List<Int>) == col.as(List<Int>);

        for (Int i : 0 ..< list.size)
            {
            assert list[i] == col[i];
            }
        }

    String calcPct(Int from, Int to)
        {
        Int pct = (to - from) * 100 / from;
        return $"{pct < 0 ? "" : "+"}{pct}%";
        }
    }