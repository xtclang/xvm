/**
 * A DateTime is a value that can provide Date and Time information based on a combination of the
 * number of picoseconds elapsed since 1970-01-01T00:00:00Z, and a TimeZone.
 *
 * The DateTime _renders_ dates according to the Gregorian calendar, which began on 1582-10-15.
 */
const DateTime(Int128 epochPicos, TimeZone timezone)
    {
    construct(Date date, Time time, TimeZone timezone)
        {
        // it is not possible to reverse a date and time from an unresolved TimeZone, such as
        // America/New_York, because it will have both duplicate (DST "fall back") and missing
        // (DST "spring forward") date and time values
        assert timezone.resolved;

        Int128 picos = date.epochDay.to<Int128>() * Time.PICOS_PER_DAY
                     + time.picos.to<Int128>();
                     - timezone.picos.to<Int128>();

        construct DateTime(picos, timezone);
        }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * The offset (in number of picoseconds) from the epoch to the beginning of the Gregorian
     * calendar, 1582-10-14T00:00:00.
     */
    static Int128 GREGORIAN_OFFSET = Date.GREGORIAN_OFFSET * Time.PICOS_PER_DAY;

    Int128 adjustedPicos.get()
        {
        return timezone.resolve(this).picos.to<Int128>() + epochPicos;
        }

    /**
     * The date portion of the date/time value.
     */
    Date date.get()
        {
        Int128 picos = adjustedPicos;
        return new Date(picos >= 0
                ? (picos / Time.PICOS_PER_DAY).to<Int>()
                : -1 - ((picos.abs() - 1) / Time.PICOS_PER_DAY).to<Int>());
        }

    /**
     * The time portion of the date/time value.
     */
    Time time.get()
        {
        return new Time(adjustedPicos % Time.PICOS_PER_DAY);
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
        return new DateTime(epochPicos + duration.picosecondsTotal.to<Int128>(), timezone);
        }

    /**
     * Subtract a Duration from this DateTime, resulting in a DateTime.
     */
    @Op("-") DateTime sub(Duration duration)
        {
        return new DateTime(epochPicos - duration.picosecondsTotal.to<Int128>(), timezone);
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
        return new Duration((this.epochPicos - datetime.epochPicos).to<UInt128>());
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
    void appendTo(Appender<Char> appender)
        {
        date.appendTo(appender);
        appender.add(' ');
        time.appendTo(appender);
        appender.add(' ');
        timezone.appendTo(appender);
        }
    }
