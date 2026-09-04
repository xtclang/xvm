/**
 * Focused tests for the JIT primitive representation of Duration.
 *
 * xtc run -L build/xtc/main/lib -o build/xtc/main/lib --jit src/main/x/jit/duration_tests.x
 */
module duration_tests.examples.org {

    Int run() {
        Duration hour = Duration.Hour;
        assert hour.picoseconds == Duration.PicosPerHour;
        assert hour.seconds == 3_600;
        assert hour.hoursPart == 1;

        Duration minute = Duration.Minute;
        assert hour / minute == 60;
        assert hour + minute == Duration.ofMinutes(61);
        assert hour - minute == Duration.ofMinutes(59);
        assert -minute == Duration.ofMinutes(-1);
        assert hour * -2 == Duration.ofHours(-2);
        assert hour / 2 == Duration.ofMinutes(30);
        assert Duration.None < minute;
        assert minute <=> hour == Lesser;

        Duration adjusted = minute;
        adjusted += Duration.Second;
        adjusted *= 2;
        assert adjusted == Duration.ofSeconds(122);

        Duration millis = Duration.ofMillis(1_500);
        assert millis.milliseconds == 1_500;
        assert millis.secondsPart == 1;
        assert millis.millisecondsPart == 500;

        Int[] ints = new Int[];
        ints.add(1);
        ints[0] += 2;
        assert ints[0] == 3;

        Int128[] wideInts = new Int128[];
        wideInts.add(18446744073709551616);
        wideInts[0] += 2;
        assert wideInts[0] == 18446744073709551618;

        Duration[] units = [Duration.Minute, Duration.Hour];
        assert units[0] == minute;
        assert units[1] == hour;

        Duration[] mutable = new Duration[];
        mutable.add(Duration.Second);
        mutable.add(Duration.Minute);
        mutable.insert(1, Duration.ofSeconds(30));
        assert mutable.size == 3;
        assert mutable[0] == Duration.Second;
        assert mutable[1] == Duration.ofSeconds(30);
        assert mutable[2] == Duration.Minute;
        mutable.delete(1);
        assert mutable.size == 2;
        assert mutable[0] == Duration.Second;
        assert mutable[1] == Duration.Minute;
        mutable[0] += Duration.Second;
        assert mutable[0] == Duration.ofSeconds(2);

        Duration[] supplied = new Duration[3](Duration.Second);
        assert supplied.size == 3;
        assert supplied[0] == Duration.Second;
        assert supplied[1] == Duration.Second;
        assert supplied[2] == Duration.Second;

        Duration parsed = new Duration("P1DT2H3M4.005006007008S");
        assert parsed.toString() == "26:03:04.005006007008";
        assert parsed.toString(True) == "P1DT2H3M4.005006007008S";

        return 0;
    }
}
