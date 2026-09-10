class DurationTests {

    @Inject Console console;

    void run() {
        testCreateDuration();
        testCreateDurationFromString();
        testCreateDurationOfDays();
        testCreateDurationOfHours();
        testCreateDurationOfMinutes();
        testCreateDurationOfSeconds();
        testCreateDurationOfMillis();
        testCreateDurationOfMicros();
        testCreateDurationOfNanos();
        testCreateDurationOfPicos();

        testSign();
        testNegative();
        testMagnitude();

        testNegate();
        testAdd();
        testSub();
        testMultiplyInt();
        testMultiplyDec();
        testDivInt();
        testDivDuration();

        testToString();
        testAppendTo();

        testDurationConstArray();
        testDurationMutableArray();
        testDurationSuppliedArray();
    }

    void testCreateDuration() {
        Duration d1 = new Duration(10, 6, 30);
        assert d1.days == 10;
        assert d1.hoursPart == 6;
        assert d1.minutesPart == 30;
        assert d1.secondsPart == 0;
        assert d1.millisecondsPart == 0;
        assert d1.microsecondsPart == 0;
        assert d1.nanosecondsPart == 0;
        assert d1.picosecondsPart == 0;

        d1 = new Duration(10, 6, 30, 50, 245, 9876543);
        assert d1.days == 10;
        assert d1.hoursPart == 6;
        assert d1.minutesPart == 30;
        assert d1.secondsPart == 50;
        assert d1.millisecondsPart == 245;
        assert d1.microsecondsPart == 245009;
        assert d1.nanosecondsPart == 245009876;
        assert d1.picosecondsPart == 245009876543;
    }

    void testCreateDurationFromString() {
        Duration d1 = new Duration("P1DT2H3M4.005006007008S");
        assert d1.days == 1;
        assert d1.hoursPart == 2;
        assert d1.minutesPart == 3;
        assert d1.secondsPart == 4;
        assert d1.millisecondsPart == 5;
        assert d1.microsecondsPart == 5006;
        assert d1.nanosecondsPart == 5006007;
        assert d1.picosecondsPart == 5006007008;
    }

    void testCreateDurationOfDays() {
        Duration d = Duration.ofDays(10);
        assert d.days == 10;
        assert d.hoursPart == 0;
        assert d.minutesPart == 0;
        assert d.secondsPart == 0;
        assert d.millisecondsPart == 0;
        assert d.microsecondsPart == 0;
        assert d.nanosecondsPart == 0;
        assert d.picosecondsPart == 0;
    }

    void testCreateDurationOfHours() {
        Duration d = Duration.ofHours(26);
        assert d.days == 1;
        assert d.hoursPart == 2;
        assert d.minutesPart == 0;
        assert d.secondsPart == 0;
        assert d.millisecondsPart == 0;
        assert d.microsecondsPart == 0;
        assert d.nanosecondsPart == 0;
        assert d.picosecondsPart == 0;
    }

    void testCreateDurationOfMinutes() {
        Duration d = Duration.ofMinutes(1625);
        assert d.days == 1;
        assert d.hoursPart == 3;
        assert d.minutesPart == 5;
        assert d.secondsPart == 0;
        assert d.millisecondsPart == 0;
        assert d.microsecondsPart == 0;
        assert d.nanosecondsPart == 0;
        assert d.picosecondsPart == 0;
    }

    void testCreateDurationOfSeconds() {
        Duration d = Duration.ofSeconds(97515);
        assert d.days == 1;
        assert d.hoursPart == 3;
        assert d.minutesPart == 5;
        assert d.secondsPart == 15;
        assert d.millisecondsPart == 0;
        assert d.microsecondsPart == 0;
        assert d.nanosecondsPart == 0;
        assert d.picosecondsPart == 0;
    }

    void testCreateDurationOfMillis() {
        Duration d = Duration.ofMillis(97515543);
        assert d.days == 1;
        assert d.hoursPart == 3;
        assert d.minutesPart == 5;
        assert d.secondsPart == 15;
        assert d.millisecondsPart == 543;
        assert d.microsecondsPart == 543000;
        assert d.nanosecondsPart == 543000000;
        assert d.picosecondsPart == 543000000000;
    }

    void testCreateDurationOfMicros() {
        Duration d = Duration.ofMicros(97515543876);
        assert d.days == 1;
        assert d.hoursPart == 3;
        assert d.minutesPart == 5;
        assert d.secondsPart == 15;
        assert d.millisecondsPart == 543;
        assert d.microsecondsPart == 543876;
        assert d.nanosecondsPart == 543876000;
        assert d.picosecondsPart == 543876000000;
    }

    void testCreateDurationOfNanos() {
        Duration d = Duration.ofNanos(97515543876234);
        assert d.days == 1;
        assert d.hoursPart == 3;
        assert d.minutesPart == 5;
        assert d.secondsPart == 15;
        assert d.millisecondsPart == 543;
        assert d.microsecondsPart == 543876;
        assert d.nanosecondsPart == 543876234;
        assert d.picosecondsPart == 543876234000;
    }

    void testCreateDurationOfPicos() {
        Duration d = Duration.ofPicos(97515543876234567);
        assert d.days == 1;
        assert d.hoursPart == 3;
        assert d.minutesPart == 5;
        assert d.secondsPart == 15;
        assert d.millisecondsPart == 543;
        assert d.microsecondsPart == 543876;
        assert d.nanosecondsPart == 543876234;
        assert d.picosecondsPart == 543876234567;

        assert d.hours == 27;
        assert d.minutes == 1625;
        assert d.seconds == 97515;
        assert d.milliseconds == 97515543;
        assert d.microseconds == 97515543876;
        assert d.nanoseconds == 97515543876234;
        assert d.picoseconds == 97515543876234567;
    }

    void testSign() {
        Duration d1 = Duration.ofPicos(97515543876234567);
        Duration d2 = Duration.ofPicos(-97515543876234567);
        Duration d3 = Duration.ofPicos(0);
        assert d1.sign == Positive;
        assert d2.sign == Negative;
        assert d3.sign == Zero;
    }

    void testNegative() {
        Duration d1 = Duration.ofPicos(97515543876234567);
        Duration d2 = Duration.ofPicos(-97515543876234567);
        Duration d3 = Duration.ofPicos(0);
        assert d1.negative == False;
        assert d2.negative == True;
        assert d3.negative == False;
    }

    void testMagnitude() {
        Duration d1 = Duration.ofPicos(97515543876234567);
        Duration d2 = Duration.ofPicos(-97515543876234567);
        Duration d3 = Duration.ofPicos(0);
        assert d1.magnitude == new Duration(97515543876234567);
        assert d2.magnitude == new Duration(97515543876234567);
        assert d3.magnitude == Duration.None;
    }

    void testNegate() {
        Duration d1 = Duration.ofPicos(97515543876234567);
        Duration d2 = Duration.ofPicos(-97515543876234567);
        Duration d3 = Duration.ofPicos(0);
        assert d1.neg() == new Duration(-97515543876234567);
        assert d2.neg() == new Duration(97515543876234567);
        assert d3.neg() == Duration.None;
    }

    void testAdd() {
        Int128   n1 = 97515543876234567;
        Int128   n2 = 87134918374929108;
        Duration d1 = Duration.ofPicos(n1);
        Duration d2 = Duration.ofPicos(n2);
        assert d1 + d2 == new Duration(n1 + n2);
    }

    void testSub() {
        Int128   n1 = 97515543876234567;
        Int128   n2 = 87134918374929108;
        Duration d1 = Duration.ofPicos(n1);
        Duration d2 = Duration.ofPicos(n2);
        assert d1 - d2 == new Duration(n1 - n2);
    }

    void testMultiplyInt() {
        Int128   n1 = 97515543876234567;
        Duration d1 = Duration.ofPicos(n1);
        assert d1 * 2 == new Duration(n1 * 2);
    }

    void testMultiplyDec() {
// TODO requires Int128 to Dec128 conversion support
//        Int128   n1     = 97515543876234567;
//        Duration d1     = Duration.ofPicos(n1);
//        Dec      factor = 3.5;
//        assert d1 * factor == new Duration(n1 * factor.toInt128());
    }

    void testDivInt() {
        Int128   n1 = 97515543876234567;
        Duration d1 = Duration.ofPicos(n1);
        assert d1 / 2 == new Duration(n1 / 2);
    }

    void testDivDuration() {
        Int128   n1 = 97515543876234567;
        Duration d1 = Duration.ofPicos(n1);
        Duration d2 = new Duration(n1 / 2);
        assert d1 / d2 == 2;
    }

    void testToString() {
        Duration d = new Duration(10, 6, 30, 50, 245, 9876543);
        assert d.toString() == "246:30:50.245009876543";
    }

    void testAppendTo() {
        Duration     d   = new Duration(10, 6, 30, 50, 245, 9876543);
        StringBuffer buf = new StringBuffer();
        d.appendTo(buf);
        assert buf.toString() == "246:30:50.245009876543";
    }

    void testDurationConstArray() {
        Duration[] units = [Duration.Minute, Duration.Hour];
        assert units[0] == Duration.Minute;
        assert units[1] == Duration.Hour;
    }

    void testDurationMutableArray() {
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
    }

    void testDurationSuppliedArray() {
        Duration[] supplied = new Duration[3](Duration.Second);
        assert supplied.size == 3;
        assert supplied[0] == Duration.Second;
        assert supplied[1] == Duration.Second;
        assert supplied[2] == Duration.Second;
    }
}
