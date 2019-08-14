/**
 * A TimeZone contains the necessary information to convert a UTC DateTime value into a localized
 * Date and Time value.
 *
 * There are four categories of TimeZones:
 * * UTC - Raw, unadjusted, universally-coordinated DateTime information. This is the internal form
 *   of the information held by DateTime, such that all DateTime conversions are performed starting
 *   from UTC data, and all DateTime values with a legitimate TimeZone can be trivially converted to
 *   UTC data (or to any other TimeZone).
 * * Fixed - an offset from UTC measured in terms of picoseconds, but generally measured in terms of
 *   hour, half-hour, or quarter-hour increments, and optionally associated with a timezone name.
 * * Rule-Based - Represents a complex timezone in which more than one fixed offset has been or will
 *   be used at some point in time. The most common example is Daylight Savings Time, which allows
 *   a single Rule-Based TimeZone to provide two different Fixed TimeZone instances, with the
 *   selection of the Fixed TimeZone being a function of the underlying UTC DateTime. Far more
 *   complex examples exist, such as "America/New_York", which has well over a dozen historical
 *   rules.
 * * The "Un"-TimeZone - a special TimeZone that specifies the absence of any TimeZone information,
 *   useful for acting like a UTC time, but whose DateTime values are incapable of being compared
 *   with those of any other TimeZone.
 */
const TimeZone(Int picos, String? name = null)
    {
    /**
     * Construct a resolved TimeZone.
     *
     * @param picos  the picosecond offset for the TimeZone, which is the adjustment made to a UTC
     *               picosecond value in order to calculate a TimeZone-adjusted picosecond value
     * @param name   the name of the TimeZone
     */
    construct(Int picos, String? name = null)
        {
        assert picos.abs() <= Time.PICOS_PER_DAY;
        this.picos = picos;
        this.name  = name;
        }

    /**
     * Construct a TimeZone from an ISO-8601 timezone indicator string of one of the following
     * offset formats:
     *
     *   ±hh:mm
     *   ±hhmm
     *   ±hh
     */
    construct(String tz)
        {
        static Int valOf(Char ch)
            {
            return ch >= '0' && ch <= '9' ? ch - '0' : -999;
            }

        static Int parseInt(String s, Int of)
            {
            return valOf(s[of]) * 10 + valOf(s[of+1]);
            }

        if (tz.size >= 2 && (tz[0]=='+' || tz[0]=='-'))
            {
            Int hours = -1;
            Int mins  = 0;
            if (Int colon := tz.indexOf(':'))
                {
                if (colon > 1 && colon < tz.size-1)
                    {
                    hours = new IntLiteral(tz[1..colon-1]).toInt();
                    mins  = new IntLiteral(tz.substring(colon+1)).toInt();
                    }
                }
            else
                {
                switch (tz.size)
                    {
                    case 5:
                        mins  = parseInt(tz, 3);
                        continue;
                    case 3:
                        hours = parseInt(tz, 1);
                        break;
                    }
                }

            if (hours >= 0 && hours <= 16 && mins >= 0 && mins <= 59)
                {
                Int offset = (hours * Time.PICOS_PER_HOUR + mins * Time.PICOS_PER_MINUTE);
                construct TimeZone((tz[0]=='-' ? -1 : +1) * offset);
                return;
                }
            }

        throw new IllegalArgument($"invalid ISO-8601 timezone offset: \"{tz}\"");
        }

    /**
     * Represents a TimeZone rule that implements the details from the "IANA Time Zone Database".
     */
    static const Rule
        {
        // TODO
        }

    /**
     * Construct a TimeZone that uses a series of rules to translate information from UTC to other
     * TimeZones.
     *
     * @param name   the name of the TimeZone
     * @param rules  a Sequence of Rules
     */
    construct(String name, Rule... rules)
        {
        this.name = name;
        TODO this.rules = ...   `
        }

    /**
     * The UTC TimeZone.
     */
    static TimeZone UTC = new TimeZone(0, "UTC")
        {
        @Override
        @RO Boolean isUTC.get()
            {
            return true;
            }

        @Override
        @RO Boolean resolved.get()
            {
            return true;
            }

        @Override
        TimeZone resolve(DateTime datetime)
            {
            assert datetime.timezone.isUTC;
            return this;
            }

        @Override
        @RO Int hours.get()
            {
            return 0;
            }

        @Override
        @RO Int minutes.get()
            {
            return 0;
            }
        };

    /**
     * The "not-a-TimeZone" TimeZone.
     */
    static TimeZone NoTZ = new TimeZone(0, "NoTZ")
        {
        @Override
        @RO Boolean isNoTZ.get()
            {
            return true;
            }

        @Override
        @RO Boolean resolved.get()
            {
            return true;
            }

        @Override
        TimeZone resolve(DateTime datetime)
            {
            assert datetime.timezone.isNoTZ;
            return this;
            }

        @Override
        @RO Int hours.get()
            {
            return 0;
            }

        @Override
        @RO Int minutes.get()
            {
            return 0;
            }

        @Override
        DateTime adopt(DateTime orig)
            {
            // strip off the timezone, but leave the date and time reflecting what the original
            // TimeZone would have calculated
            // note: this only supports dates on or after 1582-10-15 on the Gregorian calendar
            return orig.timezone.isNoTZ ? orig : new DateTime(orig.date, orig.time, this);
            }
        };

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Determine if this TimeZone is Universal Coordinated Time (UTC).
     */
    @RO Boolean isUTC.get()
        {
        return false;
        }

    /**
     * Determine if this TimeZone is a special TimeZone that represents the lack of a TimeZone.
     */
    @RO Boolean isNoTZ.get()
        {
        return false;
        }

    /**
    * The resolving rules for the TimeZone.
    */
    Rule[] rules = [];

    @RO Boolean resolved.get()
        {
        return rules.size == 0;
        }

    /**
     * Using this TimeZone information, obtain a TimeZone for the given DateTime that
     */
    TimeZone! resolve(DateTime datetime)
        {
        if (resolved)
            {
            assert this.isNoTZ == datetime.timezone.isNoTZ;
            return this;
            }

        TODO rules allow this to substitute a fixed TimeZone
        }

    @RO Int hours.get()
        {
        assert resolved;
        return picos / Time.PICOS_PER_HOUR;
        }

    @RO Int minutes.get()
        {
        // note: calculate the remainder (may be negative), and not the modulo
        return (picos - hours * Time.PICOS_PER_HOUR) / Time.PICOS_PER_MINUTE;
        }

    /**
     * Given a DateTime value, provide back a corresponding DateTime value that is in this TimeZone.
     *
     * @param orig  the original DateTime value
     *
     * @return a DateTime that is in this TimeZone
     */
    DateTime adopt(DateTime orig)
        {
        if (orig.&timezone == &this)
            {
            return orig;
            }

        if (orig.timezone.isNoTZ)
            {
            // transplant the date and time from the timezoneless DateTime into a DateTime using
            // this TimeZone
            return new DateTime(orig.date, orig.time, this);
            }

        return new DateTime(orig.epochPicos, this);
        }

    // ----- operators -----------------------------------------------------------------------------

    @Op("+") TimeZone add(Duration duration)
        {
        assert resolved && !isNoTZ;
        return new TimeZone(normalize(this.picos.toInt128() + duration.picoseconds.toInt128()));
        }

    @Op("-") TimeZone sub(Duration duration)
        {
        assert resolved && !isNoTZ;
        return new TimeZone(normalize(this.picos.toInt128() - duration.picoseconds.toInt128()));
        }

    /**
     * Normalize a potentially-large timezone offset to the range -12:00:00 .. +12:00:00.
     */
    private static Int normalize(Int128 picos)
        {
        Boolean negative = picos < 0;
        if (negative)
            {
            picos = picos.abs();
            }

        Int normalized = (picos % Time.PICOS_PER_DAY).toInt();
        if (normalized > 12 * Time.PICOS_PER_HOUR)
            {
            normalized -= Time.PICOS_PER_DAY;
            }

        return negative ? -normalized : +normalized;
        }

    @Op("-") Duration sub(TimeZone timezone)
        {
        assert this.resolved && timezone.resolved;
        assert this.isNoTZ == timezone.isNoTZ;

        Int difference = this.picos - timezone.picos;
        if (difference < 0)
            {
            difference += Time.PICOS_PER_DAY;
            }

        return new Duration(difference.toUInt128());
        }

    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int nameLength   = name?.size : 0;
        Int offsetLength = 0;
        if (resolved)
            {
            if (picos != 0)
                {
                if (picos % Time.PICOS_PER_MINUTE == 0)
                    {
                    // +00:00
                    offsetLength = 6;
                    }
                else
                    {
                    // we would have to do something like this to get a real estimate:
                    //   new Duration(picos.abs().toUInt128()).estimateStringLength();
                    // so make a guess instead
                    offsetLength = 10;
                    }
                }
            }

// TODO
//        return switch (nameLength > 0, offsetLength > 0)
//            {
//            case (true , true ): nameLength + offsetLength + 3;
//            case (true , false): nameLength;
//            case (false, true ): offsetLength;
//            case (false, false): 6;
//            }
// or
//        return switch ()
//            {
//            case nameLength > 0 && offsetLength > 0: nameLength + offsetLength + 3;
//            case nameLength > 0: nameLength;
//            case offsetLength > 0: offsetLength;
//            default: 6;
//            };

        if (nameLength > 0 && offsetLength > 0)
            {
            return nameLength + offsetLength + 3;
            }
        if (nameLength > 0)
            {
            return nameLength;
            }
        if (offsetLength > 0)
            {
            return offsetLength;
            }
        return 6;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        Boolean showPicos = resolved;
        String? name      = this.name;
        if (name != null)
            {
            appender.add(name);

            showPicos &&= picos != 0;
            if (showPicos)
                {
                appender.add(" (");
                }
            }

        if (showPicos)
            {
            appender.add(picos < 0 ? '-' : '+');
            Int hours   = this.hours.abs();
            Int minutes = this.minutes.abs();
            if (hours < 10)
                {
                appender.add('0');
                }
            hours.appendTo(appender);
            appender.add(':');
            if (minutes < 10)
                {
                appender.add('0');
                }
            minutes.appendTo(appender);

            if (picos % Time.PICOS_PER_MINUTE != 0)
                {
                Int remainder = (picos - hours * Time.PICOS_PER_HOUR - minutes * Time.PICOS_PER_MINUTE).abs();
                Int seconds   = remainder / Time.PICOS_PER_SECOND;
                appender.add(':');
                if (seconds < 10)
                    {
                    appender.add('0');
                    }
                seconds.appendTo(appender);
                remainder -= seconds * Time.PICOS_PER_SECOND;

                if (remainder > 0)
                    {
                    appender.add('.');
                    Duration.picosFractional(remainder).appendTo(appender);
                    }
                }

            if (name != null)
                {
                appender.add(')');
                }
            }
        }
    }
