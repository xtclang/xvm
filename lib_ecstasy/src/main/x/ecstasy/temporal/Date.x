/**
 * The Date value type is used to represent the information about a date in Gregorian form.
 *
 * The data structure is based on the UNIX epoch date of 1970-01-01, and tracks only the offset
 * from that date, which offset is called epochDay. The modern concept of year/month/day is on an
 * uninterrupted continuum that began in 1582-10-15, when the Gregorian calendar was first adopted.
 * Dates before 1582-10-15 do exist, but they cannot be converted by the Date class into
 * year/month/day values.
 */
const Date(Int epochDay)
        implements Sequential
    {
    enum DayOfWeek {Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday}

    enum MonthOfYear {January, February, March, April, May, June,
                      July, August, September, October, November, December}

    /**
     * Construct a Date using a valid Gregorian year, month, and day. Valid Gregorian dates are any
     * valid dates starting with 1582-10-15.
     *
     * @param year   a legal Gregorian year
     * @param month  the month in the range 1..12
     * @param day    a day in the range 1..31 (or a smaller legal range, depending on the year and
     *               month)
     */
    construct (Int year, Int month, Int day)
        {
        construct Date(calcEpochOffset(year, month, day));
        }

    /**
     * Construct a Date from an ISO-8601 date string.
     */
    construct (String date)
        {
        Int year;
        Int month;
        Int day;

        String[] parts = date.split('-');
        switch (parts.size)
            {
            case 3:
                year  = new IntLiteral(parts[0]);
                month = new IntLiteral(parts[1]);
                day   = new IntLiteral(parts[2]);
                break;

            case 1:
                Int len = date.size;
                if (len >= 8)
                    {
                    year  = new IntLiteral(date[0    ..len-5]);
                    month = new IntLiteral(date[len-4..len-3]);
                    day   = new IntLiteral(date[len-2..len-1]);
                    break;
                    }
                continue;

            default:
                throw new IllegalArgument($"invalid ISO-8601 date: \"{date}\"");
            }

        construct Date(year, month, day);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * True iff the year is a leap year.
     */
    @RO Boolean leapYear.get()
        {
        return isLeapYear(year);
        }

    /**
     * The year portion of the date.
     *
     * This uses the Gregorian calendar system, so while dates before 1582-10-15 are supported by
     * the Date class, they are not expressible in terms of year/month/day.
     */
    @RO Int year.get()
        {
        (Int year, _, _, _) = calcDate(epochDay);
        return year;
        }

    /**
     * The month portion of the date.
     *
     * This uses the Gregorian calendar system, so while dates before 1582-10-15 are supported by
     * the Date class, they are not expressible in terms of year/month/day.
     */
    @RO Int month.get()
        {
        (_, Int month, _, _) = calcDate(epochDay);
        return month;
        }

    /**
     * The day portion of the date.
     *
     * This uses the Gregorian calendar system, so while dates before 1582-10-15 are supported by
     * the Date class, they are not expressible in terms of year/month/day.
     */
    @RO Int day.get()
        {
        (_, _, Int day, _) = calcDate(epochDay);
        return day;
        }

    /**
     * The day-of-year portion of the date.
     *
     * This uses the Gregorian calendar system, so while dates before 1582-10-15 are supported by
     * the Date class, they are not expressible in terms of year/month/day.
     */
    @RO Int dayOfYear.get()
        {
        (_, _, _, Int dayOfYear) = calcDate(epochDay);
        return dayOfYear;
        }

    /**
     * The day of the week represented by the date.
     */
    @RO DayOfWeek dayOfWeek.get()
        {
        // epoch 0 was a Thursday, so we need to shift it forward 4 days to make Monday be day 0
        return DayOfWeek.values[(epochDay + 4) % 7];
        }

    /**
     * The month of the year represented by the date.
     *
     * This uses the Gregorian calendar system, so while dates before 1582-10-15 are supported by
     * the Date class, they are not expressible in terms of year/month/day.
     */
    @RO MonthOfYear monthOfYear.get()
        {
        return MonthOfYear.values[month-1];
        }


    // ----- operators -----------------------------------------------------------------------------

    @Op("+") Date add(Duration duration)
        {
        return new Date(this.epochDay + duration.days);
        }

    @Op("-") Date sub(Duration duration)
        {
        return new Date(this.epochDay - duration.days);
        }

    @Op("-") Duration sub(Date date)
        {
        return Duration.ofDays(this.epochDay - date.epochDay);
        }


    // ----- Sequential ----------------------------------------------------------------------------

    @Override
    conditional Date prev()
        {
        return True, new Date(this.epochDay-1);
        }

    @Override
    conditional Date next()
        {
        return True, new Date(this.epochDay+1);
        }

    @Override
    Int stepsTo(Date that)
        {
        return this.epochDay - that.epochDay;
        }

    @Override
    Date skip(Int steps)
        {
        return new Date(epochDay + steps);
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return a DateTime that corresponds to midnight (start of day) on this Date
     */
    DateTime toDateTime()
        {
        return new DateTime(this, Time.MIDNIGHT, TimeZone.NoTZ);
        }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 10;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        year.appendTo(buf);
        buf.add('-');

        Int month = this.month;
        if (month < 10)
            {
            buf.add('0');
            }
        month.appendTo(buf);
        buf.add('-');

        Int day = this.day;
        if (day < 10)
            {
            buf.add('0');
            }
        return day.appendTo(buf);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Determine if the specified combination of year, month, and day is a valid date on the
     * Gregorian calendar. The Gregorian calendar began on 1582-10-15, so any date before that
     * does not exist on the Gregorian calendar.
     *
     * @param year   the year to test
     * @param month  the month to test
     * @param day    the day to test
     *
     * @return True iff the specified date is a valid date on the Gregorian calendar
     */
    static Boolean isGregorian(Int year, Int month, Int day)
        {
        // Gregorian dates did not exist before 15 October 1582
        return (year > 1582 || year == 1582 && (month == 10 && day >= 15 || month > 10))
                && 1 <= month <= 12
                && 1 <= day <= daysInMonth(year, month);
        }

    /**
     * The starting year of the 400-year block that began on 1201-01-01T00:00:00. (Note that we use
     * an illegal year for the basis of this block, but this is the first block that contains any
     * valid Gregorian dates, so this keeps all of the math in the positive range.)
     */
    static IntLiteral QCENTURY_YEAR     = 1201;

    /**
     * The offset (in number of days) from the epoch to the beginning of the 400-year block that
     * began on 1201-01-01T00:00:00. (Note that we use an illegal year for the basis of this block,
     * but this is the first block that contains any valid Gregorian dates, so this keeps all of the
     * math in the positive range.)
     */
    static IntLiteral QCENTURY_OFFSET   = -280871;

    /**
     * The offset (in number of days) from the epoch to the beginning of the Gregorian calendar,
     * 1582-10-14T00:00:00.
     */
    static IntLiteral GREGORIAN_OFFSET  = -141427;

    /**
     * The number of days in a normal year.
     */
    static IntLiteral DAYS_PER_YEAR     = 365;

    /**
     * The number of days in the first 24 4-year blocks ofa 100-year block.
     */
    static IntLiteral DAYS_PER_QYEAR    = DAYS_PER_YEAR * 4 + 1;

    /**
     * The number of days in a 100-year block (the first 3 of 4 centuries in a 400 year block).
     */
    static IntLiteral DAYS_PER_CENTURY  = DAYS_PER_QYEAR * 25 - 1;

    /**
     * The number of days in a 400-year block.
     */
    static IntLiteral DAYS_PER_QCENTURY = DAYS_PER_CENTURY * 4 + 1;

    /**
     * Calculate the number of days to add to the beginning of the epoch to get to January 1 of the
     * specified year.
     *
     * @param year  a legal Gregorian year
     *
     * @return the days offset of the specified year from the beginning of the epoch, 00:00:00 UTC,
     *         1 January 1970
     */
    static Int calcEpochOffset(Int year)
        {
        assert year >= 1582;

        Int qcenturyNum  = (year - QCENTURY_YEAR) / 400;
        Int qcenturyYear = QCENTURY_YEAR + qcenturyNum * 400;
        Int centuryNum   = (year - qcenturyYear) / 100;
        Int centuryYear  = qcenturyYear + centuryNum * 100;
        Int qennialNum   = (year - centuryYear) / 4;
        Int qennialYear  = centuryYear + qennialNum * 4;
        Int ennialNum    = year - qennialYear;

        return QCENTURY_OFFSET + qcenturyNum * DAYS_PER_QCENTURY
                               + centuryNum  * DAYS_PER_CENTURY
                               + qennialNum  * DAYS_PER_QYEAR
                               + ennialNum   * DAYS_PER_YEAR;
        }

    /**
     * Calculate the number of days to add to the beginning of the epoch to get to the specified
     * year, month, and day.
     *
     * @param year   a legal Gregorian year
     * @param month  the month in the range 1..12
     * @param day    a day in the range 1..31 (or a smaller legal range, depending on the year and
     *               month)
     *
     * @return the day offset from the beginning of the epoch, 00:00:00 UTC, 1 January 1970
     */
    static Int calcEpochOffset(Int year, Int month, Int day)
        {
        assert isGregorian(year, month, day);
        return calcEpochOffset(year) + daysInYearBefore(year, month) + day - 1;
        }

    /**
     * Convert an epoch day value (the number of days since the epoch began) into a year, month, and
     * day value.
     *
     * @param epochDay  the number of days since the epoch began
     *
     * @return a tuple of year, month, day, and day-of-year
     */
    static (Int year, Int month, Int day, Int dayOfYear) calcDate(Int epochDay)
        {
        assert epochDay >= GREGORIAN_OFFSET;

        Int daysLeft     = epochDay - QCENTURY_OFFSET;

        Int qcenturyNum  = daysLeft / DAYS_PER_QCENTURY;
        daysLeft        -= qcenturyNum * DAYS_PER_QCENTURY;

        Int centuryNum   = daysLeft / DAYS_PER_CENTURY;
        daysLeft        -= centuryNum * DAYS_PER_CENTURY;

        Int qennialNum   = daysLeft / DAYS_PER_QYEAR;
        daysLeft        -= qennialNum * DAYS_PER_QYEAR;

        Int ennialNum    = daysLeft / DAYS_PER_YEAR;
        daysLeft        -= ennialNum * DAYS_PER_YEAR;

        Int year         = QCENTURY_YEAR + qcenturyNum * 400
                                         + centuryNum  * 100
                                         + qennialNum  * 4
                                         + ennialNum;

        Int dayOfYear = daysLeft + 1;
        (Int month, Int day) = calcDate(year, dayOfYear);
        return year, month, day, dayOfYear;
        }

    /**
     * Convert a day-of-year value into a month and day value.
     *
     * @param year       the year number
     * @param dayOfYear  the "day of the year" value, in the range 1..365 (or 1..366 for a leap
     *                   year)
     *
     * @return a tuple of month and day
     */
    static (Int month, Int day) calcDate(Int year, Int dayOfYear)
        {
        assert 1 <= dayOfYear <= daysInYear(year);

        Int month = (dayOfYear - 1) / 31 + 1;
        if (daysInYearAtEndOf(year, month) < dayOfYear)
            {
            ++month;
            }

        return month, dayOfYear - daysInYearBefore(year, month);
        }

    /**
     * Calculate the day of the year, such that the first of January is day 1, and so on.
     *
     * @param year   a legal Gregorian year containing the month
     * @param month  the month in the range 1..12
     * @param day    a day in the range 1..31 (or a smaller legal range, depending on the year and
     *               month)
     *
     * @return the "n-th day of the year" (starting with 1) for the specified year, month, and day
     */
    static Int calcDayOfYear(Int year, Int month, Int day)
        {
        assert isGregorian(year, month, day);
        return daysInYearBefore(year, month) + day;
        }

    /**
     * Determine if the specified year is a leap year.
     *
     * @param year  the year to test
     *
     * @return True iff the specified year is a leap year
     */
    static Boolean isLeapYear(Int year)
        {
        return year > 1582              // before this, there were no Gregorian years
                && year  % 4   == 0     // must be divisible by 4
                && (year % 100 != 0     // unless divisible by 100
                ||  year % 400 == 0);   // unless divisible by 400
        }

    /**
     * Determine the number of days in the specified year.
     *
     * @param year  the year to calculate the number of days in
     *
     * @return 365 for normal years; 366 for leap years
     */
    static Int daysInYear(Int year)
        {
        return isLeapYear(year) ? 366 : 365;
        }

    /**
     * For a valid year and month, determine the number of days in the specified month.
     *
     *   Thirty days hath September,
     *   April, June, and November;
     *   All the rest have thirty-one,
     *   Save February,
     *   Which has twenty-eight in fine,
     *   But leap year gives it twenty-nine.
     *
     * @param year   a legal Gregorian year containing the month
     * @param month  the month in the range 1..12
     *
     * @return the number of days in the specified month
     */
    static Int daysInMonth(Int year, Int month)
        {
        assert 1 <= month <= 12;
        return (isLeapYear(year) ? MONTH_DAYS_LEAP : MONTH_DAYS) [month-1];
        }

    /**
     * Calculate the number of days that come before the specified month in the specified year.
     *
     * @param year   a legal Gregorian year containing the month
     * @param month  the month in the range 1..12
     *
     * @return the number of days that have passed in the specified year before the first day of the
     *         specified month
     */
    static Int daysInYearBefore(Int year, Int month)
        {
        assert 1 <= month <= 12;
        return (isLeapYear(year) ? SUM_DAYS_LEAP : SUM_DAYS) [month-1];
        }

    /**
     * Calculate the number of days that have passed at the end of the specified month in the
     * specified year.
     *
     * @param year   a legal Gregorian year containing the month
     * @param month  the month in the range 1..12
     *
     * @return the number of days that have passed in the specified year after the last day of the
     *         specified month
     */
    static Int daysInYearAtEndOf(Int year, Int month)
        {
        assert 1 <= month <= 12;
        return (isLeapYear(year) ? SUM_DAYS_LEAP : SUM_DAYS) [month];
        }

    /**
     * The number of days in each month (January is month 0) of the year for normal years.
     */
    static Int[] MONTH_DAYS      = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

    /**
     * The number of days in each month (January is month 0) of the year for leap years.
     */
    static Int[] MONTH_DAYS_LEAP = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

    /**
     * The number of days that have elapsed in a normal year at the beginning of a month (January is
     * month 0) and at the end of a month (January is month 1).
     */
    static Int[] SUM_DAYS        = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365];

    /**
     * The number of days that have elapsed in a leap year at the beginning of a month (January is
     * month 0) and at the end of a month (January is month 1).
     */
    static Int[] SUM_DAYS_LEAP   = [0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366];
    }
