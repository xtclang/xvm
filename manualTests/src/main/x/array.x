module TestArray {
    @Inject ecstasy.io.Console console;

    void run() {
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
        testAggregation();

        testIterators();

        testConstOrdinalList();
    }

    void testSimple() {
        console.print("\n** testSimple()");

        Int[] nums = [1,7];
        console.print("array=" + nums);
        console.print("size=" + nums.size);
        console.print("[0]=" + nums[0]);
        console.print("[1]=" + nums[1]);
    }

    void testStrBuf() {
        console.print("\n** testStrBuf()");

        StringBuffer buf = new StringBuffer();
        buf.append("hello")
          .append(' ')
          .append("world")
          .append('!');

        console.print("buf=" + buf);
    }

    void testConstElement() {
        console.print("\n** testConstElement()");

        String cruel = ["hello", "cruel", "world", "!"] [1];
        console.print("array[1]=" + cruel);
    }

    void testConstSlice() {
        console.print("\n** testConstSlice()");

        String[] cruel = ["hello", "cruel", "world", "!"] [1..2];
        console.print("array[1..2]=" + cruel);

        String[] cruel2 = ["hello", "cruel", "world", "!"] [2..1];
        console.print("array[2..1]=" + cruel2);
    }

    void testArrayList() {
        console.print("\n** testArrayList()");

        String[] list = new String[];
        list[0] = "one";
        list[1] = "two";
        list[2] = "three";

        console.print("list=" + list);

        list = new Array<String>(Mutable, list);
        String one = list.getElement(0);
        list.setElement(0, "uno");
        console.print("list=" + list);

        list = list[1 ..< 3];
        console.print("list=" + list);

        list = new Array<String>(Fixed, list[0..1]);
        list.setElement(0, "один");
        list.setElement(1, "два");
        console.print("list=" + list);
    }

    void testArrayListAdd() {
        console.print("\n** testArrayListAdd()");

        String?[] list = new String?[];
        list += "one";
        list += ["two", "three"];
        list[4] = "five";

        console.print($"list={list}");

        Int[] ints = new Int[];
        ints += 1;
        ints += [2, 3];
        ints[4] = 4;

        console.print($"ints={ints}");

        Byte[] bytes = new Byte[];
        bytes += 1;
        bytes += [Byte:2, Byte:3];
        bytes[9] = 10;

        console.print($"bytes={bytes}");

        Boolean[] bools = new Boolean[];
        bools += True;
        bools += [False, True];
        bools[9] = True;

        console.print($"bools={bools}");
    }

    void testFixedArray() {
        console.print("\n** testFixedArray()");

        String[] list = new String[3](i -> ["one","two","three"][i]);
        console.print("list=" + list);

        Char[] chars = new Char[3](i -> 'a' + i.toUInt32());
        console.print("chars=" + chars);

        Int[] ints = new Int[3](i -> -i);
        console.print("ints=" + ints);

        Byte[] bytes = new Byte[3](i -> i.toByte());
        console.print("bytes=" + bytes);

        Boolean[] booleans = new Boolean[3](i -> i % 2 == 0);
        console.print("booleans=" + booleans);
    }

    void testFormalTypes() {
        Array<Int> a1 = [1, 2];
        assert checkElementType(a1);

        Array<String> a2 = ["1", "2"];
        assert !checkElementType(a2);
    }

    private static <Value> Boolean checkElementType(Value o) {
        return Value.is(Type<Array>) && Value.Element.is(Type<Int>);
    }

    void testAssignSideEffects() {
        console.print("\n** testAssignSideEffects()");

        Int n = 5;
        n += 7;
        console.print("n=" + n);

        Int[] nums = new Int[];
        Int   i    = 0;
        nums[i++] = 5;
        console.print("nums=" + nums + ", i=" + i);

        nums[--i] += 7;
        console.print("nums=" + nums + ", i=" + i);
    }

    void testNew() {
        console.print("\n** testNew()");

        String[] array = new Array<String>(10);
        console.print("array=" + array + ", size=" + array.size);

        for (Int i : 1..10) {
            array += "#" + i;
        }
        console.print("array=" + array + ", size=" + array.size);
    }

    void testNibble() {
        console.print("\n** testNibble()");

        loop:
        for (Nibble b : MinValue .. MaxValue) {
            console.print($"b{loop.count}={b}");
        }
    }

    void testBits() {
        console.print("\n** testBits()");

        Int n = 17;
        Bit[] bits = n.toBitArray();
        for (Bit bit : bits) {
            console.print(bit, suppressNewline=True);
        }
        console.print();

        bits = bits.delete(0);
        bits = bits.delete(bits.size - 1);

        for (Bit bit : bits) {
            console.print(bit, suppressNewline=True);
        }
        console.print();
    }

    void testComparable() {
        import ecstasy.collections.Hasher;
        import ecstasy.collections.NaturalHasher;

        console.print("\n** testComparable()");

        // Hasher<Int[]> hasher = new NaturalHasher(); // REVIEW with Cam; NaturalHasher is a Hasher<Int[]>
        Hasher<Int[]> hasher =  new NaturalHasher<Int[]>();

        Int[] ints = [0, 1, 2, 3, 4];
        Int hash1 = hasher.hashOf(ints);
        Int hash2 = Array<Int>.hashCode(ints);
        assert hash1 == hash2;

        Bit[] bits = [0, 1, 1, 0];
        console.print($"Array<Bit>.hashCode(bits)={Array<Bit>.hashCode(bits)}");
    }

    void testAggregation() {
        console.print("\n** testAggregation()");

        agg([]);
        agg([42]);
        agg([1, 2, 3]);
        agg([5, 4, 3, 2, 1]);
        agg([7, 5, 21, 13]);

        void agg(Int[] a) {
            console.print($"for array {a}, min={a.min() ?: "none"}, max={a.max() ?: "none"}, median={a.median()}");
        }
    }

    void testIterators() {
        console.print("\n** testIterators()");

        String[] strs = ["goodbye", "cruel", "world"];

        for (Int i : 1..10) {
            Iterator<String> iter = strs.iterator();
            switch (i) {
            case 1:
                console.print("\n   --> peeking test");
                iter = iter.peek(s -> console.print($"peeking at {s}"));
                break;

            case 2:
                console.print("\n   --> skipping test");
                iter = iter.skip(2);
                break;

            case 3:
                console.print("\n   --> compound test");
                iter = iter.concat(strs.iterator());
                break;

            case 4:
                console.print("\n   --> duplicate test");
                (Iterator<String> iter1, Iterator<String> iter2) = iter.bifurcate();
                iter = iter1.concat(iter2);
                break;

            case 5:
                console.print("\n   --> filter test");
                iter = iter.filter(e -> e[0] != 'c');
                break;

            case 6:
                console.print("\n   --> map test");
                iter = iter.map(e -> e.size.toString());
                break;

            case 7:
                console.print("\n   --> sort test");
                iter = iter.sorted();
                break;

            case 8:
                console.print("\n   --> reverse test");
                iter = iter.reversed();
                break;

            case 9:
                console.print("\n   --> flatmap test");
                iter = iter.flatMap(s -> s) /* TODO GG remove */ .as(Iterator<Char>)
                           .map(ch -> ch.toString());
                break;

            case 10:
                console.print("\n   --> dedup test");
                (Iterator<String> iter1, Iterator<String> iter2) = iter.bifurcate();
                iter = iter1.concat(iter2);
                iter = iter.dedup();
                break;
            }

            while (String s := iter.next()) {
                console.print($"s={s}");
            }
        }

        console.print("\n   --> metadata tests");
        Iterator<String> iter = strs.iterator();
        console.print($"distinct={iter.knownDistinct()}");
        console.print($"sorted={iter.knownOrder()}");
        if (Function f := iter.knownOrder()) {
            console.print($"order={f}");
        }
        console.print($"knownEmpty={iter.knownEmpty()}");
        console.print($"knownSize={iter.knownSize()}");
        if (Int size := iter.knownSize()) {
            console.print($"size={size}");
        }

        console.print("\n   --> misc tests");
        console.print($"count={strs.iterator().count()}");
        console.print($"array={strs.iterator().toArray()}");

        assert String min := strs.iterator().min();
        assert String max := strs.iterator().max();
        assert Range<String> range := strs.iterator().range();
        console.print($"min={min}; max={max}");
        console.print($"range={range}");
    }

    void testConstOrdinalList() {
        console.print("\n** testConstOrdinalList()");

        Int[] vals = [1, 2, 3, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 7, 8];
        val col = new ecstasy.collections.ConstOrdinalList(vals);
        for (Int i : 0 ..< vals.size) {
            console.print($"col[{i}]={col[i]}");
        }

        Iterator<Int> iter = col.iterator();
        Loop: while (Int val := iter.next()) {
            assert val == vals[Loop.count] && val == col[Loop.count];
        }

        console.print($"col={col}");
        console.print($"bytes={col.contents}");
    }
}