/**
 * Basic construction tests.
 */
class Basic {
    void run() {
        testUnassigned();
        testDefaults();
        testFreeze();
        testDeepFreeze();
        testUnFreezable();
    }

    /**
     * At the end of construction, all properties must be assigned.
     */
    @Test
    void testUnassigned() {
        static class Test(Int a1, String a2, String? a3) {
            construct(Int a1, String a2, String? a3) {
                this.a1 = a1;
                // missing assignment of a2
            }
        }

        try {
            Test t = new Test(1, "", Null);
            assert;
        } catch (IllegalState e) {
            assert e.message.indexOf("a2");
        }
    }

    /**
     * At the end of construction, all unassigned properties must be assigned to the corresponding
     * default values if possible.
     */
    @Test
    void testDefaults() {
        static class Test(Int a1, String? a2, Boolean a3) {
            construct(Int a1, String? a2, Boolean a3) {
                // missing all assignments
            }
        }

        Test t = new Test(1, "", True);
        assert t.a1 == 0;
        assert t.a2 == Null;
        assert t.a3 == False;
    }

    /**
     * Construction of "const" must freeze all the content at the end of the construction.
     */
    @Test
    void testFreeze() {
        static const Test(Array<Int> ary);

        Array<Int> ary = new Int[3](i -> i);
        assert !ary.is(immutable);

        Test t = new Test(ary);
        assert t.ary.is(immutable);
        assert !ary.is(immutable);
    }

    /**
     * Construction of "const" must deep-freeze all the content.
     */
    @Test
    void testDeepFreeze() {
        static const Test(Array<Array<Int>> ary);

        Array<Array<Int>> ary = new Array[];
        ary.add(new Int[3](i -> i));
        ary.add(new Int[3](i -> i*i));
        assert !ary.is(immutable);
        assert !ary[0].is(immutable);

        Test t = new Test(ary);
        assert t.ary.is(immutable);
        assert !ary.is(immutable);
        assert !ary[0].is(immutable);
    }

    /**
     * Failure to freeze should prevent the construction
     */
    @Test
    void testUnFreezable() {
        static class UnFreezable(Array<Int> array);
        static const Test(UnFreezable content);

        Array<Int> array = new Int[3](i -> i);
        assert !array.is(immutable);

        try {
            Test t = new Test(new UnFreezable(array));
            assert;
        } catch (Exception e) {
            assert e.message.indexOf("content");
        }
    }
}
