package literalTests {

    void run() {
        testHex();
        testDuration();
    }

    void testHex() {
        Byte[] bytes = #123_4567_89aB_cDeF;
        assert bytes[0] == 0x1;
        assert bytes[1] == 0x23;

        bytes = #./literalTests.x;
        assert bytes.size > 40;
    }

    void testDuration() {
//        TODO
//        assert new Duration("P3DT4H5M6S") == Duration:P3DT4H5M6S;
//        assert new Duration("1DT1H1M1.23456S") == Duration:P1DT1H1M1.23456S;
//        assert new Duration("PT10S") == Duration:PT10S;
//        assert new Duration("10S") == Duration:10S;
//        assert new Duration("PT10.5S") == Duration:PT10.5S;
//        assert new Duration("P10.5S") == Duration:P10.5S;
//        assert new Duration("T10.5S") == Duration:T10.5S;
//        assert new Duration("10.5S") == Duration:10.5S;
//
//        assert Duration.Minute / Duration.Second == Duration.Minute.seconds;
//        assert Duration.Hour   / Duration.Minute == Duration.Hour.minutes;
//        assert Duration.Day    / Duration.Hour   == Duration.Day.hours;
    }
}
