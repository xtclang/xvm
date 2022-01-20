/**
 * A mapping for Char values.
 */
const CharMapping
        implements Mapping<Char>
    {
    @Override
    String typeName.get()
        {
        return "Char";
        }

    @Override
    Serializable read(ElementInput in)
        {
        String s = in.readString();
        assert s.size == 1;
        return s[0];
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toString());
        }
    }
