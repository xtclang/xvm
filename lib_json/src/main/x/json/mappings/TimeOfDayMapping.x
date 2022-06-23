/**
 * A mapping for TimeOfDay values.
 */
const TimeOfDayMapping
        implements Mapping<TimeOfDay>
    {
    @Override
    String typeName.get()
        {
        return "TimeOfDay";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return new TimeOfDay(in.readString());
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toString());
        }
    }
