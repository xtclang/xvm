/**
 * A mapping for Time values.
 */
const TimeMapping
        implements Mapping<Time>
    {
    @Override
    String typeName.get()
        {
        return "Time";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return new Time(in.readString());
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toString());
        }
    }
