
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
        dynamicRangeOperators();
        descendingRange();

        console.print("<<<< Finished Int64 Range Tests <<<<<");
    }

    void newRange() {
        Range<Int> r = new Range(10, 20);
        assert r.lowerBound == 10 && r.upperBound == 20;
        assert !r.lowerExclusive && !r.upperExclusive;
    }

    void newRangeLowerExclusive() {
        Range<Int> r = new Range(10, 20, firstExclusive=True);
        assert r.lowerBound == 10 && r.upperBound == 20;
        assert r.lowerExclusive && !r.upperExclusive;
    }

    void newRangeUpperExclusive() {
        Range<Int> r = new Range(10, 20, lastExclusive=True);
        assert r.lowerBound == 10 && r.upperBound == 20;
        assert !r.lowerExclusive && r.upperExclusive;
    }

    void newRangeUpperBothExclusive() {
        Range<Int> r = new Range(10, 20, True, True);
        assert r.lowerBound == 10 && r.upperBound == 20;
        assert r.lowerExclusive && r.upperExclusive;
    }

    void rangeConstWithLowerAndUpperInclusive() {
        Range<Int64> r = 1..10;
        assert r.lowerBound == 1 && r.upperBound == 10;
        assert !r.lowerExclusive && !r.upperExclusive;
    }

    void rangeConstWithLowerExclusiveAndUpperInclusive() {
        Range<Int64> r = 100>..900;
        assert r.lowerBound == 100 && r.upperBound == 900;
        assert r.lowerExclusive && !r.upperExclusive;
    }

    void rangeConstWithLowerInclusiveAndUpperExclusive() {
        Range<Int64> r = 19..<20;
        assert r.lowerBound == 19 && r.upperBound == 20;
        assert !r.lowerExclusive && r.upperExclusive;
    }

    void rangeConstWithLowerAndUpperExclusive() {
        Range<Int64> r = 50>..<60;
        assert r.lowerBound == 50 && r.upperBound == 60;
        assert r.lowerExclusive && r.upperExclusive;
    }

    void dynamicRangeOperators() {
        Int64 lower = 10;
        Int64 upper = 20;

        Range<Int64> inclusive = lower..upper;
        assert !inclusive.lowerExclusive && !inclusive.upperExclusive;
        assert inclusive.effectiveFirst == 10    && inclusive.effectiveLast == 20;

        Range<Int64> lowerExclusive = lower>..upper;
        assert lowerExclusive.lowerExclusive && !lowerExclusive.upperExclusive;
        assert lowerExclusive.effectiveFirst == 11   && lowerExclusive.effectiveLast == 20;

        Range<Int64> upperExclusive = lower..<upper;
        assert !upperExclusive.lowerExclusive && upperExclusive.upperExclusive;
        assert upperExclusive.effectiveFirst == 10    && upperExclusive.effectiveLast == 19;

        Range<Int64> bothExclusive = lower>..<upper;
        assert bothExclusive.lowerExclusive && bothExclusive.upperExclusive;
        assert bothExclusive.effectiveFirst == 11   && bothExclusive.effectiveLast == 19;
    }

    void descendingRange() {
        Range<Int64> r = 100..1;
        assert r.descending && r.lowerBound == 1 && r.upperBound == 100;
    }
}
