class DateTests {

    @Inject Console console;

    void run() {
        testCreateDate();
        testCreateDateFromString();
        testLeapYear();
        testDayOfYear();
        testDayOfWeek();
        testMonthOfYear();
        testAdd();
        testSubDuration();
        testSubDate();
        testPrev();
        testNext();
        testStepsTo();
        testToTime();
        testToString();
        testAppendTo();
        testDateConstArray();
        testDateMutableArray();
        testDateSuppliedArray();
    }

    void testCreateDate() {
        Date d = new Date(1976, 6, 20);
        assert d.year == 1976;
        assert d.month == 6;
        assert d.day == 20;
    }

    void testCreateDateFromString() {
// TODO fails calling String.split$p()
//        Date d = new Date("1976/6/20");
//        assert d.year == 1976;
//        assert d.month == 6;
//        assert d.day == 20;
    }

    void testLeapYear() {
        Date d1 = new Date(2024, 10, 2);
        Date d2 = new Date(2025, 10, 2);
        Date d3 = new Date(2000, 10, 2);
        Date d4 = new Date(2100, 10, 2);
        assert d1.leapYear;
        assert !d2.leapYear;
        assert d3.leapYear;
        assert !d4.leapYear;
    }

    void testDayOfYear() {
        Date d = new Date(1966, 7, 5);
        assert d.dayOfYear == 186;
    }

    void testDayOfWeek() {
// TODO enums!
//        Date d = new Date(2026, 9, 8);
//        assert d.dayOfWeek == Tuesday;
    }

    void testMonthOfYear() {
// TODO enums!
//        Date d = new Date(2026, 9, 8);
//        assert d.monthOfYear == September;
    }

    void testAdd() {
        Date d1 = new Date(1976, 6, 20);
        Date d2 = d1 + Duration.Day * 2;
        assert d2.year == 1976;
        assert d2.month == 6;
        assert d2.day == 22;

        d2 = d1 + Duration.Day * 30;
        assert d2.year == 1976;
        assert d2.month == 7;
        assert d2.day == 20;

        d2 = d1 + Duration.Day * 365;
        assert d2.year == 1977;
        assert d2.month == 6;
        assert d2.day == 20;
    }

    void testSubDuration() {
        Date d1 = new Date(1976, 6, 20);
        Date d2 = d1 - Duration.Day * 2;
        assert d2.year == 1976;
        assert d2.month == 6;
        assert d2.day == 18;

        d2 = d1 - Duration.Day * 30;
        assert d2.year == 1976;
        assert d2.month == 5;
        assert d2.day == 21;

        d2 = d1 - Duration.Day * 365;
        assert d2.year == 1975;
        assert d2.month == 6;
        assert d2.day == 21;
    }

    void testSubDate() {
        Date d1 = new Date(1976, 6, 20);
        Date d2 = new Date(1976, 6, 18);
        assert d1 - d2 == Duration.Day * 2;

        d2 = new Date(1976, 5, 21);
        assert d1 - d2 == Duration.Day * 30;

        d2 = new Date(1975, 6, 21);
        assert d1 - d2 == Duration.Day * 365;
    }

    void testPrev() {
        Date d1 = new Date(2000, 10, 2);
        assert Date d2 := d1.prev();
        assert d2 == new Date(2000, 10, 1);
    }

    void testNext() {
        Date d1 = new Date(2000, 10, 2);
        assert Date d2 := d1.next();
        assert d2 == new Date(2000, 10, 3);
    }

    void testStepsTo() {
        Date d1 = new Date(2000, 10, 2);
        assert d1.stepsTo(new Date(2000, 10, 12)) == 10;
        assert d1.stepsTo(new Date(2000, 9, 2)) == -30;
        assert d1.stepsTo(new Date(1999, 10, 2)) == -366;
    }

    void testToTime() {
// TODO requires TimeZone
//        Date d = new Date(1966, 5, 7);
//        Time t = d.toTime();
//        assert t.date == d;
//        assert t.timeOfDay == MIDNIGHT;
//        assert t.timezone.isNoTZ;
    }

    void testToString() {
        Date d = new Date(1966, 7, 5);
        assert d.toString() == "1966-07-05";
    }

    void testAppendTo() {
        Date         d   = new Date(1966, 7, 5);
        StringBuffer buf = new StringBuffer();
        d.appendTo(buf);
        assert buf.toString() == "1966-07-05";
    }

    void testDateConstArray() {
        Date[] dates = [new Date(2000, 10, 2), new Date(1976, 6, 20)];
        assert dates[0] == new Date(2000, 10, 2);
        assert dates[1] == new Date(1976, 6, 20);
    }

    void testDateMutableArray() {
        Date   d1      = new Date(2000, 10, 2);
        Date   d2      = new Date(1976, 6, 20);
        Date   d3      = new Date(1966, 7, 5);
        Date[] mutable = new Date[];
        mutable.add(d1);
        mutable.add(d2);
        mutable.insert(1, d3);
        assert mutable.size == 3;
        assert mutable[0] == d1;
        assert mutable[1] == d3;
        assert mutable[2] == d2;
        mutable.delete(1);
        assert mutable.size == 2;
        assert mutable[0] == d1;
        assert mutable[1] == d2;
    }

    void testDateSuppliedArray() {
        Date   date     = new Date(1976, 6, 20);
        Date[] supplied = new Date[3](date);
        assert supplied.size == 3;
        assert supplied[0] == date;
        assert supplied[1] == date;
        assert supplied[2] == date;
    }

}
