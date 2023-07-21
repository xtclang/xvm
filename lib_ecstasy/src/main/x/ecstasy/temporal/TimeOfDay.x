/**
 * A `TimeOfDay` represents information about "time-of-day", also referred to as "wall clock time".
 * It is primarily intended to support human interface concepts, such as scheduling, or to assist in
 * displaying information.
 *
 * The `TimeOfDay` has rudimentary support for representing and dealing with leap seconds.
 */
const TimeOfDay(Int picos)
        implements Destringable {
    static IntLiteral PicosPerNano   = 1K;
    static IntLiteral PicosPerMicro  = 1K * PicosPerNano;
    static IntLiteral PicosPerMilli  = 1K * PicosPerMicro;
    static IntLiteral PicosPerSecond = 1K * PicosPerMilli;
    static IntLiteral PicosPerMinute = 60 * PicosPerSecond;
    static IntLiteral PicosPerHour   = 60 * PicosPerMinute;
    static IntLiteral PicosPerDay    = 24 * PicosPerHour;

    static TimeOfDay MIDNIGHT = new TimeOfDay(0);

    /**
     * Construct a `TimeOfDay` based on the number of picoseconds elapsed since `00:00:00`.
     *
     * @param picos  the number of picoseconds elapsed since `00:00:00`
     */
    construct(Int picos) {
        assert picos < PicosPerDay + PicosPerSecond;  // allow for a leap-second
        this.picos = picos;
    }

    /**
     * Construct a `TimeOfDay` based on number hours, minutes, seconds, and picoseconds.
     *
     * @param hour    the number of hours, in the range `0..23`
     * @param minute  the number of minutes, in the range `0..59`
     * @param second  the number of seconds, in the range `0..59`; the leap second `23:59:60` is
     *                also supported
     * @param picos   the number of picoseconds, in the range `0..999999999999`
     */
    construct(Int hour, Int minute, Int second=0, Int picos=0) {
        assert 0 <= hour   < 24;
        assert 0 <= minute < 60;
        assert 0 <= second < 60
                || hour == 23 && minute == 59 && second == 60; // allow for a leap-second
        assert 0 <= picos  < PicosPerSecond;
        construct TimeOfDay(((hour * 60 + minute) * 60 + second) * PicosPerSecond + picos);
    }

    /**
     * Construct a `TimeOfDay` from an ISO-8601 time string.
     */
    @Override
    construct(String text) {
        String   hours;
        String   mins;
        String   secs  = "";
        String[] parts = text.split(':');
        switch (parts.size) {
        case 3:
            secs  = parts[2];
            continue;
        case 2:
            hours = parts[0];
            mins  = parts[1];
            break;

        case 1:
            Int len = text.size;
            if (len >= 4) {
                hours = text[0 .. 1];
                mins  = text[2 .. 3];
                if (len > 4) {
                    secs = text[4 ..< len];
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

        if (secs != "") {
            if (Int dot := secs.indexOf('.')) {
                if (dot > 0) {
                    sec = new IntLiteral(secs[0 ..< dot]);
                }

                Int len = secs.size;
                if (dot < len-1) {
                    String picos = secs[dot >..< len];
                    if (picos.size > 12) {
                        picos = picos[0 ..< 12];
                    }

                    pico = new IntLiteral(picos) * SCALE_10[picos.size];
                }
            } else {
                sec = new IntLiteral(secs);
            }
        }

        construct TimeOfDay(hour, min, sec, pico);
    }

    /**
     * Validate the hours, minutes, seconds, and picoseconds components of a `TimeOfDay`.
     *
     * @param hour    the number of hours, in the range `0..23`
     * @param minute  the number of minutes, in the range `0..59`
     * @param second  the number of seconds, in the range `0..59`; the leap second `23:59:60` is
     *                also allowed
     * @param picos   the number of picoseconds, in the range `0..999999999999`
     *
     * @return True iff the passed values are in their allowed ranges
     */
    static Boolean validate(Int hour, Int minute, Int second=0, Int picos=0) {
        return 0 <= hour   < 24
            && 0 <= minute < 60
            && (0 <= second < 60
                || hour == 23 && minute == 59 && second == 60)  // allow for a leap-second
            && 0 <= picos  < PicosPerSecond;
    }

    /**
     * The hour of the day, in the range `0..23`.
     */
    UInt8 hour.get() {
        return picos >= PicosPerDay
                ? 23
                : (picos / PicosPerHour).toUInt8();
    }

    /**
     * The minute of the hour, in the range `0..59`.
     */
    UInt8 minute.get() {
        return picos >= PicosPerDay
                ? 59
                : (picos / PicosPerMinute % 60).toUInt8();
    }

    /**
     * The second of the minute, in the range `0..59` (or `60`, in the extremely rare case of a leap
     * second).
     */
    UInt8 second.get() {
        return picos >= PicosPerDay
                ? 60
                : (picos / PicosPerSecond % 60).toUInt8();
    }

    /**
     * The fraction of a second, represented as milliseconds, in the range `0..999`.
     * This is the same as `microseconds / 1000`.
     */
    UInt16 milliseconds.get() = (picoseconds / PicosPerMilli).toUInt16();

    /**
     * The fraction of a second, represented as microseconds, in the range `0..999999`.
     * This is the same as `nanoseconds / 1000`.
     */
    UInt32 microseconds.get() = (picoseconds / PicosPerMicro).toUInt32();

    /**
     * The fraction of a second, represented as nanoseconds, in the range `0..999999999`.
     * This is the same as `picoseconds / 1000`.
     */
    UInt32 nanoseconds.get() = (picoseconds / PicosPerNano).toUInt32();

    /**
     * The fraction of a second, represented as picoseconds, in the range `0..999999999999`.
     */
    Int picoseconds.get() = (picos % PicosPerSecond).toInt();


    // ----- operators -----------------------------------------------------------------------------

    @Op("+") TimeOfDay add(Duration duration) {
        Int period = (duration.picoseconds % PicosPerDay).toInt();
        if (period == 0) {
            return this;
        }

        Int sum = picos + period;
        if (sum > PicosPerDay) {
            // check if this TimeOfDay is a leap second
            if (picos > PicosPerDay) {
                // this TimeOfDay is a leap second, so treat the result accordingly
                if (sum > PicosPerDay + PicosPerSecond) {
                    sum -= PicosPerDay + PicosPerSecond;
                }
            } else {
                sum -= PicosPerDay;
            }
        }

        return new TimeOfDay(sum);
    }

    @Op("-") TimeOfDay sub(Duration duration) {
        Int minuend    = this.picos;
        Int subtrahend = (duration.picoseconds % PicosPerDay).toInt();
        if (subtrahend > minuend) {
            minuend += PicosPerDay;
        }
        return new TimeOfDay(minuend - subtrahend);
    }

    @Op("-") Duration sub(TimeOfDay timeOfDay) {
        Int picosStop  = this.picos;
        Int picosStart = timeOfDay.picos;

        if (picosStart > picosStop) {
            // treat the time-of-day being subtracted as being from "yesterday"
            picosStop += PicosPerDay;

            // check for the possibility that "yesterday" had an obvious leap second
            if (picosStart > PicosPerDay) {
                picosStop += PicosPerSecond;
            }
        }
        return new Duration(picosStop - picosStart);
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * @return the [Duration] of time since midnight represented by this `TimeOfDay` object
     */
    Duration toDuration() = new Duration(picos);


    // ----- Stringable interface ------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        Int fraction = picoseconds;
        return fraction == 0
                ? 8
                : 9 + Duration.picosFractionalLength(fraction);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        Int hour = this.hour;
        if (hour < 10) {
            buf.add('0');
        }
        hour.appendTo(buf);
        buf.add(':');

        Int minute = this.minute;
        if (minute < 10) {
            buf.add('0');
        }
        minute.appendTo(buf);
        buf.add(':');

        Int second = this.second;
        if (second < 10) {
            buf.add('0');
        }
        second.appendTo(buf);
        return Duration.appendPicosFractional(buf, picoseconds);
    }
}