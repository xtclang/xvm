/**
 * A mapping for Duration values.
 */
const DurationMapping
        implements Mapping<Duration>
    {
    @Override
    String typeName.get()
        {
        return "Duration";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return new Duration(in.readString());
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toString(iso8601=True));
        }
    }
