package mapTests {

    void run() {
        testSpecializedCovariantReturn();
        testHashMapDuplicate();
    }

    void testSpecializedCovariantReturn() {
        // constructing the map verifies the ListMapIndex.makeImmutable() cap to ListMap
        Map<Int, String> map = [Int:4="now"];
    }

    void testHashMapDuplicate() {
        // TODO: constructing HashMap<Int, String> fails verification in the specialized
        // HasherMap.hashCode$p(), before this test can exercise duplicate().
        // HashMap<Int, String> original = new HashMap();
        // original.put(1, "one");
        // HashMap<Int, String> copy = original.duplicate((key, value) -> (key, value))
        //         .as(HashMap<Int, String>);
        // assert copy.size == 1;
        // copy.put(2, "two");
        // assert original.size == 1;
    }
}
