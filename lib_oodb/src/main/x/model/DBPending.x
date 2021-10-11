import DBProcessor.Prioritizable.Priority;
import DBProcessor.Repeatable.Policy;

/**
 * A simple implementation of the [oodb.DBPending] interface.
 */
const DBPending<Element extends immutable Const>
        (
        Path      processor,
        Element   element,
        DateTime? scheduledAt      = Null,
        Time?     scheduledDaily   = Null,
        Duration? repeatInterval   = Null,
        Policy    repeatPolicy     = AllowOverlapping,
        Priority  priority         = Normal,
        Int       previousFailures = 0,
        )
        implements oodb.DBPending<Element>
    {
    assert()
        {
        if (scheduledDaily != Null)
            {
            // TODO GG Error: /Users/cameron/Development/xvm/lib_oodb/src/main/x/model/DBPending.x [25:39..25:42] COMPILER-43: Type mismatch: Null expected, Duration found. ("24h")
            // assert repeatInterval == Null;
            repeatInterval = Duration:24h;
            }

        // can't be scheduled both at a specific date/time and the same time every day
        assert scheduledAt == Null || scheduledDaily == Null;
        }

    @Override
    conditional DateTime | Time isScheduled()
        {
        if (scheduledAt != Null)
            {
            return True, scheduledAt;
            }

        if (scheduledDaily != Null)
            {
            return True, scheduledDaily;
            }

        return False;
        }

    @Override
    conditional (Duration repeatInterval, DBProcessor.Repeatable.Policy repeatPolicy) isRepeating()
        {
        if (repeatInterval != Null)
            {
            return True, repeatInterval, repeatPolicy;
            }

        return False;
        }
    }
