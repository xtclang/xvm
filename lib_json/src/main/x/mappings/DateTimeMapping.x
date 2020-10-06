/**
 * A mapping for DateTime values.
 */
const DateTimeMapping
        implements Mapping<DateTime>
    {
    @Override
    String typeName.get()
        {
        return "DateTime";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return new DateTime(in.readString());
        }

    @Override
    // TODO GG void write(ElementOutput out, Serializable value)
    void write(ElementOutput out, DateTime value)
        {
        out.add(value.toString(True));
        }
    }
