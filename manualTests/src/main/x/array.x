module TestArray {
    @Inject ecstasy.io.Console console;

    void run() {
        testSimple();
        testStrBuf();
        testConstElement();
        testConstSlice();
        testSliceIdentity();

        testArrayList();
        testArrayListAdd();
        testFixedArray();

        testUnordered();

        testFormalTypes();

        testAssignSideEffects();
        testNew();
        testNibble();
        testBits();
        testDoubles();

        testComparable();
        testAggregation();
        testDeleteRange();
        testIndexBounds();

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

    void testSliceIdentity() {
        console.print("\n** testSliceIdentity()");

        Int[] nums = [1, 2, 3, 4, 5];
        Int[] s1   = nums[1..3];
        Int[] s2   = nums[1..3];
        Int[] s3   = nums[0..2];

        // Comparing two slices by reference must answer rather than raise. A slice delegate owns no
        // element storage, and used to inherit the object-array implementation of compareIdentity,
        // which opened by casting the handle to the object-array one - so this raised a run-time
        // error instead of comparing.
        assert &s1 == &s2;
        assert &s1 != &s3;

        // Same for a view, which likewise owns no element storage.
        UInt8[] bytes = [1, 2, 3, 4];
        Bit[]   v1    = bytes.asBitArray();
        Bit[]   v2    = bytes.asBitArray();
        assert &v1 == &v2;

        // The reverse direction dispatches to the CONCRETE delegate, which resolves the template
        // from the first handle only and used to cast the second one too. Each element type below
        // is backed by a different delegate, and every one had the same defect, so each needs its
        // own case: array-vs-slice and slice-vs-array must both answer rather than raise.
        Char[]    chars   = ['a', 'b', 'c', 'd', 'e'];
        String[]  strs    = ["a", "b", "c", "d", "e"];
        Float64[] floats  = [1.0, 2.0, 3.0, 4.0, 5.0];
        Int8[]    int8s   = [1, 2, 3, 4, 5];
        Int[]     ints    = [1, 2, 3, 4, 5];
        Int128[]  int128s = [1, 2, 3, 4, 5];
        Object[]  objs    = ["a", 2, 3.0, 4, 5];

        Char[]    charsSlice   = chars[1..3];
        String[]  strsSlice    = strs[1..3];
        Float64[] floatsSlice  = floats[1..3];
        Int8[]    int8sSlice   = int8s[1..3];
        Int[]     intsSlice    = ints[1..3];
        Int128[]  int128sSlice = int128s[1..3];
        Object[]  objsSlice    = objs[1..3];

        assert &chars   != &charsSlice;
        assert &strs    != &strsSlice;
        assert &floats  != &floatsSlice;
        assert &int8s   != &int8sSlice;
        assert &ints    != &intsSlice;
        assert &int128s != &int128sSlice;
        assert &objs    != &objsSlice;

        assert &charsSlice   != &chars;
        assert &strsSlice    != &strs;
        assert &floatsSlice  != &floats;
        assert &int8sSlice   != &int8s;
        assert &intsSlice    != &ints;
        assert &int128sSlice != &int128s;
        assert &objsSlice    != &objs;
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

    void testUnordered() {
        Int[] nums = new Int[].addAll([7, 2, 5, 21, 13, 42]);
        nums.removeUnordered(5);
        nums.deleteUnordered(3);
        nums.removeUnordered(13);
        nums.deleteUnordered(nums.size-1);
        nums.removeUnordered(7);
        nums.deleteUnordered(0);
        assert nums.empty;
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

    void testDoubles() {
        Double[] doubles = [1.01, 3.14, 7.89, 1001.1];
        Byte[]   bytes   = doubles.asByteArray();

        assert bytes.toFloat64Array() == doubles;
    }

    void testComparable() {
        import ecstasy.collections.Hasher;
        import ecstasy.collections.NaturalHasher;

        console.print("\n** testComparable()");

        Hasher<Int[]> hasher = new NaturalHasher();

        Int[] ints = [0, 1, 2, 3, 4];
        Int hash1 = hasher.hashOf(ints);
        Int hash2 = Array<Int>.hashCode(ints);
        assert hash1 == hash2;

        Bit[] bits = [0, 1, 1, 0];
        console.print($"{Array<Bit>.hashCode(bits)=}");
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
                iter = iter.observe(s -> console.print($"peeking at {s}"));
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
                iter = iter.flatMap(s -> s)
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
                console.print($"{s=}");
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
            console.print($"{size=}");
        }

        console.print("\n   --> misc tests");
        console.print($"count={strs.iterator().count()}");
        console.print($"array={strs.iterator().toArray()}");

        assert String min := strs.iterator().min();
        assert String max := strs.iterator().max();
        assert Range<String> range := strs.iterator().range();
        console.print($"{min=}; {max=}");
        console.print($"{range=}");
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

        console.print($"{col=}");
        console.print($"bytes={col.contents}");
    }

    /**
     * `deleteAll(range)` across every element type that has its own storage.
     */
    void testDeleteRange() {
        console.print("\n** testDeleteRange()");

        // removing 1..2 from a five-element array must always leave the 1st, 4th and 5th
        assert new Array<Int64>  (Mutable, [1, 2, 3, 4, 5]).deleteAll(1..2) == [1, 4, 5];
        assert new Array<Int8>   (Mutable, [1, 2, 3, 4, 5]).deleteAll(1..2) == [1, 4, 5];
        assert new Array<Int16>  (Mutable, [1, 2, 3, 4, 5]).deleteAll(1..2) == [1, 4, 5];
        assert new Array<Int128> (Mutable, [1, 2, 3, 4, 5]).deleteAll(1..2) == [1, 4, 5];
        assert new Array<UInt8>  (Mutable, [1, 2, 3, 4, 5]).deleteAll(1..2) == [1, 4, 5];
        assert new Array<Float64>(Mutable, [1.0, 2.0, 3.0, 4.0, 5.0]).deleteAll(1..2) == [1.0, 4.0, 5.0];
        assert new Array<Char>   (Mutable, ['a', 'b', 'c', 'd', 'e']).deleteAll(1..2) == ['a', 'd', 'e'];
        assert new Array<String> (Mutable, ["a", "b", "c", "d", "e"]).deleteAll(1..2) == ["a", "d", "e"];
        assert new Array<Boolean>(Mutable, [True, False, True, False, True]).deleteAll(1..2)
                == [True, False, True];
        assert new Array<Bit>    (Mutable, [0, 1, 0, 1, 0]).deleteAll(1..2) == [0, 1, 0];
        assert new Array<Nibble> (Mutable, [0, 1, 2, 3, 4]).deleteAll(1..2) == [0, 3, 4];
        assert new Array<Object> (Mutable, [1, 2, 3, 4, 5]).deleteAll(1..2).size == 3;

        // a single-element delete
        assert new Array<String>(Mutable, ["a", "b", "c", "d"]).deleteAll(1..1) == ["a", "c", "d"];
        assert new Array<Int8>  (Mutable, [1, 2, 3, 4]).deleteAll(1..1) == [1, 3, 4];

        // a range reaching the end
        assert new Array<Int8>  (Mutable, [1, 2, 3, 4, 5]).deleteAll(3..4) == [1, 2, 3];
        assert new Array<String>(Mutable, ["a", "b", "c", "d", "e"]).deleteAll(3..4) == ["a", "b", "c"];
    }

    void testIndexBounds() {
        console.print("\n** testIndexBounds()");

        String s   = "abcdefgh";
        Int[]  arr = [10, 11, 12, 13, 14, 15, 16, 17];

        assert s[0] == 'a';
        assert s[4] == 'e';
        assert s[7] == 'h';
        assert arr[4] == 14;

        // An index arrives as a 64-bit Int and the underlying storage is addressed by a 32-bit
        // one, so an index whose LOW 32 BITS land inside the container must still be rejected.
        // 2^32 + 4 narrows to 4; String used to answer 'e' for it while Int[] raised, so the two
        // disagreed about the same out-of-range index.
        Int lowBitsInRange = 4294967300;        // 2^32 + 4

        assert !checkedGet(s, lowBitsInRange);
        assert !checkedGet(arr, lowBitsInRange);

        // and the ordinary out-of-range cases still behave
        assert !checkedGet(s, 8);
        assert !checkedGet(s, -1);
        assert !checkedGet(arr, 8);
        assert !checkedGet(arr, -1);
    }

    /**
     * @return True iff the index could be read; False if it raised OutOfBounds
     */
    Boolean checkedGet(String s, Int index) {
        try {
            Char ignored = s[index];
            return True;
        } catch (OutOfBounds e) {
            return False;
        }
    }

    /**
     * @return True iff the index could be read; False if it raised OutOfBounds
     */
    Boolean checkedGet(Int[] arr, Int index) {
        try {
            Int ignored = arr[index];
            return True;
        } catch (OutOfBounds e) {
            return False;
        }
    }
}
