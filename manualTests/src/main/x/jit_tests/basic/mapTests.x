package mapTests {

    void run() {
        testSpecializedCovariantReturn();
    }

    void testSpecializedCovariantReturn() {
        // constructing the map verifies the ListMapIndex.makeImmutable() cap to ListMap
        Map<Int, String> map = [Int:4="now"];
    }
}
