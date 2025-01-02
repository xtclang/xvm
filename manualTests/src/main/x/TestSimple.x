module TestSimple {
    @Inject static Console console;
    static void out(String s="") = console.print(s);

    void run(String[] args = []) {
        static Boolean check(Date date, Date actual, Int year, Int month, Int day) {
            if (date != actual || date.year != year || date.month != month || date.day != day) {
                out($"{date=}, year={date.year}, month={date.month}, day={date.day}, {actual=}, year={actual.year} ({year}), month={actual.month} ({month}), day={actual.day} ({day})");
                return False;
            }
            return True;
        }

        for (Int year : 1600..2500) {
            for (Int month : 1..12) {
                for (Int day : 1..Date.daysInMonth(year, month)) {
                    Date date = new Date(year, month, day);
                    assert check(date, date, year, month, day);

                    Date date2 = new Date(date.epochDay);
                    assert check(date2, date, year, month, day);

                    Date date3 = new Date(date.toString());
                    assert check(date3, date, year, month, day);
                }
            }
        }
    }
}