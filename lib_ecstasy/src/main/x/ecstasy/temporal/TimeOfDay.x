/**
 * A `TimeOfDay` represents information about "time-of-day", also referred to as "wall clock time".
 * It is primarily intended to support human interface concepts, such as scheduling, or to assist in
 * displaying information.
 *
 * The `TimeOfDay` has rudimentary support for representing and dealing with leap seconds.
 */
const TimeOfDay(Int picos)
    {
    static IntLiteral PICOS_PER_NANO   = 1K;
    static IntLiteral PICOS_PER_MICRO  = 1K * PICOS_PER_NANO;
    static IntLiteral PICOS_PER_MILLI  = 1K * PICOS_PER_MICRO;
    static IntLiteral PICOS_PER_SECOND = 1K * PICOS_PER_MILLI;
    static IntLiteral PICOS_PER_MINUTE = 60 * PICOS_PER_SECOND;
    static IntLiteral PICOS_PER_HOUR   = 60 * PICOS_PER_MINUTE;
    static IntLiteral PICOS_PER_DAY    = 24 * PICOS_PER_HOUR;

    static TimeOfDay MIDNIGHT = new TimeOfDay(0);

    /**
     * Construct a `TimeOfDay` based on the number of picoseconds elapsed since `00:00:00`.
     *
     * @param picos  the number of picoseconds elapsed since `00:00:00`
     */
    construct(Int picos)
        {
        assert 0 <= picos < PICOS_PER_DAY + PICOS_PER_SECOND;  // allow for a leap-second
        this.picos = picos;
        }

    /**
     * Construct a `TimeOfDay` based on number hours, minutes, seconds, and picoseconds.
     *
     * @param hour    the number of hours, in the range `0..23`
     * @param minute  the number of minutes, in the range `0..59`
     * @param second  the number of seconds, in the range `0..59`; the leap second `23:59:60` is also
     *                supported
     * @param picos   the number of picoseconds, in the range `0..999999999999`
     */
    construct(Int hour, Int minute, Int second=0, Int picos=0)
        {
        assert 0 <= hour   < 24;
        assert 0 <= minute < 60;
        assert 0 <= second < 60
                || hour == 23 && minute == 59 && second == 60; // allow for a leap-second
        assert 0 <= picos  < PICOS_PER_SECOND;
        construct TimeOfDay(((hour * 60 + minute) * 60 + second) * PICOS_PER_SECOND + picos);
        }

    /**
     * Construct a `TimeOfDay` from an ISO-8601 time string.
     */
    construct (String text)
        {
        String   hours;
        String   mins;
        String   secs  = "";
        String[] parts = text.split(':');
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
                Int len = text.size;
                if (len >= 4)
                    {
                    hours = text[0..1];
                    mins  = text[2..3];
                    if (len > 4)
                        {
                        secs = text[4..len);
                        }
                    break;
                    }
                continue;

            default:
                throw new IllegalArgument($"invalid ISO-8601 time: \"{text}\"");
            }

        Int hour = new IntLiteral(hours);
        Int min  = new IntLiteral(mins );
        Int sec  = 0;
        Int pico = 0;

        static Int[] SCALE_10 = [1T, 100G, 10G, 1G, 100M, 10M, 1M, 100K, 10K, 1K, 100, 10, 1];

        if (secs != "")
            {
            if (Int dot := secs.indexOf('.'))
                {
                if (dot > 0)
                    {
                    sec = new IntLiteral(secs[0..dot));
                    }

                Int len = secs.size;
                if (dot < len-1)
                    {
                    String picos = secs[dot+1..len);
                    if (picos.size > 12)
                        {
                        picos = picos[0..12);
                        }

                    pico = new IntLiteral(picos) * SCALE_10[picos.size];
                    }
                }
            else
                {
                sec = new IntLiteral(secs);
                }
            }

        construct TimeOfDay(hour, min, sec, pico);
        }

    /**
     * The hour of the day, in the range `0..23`.
     */
    Int hour.get()
        {
        return picos >= PICOS_PER_DAY
                ? 23
                : picos / PICOS_PER_HOUR;
        }

    /**
     * The minute of the hour, in the range `0..59`.
     */
    Int minute.get()
        {
        return picos >= PICOS_PER_DAY
                ? 59
                : picos / PICOS_PER_MINUTE % 60;
        }

    /**
     * The second of the minute, in the range `0..59` (or `60`, in the extremely rare case of a leap
     * second).
     */
    Int second.get()
        {
        return picos >= PICOS_PER_DAY
                ? 60
                : picos / PICOS_PER_SECOND % 60;
        }

    /**
     * The fraction of a second, represented as milliseconds, in the range `0..999`.
     * This is the same as `microseconds / 1000`.
     */
    Int milliseconds.get()
        {
        return picoseconds / PICOS_PER_MILLI;
        }

    /**
     * The fraction of a second, represented as microseconds, in the range `0..999999`.
     * This is the same as `nanoseconds / 1000`.
     */
    Int microseconds.get()
        {
        return picoseconds / PICOS_PER_MICRO;
        }

    /**
     * The fraction of a second, represented as nanoseconds, in the range `0..999999999`.
     * This is the same as `picoseconds / 1000`.
     */
    Int nanoseconds.get()
        {
        return picoseconds / PICOS_PER_NANO;
        }

    /**
     * The fraction of a second, represented as picoseconds, in the range `0..999999999999`.
     */
    Int picoseconds.get()
        {
        return picos % PICOS_PER_SECOND;
        }


    // ----- operators -----------------------------------------------------------------------------

    @Op("+") TimeOfDay add(Duration duration)
        {
        Int period = (duration.picoseconds % PICOS_PER_DAY).toInt64();
        if (period == 0)
            {
            return this;
            }

        Int sum = picos + period;
        if (sum > PICOS_PER_DAY)
            {
            // check if this TimeOfDay is a leap second
            if (picos > PICOS_PER_DAY)
                {
                // this TimeOfDay is a leap second, so treat the result accordingly
                if (sum > PICOS_PER_DAY + PICOS_PER_SECOND)
                    {
                    sum -= PICOS_PER_DAY + PICOS_PER_SECOND;
                    }
                }
            else
                {
                sum -= PICOS_PER_DAY;
                }
            }

        return new TimeOfDay(sum);
        }

    @Op("-") TimeOfDay sub(Duration duration)
        {
        Int minuend    = this.picos;
        Int subtrahend = (duration.picoseconds % PICOS_PER_DAY).toInt64();
        if (subtrahend > minuend)
            {
            minuend += PICOS_PER_DAY;
            }
        return new TimeOfDay(minuend - subtrahend);
        }

    @Op("-") Duration sub(TimeOfDay timeOfDay)
        {
        Int picosStop  = this.picos;
        Int picosStart = timeOfDay.picos;

        if (picosStart > picosStop)
            {
            // treat the time-of-day being subtracted as being from "yesterday"
            picosStop += PICOS_PER_DAY;

            // check for the possibility that "yesterday" had an obvious leap second
            if (picosStart > PICOS_PER_DAY)
                {
                picosStop += PICOS_PER_SECOND;
                }
            }
        return new Duration((picosStop - picosStart).toUInt128());
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the [Duration] of time since midnight represented by this `TimeOfDay` object
     */
    Duration toDuration()
        {
        return new Duration(picos.toUInt128());
        }


    // ----- Stringable interface ------------------------------------------------------------------

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
