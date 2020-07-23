import numbers.Int128;

/**
 * A DateTime is a value that can provide Date and Time information based on a combination of the
 * number of picoseconds elapsed since 1970-01-01T00:00:00Z, and a TimeZone.
 *
 * The DateTime _renders_ dates according to the Gregorian calendar, which began on 1582-10-15.
 */
const DateTime(Int128 epochPicos, TimeZone timezone = UTC)
    {
    construct(Date date, Time time, TimeZone timezone)
        {
        // it is not possible to reverse a date and time from an unresolved TimeZone, such as
        // America/New_York, because it will have both duplicate (DST "fall back") and missing
        // (DST "spring forward") date and time values
        assert timezone.resolved;

        Int128 picos = date.epochDay.toInt128() * Time.PICOS_PER_DAY
                     + time.picos.toInt128()
                     - timezone.picos.toInt128();

        construct DateTime(picos, timezone);
        }

    /**
     * Construct a DateTime from an ISO-8601 date and time string with optional timezone.
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

                Date date = new Date(dt[0..timeOffset));
                Time time = new Time(dt[timeOffset+1..tzOffset));
                construct DateTime(date, time, zone);
                return;
                }
            }

        throw new IllegalArgument($"invalid ISO-8601 datetime: \"{dt}\"");
        }

    static DateTime EPOCH = new DateTime(0, UTC);

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * The offset (in number of picoseconds) from the epoch to the beginning of the Gregorian
     * calendar, 1582-10-14T00:00:00.
     */
    static Int128 GREGORIAN_OFFSET = Date.GREGORIAN_OFFSET * Time.PICOS_PER_DAY;

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
                ? (picos / Time.PICOS_PER_DAY).toInt()
                : -1 - ((picos.abs() - 1) / Time.PICOS_PER_DAY).toInt());
        }

    /**
     * The time portion of the date/time value.
     */
    Time time.get()
        {
        return new Time((adjustedPicos % Time.PICOS_PER_DAY).toInt());
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
    Int estimateStringLength()
        {
        // assume "yyyy-mm-dd hh:mm:ss tz"
        return 20 + timezone.estimateStringLength();
        }

    @Override
    void appendTo(Appender<Char> buf)
        {
        date.appendTo(buf);
        buf.add(' ');
        time.appendTo(buf);
        buf.add(' ');
        timezone.appendTo(buf);
        }
    }
