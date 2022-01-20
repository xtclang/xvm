import ecstasy.temporal.TimeZoneCache;

/**
 * A mapping for TimeZone values.
 */
const TimeZoneMapping
        implements Mapping<TimeZone>
    {
    @Override
    String typeName.get()
        {
        return "TimeZone";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return TimeZoneCache.find(in.readString(""));
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.name ?: value.toString(True));
        }
    }
