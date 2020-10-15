/**
 * A mapping for Version values.
 */
const VersionMapping
        implements Mapping<Version>
    {
    @Override
    String typeName.get()
        {
        return "Version";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return new Version(in.readString());
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toString());
        }
    }
