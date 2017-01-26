typedef function Void Alarm();
typedef function Void Cancellable();

/**
 * some possible examples:
 * wall clock
 * -> adjusted to a particular timezone
 * monotonic clock
 * runtime clock for the application
 * container-specific clocks
 * service-specific clocks
 */
service Clock
    {
    @ro DateTime epoch;
    @ro TimeZone timezone
    @ro Interval precision;
    @ro Boolean monotonic;
    @ro Boolean realtime;

    @ro DateTime time;

    Timer createTimer();

    /**
     *
     * <p>
     * Invoking the returned #Cancellable will attempt to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, for example if the request for cancellation occurs concurrently
     * with the clock attempting to invoke the alarm.
     */
    Cancellable scheduleAlarm(Alarm alarm, DateTime timeToWakeUp);
    }

// TODO handy class that does auto-repeating alarms etc.
trait  appliesto Clock
    {

    }

const Date
    {
    }

/**
 * The Date value type is used to represent the information about a date in Gregorian form.
 */
const Date(Int year, Int month, Int day)
    {
    @ro dayOfYear;

    Time add(Duration);
    Duration sub(Time);
    Time sub(Duration);

    DateTime to<DateTime>()
        {
        return new DateTime(this, Time:"00:00");
        }
    }

const Time(Int hour, Int minute, Int second=0, Int nanoseconds=0)
    {
    Time add(Duration);
    Duration sub(Time);
    Time sub(Duration);
    }

// TODO week starts on Sunday or Monday?
enum DayOfWeek(Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday);

const DateTime(Date date, Time time)
    {

    DateTime add(Duration);
    Duration sub(DateTime);
    DateTime sub(Duration);
    }

const Duration
    {
    /**
     * The total number of hours, rounded down. This is the same as
     * #minutesTotal / 60.
     */
    @ro Int hoursTotal;
    /**
     * The total number of minutes, rounded down. This is the same as
     * #secondsTotal / 60.
     */
    @ro Int minutesTotal;
    /**
     * The total number of seconds, rounded down. This is the same as
     * #nanosecondsTotal / 1000000000.
     */
    @ro Int secondsTotal;
    /**
     * The total number of nanoseconds, rounded down.
     */
    @ro Int nanosecondsTotal;

    /**
     * Exclusive of the time represented by #hoursTotal, the number of minutes,
     * rounded down. This is the same as #minutesTotal - (#hoursTotal * 60).
     */
    @ro Int minutesPart;
    /**
     * Exclusive of the time represented by #minutesTotal, the number of seconds,
     * rounded down. This is the same as #secondsTotal - (#minutesTotal * 60).
     */
    @ro Int secondsPart;
    /**
     * Exclusive of the time represented by #secondsTotal, the number of nanoseconds,
     * rounded down. This is the same as #nanosecondsTotal - (#secondsTotal * 1000000000).
     */
    @ro Int nanosecondsPart;

    Duration add(Duration duration);
    Duration sub(Duration duration);
    Duration mul(Number factor);
    Duration div(Number divisor);

    Duration min(Duration duration);
    Duration max(Duration duration);
    }

service Timer
    {
    /**
     * Determine the resolution of this timer.
     */
    @ro Duration resolution;

    /**
     * Obtain the duration of time that has elapsed on this timer. Durations from this
     * Timer should only be compared with other Durations from this Timer.
     */
    @ro Duration elapsed;

    /**
     * Schedule an #Alarm that will be invoked after the specified Duration completes.
     */
    Cancellable scheduleAlarm(Alarm alarm, Duration durationBeforeAlarm);
    }
