/**
 * A Duration represents a magnitude of time, with picosecond resolution.
 */
const Duration(UInt128 picosecondsTotal)
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

                                // TODO (GG?) this should be allowed here (since it is a constant)
                                //private static UInt128[] SCALE_10 = [       1_000_000_000_000,
                                //        100_000_000_000,    10_000_000_000,     1_000_000_000,
                                //            100_000_000,        10_000_000,         1_000_000,
                                //                100_000,            10_000,             1_000,
                                //                    100,                10,                 1 ];

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

    private static UInt128[] SCALE_10 = [       1_000_000_000_000,
            100_000_000_000,    10_000_000_000,     1_000_000_000,
                100_000_000,        10_000_000,         1_000_000,
                    100_000,            10_000,             1_000,
                        100,                10,                 1 ];
                        
    /**
     * Create a Duration of a certain number of days.
     *
     * @param the number of days in the requested Duration
     */
    static Duration ofDays(Int days)
        {
        return new Duration(days.to<UInt128>() * PICOS_PER_DAY);
        }

    /**
     * Create a Duration of a certain number of hours.
     *
     * @param the number of hours in the requested Duration
     */
    static Duration ofHours(Int hours)
        {
        return new Duration(hours.to<UInt128>() * PICOS_PER_HOUR);
        }

    /**
     * Create a Duration of a certain number of minutes.
     *
     * @param the number of minutes in the requested Duration
     */
    static Duration ofMinutes(Int minutes)
        {
        return new Duration(minutes.to<UInt128>() * PICOS_PER_MINUTE);
        }

    /**
     * Create a Duration of a certain number of seconds.
     *
     * @param the number of seconds in the requested Duration
     */
    static Duration ofSeconds(Int seconds)
        {
        return new Duration(seconds.to<UInt128>() * PICOS_PER_SECOND);
        }

    /**
     * Create a Duration of a certain number of milliseconds.
     *
     * @param the number of milliseconds in the requested Duration
     */
    static Duration ofMillis(Int millis)
        {
        return new Duration(millis.to<UInt128>() * PICOS_PER_MILLI);
        }

    /**
     * Create a Duration of a certain number of microseconds.
     *
     * @param the number of microseconds in the requested Duration
     */
    static Duration ofMicros(Int micros)
        {
        return new Duration(micros.to<UInt128>() * PICOS_PER_MICRO);
        }

    /**
     * Create a Duration of a certain number of nanoseconds.
     *
     * @param the number of nanoseconds in the requested Duration
     */
    static Duration ofNanos(Int nanos)
        {
        return new Duration(nanos.to<UInt128>() * PICOS_PER_NANO);
        }

    /**
     * Create a Duration of a certain number of picoseconds.
     *
     * @param the number of picoseconds in the requested Duration
     */
    static Duration ofPicos(Int picos)
        {
        return new Duration(picos.to<UInt128>());
        }

    /**
     * Construct a Duration based on a total number of picoseconds.
     *
     * @param picosecondsTotal  the total number of picoseconds in the Duration
     */
    construct(UInt128 picosecondsTotal)
        {
        assert picosecondsTotal >= 0;
        this.picosecondsTotal = picosecondsTotal;
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
                            + millis ).to<UInt128>() * PICOS_PER_MILLI + picos.to<UInt128>());
        }

    /**
     * The number of days, rounded down. This is the same as daysTotal.
     */
    Int days.get()
        {
        return daysTotal;
        }

    /**
     * Exclusive of the time represented by daysTotal, the number of hours, rounded down. This is
     * the same as:
     *
     *   hoursTotal - (daysTotal * 24)
     */
    Int hours.get()
        {
        return hoursTotal % 24;
        }

    /**
     * Exclusive of the time represented by hoursTotal, the number of minutes, rounded down. This is
     * the same as:
     *
     *   minutesTotal - (hoursTotal * 60)
     */
    Int minutes.get()
        {
        return minutesTotal % 60;
        }

    /**
     * Exclusive of the time represented by minutesTotal, the number of seconds, rounded down. This
     * is the same as:
     *
     *   secondsTotal - (minutesTotal * 60)
     */
    Int seconds.get()
        {
        return secondsTotal % 60;
        }

    /**
     * Exclusive of the time represented by secondsTotal, the number of milliseconds, rounded down.
     * This is the same as:
     *
     *   microseconds / 1000
     *
     * This property represents the fractional portion of a second, with a significant portion of
     * the Duration's precision thrown away. As such, it can be useful for rending human-readable
     * information when higher precision is not required.
     */
    Int milliseconds.get()
        {
        return picoseconds / PICOS_PER_MILLI;
        }

    /**
     * Exclusive of the time represented by secondsTotal, the number of microseconds, rounded down.
     * This is the same as:
     *
     *   nanoseconds / 1000
     *
     * This property represents the fractional portion of a second, with a significant portion of
     * the Duration's precision thrown away. As such, it can be useful for rending human-readable
     * information when higher precision is not required.
     */
    Int microseconds.get()
        {
        return picoseconds / PICOS_PER_MICRO;
        }

    /**
     * Exclusive of the time represented by secondsTotal, the number of nanoseconds, rounded down.
     * This is the same as:
     *
     *   picoseconds / 1000
     *
     * This property represents the fractional portion of a second, with a significant portion of
     * the Duration's precision thrown away. As such, it can be useful for rending human-readable
     * information when higher precision is not required.
     */
    Int nanoseconds.get()
        {
        return picoseconds / PICOS_PER_NANO;
        }

    /**
     * Exclusive of the time represented by secondsTotal, the number of picoseconds, rounded down.
     * This is the same as:
     *
     *   picosecondsTotal - (secondsTotal * 1000000000000)
     */
    Int picoseconds.get()
        {
        return (picosecondsTotal % PICOS_PER_SECOND).to<Int>();
        }

    /**
     * The total number of days, rounded down. This is the same as:
     *
     *   hoursTotal / 24.
     */
    Int daysTotal.get()
        {
        return (picosecondsTotal / PICOS_PER_DAY).to<Int>();
        }

    /**
     * The total number of hours, rounded down. This is the same as:
     *
     *   minutesTotal / 60.
     */
    Int hoursTotal.get()
        {
        return (picosecondsTotal / PICOS_PER_HOUR).to<Int>();
        }

    /**
     * The total number of minutes, rounded down. This is the same as:
     *
     *   secondsTotal / 60
     */
    Int minutesTotal.get()
        {
        return (picosecondsTotal / PICOS_PER_MINUTE).to<Int>();
        }

    /**
     * The total number of seconds, rounded down. This is the same as:
     *
     *   millisecondsTotal / 1000
     *
     * Or:
     *
     *   picosecondsTotal / 1000000000000
     */
    Int secondsTotal.get()
        {
        return (picosecondsTotal / PICOS_PER_SECOND).to<Int>();
        }

    /**
     * The total number of milliseconds, rounded down. This is the same as:
     *
     *   microsecondsTotal / 1000
     */
    Int millisecondsTotal.get()
        {
        return (picosecondsTotal / PICOS_PER_MILLI).to<Int>();
        }

    /**
     * The total number of microseconds, rounded down. This is the same as:
     *
     *   nanosecondsTotal / 1000
     */
    Int microsecondsTotal.get()
        {
        return (picosecondsTotal / PICOS_PER_MICRO).to<Int>();
        }

    /**
     * The total number of nanoseconds, rounded down. This is the same as:
     *
     *   picosecondsTotal / 1000
     */
    Int nanosecondsTotal.get()
        {
        return (picosecondsTotal / PICOS_PER_NANO).to<Int>();
        }

    /**
     * The total number of picoseconds.
     */
    UInt128 picosecondsTotal;

    /**
     * Addition: return a sum of durations.
     */
    @Op("+") Duration add(Duration duration)
        {
        return new Duration(this.picosecondsTotal + duration.picosecondsTotal);
        }

    /**
     * Subtraction: return a difference of durations.
     */
    @Op("-") Duration sub(Duration duration)
        {
        return new Duration(this.picosecondsTotal - duration.picosecondsTotal);
        }

    /**
     * Multiplication: return a multiple of this duration.
     */
    @Op("*") Duration mul(Int factor)
        {
        return new Duration(this.picosecondsTotal * factor.to<UInt128>());
        }

    /**
     * Multiplication: return a multiple of this duration.
     */
    @Op("*") Duration mul(Dec factor)
        {
        return new Duration((this.picosecondsTotal.to<VarDec>() * factor.to<VarDec>()).to<UInt128>());
        }

    /**
     * Division: return a fraction of this duration.
     */
    @Op("/") Duration div(Int divisor)
        {
        return new Duration(this.picosecondsTotal / divisor.to<UInt128>());
        }

    @Override
    Int estimateStringLength()
        {
        // format: ...###:00:00.###...
        // format:        ##:00.###...
        // format:           ##.###...
        Int length = switch()
            {
            case picosecondsTotal >= PICOS_PER_HOUR  : hoursTotal  .estimateStringLength() + 6;
            case picosecondsTotal >= PICOS_PER_MINUTE: minutesTotal.estimateStringLength() + 3;
            default                                  : secondsTotal.estimateStringLength();
            };

        return length + picosFractionalLength(picoseconds);
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        Boolean zerofill = false;

        if (picosecondsTotal >= PICOS_PER_HOUR)
            {
            hoursTotal.appendTo(appender);
            appender.add(':');
            zerofill = true;
            }

        if (picosecondsTotal >= PICOS_PER_MINUTE)
            {
            Int part = minutes;
            if (part < 10 && zerofill)
                {
                appender.add('0');
                }
            else
                {
                zerofill = true;
                }
            part.appendTo(appender);
            appender.add(':');
            }

        Int part = seconds;
        if (part < 10 && zerofill)
            {
            appender.add('0');
            }
        part.appendTo(appender);

        Int picos = picoseconds;
        if (picos > 0)
            {
            appender.add('.');
            picosFractional(picos).appendTo(appender);
            }
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
        Int length = 13;
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
    static Int picosFractional(Int picos)
        {
        assert picos > 0;

        // drop any trailing zeros
        if (picos % 100_000_000 == 0)
            {
            picos /= 100_000_000;
            }
        if (picos % 10_000 == 0)
            {
            picos /= 10_000;
            }
        if (picos % 100 == 0)
            {
            picos /= 100;
            }
        if (picos % 10 == 0)
            {
            picos /= 10;
            }
        return picos;
        }
    }
