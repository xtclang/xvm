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

        Int?[] result = ts.query(Minute, 1);
        assert result.size == 1;
        assert result[0] == 42;
    }

    @Test
    void shouldReturnNullForEmptyWindows() {
        TimeSeries<Int> ts = new TimeSeries(Minute, Hour);
        Time t0 = new Time(0);

        ts.add(t0,                         7);
        ts.add(t0 + Duration.ofMinutes(3), 11);

        Int?[] result = ts.query(Minute, 4);
        assert result.size == 4;
        assert result[0] == 7;
        assert result[1] == Null;
        assert result[2] == Null;
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

        Int?[] result = ts.query(Duration.ofMinutes(2), 2, folder=new Sum<Int>());
        assert result.size == 2;
        assert result[0] == 3;   // 1 + 2
        assert result[1] == 7;   // 3 + 4
    }
}
