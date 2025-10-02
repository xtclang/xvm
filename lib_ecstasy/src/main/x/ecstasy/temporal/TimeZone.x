import TimeOfDay.PicosPerDay;
import TimeOfDay.PicosPerHour;
import TimeOfDay.PicosPerMinute;
import TimeOfDay.PicosPerSecond;

/**
 * A TimeZone contains the necessary information to convert a UTC Time value into a localized
 * Date and TimeOfDay value.
 *
 * There are four categories of TimeZones:
 * * International Atomic Time (TAI) - Raw, unadjusted time information. This would logically be an
 *   ideal internal form of the information held by `Time`, but instead the `Time` implementation
 *   uses the UTC timezone and the POSIX model for time (see
 *   [A.4.15 Seconds Since the Epoch](https://pubs.opengroup.org/onlinepubs/9699919799.2008edition/))
 * * UTC - Raw, adjusted for leap second, universally-coordinated Time information. The internal
 *   information held by `Time` is considered to be in the UTC timezone, which is the International
 *   Atomic Time (TAI), but adjusted for leap seconds. However, following the POSIX model, the leap
 *   seconds are not actually applied (i.e. every day is assumed to be exactly 86400 seconds);
 *   as a result, the 27 leap seconds since the start of the epoch (through 2022) as well as the 10
 *   previously assumed leap seconds (from 1958 to the start of epoch) are _missing_. Any strict
 *   interpretation of the `Time` data, and conversion to  TAI will need to take this historical
 *   anomaly into consideration. Almost all TimeZone conversions are performed starting from UTC
 *   data, and almost all Time values with a legitimate TimeZone can be trivially converted to UTC
 *   data (or to any other TimeZone). (Fortunately, it is likely that leap seconds [will be done
 *   away with](https://www.cnet.com/tech/computing/tech-giants-try-banishing-the-leap-second-to-stop-internet-crashes/)).
 * * Fixed - an offset from UTC measured in terms of picoseconds, but generally measured in terms of
 *   hour, half-hour, or quarter-hour increments.
 * * The "Un"-TimeZone, or "unknown" TimeZone, aka "NoTZ" - a special TimeZone that specifies the
 *   absence of *any* TimeZone information, useful for acting like a UTC time, but whose Time values
 *   are incapable of being compared with those of any other TimeZone.
 */
const TimeZone(Int64 picos) {

    /**
     * Internal constructor for the special "NoTZ" timezone.
     */
    private construct() {
        construct TimeZone(0);
        isNoTZ = True;
    }

    assert() {
        // there's no hard and fast rule that a timezone shouldn't exceed 24 hours, but to date,
        // none has come close to doing so
        assert:arg picos.abs() <= PicosPerDay;
    }

    /**
     * Obtain a [TimeZone] for the specified picosecond offset.
     *
     * @param picosOffset  the number of picoseconds difference from UTC that the TimeZone
     *                     represents
     *
     * @return the [TimeZone]
     */
    static TimeZone of(Int picosOffset) {
        return Zones.get(picosOffset) ?: new TimeZone(picosOffset);
    }

    /**
     * Obtain a [TimeZone] from an ISO-8601 timezone indicator string of one of the following
     * offset formats:
     *
     *     Z
     *     ±hh:mm
     *     ±hhmm
     *     ±hh
     *
     * @param tz  an ISO-8601 timezone indicator string
     *
     * @return `True` iff the ISO-8601 timezone indicator was valid
     * @return (conditional) the specified [TimeZone]
     */
    static conditional TimeZone of(String tz) {
        static Int valOf(Char ch) = ch >= '0' && ch <= '9' ? ch - '0' : -999;
        static Int parseInt(String s, Int of) = valOf(s[of]) * 10 + valOf(s[of+1]);

        if (tz == "Z" || tz == "UTC") {
            return True, UTC;
        }

        if (tz == "" || tz == "NoTZ") {
            return True, NoTZ;
        }

        if (tz.size >= 2 && (tz[0]=='+' || tz[0]=='-')) {
            Int hours = -999;   // ridiculously out of range
            Int mins  = 0;
            if (Int colon := tz.indexOf(':')) {
                if (colon > 1 && colon < tz.size-1) {
                    if (!(hours := Int.parse(tz[1 ..< colon]))) {
                        return False;
                    }
                    if (!(mins := Int.parse(tz.substring(colon+1)))) {
                        return False;
                    }
                }
            } else {
                switch (tz.size) {
                case 5:
                    mins  = parseInt(tz, 3);
                    continue;
                case 3:
                    hours = parseInt(tz, 1);
                    break;
                }
            }

            // as of 2025, no timezone offset is >= ±16, and historically the limit was < ±13 hours,
            // but then countries started to compete over who-gets-to-celebrate-New-Years-day-first
            if (0 <= hours <= 23 && 0 <= mins <= 59) {
                Int offset = (tz[0]=='-' ? -1 : +1) * (hours * PicosPerHour + mins * PicosPerMinute);
                return True, TimeZone.of(offset);
            }
        }

        return False;
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * The UTC TimeZone.
     */
    static TimeZone UTC = new TimeZone(0);

    /**
     * The "not-a-TimeZone" TimeZone.
     */
    static TimeZone NoTZ = new TimeZone();

    /**
     * A cache of expected TimeZones.
     */
    private static HashMap<Int, TimeZone> Zones = {
        HashMap<Int, TimeZone> map = new HashMap();
        for (Int hour : -12..13) {
            Int picos = hour * PicosPerHour;
            map.put(picos, new TimeZone(picos));
            picos += 30 * PicosPerMinute;
            map.put(picos, new TimeZone(picos));
        }
        return map.freeze(inPlace=True); // TODO GG this needs to error if it's not frozen (or better yet, auto freeze it)
    };

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Determine if this TimeZone is Universal Coordinated Time (UTC), aka "Z" or "Zulu" time.
     */
    Boolean isUTC.get() = picos == 0 && !isNoTZ;

    /**
     * Determine if this TimeZone is a special TimeZone that represents the lack of a TimeZone.
     */
    Boolean isNoTZ;

    Int hours.get() = picos / PicosPerHour;

    // note: calculate the remainder (may be negative), and not the modulo
    Int minutes.get() = (picos - hours * PicosPerHour) / PicosPerMinute;

    /**
     * The fraction of a minute, represented as picoseconds, in the range `0..59999999999999`.
     */
    Int picoseconds.get() = (picos % PicosPerMinute).toInt();

    /**
     * Given a Time value, provide back a corresponding Time value that is in this TimeZone.
     *
     * @param orig  the original Time value
     *
     * @return a Time that is in this TimeZone
     */
    Time adopt(Time orig) {
        if (orig.&timezone == &this) {
            return orig;
        }

        // if this is the `NoTZ` TimeZone then strip the TimeZone off the passed Time value, but
        // leave the date and time-of-day reflecting what the original TimeZone would have
        // calculated (this only supports dates on or after 1582-10-15 on the Gregorian calendar);
        // otherwise, if the Time value is from the `NoTZ` TimeZone, then transplant the Date and
        // TimeOfDay from the `NoTZ` Time into a Time using this TimeZone
        if (isNoTZ || orig.timezone.isNoTZ) {
            return new Time(orig.date, orig.timeOfDay, this);
        }

        return new Time(orig.epochPicos, this);
    }

    /**
     * The TimeZone name. Usually Null.
     */
    String? name.get() = picos == 0 ? (isNoTZ ? "NoTZ" : "UTC") : Null;

    // ----- operators -----------------------------------------------------------------------------

    @Op("+") TimeZone add(Duration duration) {
        assert !isNoTZ;
        return new TimeZone(normalize(this.picos.toInt128() + duration.picoseconds.toInt128()));
    }

    @Op("-") TimeZone sub(Duration duration) {
        assert !isNoTZ;
        return new TimeZone(normalize(this.picos.toInt128() - duration.picoseconds.toInt128()));
    }

    /**
     * Normalize a potentially-large timezone offset to the range -12:00:00 .. +12:00:00.
     */
    private static Int64 normalize(Int128 picos) {
        Boolean negative = picos < 0;
        if (negative) {
            picos = picos.abs();
        }

        Int64 normalized = (picos % PicosPerDay).toInt64();
        if (normalized > 12 * PicosPerHour) {
            normalized -= PicosPerDay;
        }

        return negative ? -normalized : +normalized;
    }

    @Op("-") Duration sub(TimeZone timezone) {
        assert this.isNoTZ == timezone.isNoTZ;

        Int difference = this.picos - timezone.picos;
        if (difference < 0) {
            difference += PicosPerDay;
        }

        return new Duration(difference);
    }

    // ----- Stringable ----------------------------------------------------------------------------

    @Override
    String toString(Boolean iso8601 = False) {
        return appendTo(new StringBuffer(estimateStringLength(iso8601)), iso8601).toString();
    }

    @Override
    Int estimateStringLength(Boolean iso8601 = False) {
        if (iso8601) {
            if (picos == 0) {
                return isNoTZ ? 0 : 1; // "Z"
            }
            return 6;
        }

        String? name     = name;
        Boolean showName = name != Null;
        Int     size     = 0;
        if (showName) {
            size += name?.size;
        }

        if (!showName || picos != 0) {
            // " (...)"
            if (showName) {
                size += 3;
            }

            // +hh:mm or -hh:mm
            size += 6;

            if (picos % PicosPerMinute != 0) {
                Int remainder = (picos - hours * PicosPerHour - minutes * PicosPerMinute).abs();
                Int seconds   = remainder / PicosPerSecond;
                size += 3;

                remainder -= seconds * PicosPerSecond;
                if (remainder > 0) {
                    size += 1 + Duration.picosFractionalLength(remainder);
                }
            }
        }

        return size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf, Boolean iso8601 = False) {
        if (iso8601 && picos == 0) {
            return isNoTZ ? buf : buf.add('Z');
        }

        String? name     = name;
        Boolean showName = !iso8601 && name != Null;
        if (showName) {
            name?.appendTo(buf);
        }

        if (!showName || picos != 0) {
            if (showName) {
                " (".appendTo(buf);
            }

            buf.add(picos < 0 ? '-' : '+');
            Int hours   = this.hours.abs();
            Int minutes = this.minutes.abs();
            if (hours < 10) {
                buf.add('0');
            }
            hours.appendTo(buf);
            buf.add(':');
            if (minutes < 10) {
                buf.add('0');
            }
            minutes.appendTo(buf);

            if (picos % PicosPerMinute != 0 && !iso8601) {
                assert !iso8601;

                Int remainder = (picos - hours * PicosPerHour - minutes * PicosPerMinute).abs();
                Int seconds   = remainder / PicosPerSecond;
                buf.add(':');
                if (seconds < 10) {
                    buf.add('0');
                }
                seconds.appendTo(buf);
                Duration.appendPicosFractional(buf, remainder - seconds * PicosPerSecond);
            }

            if (showName) {
                buf.add(')');
            }
        }

        return buf;
    }
}
