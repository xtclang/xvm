import metrics.TimeSeries;
import agg.Sum;

/**
 * Basic tests for [TimeSeries].
 */
class TimeSeriesTest {
    @Test
    void shouldReturnLatestSample() {
        TimeSeries<Int> ts = new TimeSeries(Minute, Hour);
        Time t0 = new Time(0);

        ts.add(t0, 42);

        (Int[] result, Time oldest) = ts.query(Minute, 1);
        assert result.size == 1;
        assert result[0] == 42;
        assert oldest == t0;
    }

    @Test
    void shouldReturnNullForEmptyWindows() {
        TimeSeries<Int> ts = new TimeSeries(Minute, Hour);
        Time t0 = new Time(0);

        ts.add(t0,                         7);
        ts.add(t0 + Duration.ofMinutes(3), 11);

        Int[] result = ts.query(Minute, 4);
        assert result.size == 4;
        assert result[0] == 7;
        assert result[1] == 0;
        assert result[2] == 0;
        assert result[3] == 11;
    }

    @Test
    void shouldFoldAdjacentBucketsWithAggregator() {
        TimeSeries<Int> ts = new TimeSeries(Minute, Hour);
        Time t0 = new Time(0);

        ts.add(t0                        , 1);
        ts.add(t0 + Duration.ofMinutes(1), 2);
        ts.add(t0 + Duration.ofMinutes(2), 3);
        ts.add(t0 + Duration.ofMinutes(3), 4);

        Int[] result = ts.query(Duration.ofMinutes(2), 2, folder=new Sum<Int>());
        assert result.size == 2;
        assert result[0] == 3;   // 1 + 2
        assert result[1] == 7;   // 3 + 4
    }

    @Test
    void shouldReturnOldestAggregatedSampleTime() {
        TimeSeries<Int> ts = new TimeSeries(Minute, Duration.ofDays(7));
        Time t0 = new Time(0);

        for (Int day : 0..<7) {
            ts.add(t0 + Duration.ofDays(day), day + 1);
        }

        (Int[] result, Time oldest) = ts.query(Day, 7, folder=new Sum<Int>());
        assert result.size == 7;
        assert oldest == t0;

        (Int[] partial, Time partialOldest) = ts.query(
                Day, 2, folder=new Sum<Int>(), endTime=t0 + Duration.ofDays(4));
        assert partial.size == 2;
        assert partialOldest == t0 + Duration.ofDays(3);
    }
}
