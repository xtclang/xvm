/**
 * A Duration represents a magnitude of time, with picosecond resolution.
 */
const Duration(Int128 picoseconds)
        implements Destringable
        default(None) {

    static IntLiteral PicosPerNano   = 1000;
    static IntLiteral PicosPerMicro  = 1000 * PicosPerNano;     // assume <=59 bits for micros
    static IntLiteral PicosPerMilli  = 1000 * PicosPerMicro;    // assume <=49 bits for millis
    static IntLiteral PicosPerSecond = 1000 * PicosPerMilli;    // assume <=39 bits for seconds
    static IntLiteral PicosPerMinute = 60   * PicosPerSecond;   // assume <=33 bits for minutes
    static IntLiteral PicosPerHour   = 60   * PicosPerMinute;   // assume <=27 bits for hours
    static IntLiteral PicosPerDay    = 24   * PicosPerHour;     // assume <=22 bits for days

    static Duration None     = new Duration(0);
    static Duration Picosec  = new Duration(1);
    static Duration Nanosec  = new Duration(PicosPerNano);
    static Duration Microsec = new Duration(PicosPerMicro);
    static Duration Millisec = new Duration(PicosPerMilli);
    static Duration Second   = new Duration(PicosPerSecond);
    static Duration Minute   = new Duration(PicosPerMinute);
    static Duration Hour     = new Duration(PicosPerHour);
    static Duration Day      = new Duration(PicosPerDay);

    /**
     * Construct a time duration from an ISO-8601 format string. Note that this does not include
     * support for year or month values, since neither a year nor a month has a fixed size.
     *
     * @param duration  an ISO-8601 format string of the form "PnDTnHnMnS"
     */
    @Override
    construct(String duration) {
        // state machine stages:
        //     P   nD  T   nH  nM  n.  nS
        //   0   1   2   3   4   5   6   7

        Int128  total    = 0;
        Int     index    = 0;
        Int     length   = duration.size;
        Int     last     = length -1;
        Int     stage    = 0;
        Boolean any      = False;    // set to True if any data has been encountered
        Boolean iso      = False;    // set to True if any of the ISO indicators are encountered
        Int     colons   = 0;        // count of the ':' characters encountered
        Int128  seconds  = 0;        // only used for ':' delimited string
        Boolean negative = False;
        if (length > 0 && duration[0] == '-') {
            negative = True;
            index    = 1;
        }

        Loop: while (index < length) {
            switch (Char ch = duration[index++]) {
            case 'P', 'p':
                // while 'P' is required per the ISO-8601 specification, this constructor is
                // only used for duration values, so
                assert:arg stage == 0 as
                        $|Duration must begin with 'P' and must contain no other occurrences of\
                         | 'P': {duration.quoted()}
                        ;
                stage = 1;
                iso   = True;
                break;

            case 'T', 't':
                assert:arg stage < 3 as
                        $|Duration includes 'T' to separate date from time-of-day components,\
                         | and must contain no other occurrences of 'T': {duration.quoted()}
                        ;
                stage = 3;
                iso   = True;
                break;

            case '0'..'9':
                // parse the number as an integer
                Int128 part   = 0;
                Int    digits = 0;
                do {
                    ++digits;
                    part = part * 10 + (ch - '0');

                    if (index > last) {
                        assert:arg !iso as
                                $"Duration is missing a trailing indicator: {duration.quoted()}";

                        // pretend we found a seconds indicator
                        ch = 'S';
                        break;
                    }

                    ch = duration[index++];
                } while (ch >= '0' && ch <= '9');

                Int    stageNew;
                Int128 factor;
                switch (ch) {
                case 'D', 'd':
                    stageNew = 2;
                    iso      = True;
                    factor   = TimeOfDay.PicosPerDay;
                    break;

                case 'H', 'h':
                    stageNew = 4;
                    iso      = True;
                    factor   = TimeOfDay.PicosPerHour;
                    break;

                case 'M', 'm':
                    stageNew = 5;
                    iso      = True;
                    factor   = TimeOfDay.PicosPerMinute;
                    break;

                case '.':
                    stageNew = 6;
                    factor   = TimeOfDay.PicosPerSecond;
                    part    += seconds;
                    seconds  = 0;
                    break;

                case 'S', 's':
                    if (stage == 6) {
                        // this is a fractional value
                        while (digits > 12) {
                            part /= 10;
                            --digits;
                        }

                        static Int128[] ScaleX10 = [               1_000_000_000_000,
                               100_000_000_000,    10_000_000_000,     1_000_000_000,
                                   100_000_000,        10_000_000,         1_000_000,
                                       100_000,            10_000,             1_000,
                                           100,                10,                 1 ];

                        factor = ScaleX10[digits];
                    } else {
                        factor  = TimeOfDay.PicosPerSecond;
                        part   += seconds;
                        seconds = 0;
                    }
                    stageNew = 7;
                    break;

                case ':':
                    assert:arg !iso as $"Duration includes an unexpected character ':': {duration.quoted()}";
                    assert:arg ++colons <= 2 as $"Too many ':' sections in Duration: {duration.quoted()}";
                    factor   = 0;
                    stageNew = 2 + colons;
                    seconds  = (seconds + part) * 60;
                    break;

                default:
                    throw new IllegalArgument(
                            $"Duration includes an unexpected character {ch.quoted()}: {duration.quoted()}");
                }

                assert:arg stageNew > stage as
                        $"Duration components are out of order: {duration.quoted()}";

                total += part * factor;
                stage  = stageNew;
                any    = True;
                break;

            default:
                throw new IllegalArgument(
                        $|Duration includes an illegal character or character\
                         | sequence starting with {ch.quoted()}: {duration.quoted()}
                        );
            }
        }

        assert:arg stage != 6          as $"Duration is missing fractional seconds: {duration.quoted()}";
        assert:arg any                 as $"No duration information provided: {duration.quoted()}";
        assert:arg !iso || colons == 0 as $"Invalid ISO format: {duration.quoted()}";

        construct Duration(negative ? -total : total);
    }

    /**
     * Create a Duration of a certain number of days.
     *
     * @param the number of days in the requested Duration
     */
    static Duration ofDays(Int days) {
        return new Duration(days.toInt128() * PicosPerDay);
    }

    /**
     * Create a Duration of a certain number of hours.
     *
     * @param the number of hours in the requested Duration
     */
    static Duration ofHours(Int hours) {
        return new Duration(hours.toInt128() * PicosPerHour);
    }

    /**
     * Create a Duration of a certain number of minutes.
     *
     * @param the number of minutes in the requested Duration
     */
    static Duration ofMinutes(Int minutes) {
        return new Duration(minutes.toInt128() * PicosPerMinute);
    }

    /**
     * Create a Duration of a certain number of seconds.
     *
     * @param the number of seconds in the requested Duration
     */
    static Duration ofSeconds(Int seconds) {
        return new Duration(seconds.toInt128() * PicosPerSecond);
    }

    /**
     * Create a Duration of a certain number of milliseconds.
     *
     * @param the number of milliseconds in the requested Duration
     */
    static Duration ofMillis(Int millis) = new Duration(millis.toInt128() * PicosPerMilli);

    /**
     * Create a Duration of a certain number of microseconds.
     *
     * @param the number of microseconds in the requested Duration
     */
    static Duration ofMicros(Int micros) = new Duration(micros.toInt128() * PicosPerMicro);

    /**
     * Create a Duration of a certain number of nanoseconds.
     *
     * @param the number of nanoseconds in the requested Duration
     */
    static Duration ofNanos(Int nanos) = new Duration(nanos.toInt128() * PicosPerNano);

    /**
     * Create a Duration of a certain number of picoseconds.
     *
     * @param the number of picoseconds in the requested Duration
     */
    static Duration ofPicos(Int128 picos) = new Duration(picos);

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
    construct(Int days, Int hours, Int minutes, Int seconds=0, Int millis=0, Int picos=0) {
        assert days    >= 0;
        assert hours   >= 0;
        assert minutes >= 0;
        assert seconds >= 0;
        assert millis  >= 0;
        construct Duration(((((days                * 24
                             + hours  )            * 60
                             + minutes)            * 60
                             + seconds)            * 1000
                             + millis ).toInt128() * PicosPerMilli
                             + picos.toInt128());
    }

    /**
     * The total number of days, rounded down. This is the same as:
     *
     *   hours / 24.
     */
    Int days.get() = (picoseconds / PicosPerDay).toInt(checkBounds=True);

    /**
     * The total number of hours, rounded down. This is the same as:
     *
     *   minutes / 60.
     */
    Int hours.get() = (picoseconds / PicosPerHour).toInt(checkBounds=True);

    /**
     * The total number of minutes, rounded down. This is the same as:
     *
     *   seconds / 60
     */
    Int minutes.get() = (picoseconds / PicosPerMinute).toInt(checkBounds=True);

    /**
     * The total number of seconds, rounded down. This is the same as:
     *
     *   milliseconds / 1000
     *
     * Or:
     *
     *   picoseconds / 1000000000000
     */
    Int seconds.get() = (picoseconds / PicosPerSecond).toInt(checkBounds=True);

    /**
     * The total number of milliseconds, rounded down. This is the same as:
     *
     *   microseconds / 1000
     */
    Int milliseconds.get() = (picoseconds / PicosPerMilli).toInt(checkBounds=True);

    /**
     * The total number of microseconds, rounded down. This is the same as:
     *
     *   nanoseconds / 1000
     */
    Int microseconds.get() = (picoseconds / PicosPerMicro).toInt(checkBounds=True);

    /**
     * The total number of nanoseconds, rounded down. This is the same as:
     *
     *   picoseconds / 1000
     */
    Int nanoseconds.get() = (picoseconds / PicosPerNano).toInt(checkBounds=True);

    /**
     * The total number of picoseconds.
     */
    Int128 picoseconds;


    // ----- partial measures ----------------------------------------------------------------------

    /**
     * Exclusive of the time represented by days, the number of hours, rounded down. This is
     * the same as:
     *
     *   hours.abs() - (days.abs() * 24)
     */
    UInt8 hoursPart.get() = (hours.abs() % 24).toUInt8();

    /**
     * Exclusive of the time represented by hours, the number of minutes, rounded down. This is
     * the same as:
     *
     *   minutes.abs() - (hours.abs() * 60)
     */
    UInt8 minutesPart.get() = (minutes.abs() % 60).toUInt8();

    /**
     * Exclusive of the time represented by minutes, the number of seconds, rounded down. This
     * is the same as:
     *
     *   seconds.abs() - (minutes.abs() * 60)
     */
    UInt8 secondsPart.get() = (seconds.abs() % 60).toUInt8();

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
    UInt16 millisecondsPart.get() = (picosecondsPart.abs() / PicosPerMilli).toUInt16();

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
    UInt32 microsecondsPart.get() = (picosecondsPart.abs() / PicosPerMicro).toUInt32();

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
    UInt32 nanosecondsPart.get()= (picosecondsPart.abs() / PicosPerNano).toUInt32();

    /**
     * Exclusive of the time represented by seconds, the number of picoseconds, rounded down.
     * This is the same as:
     *
     *   picoseconds.abs() - (seconds.abs() * 1000000000000)
     */
    Int picosecondsPart.get() = (picoseconds.abs() % PicosPerSecond).toInt();

    /**
     * The Sign of the Duration. The [None] Duration has a [Signum] of `Zero`. Most Durations have a
     * `Positive Signum`, but a Duration with `Negative Signum` indicates that something occurred in
     * the past, such as a timeout that has expired.
     */
    @RO Signum sign.get() = picoseconds.sign;

    /**
     * True iff the duration is negative (less than zero picoseconds).
     */
    @RO Boolean negative.get() = picoseconds < 0;

    /**
     * The magnitude of this duration (its distance from zero).
     */
    @RO Duration magnitude.get() = picoseconds < 0 ? new Duration(-picoseconds) : this;

    /**
     * Calculate the negative of this Duration.
     *
     * @return the negative of this Duration, equal to `None-this`
     */
    @Op("-#") Duration neg() = new Duration(-picoseconds);

    /**
     * Addition: return a sum of durations.
     */
    @Op("+") Duration add(Duration duration) = new Duration(this.picoseconds + duration.picoseconds);

    /**
     * Subtraction: return a difference of durations.
     */
    @Op("-") Duration sub(Duration duration) = new Duration(this.picoseconds - duration.picoseconds);

    /**
     * Multiplication: return a multiple of this duration.
     */
    @Op("*") Duration mul(Int factor) = new Duration(this.picoseconds * factor.toInt128());

    /**
     * Multiplication: return a multiple of this duration.
     */
    @Op("*") Duration mul(Dec factor) {
        return new Duration((this.picoseconds.toDec128() * factor.toDec128()).toInt128());
    }

    /**
     * Division: return a fraction of this duration.
     */
    @Op("/") Duration div(Int divisor) {
        return new Duration(this.picoseconds / divisor.toInt128());
    }


    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    String toString(Boolean iso8601 = False) {
        return appendTo(new StringBuffer(estimateStringLength(iso8601)), iso8601).toString();
    }

    @Override
    Int estimateStringLength(Boolean iso8601 = False) {
        if (iso8601) {                                          // PnDTnHnMn.nS
            if (picoseconds == 0) {
                return 4; // PT0S
            }

            Int size = picoseconds < 0 ? 2 : 1;                 // -P or P

            Int days = this.days;
            if (days != 0) {
                size += days.estimateStringLength() + 1;        // nD
            }

            Int hours   = this.hoursPart;
            Int minutes = this.minutesPart;
            Int seconds = this.secondsPart;
            Int picos   = this.picosecondsPart;
            if (hours != 0 && minutes != 0 && seconds != 0 && picos != 0) {
                ++size;                                         // T

                if (hours != 0) {
                    size += hours.estimateStringLength() + 1;   // nH
                }

                if (minutes != 0) {
                    size += minutes.estimateStringLength() + 1; // nM
                }

                if (seconds != 0 || picos != 0) {
                    size += seconds.estimateStringLength() + 1; // nS
                    if (picos != 0) {
                        size += 1+picosFractionalLength(picos); // n.nS
                    }
                }
            }

            return size;
        }

        // format: ...###:00:00.###...
        // format:        ##:00.###...
        // format:           ##.###...
        Int length = picoseconds.abs() >= PicosPerHour   ? hours  .estimateStringLength() + 6 :
                     picoseconds.abs() >= PicosPerMinute ? minutes.estimateStringLength() + 3 :
                                                           seconds.estimateStringLength();

        Int picos = picosecondsPart;
        if (picos != 0) {
            length += 1 + picosFractionalLength(picos);
        }
        return length;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf, Boolean iso8601 = False) {
        if (picoseconds < 0) {
            return magnitude.appendTo(buf.add('-'), iso8601);
        }

        if (iso8601) {                                          // PnDTnHnMn.nS
            if (picoseconds == 0) {
                return "PT0S".appendTo(buf);
            }

            buf.add('P');

            Int days = this.days;
            if (days != 0) {
                days.appendTo(buf).add('D');
            }

            Int hours   = this.hoursPart;
            Int minutes = this.minutesPart;
            Int seconds = this.secondsPart;
            Int picos   = this.picosecondsPart;
            if (hours != 0 || minutes != 0 || seconds != 0 || picos != 0) {
                buf.add('T');

                if (hours != 0) {
                    hours.appendTo(buf).add('H');
                }

                if (minutes != 0) {
                    minutes.appendTo(buf).add('M');
                }

                if (seconds != 0 || picos != 0) {
                    seconds.appendTo(buf);
                    Duration.appendPicosFractional(buf, picos);
                    buf.add('S');
                }
            }

            return buf;
        }

        Boolean zerofill = False;

        if (picoseconds >= PicosPerHour) {
            hours.appendTo(buf);
            buf.add(':');
            zerofill = True;
        }

        if (picoseconds >= PicosPerMinute) {
            Int part = minutesPart;
            if (part < 10 && zerofill) {
                buf.add('0');
            } else {
                zerofill = True;
            }
            part.appendTo(buf);
            buf.add(':');
        }

        Int part = secondsPart;
        if (part < 10 && zerofill) {
            buf.add('0');
        }
        part.appendTo(buf);

        return Duration.appendPicosFractional(buf, picosecondsPart);
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Calculate the String length of an optional picosecond fraction of a second.
     */
    static Int picosFractionalLength(Int picos) {
        if (picos == 0) {
            return 0;
        }

        // add a decimal point and the picoseconds, but drop any trailing zeros
        Int length = picos.estimateStringLength();
        if (picos % 100_000_000 == 0) {
            picos  /= 100_000_000;
            length -= 8;
        }
        if (picos % 10_000 == 0) {
            picos  /= 10_000;
            length -= 4;
        }
        if (picos % 100 == 0) {
            picos  /= 100;
            length -= 2;
        }
        if (picos % 10 == 0) {
            picos  /= 10;
            length -= 1;
        }
        return length;
    }

    /**
     * Calculate an Int value to use as the number to follow a decimal point to represent a
     * picosecond fraction of a second.
     *
     * @param picos a positive number of picoseconds
     *
     * @return leadingZeroes the number of leading zeros after the decimal point
     * @return value the digits of the picoseconds, with the trailing zeros removed
     */
    private static (Int leadingZeroes, Int value) picosFractional(Int picos) {
        assert picos > 0;
        Int chopped = 0;

        // drop any trailing zeros
        if (picos % 100_000_000 == 0) {
            picos   /= 100_000_000;
            chopped += 8;
        }
        if (picos % 10_000 == 0) {
            picos   /= 10_000;
            chopped += 4;
        }
        if (picos % 100 == 0) {
            picos /= 100;
            chopped += 2;
        }
        if (picos % 10 == 0) {
            picos /= 10;
            ++chopped;
        }
        return 12 - picos.estimateStringLength() - chopped, picos;
    }

    /**
     * Add the fractional picoseconds value to the buffer as a decimal string. If the picoseconds
     * value is zero, the buffer is returned unchanged; otherwise, a decimal point is added, and the
     * fractional picoseconds value is appended with no trailing zeroes.
     *
     * @param buf    the buffer to render the fractional value into
     * @param picos  a non-negative number of picoseconds
     *
     * @return the buffer
     */
    static Appender<Char> appendPicosFractional(Appender<Char> buf, Int picos) {
        if (picos == 0) {
            return buf;
        }

        // TODO GG - moving picosFractional function into here results in a compiler error

        buf.add('.');
        (Int leadingZeroes, Int digits) = Duration.picosFractional(picos);
        while (leadingZeroes-- > 0) {
            buf.add('0');
        }
        return digits.appendTo(buf);
    }
}