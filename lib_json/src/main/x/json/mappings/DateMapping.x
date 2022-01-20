/**
 * A mapping for Date values.
 */
const DateMapping
        implements Mapping<Date>
    {
    @Override
    String typeName.get()
        {
        return "Date";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return new Date(in.readString());
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toString());
        }
    }
