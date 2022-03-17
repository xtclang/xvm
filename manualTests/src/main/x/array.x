module TestArray
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        testSimple();
        testStrBuf();
        testConstElement();
        testConstSlice();

        testArrayList();
        testArrayListAdd();
        testFixedArray();

        testFormalTypes();

        testAssignSideEffects();
        testNew();
        testNibble();
        testBits();

        testComparable();

        testIterators();

        testConstOrdinalList();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        Int[] nums = [1,7];
        console.println("array=" + nums);
        console.println("size=" + nums.size);
        console.println("[0]=" + nums[0]);
        console.println("[1]=" + nums[1]);
        }

    void testStrBuf()
        {
        console.println("\n** testStrBuf()");

        StringBuffer buf = new StringBuffer();
        buf.append("hello")
          .append(' ')
          .append("world")
          .append('!');

        console.println("buf=" + buf);
        }

    void testConstElement()
        {
        console.println("\n** testConstElement()");

        String cruel = ["hello", "cruel", "world", "!"] [1];
        console.println("array[1]=" + cruel);
        }

    void testConstSlice()
        {
        console.println("\n** testConstSlice()");

        String[] cruel = ["hello", "cruel", "world", "!"] [1..2];
        console.println("array[1..2]=" + cruel);

        String[] cruel2 = ["hello", "cruel", "world", "!"] [2..1];
        console.println("array[2..1]=" + cruel2);
        }

    void testArrayList()
        {
        console.println("\n** testArrayList()");

        String[] list = new String[];
        list[0] = "one";
        list[1] = "two";
        list[2] = "three";

        console.println("list=" + list);

        list = new Array<String>(Mutable, list);
        String one = list.getElement(0);
        list.setElement(0, "uno");
        console.println("list=" + list);

        list = list[1..3);
        console.println("list=" + list);

        list = new Array<String>(Fixed, list[0..1]);
        list.setElement(0, "один");
        list.setElement(1, "два");
        console.println("list=" + list);
        }

    void testArrayListAdd()
        {
        console.println("\n** testArrayListAdd()");

        String?[] list = new String?[];
        list += "one";
        list += ["two", "three"];
        list[4] = "five";

        console.println($"list={list}");

        Int[] ints = new Int[];
        ints += 1;
        ints += [Int:2, Int:3];
        ints[4] = 4;

        console.println($"ints={ints}");

        Byte[] bytes = new Byte[];
        bytes += 1;
        bytes += [Byte:2, Byte:3];
        bytes[9] = 10;

        console.println($"bytes={bytes}");

        Boolean[] bools = new Boolean[];
        bools += True;
        bools += [False, True];
        bools[9] = True;

        console.println($"bools={bools}");
        }

    void testFixedArray()
        {
        console.println("\n** testFixedArray()");

        String[] list = new Array<String>(3, i -> ["one","two","three"][i]);
        console.println("list=" + list);

        Char[] chars = new Array<Char>(3, i -> 'a' + i.toUInt32());
        console.println("chars=" + chars);

        Int[] ints = new Array<Int>(3, i -> -i);
        console.println("ints=" + ints);

        Byte[] bytes = new Array<Byte>(3, i -> i.toByte());
        console.println("bytes=" + bytes);

        Boolean[] booleans = new Array<Boolean>(3, i -> i % 2 == 0);
        console.println("booleans=" + booleans);
        }

    void testFormalTypes()
        {
        Array<Int> a1 = [1, 2];
        assert checkElementType(a1);

        Array<String> a2 = ["1", "2"];
        assert !checkElementType(a2);
        }

    private static <Value> Boolean checkElementType(Value o)
        {
        return Value.is(Type<Array>) && Value.Element.is(Type<Int>);
        }

    void testAssignSideEffects()
        {
        console.println("\n** testAssignSideEffects()");

        Int n = 5;
        n += 7;
        console.println("n=" + n);

        Int[] nums = new Int[];
        Int   i    = 0;
        nums[i++] = 5;
        console.println("nums=" + nums + ", i=" + i);

        nums[--i] += 7;
        console.println("nums=" + nums + ", i=" + i);
        }

    void testNew()
        {
        console.println("\n** testNew()");

        String[] array = new Array<String>(10);
        console.println("array=" + array + ", size=" + array.size);

        for (Int i : 1..10)
            {
            array += "#" + i;
            }
        console.println("array=" + array + ", size=" + array.size);
        }

    void testNibble()
        {
        console.println("\n** testNibble()");

        loop:
        for (Nibble b : Nibble.minvalue..Nibble.maxvalue)
            {
            console.println($"b{loop.count}={b}");
            }
        }

    void testBits()
        {
        console.println("\n** testBits()");

        Int n = 17;
        Bit[] bits = n.toBitArray();
        for (Bit bit : bits)
            {
            console.print(bit);
            }
        console.println();

        bits = bits.delete(0);
        bits = bits.delete(bits.size - 1);

        for (Bit bit : bits)
            {
            console.print(bit);
            }
        console.println();
        }

    void testComparable()
        {
        import ecstasy.collections.Hasher;
        import ecstasy.collections.NaturalHasher;

        console.println("\n** testComparable()");

        // Hasher<Int[]> hasher = new NaturalHasher(); // REVIEW with Cam; NaturalHasher is a Hasher<Int[]>
        Hasher<Int[]> hasher =  new NaturalHasher<Int[]>();

        Int[] ints = [0, 1, 2, 3, 4];
        Int hash1 = hasher.hashOf(ints);
        Int hash2 = Array<Int>.hashCode(ints);
        assert hash1 == hash2;

        Bit[] bits = [0, 1, 1, 0];
        console.println($"Array<Bit>.hashCode(bits)={Array<Bit>.hashCode(bits)}");
        }

    void testIterators()
        {
        console.println("\n** testIterators()");

        String[] strs = ["goodbye", "cruel", "world"];

        for (Int i : 1..10)
            {
            Iterator<String> iter = strs.iterator();
            switch (i)
                {
                case 1:
                    console.println("\n   --> peeking test");
                    iter = iter.peek(s -> console.println($"peeking at {s}"));
                    break;

                case 2:
                    console.println("\n   --> skipping test");
                    iter = iter.skip(2);
                    break;

                case 3:
                    console.println("\n   --> compound test");
                    iter = iter.concat(strs.iterator());
                    break;

                case 4:
                    console.println("\n   --> duplicate test");
                    (Iterator<String> iter1, Iterator<String> iter2) = iter.bifurcate();
                    iter = iter1.concat(iter2);
                    break;

                case 5:
                    console.println("\n   --> filter test");
                    iter = iter.filter(e -> e[0] != 'c');
                    break;

                case 6:
                    console.println("\n   --> map test");
                    iter = iter.map(e -> e.size.toString());
                    break;

                case 7:
                    console.println("\n   --> sort test");
                    iter = iter.sorted();
                    break;

                case 8:
                    console.println("\n   --> reverse test");
                    iter = iter.reversed();
                    break;

                case 9:
                    console.println("\n   --> flatmap test");
                    iter = iter.flatMap(s -> s.iterator()).map(ch -> ch.toString());
                    break;

                case 10:
                    console.println("\n   --> dedup test");
                    (Iterator<String> iter1, Iterator<String> iter2) = iter.bifurcate();
                    iter = iter1.concat(iter2);
                    iter = iter.dedup();
                    break;
                }

            while (String s := iter.next())
                {
                console.println($"s={s}");
                }
            }

        console.println("\n   --> metadata tests");
        Iterator<String> iter = strs.iterator();
        console.println($"distinct={iter.knownDistinct()}");
        console.println($"sorted={iter.knownOrder()}");
        if (Function f := iter.knownOrder())
            {
            console.println($"order={f}");
            }
        console.println($"knownEmpty={iter.knownEmpty()}");
        console.println($"knownSize={iter.knownSize()}");
        if (Int size := iter.knownSize())
            {
            console.println($"size={size}");
            }

        console.println("\n   --> misc tests");
        console.println($"count={strs.iterator().count()}");
        console.println($"array={strs.iterator().toArray()}");

        assert String min := strs.iterator().min();
        assert String max := strs.iterator().max();
        assert Range<String> range := strs.iterator().range();
        console.println($"min={min}; max={max}");
        console.println($"range={range}");
        }

    void testConstOrdinalList()
        {
        console.println("\n** testConstOrdinalList()");

        Int[] vals = [1, 2, 3, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 7, 8];
        val col = new ecstasy.collections.ConstOrdinalList(vals);
        for (Int i : [0..vals.size))
            {
            console.println($"col[{i}]={col[i]}");
            }

        Iterator<Int> iter = col.iterator();
        Loop: while (Int val := iter.next())
            {
            assert val == vals[Loop.count] && val == col[Loop.count];
            }

        console.println($"col={col}");
        console.println($"bytes={col.contents}");
        }
    }