import numbers.UInt128;

/**
 * A Duration represents a magnitude of time, with picosecond resolution.
 */
const Duration(UInt128 picoseconds)
    {
    static IntLiteral PICOS_PER_NANO   = 1000;
    static IntLiteral PICOS_PER_MICRO  = 1000 * PICOS_PER_NANO;
    static IntLiteral PICOS_PER_MILLI  = 1000 * PICOS_PER_MICRO;
    static IntLiteral PICOS_PER_SECOND = 1000 * PICOS_PER_MILLI;
    static IntLiteral PICOS_PER_MINUTE = 60   * PICOS_PER_SECOND;
    static IntLiteral PICOS_PER_HOUR   = 60   * PICOS_PER_MINUTE;
    static IntLiteral PICOS_PER_DAY    = 24   * PICOS_PER_HOUR;

    static Duration NONE     = new Duration(0);
    static Duration PICOSEC  = new Duration(1);
    static Duration NANOSEC  = new Duration(PICOS_PER_NANO);
    static Duration MICROSEC = new Duration(PICOS_PER_MICRO);
    static Duration MILLISEC = new Duration(PICOS_PER_MILLI);
    static Duration SECOND   = new Duration(PICOS_PER_SECOND);
    static Duration MINUTE   = new Duration(PICOS_PER_MINUTE);
    static Duration HOUR     = new Duration(PICOS_PER_HOUR);
    static Duration DAY      = new Duration(PICOS_PER_DAY);

    /**
     * Construct a time duration from an ISO-8601 format string. Note that this does not include
     * support for year or month values, since neither a year nor a month has a fixed size.
     *
     * @param duration  an ISO-8601 format string of the form "PnDTnHnMnS"
     */
    construct(String duration)
        {
        // state machine stages:
        //     P   nD  T   nH  nM  n.  nS
        //   0   1   2   3   4   5   6   7

        UInt128 total  = 0;
        Int     index  = 0;
        Int     length = duration.size;
        Int     last   = length -1;
        Int     stage  = 0;
        Boolean any    = False;

        Loop: while (index < length)
            {
            switch (Char ch = duration[index++])
                {
                case 'P':
                    // while 'P' is required per the ISO-8601 specification, this constructor is
                    // only used for duration values, so
                    if (stage >= 1)
                        {
                        throw new IllegalArgument("duration must begin with 'P' and must contain"
                                + $" no other occurrences of 'P': \"{duration}\"");
                        }
                    stage = 1;
                    break;

                case 'T':
                    if (stage >= 3)
                        {
                        throw new IllegalArgument("duration includes 'T' to separate date from time"
                                +  " components,  and must contain no other occurrences of 'T':"
                                + $" \"{duration}\"");
                        }
                    stage = 3;
                    break;

                case '0'..'9':
                    // parse the number as an integer
                    UInt128 part   = 0;
                    Int     digits = 0;
                    do
                        {
                        ++digits;
                        part = part * 10 + (ch - '0');
                        if (index > last)
                            {
                            throw new IllegalArgument("duration is missing a trailing indicator:"
                                    + $" \"{duration}\"");
                            }

                        ch = duration[index++];
                        }
                    while (ch >= '0' && ch <= '9');

                    Int     stageNew;
                    UInt128 factor;
                    switch (ch)
                        {
                        case 'D':
                            stageNew = 2;
                            factor   = Time.PICOS_PER_DAY;
                            break;

                        case 'H':
                            stageNew = 4;
                            factor   = Time.PICOS_PER_HOUR;
                            break;

                        case 'M':
                            stageNew = 5;
                            factor   = Time.PICOS_PER_MINUTE;
                            break;

                        case '.':
                            stageNew = 6;
                            factor   = Time.PICOS_PER_SECOND;
                            break;

                        case 'S':
                            if (stage == 6)
                                {
                                // this is a fractional value
                                while (digits > 12)
                                    {
                                    part /= 10;
                                    --digits;
                                    }

                                static UInt128[] SCALE_10 = [               1_000_000_000_000,
                                        100_000_000_000,    10_000_000_000,     1_000_000_000,
                                            100_000_000,        10_000_000,         1_000_000,
                                                100_000,            10_000,             1_000,
                                                    100,                10,                 1 ];

                                factor = SCALE_10[digits];
                                }
                            else
                                {
                                factor = Time.PICOS_PER_SECOND;
                                }
                            stageNew = 7;
                            break;

                        default:
                            throw new IllegalArgument("duration is missing a trailing indicator:"
                                    + $" \"{duration}\"");
                        }

                    if (stageNew <= stage)
                        {
                        throw new IllegalArgument("duration components are out of order:"
                                + $" \"{duration}\"");
                        }

                    total += part * factor;
                    stage  = stageNew;
                    any    = True;
                    break;

                default:
                    throw new IllegalArgument("duration includes an illegal character or character"
                            + $" sequence starting with \"{duration[index]}\": \"{duration}\"");
                }
            }

        if (stage == 6)
            {
            throw new IllegalArgument($"missing fractional second duration: \"{duration}\"");
            }

        if (any)
            {
            construct Duration(total);
            }
        else
            {
            throw new IllegalArgument($"no duration information provided: \"{duration}\"");
            }
        }

    /**
     * Create a Duration of a certain number of days.
     *
     * @param the number of days in the requested Duration
     */
    static Duration ofDays(Int days)
        {
        return new Duration(days.toUInt128() * PICOS_PER_DAY);
        }

    /**
     * Create a Duration of a certain number of hours.
     *
     * @param the number of hours in the requested Duration
     */
    static Duration ofHours(Int hours)
        {
        return new Duration(hours.toUInt128() * PICOS_PER_HOUR);
        }

    /**
     * Create a Duration of a certain number of minutes.
     *
     * @param the number of minutes in the requested Duration
     */
    static Duration ofMinutes(Int minutes)
        {
        return new Duration(minutes.toUInt128() * PICOS_PER_MINUTE);
        }

    /**
     * Create a Duration of a certain number of seconds.
     *
     * @param the number of seconds in the requested Duration
     */
    static Duration ofSeconds(Int seconds)
        {
        return new Duration(seconds.toUInt128() * PICOS_PER_SECOND);
        }

    /**
     * Create a Duration of a certain number of milliseconds.
     *
     * @param the number of milliseconds in the requested Duration
     */
    static Duration ofMillis(Int millis)
        {
        return new Duration(millis.toUInt128() * PICOS_PER_MILLI);
        }

    /**
     * Create a Duration of a certain number of microseconds.
     *
     * @param the number of microseconds in the requested Duration
     */
    static Duration ofMicros(Int micros)
        {
        return new Duration(micros.toUInt128() * PICOS_PER_MICRO);
        }

    /**
     * Create a Duration of a certain number of nanoseconds.
     *
     * @param the number of nanoseconds in the requested Duration
     */
    static Duration ofNanos(Int nanos)
        {
        return new Duration(nanos.toUInt128() * PICOS_PER_NANO);
        }

    /**
     * Create a Duration of a certain number of picoseconds.
     *
     * @param the number of picoseconds in the requested Duration
     */
    static Duration ofPicos(Int picos)
        {
        return new Duration(picos.toUInt128());
        }

    /**
     * Construct a Duration based on a total number of picoseconds.
     *
     * @param picoseconds  the total number of picoseconds in the Duration
     */
    construct(UInt128 picoseconds)
        {
        assert picoseconds >= 0;
        this.picoseconds = picoseconds;
        }

    /**
     * Construct a Duration based on a total number of picoseconds.
     *
     * @param days     a number of days to add to the duration
     * @param hours    a number of hours to add to the duration
     * @param minutes  a number of minutes to add to the duration
     * @param seconds  (optional) a number of seconds to add to the duration
     * @param millis   (optional) a number of milliseconds to add to the duration
     * @param picos    (optional) a number of picoseconds to add to the duration
     */
    construct(Int days, Int hours, Int minutes, Int seconds=0, Int millis=0, Int picos=0)
        {
        assert days    >= 0;
        assert hours   >= 0;
        assert minutes >= 0;
        assert seconds >= 0;
        assert millis  >= 0;
        construct Duration(((((days     * 24
                            + hours  ) * 60
                            + minutes) * 60
                            + seconds) * 1000
                            + millis ).toUInt128() * PICOS_PER_MILLI + picos.toUInt128());
        }

    /**
     * The total number of days, rounded down. This is the same as:
     *
     *   hours / 24.
     */
    Int days.get()
        {
        return (picoseconds / PICOS_PER_DAY).toInt();
        }

    /**
     * The total number of hours, rounded down. This is the same as:
     *
     *   minutes / 60.
     */
    Int hours.get()
        {
        return (picoseconds / PICOS_PER_HOUR).toInt();
        }

    /**
     * The total number of minutes, rounded down. This is the same as:
     *
     *   seconds / 60
     */
    Int minutes.get()
        {
        return (picoseconds / PICOS_PER_MINUTE).toInt();
        }

    /**
     * The total number of seconds, rounded down. This is the same as:
     *
     *   milliseconds / 1000
     *
     * Or:
     *
     *   picoseconds / 1000000000000
     */
    Int seconds.get()
        {
        return (picoseconds / PICOS_PER_SECOND).toInt();
        }

    /**
     * The total number of milliseconds, rounded down. This is the same as:
     *
     *   microseconds / 1000
     */
    Int milliseconds.get()
        {
        return (picoseconds / PICOS_PER_MILLI).toInt();
        }

    /**
     * The total number of microseconds, rounded down. This is the same as:
     *
     *   nanoseconds / 1000
     */
    Int microseconds.get()
        {
        return (picoseconds / PICOS_PER_MICRO).toInt();
        }

    /**
     * The total number of nanoseconds, rounded down. This is the same as:
     *
     *   picoseconds / 1000
     */
    Int nanoseconds.get()
        {
        return (picoseconds / PICOS_PER_NANO).toInt();
        }

    /**
     * The total number of picoseconds.
     */
    UInt128 picoseconds;


    // ----- partial measures ----------------------------------------------------------------------

    /**
     * Exclusive of the time represented by days, the number of hours, rounded down. This is
     * the same as:
     *
     *   hours - (days * 24)
     */
    Int hoursPart.get()
        {
        return hours % 24;
        }

    /**
     * Exclusive of the time represented by hours, the number of minutes, rounded down. This is
     * the same as:
     *
     *   minutes - (hours * 60)
     */
    Int minutesPart.get()
        {
        return minutes % 60;
        }

    /**
     * Exclusive of the time represented by minutes, the number of seconds, rounded down. This
     * is the same as:
     *
     *   seconds - (minutes * 60)
     */
    Int secondsPart.get()
        {
        return seconds % 60;
        }

    /**
     * Exclusive of the time represented by seconds, the number of milliseconds, rounded down.
     * This is the same as:
     *
     *   microsecondsPart / 1000
     *
     * This property represents the fractional portion of a second, with a significant portion of
     * the Duration's precision thrown away. As such, it can be useful for rending human-readable
     * information when higher precision is not required.
     */
    Int millisecondsPart.get()
        {
        return picosecondsPart / PICOS_PER_MILLI;
        }

    /**
     * Exclusive of the time represented by seconds, the number of microseconds, rounded down.
     * This is the same as:
     *
     *   nanosecondsPart / 1000
     *
     * This property represents the fractional portion of a second, with a significant portion of
     * the Duration's precision thrown away. As such, it can be useful for rending human-readable
     * information when higher precision is not required.
     */
    Int microsecondsPart.get()
        {
        return picosecondsPart / PICOS_PER_MICRO;
        }

    /**
     * Exclusive of the time represented by seconds, the number of nanoseconds, rounded down.
     * This is the same as:
     *
     *   picosecondsPart / 1000
     *
     * This property represents the fractional portion of a second, with a significant portion of
     * the Duration's precision thrown away. As such, it can be useful for rending human-readable
     * information when higher precision is not required.
     */
    Int nanosecondsPart.get()
        {
        return picosecondsPart / PICOS_PER_NANO;
        }

    /**
     * Exclusive of the time represented by seconds, the number of picoseconds, rounded down.
     * This is the same as:
     *
     *   picoseconds - (seconds * 1000000000000)
     */
    Int picosecondsPart.get()
        {
        return (picoseconds % PICOS_PER_SECOND).toInt();
        }

    /**
     * Addition: return a sum of durations.
     */
    @Op("+") Duration add(Duration duration)
        {
        return new Duration(this.picoseconds + duration.picoseconds);
        }

    /**
     * Subtraction: return a difference of durations.
     */
    @Op("-") Duration sub(Duration duration)
        {
        return new Duration(this.picoseconds - duration.picoseconds);
        }

    /**
     * Multiplication: return a multiple of this duration.
     */
    @Op("*") Duration mul(Int factor)
        {
        return new Duration(this.picoseconds * factor.toUInt128());
        }

    /**
     * Multiplication: return a multiple of this duration.
     */
    @Op("*") Duration mul(Dec factor)
        {
        return new Duration((this.picoseconds.toDecN() * factor.toDecN()).toUInt128());
        }

    /**
     * Division: return a fraction of this duration.
     */
    @Op("/") Duration div(Int divisor)
        {
        return new Duration(this.picoseconds / divisor.toUInt128());
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
        if (iso8601)                                            // PnDTnHnMn.nS
            {
            if (picoseconds == 0)
                {
                return 4; // PT0S
                }

            Int size = 1;                                       // P

            Int days = this.days;
            if (days != 0)
                {
                size += days.estimateStringLength() + 1;        // nD
                }

            Int hours   = this.hoursPart;
            Int minutes = this.minutesPart;
            Int seconds = this.secondsPart;
            Int picos   = this.picosecondsPart;
            if (hours != 0 && minutes != 0 && seconds != 0 && picos != 0)
                {
                ++size;                                         // T

                if (hours != 0)
                    {
                    size += hours.estimateStringLength() + 1;   // nH
                    }

                if (minutes != 0)
                    {
                    size += minutes.estimateStringLength() + 1; // nM
                    }

                if (seconds != 0 || picos != 0)
                    {
                    size += seconds.estimateStringLength() + 1; // nS
                    if (picos != 0)
                        {
                        size += 1+picosFractionalLength(picos); // n.nS
                        }
                    }
                }

            return size;
            }

        // format: ...###:00:00.###...
        // format:        ##:00.###...
        // format:           ##.###...
        Int length = switch()
            {
            case picoseconds >= PICOS_PER_HOUR  : hours  .estimateStringLength() + 6;
            case picoseconds >= PICOS_PER_MINUTE: minutes.estimateStringLength() + 3;
            default                             : seconds.estimateStringLength();
            };

        Int picos = picosecondsPart;
        if (picos != 0)
            {
            length += 1 + picosFractionalLength(picos);
            }
        return length;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf, Boolean iso8601 = False)
        {
        if (iso8601)                                            // PnDTnHnMn.nS
            {
            if (picoseconds == 0)
                {
                return "PT0S".appendTo(buf);
                }

            buf.add('P');

            Int days = this.days;
            if (days != 0)
                {
                days.appendTo(buf).add('D');
                }

            Int hours   = this.hoursPart;
            Int minutes = this.minutesPart;
            Int seconds = this.secondsPart;
            Int picos   = this.picosecondsPart;
            if (hours != 0 && minutes != 0 && seconds != 0 && picos != 0)
                {
                buf.add('T');

                if (hours != 0)
                    {
                    hours.appendTo(buf).add('H');
                    }

                if (minutes != 0)
                    {
                    minutes.appendTo(buf).add('M');
                    }

                if (seconds != 0 || picos != 0)
                    {
                    seconds.appendTo(buf);
                    Duration.appendPicosFractional(buf, picos);
                    buf.add('S');
                    }
                }

            return buf;
            }

        Boolean zerofill = False;

        if (picoseconds >= PICOS_PER_HOUR)
            {
            hours.appendTo(buf);
            buf.add(':');
            zerofill = True;
            }

        if (picoseconds >= PICOS_PER_MINUTE)
            {
            Int part = minutesPart;
            if (part < 10 && zerofill)
                {
                buf.add('0');
                }
            else
                {
                zerofill = True;
                }
            part.appendTo(buf);
            buf.add(':');
            }

        Int part = secondsPart;
        if (part < 10 && zerofill)
            {
            buf.add('0');
            }
        part.appendTo(buf);

        return Duration.appendPicosFractional(buf, picosecondsPart);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Calculate the String length of an optional picosecond fraction of a second.
     */
    static Int picosFractionalLength(Int picos)
        {
        if (picos == 0)
            {
            return 0;
            }

        // add a decimal point and the picoseconds, but drop any trailing zeros
        Int length = picos.estimateStringLength();
        if (picos % 100_000_000 == 0)
            {
            picos  /= 100_000_000;
            length -= 8;
            }
        if (picos % 10_000 == 0)
            {
            picos  /= 10_000;
            length -= 4;
            }
        if (picos % 100 == 0)
            {
            picos  /= 100;
            length -= 2;
            }
        if (picos % 10 == 0)
            {
            picos  /= 10;
            length -= 1;
            }
        return length;
        }

    /**
     * Calculate an Int value to use as the number to follow a decimal point to represent a
     * picosecond fraction of a second.
     */
    static (Int leadingZeroes, Int value) picosFractional(Int picos)
        {
        assert picos > 0;

        Int chopped = 0;

        // drop any trailing zeros
        if (picos % 100_000_000 == 0)
            {
            picos   /= 100_000_000;
            chopped += 8;
            }
        if (picos % 10_000 == 0)
            {
            picos   /= 10_000;
            chopped += 4;
            }
        if (picos % 100 == 0)
            {
            picos /= 100;
            chopped += 2;
            }
        if (picos % 10 == 0)
            {
            picos /= 10;
            ++chopped;
            }

        return 12 - picos.estimateStringLength() - chopped, picos;
        }

    static Appender<Char> appendPicosFractional(Appender<Char> buf, Int picos)
        {
        if (picos == 0)
            {
            return buf;
            }

        buf.add('.');
        (Int leadingZeroes, Int digits) = Duration.picosFractional(picos);
        while (leadingZeroes-- > 0)
            {
            buf.add('0');
            }
        return digits.appendTo(buf);
        }
    }
