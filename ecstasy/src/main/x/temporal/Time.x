import numbers.UInt128;

/**
 * A Time represents information about "time-of-day". It is primarily intended to support human
 * interface concepts, such as scheduling, or to assist in displaying information.
 */
const Time(Int picos)
    {
    static IntLiteral PICOS_PER_NANO   = 1000;
    static IntLiteral PICOS_PER_MICRO  = 1000 * PICOS_PER_NANO;
    static IntLiteral PICOS_PER_MILLI  = 1000 * PICOS_PER_MICRO;
    static IntLiteral PICOS_PER_SECOND = 1000 * PICOS_PER_MILLI;
    static IntLiteral PICOS_PER_MINUTE = 60   * PICOS_PER_SECOND;
    static IntLiteral PICOS_PER_HOUR   = 60   * PICOS_PER_MINUTE;
    static IntLiteral PICOS_PER_DAY    = 24   * PICOS_PER_HOUR;

    static Time MIDNIGHT = new Time(0);

    /**
     * Construct a Time based on the number of picoseconds elapsed since 00:00:00.
     *
     * @param picos  the number of picoseconds elapsed since 00:00:00
     */
    construct(Int picos)
        {
        assert picos >= 0 && picos < PICOS_PER_DAY;
        this.picos = picos;
        }

    /**
     * Construct a Time based on number hours, minutes, seconds, and so on.
     *
     * @param hour    the number of hours, in the range 0..23
     * @param minute  the number of minutes, in the range 0..59
     * @param second  the number of seconds, in the range 0..59
     * @param picos   the number of picoseconds, in the range 0..999999999999
     */
    construct(Int hour, Int minute, Int second=0, Int picos=0)
        {
        assert hour   >= 0 && hour   < 24;
        assert minute >= 0 && minute < 60;
        assert second >= 0 && second < 60;
        assert picos  >= 0 && picos  < PICOS_PER_SECOND;
        construct Time(((hour * 60 + minute) * 60 + second) * PICOS_PER_SECOND + picos);
        }

    /**
     * Construct a Time from an ISO-8601 time string.
     */
    construct (String time)
        {
        String   hours;
        String   mins;
        String   secs  = "";
        String[] parts = time.split(':');
        switch (parts.size)
            {
            case 3:
                secs  = parts[2];
                continue;
            case 2:
                hours = parts[0];
                mins  = parts[1];
                break;

            case 1:
                Int len = time.size;
                if (len >= 4)
                    {
                    hours = time[0..1];
                    mins  = time[2..3];
                    if (len > 4)
                        {
                        secs = time[4..len);
                        }
                    break;
                    }
                continue;

            default:
                throw new IllegalArgument($"invalid ISO-8601 time: \"{time}\"");
            }

        Int hour = new IntLiteral(hours).toInt();
        Int min  = new IntLiteral(mins ).toInt();
        Int sec  = 0;
        Int pico = 0;

        if (secs != "")
            {
            if (Int dot := secs.indexOf('.'))
                {
                if (dot > 0)
                    {
                    sec = new IntLiteral(secs[0..dot)).toInt();
                    }

                Int len = secs.size;
                if (dot < len-1)
                    {
                    String picos = secs[dot+1..len);
                    if (picos.size > 12)
                        {
                        picos = picos[0..11];
                        }

                    pico = new IntLiteral(picos).toInt() * SCALE_10[picos.size];
                    }
                }
            else
                {
                sec = new IntLiteral(secs).toInt();
                }
            }

        construct Time(hour, min, sec, pico);
        }

    private static Int[] SCALE_10 = [           1_000_000_000_000,
            100_000_000_000,    10_000_000_000,     1_000_000_000,
                100_000_000,        10_000_000,         1_000_000,
                    100_000,            10_000,             1_000,
                        100,                10,                 1 ];

    /**
     * The hour of the day, in the range 0..23.
     */
    Int hour.get()
        {
        return picos / PICOS_PER_HOUR;
        }

    /**
     * The minute of the hour, in the range 0..59.
     */
    Int minute.get()
        {
        return picos / PICOS_PER_MINUTE % 60;
        }

    /**
     * The second of the minute, in the range 0..59.
     */
    Int second.get()
        {
        return picos / PICOS_PER_SECOND % 60;
        }

    /**
     * The fraction of a second, represented as milliseconds, in the range 0..999.
     * This is the same as:
     *
     *   microseconds / 1000
     */
    Int milliseconds.get()
        {
        return picoseconds / PICOS_PER_MILLI;
        }

    /**
     * The fraction of a second, represented as microseconds, in the range 0..999999.
     * This is the same as:
     *
     *   nanoseconds / 1000
     */
    Int microseconds.get()
        {
        return picoseconds / PICOS_PER_MICRO;
        }

    /**
     * The fraction of a second, represented as nanoseconds, in the range 0..999999999.
     * This is the same as:
     *
     *   picoseconds / 1000
     */
    Int nanoseconds.get()
        {
        return picoseconds / PICOS_PER_NANO;
        }

    /**
     * The fraction of a second, represented as picoseconds, in the range 0..999999999999.
     */
    Int picoseconds.get()
        {
        return picos % PICOS_PER_SECOND;
        }

    // ----- operators -----------------------------------------------------------------------------

    @Op("+") Time add(Duration duration)
        {
        UInt128 period = duration.picoseconds;
        if (period == 0)
            {
            return this;
            }

        return new Time(((picos.toUInt128() + period) % PICOS_PER_DAY).toInt());
        }

    @Op("-") Time sub(Duration duration)
        {
        Int minuend    = this.picos;
        Int subtrahend = (duration.picoseconds % PICOS_PER_DAY).toInt();
        if (subtrahend > minuend)
            {
            minuend += PICOS_PER_DAY;
            }
        return new Time(minuend - subtrahend);
        }

    @Op("-") Duration sub(Time time)
        {
        Int picosStop  = this.picos;
        Int picosStart = time.picos;
        if (picosStart > picosStop)
            {
            picosStop += PICOS_PER_DAY;
            }
        return new Duration((picosStop - picosStart).toUInt128());
        }

    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the Duration of time since midnight represented by this Time object
     */
    Duration toDuration()
        {
        return new Duration(picos.toUInt128());
        }

    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int fraction = picoseconds;
        return fraction == 0
                ? 8
                : 9 + Duration.picosFractionalLength(fraction);
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        Int hour = this.hour;
        if (hour < 10)
            {
            buf.add('0');
            }
        hour.appendTo(buf);
        buf.add(':');

        Int minute = this.minute;
        if (minute < 10)
            {
            buf.add('0');
            }
        minute.appendTo(buf);
        buf.add(':');

        Int second = this.second;
        if (second < 10)
            {
            buf.add('0');
            }
        second.appendTo(buf);
        return Duration.appendPicosFractional(buf, picoseconds);
        }
    }
