
package int64RangeTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int64 Range Tests >>>>");

        newRange();
        newRangeLowerExclusive();
        newRangeUpperExclusive();
        newRangeUpperBothExclusive();
        rangeConstWithLowerAndUpperInclusive();
        rangeConstWithLowerExclusiveAndUpperInclusive();
        rangeConstWithLowerInclusiveAndUpperExclusive();
        rangeConstWithLowerAndUpperExclusive();
        descendingRange();

        console.print("<<<< Finished Int64 Range Tests <<<<<");
    }

    void newRange() {
        Range<Int> r = new Range(10, 20);
        assert r.lowerBound == 10;
        assert r.upperBound == 20;
        assert r.lowerExclusive == False;
        assert r.upperExclusive == False;
    }

    void newRangeLowerExclusive() {
        Range<Int> r = new Range(10, 20, firstExclusive=True);
        assert r.lowerBound == 10;
        assert r.upperBound == 20;
        assert r.lowerExclusive == True;
        assert r.upperExclusive == False;
    }

    void newRangeUpperExclusive() {
        Range<Int> r = new Range(10, 20, lastExclusive=True);
        assert r.lowerBound == 10;
        assert r.upperBound == 20;
        assert r.lowerExclusive == False;
        assert r.upperExclusive == True;
    }

    void newRangeUpperBothExclusive() {
        Range<Int> r = new Range(10, 20, True, True);
        assert r.lowerBound == 10;
        assert r.upperBound == 20;
        assert r.lowerExclusive == True;
        assert r.upperExclusive == True;
    }

    void rangeConstWithLowerAndUpperInclusive() {
        Range<Int64> r = 1..10;
        assert r.lowerBound == 1;
        assert r.upperBound == 10;
        assert r.lowerExclusive == False;
        assert r.upperExclusive == False;
    }

    void rangeConstWithLowerExclusiveAndUpperInclusive() {
        Range<Int64> r = 100>..900;
        assert r.lowerBound == 100;
        assert r.upperBound == 900;
        assert r.lowerExclusive == True;
        assert r.upperExclusive == False;
    }

    void rangeConstWithLowerInclusiveAndUpperExclusive() {
        Range<Int64> r = 19..<20;
        assert r.lowerBound == 19;
        assert r.upperBound == 20;
        assert r.lowerExclusive == False;
        assert r.upperExclusive == True;
    }

    void rangeConstWithLowerAndUpperExclusive() {
        Range<Int64> r = 50>..<60;
        assert r.lowerBound == 50;
        assert r.upperBound == 60;
        assert r.lowerExclusive == True;
        assert r.upperExclusive == True;
    }

    void descendingRange() {
        Range<Int64> r = 100..1;
        assert r.descending;
        assert r.lowerBound == 1;
        assert r.upperBound == 100;
    }
}