module TestRanges {

    @Test
    void shouldBeAbove() {
        Range<Int> range1 = 5..10;
        assert range1.isAbove(4);
    }

    @Test
    void shouldBeAboveWhenReversed() {
        Range<Int> range1 = 10..5;
        assert range1.isAbove(4);
    }

    @Test
    void shouldBeAboveValueInRange() {
        Range<Int> range1 = 5..10;
        assert range1.isAbove(6) == False;
    }

    @Test
    void shouldBeAboveValueInRangeWhenReversed() {
        Range<Int> range1 = 10..5;
        assert range1.isAbove(6) == False;
    }

    @Test
    void shouldBeAboveWhenLowerBoundExclusive() {
        Range<Int> range1 = 5>..10;
        assert range1.isAbove(5);
    }

    @Test
    void shouldBeBelow() {
        Range<Int> range1 = 5..10;
        assert range1.isBelow(11);
    }

    @Test
    void shouldBeBelowWhenReversed() {
        Range<Int> range1 = 10..5;
        assert range1.isBelow(11);
    }

    @Test
    void shouldBeBelowValueInRange() {
        Range<Int> range1 = 5..10;
        assert range1.isBelow(6) == False;
    }

    @Test
    void shouldBeBelowValueInRangeWhenReversed() {
        Range<Int> range1 = 10..5;
        assert range1.isBelow(6) == False;
    }

    @Test
    void shouldBeBelowWhenUpperBoundExclusive() {
        Range<Int> range1 = 5..<10;
        assert range1.isBelow(10);
    }
}