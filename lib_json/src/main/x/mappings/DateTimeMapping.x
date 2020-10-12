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
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toString(True));
        }
    }
