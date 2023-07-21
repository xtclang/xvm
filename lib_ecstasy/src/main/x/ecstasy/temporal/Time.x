/**
 * A Time is a value that can provide Date and TimeOfDay information based on a combination of the
 * number of picoseconds elapsed since 1970-01-01T00:00:00Z, and a TimeZone.
 *
 * The Time _renders_ dates according to the Gregorian calendar, which began on 1582-10-15.
 */
const Time(Int128 epochPicos, TimeZone timezone = UTC)
        implements Destringable {
    /**
     * Create a new Time based on a Date, a TimeOfDay, and a TimeZone.
     *
     * @param date       the date value
     * @param timeOfDay  the time-of-day value
     * @param timezone   the timezone value
     */
    construct(Date date, TimeOfDay timeOfDay, TimeZone timezone) {
        // it is not possible to reverse a date and time-of-day from an unresolved TimeZone, such as
        // America/New_York, because it will have both duplicate (DST "fall back") and missing
        // (DST "spring forward") date and time-of-day values
        assert timezone.resolved;

        Int128 picos = date.epochDay.toInt128() * TimeOfDay.PicosPerDay
                     + timeOfDay.picos.toInt128()
                     - timezone.picos.toInt128();

        construct Time(picos, timezone);
    }

    /**
     * Construct a Time from an ISO-8601 date and time-of-day string with optional timezone.
     *
     * @param dt  the Time value in an ISO-8601 format (or alternatively, in the format
     *            produced by the [toString] method)
     */
    @Override
    construct(String dt) {
        if (Int timeOffset := dt.indexOf('T')) {
            if (timeOffset >= 8 && timeOffset <= dt.size-5) {
                TimeZone zone     = TimeZone.NoTZ;
                Int      tzOffset = dt.size-1;
                FindTZ: if (dt[tzOffset] == 'Z') {
                    zone = TimeZone.UTC;
                } else {
                    Int tzStop = tzOffset - 5;
                    while (--tzOffset >= tzStop) {
                        switch (dt[tzOffset]) {
                        case '+':
                        case '-':
                            // offset timezone found
                            zone = new TimeZone(dt.substring(tzOffset));
                            break FindTZ;
                        }
                    }

                    // no timezone found
                    tzOffset = dt.size;
                }

                Date      date      = new Date(dt[0 ..< timeOffset]);
                TimeOfDay timeOfDay = new TimeOfDay(dt[timeOffset >..< tzOffset]);
                construct Time(date, timeOfDay, zone);
                return;
            }
        }

        String[] parts = dt.split(' ');
        if (2 <= parts.size <= 3) {
            Date      date      = new Date(parts[0]);
            TimeOfDay timeOfDay = new TimeOfDay(parts[1]);
            TimeZone  zone      = parts.size == 3 ? new TimeZone(parts[2]) : TimeZone.NoTZ;
            construct Time(date, timeOfDay, zone);
            return;
        }

        throw new IllegalArgument($"invalid ISO-8601 Time: \"{dt}\"");
    }

    static Time EPOCH = new Time(0, UTC);


    // ----- withers -------------------------------------------------------------------------------

    /**
     * Create a new Time based on this Time, but with the Date and/or TimeOfDay and/or TimeZone
     * replaced with a new value.
     *
     * @param date       (optional) the new date value
     * @param timeOfDay  (optional) the new time-of-day value
     * @param timezone   (optional) the new timezone value
     *
     * @return the new Time
     */
    Time with(Date?      date      = Null,
              TimeOfDay? timeOfDay = Null,
              TimeZone?  timezone  = Null) {
        return new Time(date      ?: this.date,
                        timeOfDay ?: this.timeOfDay,
                        timezone  ?: this.timezone);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * The offset (in number of picoseconds) from the epoch to the beginning of the Gregorian
     * calendar, 1582-10-14T00:00:00.
     */
    static Int128 GREGORIAN_OFFSET = Date.GREGORIAN_OFFSET * TimeOfDay.PicosPerDay;

    Int128 adjustedPicos.get() {
        return timezone.resolve(this).picos.toInt128() + epochPicos;
    }

    /**
     * The date portion of the date/time value.
     */
    Date date.get() {
        Int128 picos = adjustedPicos;
        return new Date(picos >= 0
                ? (picos / TimeOfDay.PicosPerDay).toInt32()
                : -1 - ((picos.abs() - 1) / TimeOfDay.PicosPerDay).toInt32());
    }

    /**
     * The time-of-day portion of the date/time value.
     */
    TimeOfDay timeOfDay.get() = new TimeOfDay((adjustedPicos % TimeOfDay.PicosPerDay).toInt());

    /**
     * The TimeZone of the Time value.
     */
    TimeZone timezone;

    /**
     * For a Time value, obtain the UTC Time value.
     */
    Time utc() {
        return TimeZone.UTC.adopt(this);
    }


    // ----- operators -----------------------------------------------------------------------------

    /**
     * Add a Duration to this Time, resulting in a Time.
     */
    @Op("+") Time add(Duration duration) {
        return new Time(epochPicos + duration.picoseconds.toInt128(), timezone);
    }

    /**
     * Subtract a Duration from this Time, resulting in a Time.
     */
    @Op("-") Time sub(Duration duration) {
        return new Time(epochPicos - duration.picoseconds.toInt128(), timezone);
    }

    /**
     * Subtract a second Time from this Time, resulting in a Duration that represents the
     * difference between the two Time values. It is an error for the Time to subtract (the
     * subtrahend) to be greater than the DataTime being subtracted from (minuend). It is an error
     * for exactly one of the two Time values to use the NoTZ TimeZone, as Time values with
     * a real TimeZone cannot be compared to Time values that use the NoTZ TimeZone.
     */
    @Op("-") Duration sub(Time time) {
        assert this.timezone.isNoTZ == time.timezone.isNoTZ;
        return new Duration(this.epochPicos - time.epochPicos);
    }

    /**
     * Compare two Time values for order.
     */
    static <CompileType extends Time> Ordered compare(CompileType value1, CompileType value2) {
        assert value1.timezone.isNoTZ == value2.timezone.isNoTZ;
        return value1.epochPicos <=> value2.epochPicos;
    }

    /**
     * Compare two Time values for equality.
     */
    static <CompileType extends Time> Boolean equals(CompileType value1, CompileType value2) {
        assert value1.timezone.isNoTZ == value2.timezone.isNoTZ;
        return value1.epochPicos == value2.epochPicos;
    }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    String toString(Boolean iso8601 = False) {
        return appendTo(new StringBuffer(estimateStringLength(iso8601)), iso8601).toString();
    }

    @Override
    Int estimateStringLength(Boolean iso8601 = False) {
        // assume "2020-10-02T20:02:12+00:00" for ISO8601
        // assume "yyyy-mm-dd hh:mm:ss tz" otherwise

        TimeZone tz = this.timezone;
        if (!tz.resolved) {
            tz = tz.resolve(this);
        }
        Int tzSize = iso8601 || tz.picos != 0 ? (iso8601 ? 0 : 1) + tz.estimateStringLength(iso8601) : 0;

        Int fraction = (epochPicos % Duration.PicosPerSecond).toInt64();
        Int fractionSize = fraction == 0 ? 0 : 1 + Duration.picosFractionalLength(fraction);

        return 19 + fractionSize + tzSize;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf, Boolean iso8601 = False) {
        date.appendTo(buf);
        buf.add(iso8601 ? 'T' : ' ');
        timeOfDay.appendTo(buf);

        if (iso8601 || timezone.picos != 0) {
            if (!iso8601) {
                buf.add(' ');
            }
            timezone.appendTo(buf, iso8601);
        }

        return buf;
    }
}
