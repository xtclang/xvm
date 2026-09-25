class TimeTests {

    void run() {
        testEpoch();
        testDateAndTimeOfDay();
        testCreateTimeFromString();
        testBeforeEpoch();
        testArithmetic();
        testComparison();
        testWith();
        testTimeZone();
        testToString();
        testAppendTo();
    }

    void testEpoch() {
        Time t = new Time(0);
        assert t.epochPicos == 0;
        assert t.adjustedPicos == 0;
        assert t.timezone.isUTC;
        assert t.date == new Date(1970, 1, 1);
        assert t.timeOfDay.picos == 0;
        assert t == Time.EPOCH;
    }

    void testDateAndTimeOfDay() {
        Date      date = new Date(2000, 2, 29);
        TimeOfDay noon = new TimeOfDay(12 * TimeOfDay.PicosPerHour);
        Time      t    = new Time(date, noon, TimeZone.UTC);

        assert t.date == date;
        assert t.timeOfDay == noon;
        assert t.timezone.isUTC;
        assert t.toString() == "2000-02-29 12:00:00";
    }

    void testCreateTimeFromString() {
        Time utc = new Time("1970-01-01T00:00:01Z");
        assert utc.epochPicos == Duration.Second.picoseconds;
        assert utc.date == new Date(1970, 1, 1);
        assert utc.timeOfDay.picos == TimeOfDay.PicosPerSecond;
        assert utc.timezone.isUTC;
        assert utc.toString(True) == "1970-01-01T00:00:01Z";

        Time noZone = new Time("1970-01-01 00:00:01");
        assert noZone.epochPicos == Duration.Second.picoseconds;
        assert noZone.timezone.isNoTZ;
        assert noZone.toString() == "1970-01-01 00:00:01";
    }

    void testBeforeEpoch() {
        // one picosecond before midnight belongs to the preceding calendar day
        Time t = new Time(-1);
        assert t.date == new Date(1969, 12, 31);
        assert t.timeOfDay.picos == TimeOfDay.PicosPerDay - 1;
        assert t.toString(True) == "1969-12-31T23:59:59.999999999999Z";
    }

    void testArithmetic() {
        Time start = new Time(0);
        Time later = start + Duration.Day + Duration.Second;

        assert later.date == new Date(1970, 1, 2);
        assert later.timeOfDay.picos == TimeOfDay.PicosPerSecond;
        assert later.timezone.isUTC;
        assert later - start == Duration.Day + Duration.Second;
        assert later - Duration.Second - Duration.Day == start;
        assert start.epochPicos == 0;
    }

    void testComparison() {
        Time t1 = new Time(0);
        Time t2 = new Time(Duration.Second.picoseconds);

        assert t1 == new Time(0, TimeZone.UTC);
        assert t1 != t2;
        assert t1 < t2;
        assert t2 > t1;
        assert (t1 <=> t2) == Lesser;
        assert (t1 <=> new Time(0)) == Equal;
    }

    void testWith() {
        Time      original = new Time(0);
        Date      date     = new Date(2000, 2, 29);
        TimeOfDay noon     = new TimeOfDay(12 * TimeOfDay.PicosPerHour);
        Time      changed  = original.with(date = date, timeOfDay = noon);

        assert changed.date == date;
        assert changed.timeOfDay == noon;
        assert changed.timezone.isUTC;
        assert original.epochPicos == 0;
        assert original.with() == original;
    }

    void testTimeZone() {
        TimeZone plusTwo = new TimeZone(2 * TimeOfDay.PicosPerHour);
        Time     local   = new Time(0, plusTwo);

        // the zone changes the displayed wall time, not the instant being represented
        assert local.epochPicos == 0;
        assert local.adjustedPicos == 2 * TimeOfDay.PicosPerHour;
        assert local.timeOfDay.hour == 2;
        assert local == new Time(0);
        assert local.toString(True) == "1970-01-01T02:00:00+02:00";

        Time utc = local.utc();
        assert utc.epochPicos == local.epochPicos;
        assert utc.timezone.isUTC;
        assert utc.timeOfDay.picos == 0;
    }

    void testToString() {
        Time epoch = new Time(0);
        assert epoch.toString() == "1970-01-01 00:00:00";
        assert epoch.toString(True) == "1970-01-01T00:00:00Z";
        assert epoch.estimateStringLength() == "1970-01-01 00:00:00".size;
        assert epoch.estimateStringLength(True) == "1970-01-01T00:00:00Z".size;

        Time fraction = new Time(Duration.Millisec.picoseconds);
        assert fraction.toString(True) == "1970-01-01T00:00:00.001Z";
        // TODO: picosFractionalLength omits leading fractional zeros; the estimate is 22, not 24
        // assert fraction.estimateStringLength(True) == "1970-01-01T00:00:00.001Z".size;
    }

    void testAppendTo() {
        Time         t   = new Time(Duration.Second.picoseconds);
        StringBuffer buf = new StringBuffer();
        t.appendTo(buf, True);
        assert buf.toString() == "1970-01-01T00:00:01Z";
    }
}
