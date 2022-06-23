/**
 * A DateTime is a value that can provide Date and TimeOfDay information based on a combination of the
 * number of picoseconds elapsed since 1970-01-01T00:00:00Z, and a TimeZone.
 *
 * The DateTime _renders_ dates according to the Gregorian calendar, which began on 1582-10-15.
 */
const DateTime(Int128 epochPicos, TimeZone timezone = UTC)
    {
    /**
     * Create a new DateTime based on a Date, a TimeOfDay, and a TimeZone.
     *
     * @param date       the date value
     * @param timeOfDay  the time-of-day value
     * @param timezone   the timezone value
     */
    construct(Date date, TimeOfDay timeOfDay, TimeZone timezone)
        {
        // it is not possible to reverse a date and time-of-day from an unresolved TimeZone, such as
        // America/New_York, because it will have both duplicate (DST "fall back") and missing
        // (DST "spring forward") date and time-of-day values
        assert timezone.resolved;

        Int128 picos = date.epochDay.toInt128() * TimeOfDay.PICOS_PER_DAY
                     + timeOfDay.picos.toInt128()
                     - timezone.picos.toInt128();

        construct DateTime(picos, timezone);
        }

    /**
     * Construct a DateTime from an ISO-8601 date and time-of-day string with optional timezone.
     *
     * @param dt  the datetime value in an ISO-8601 format (or alternatively, in the format
     *            produced by the [toString] method)
     */
    construct(String dt)
        {
        if (Int timeOffset := dt.indexOf('T'))
            {
            if (timeOffset >= 8 && timeOffset <= dt.size-5)
                {
                TimeZone zone     = TimeZone.NoTZ;
                Int      tzOffset = dt.size-1;
                FindTZ: if (dt[tzOffset] == 'Z')
                    {
                    zone = TimeZone.UTC;
                    }
                else
                    {
                    Int tzStop = tzOffset - 5;
                    while (--tzOffset >= tzStop)
                        {
                        switch (dt[tzOffset])
                            {
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

                Date      date      = new Date(dt[0..timeOffset));
                TimeOfDay timeOfDay = new TimeOfDay(dt[timeOffset+1..tzOffset));
                construct DateTime(date, timeOfDay, zone);
                return;
                }
            }

        String[] parts = dt.split(' ');
        if (2 <= parts.size <= 3)
            {
            Date      date      = new Date(parts[0]);
            TimeOfDay timeOfDay = new TimeOfDay(parts[1]);
            TimeZone  zone      = parts.size == 3 ? new TimeZone(parts[2]) : TimeZone.NoTZ;
            construct DateTime(date, timeOfDay, zone);
            return;
            }

        throw new IllegalArgument($"invalid ISO-8601 datetime: \"{dt}\"");
        }

    static DateTime EPOCH = new DateTime(0, UTC);


    // ----- withers -------------------------------------------------------------------------------

    /**
     * Create a new DateTime based on this DateTime, but with the Date and/or TimeOfDay and/or TimeZone
     * replaced with a new value.
     *
     * @param date       (optional) the new date value
     * @param timeOfDay  (optional) the new time-of-day value
     * @param timezone   (optional) the new timezone value
     *
     * @return the new DateTime
     */
    DateTime with(Date?      date      = Null,
                  TimeOfDay? timeOfDay = Null,
                  TimeZone?  timezone  = Null)
        {
        return new DateTime(date      ?: this.date,
                            timeOfDay ?: this.timeOfDay,
                            timezone  ?: this.timezone);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * The offset (in number of picoseconds) from the epoch to the beginning of the Gregorian
     * calendar, 1582-10-14T00:00:00.
     */
    static Int128 GREGORIAN_OFFSET = Date.GREGORIAN_OFFSET * TimeOfDay.PICOS_PER_DAY;

    Int128 adjustedPicos.get()
        {
        return timezone.resolve(this).picos.toInt128() + epochPicos;
        }

    /**
     * The date portion of the date/time value.
     */
    Date date.get()
        {
        Int128 picos = adjustedPicos;
        return new Date(picos >= 0
                ? (picos / TimeOfDay.PICOS_PER_DAY).toInt64()
                : -1 - ((picos.abs() - 1) / TimeOfDay.PICOS_PER_DAY).toInt64());
        }

    /**
     * The time-of-day portion of the date/time value.
     */
    TimeOfDay timeOfDay.get()
        {
        return new TimeOfDay((adjustedPicos % TimeOfDay.PICOS_PER_DAY).toInt64());
        }

    /**
     * The TimeZone of the DateTime value.
     */
    TimeZone timezone;

    /**
     * For a DateTime value, obtain the UTC DateTime value.
     */
    DateTime utc()
        {
        return TimeZone.UTC.adopt(this);
        }


    // ----- operators -----------------------------------------------------------------------------

    /**
     * Add a Duration to this DateTime, resulting in a DateTime.
     */
    @Op("+") DateTime add(Duration duration)
        {
        return new DateTime(epochPicos + duration.picoseconds.toInt128(), timezone);
        }

    /**
     * Subtract a Duration from this DateTime, resulting in a DateTime.
     */
    @Op("-") DateTime sub(Duration duration)
        {
        return new DateTime(epochPicos - duration.picoseconds.toInt128(), timezone);
        }

    /**
     * Subtract a second DateTime from this DateTime, resulting in a Duration that represents the
     * difference between the two DateTime values. It is an error for the DateTime to subtract (the
     * subtrahend) to be greater than the DataTime being subtracted from (minuend). It is an error
     * for exactly one of the two DateTime values to use the NoTZ TimeZone, as DateTime values with
     * a real TimeZone cannot be compared to DateTime values that use the NoTZ TimeZone.
     */
    @Op("-") Duration sub(DateTime datetime)
        {
        assert this.timezone.isNoTZ == datetime.timezone.isNoTZ;
        return new Duration((this.epochPicos - datetime.epochPicos).toUInt128());
        }

    /**
     * Compare two DateTime values for order.
     */
    static <CompileType extends DateTime> Ordered compare(CompileType value1, CompileType value2)
        {
        assert value1.timezone.isNoTZ == value2.timezone.isNoTZ;
        return value1.epochPicos <=> value2.epochPicos;
        }

    /**
     * Compare two DateTime values for equality.
     */
    static <CompileType extends DateTime> Boolean equals(CompileType value1, CompileType value2)
        {
        assert value1.timezone.isNoTZ == value2.timezone.isNoTZ;
        return value1.epochPicos == value2.epochPicos;
        }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    String toString(Boolean iso8601 = False)
        {
        return appendTo(new StringBuffer(estimateStringLength(iso8601)), iso8601).toString();
        }

    @Override
    Int estimateStringLength(Boolean iso8601 = False)
        {
        // assume "2020-10-02T20:02:12+00:00" for ISO8601
        // assume "yyyy-mm-dd hh:mm:ss tz" otherwise

        TimeZone tz = this.timezone;
        if (!tz.resolved)
            {
            tz = tz.resolve(this);
            }
        Int tzSize = iso8601 || tz.picos != 0 ? (iso8601 ? 0 : 1) + tz.estimateStringLength(iso8601) : 0;

        Int fraction = (epochPicos % Duration.PICOS_PER_SECOND).toInt64();
        Int fractionSize = fraction == 0 ? 0 : 1 + Duration.picosFractionalLength(fraction);

        return 19 + fractionSize + tzSize;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf, Boolean iso8601 = False)
        {
        date.appendTo(buf);
        buf.add(iso8601 ? 'T' : ' ');
        timeOfDay.appendTo(buf);

        if (iso8601 || timezone.picos != 0)
            {
            if (!iso8601)
                {
                buf.add(' ');
                }
            timezone.appendTo(buf, iso8601);
            }

        return buf;
        }
    }
