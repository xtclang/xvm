/**
 * Basic construction tests.
 */
class Basic {

    /**
     * At the end of construction, all properties must be assigned.
     */
    @Test
    void testUnassigned() {
        static class Test(Int a1, String a2) {
            construct(Int a1, String a2) {
                this.a1 = a1;
                // missing assignment of a2
            }
        }

        try {
            Test t = new Test(1, "");
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
        static const Test(List<Int> list);

        List<Int> list = new Int[3](i -> i);
        assert !list.is(immutable);

        Test t = new Test(list);
        assert t.list.is(immutable);
        assert !list.is(immutable);
    }

    /**
     * Construction of "const" must deep-freeze all the content.
     */
    @Test
    void testDeepFreeze() {
        static const Test(List<List<Int>> list);

        List<List<Int>> list = new List[];
        list.add(new Int[3](i -> i));
        list.add(new Int[3](i -> i*i));
        assert !list.is(immutable);
        assert !list[0].is(immutable);

        Test t = new Test(list);
        assert t.list.is(immutable);
        assert !list.is(immutable);
        assert !list[0].is(immutable);
    }

    /**
     * Failure to freeze should prevent the construction
     */
    @Test
    void testUnFreezable() {
        static class UnFreezable(List<Int> list);
        static const Test(UnFreezable content);

        List<Int> list = new Int[3](i -> i);
        assert !list.is(immutable);

        try {
          Test t = new Test(new UnFreezable(list));
          assert;
        } catch (Exception e) {
            assert e.message.indexOf("content");
        }
    }
}
