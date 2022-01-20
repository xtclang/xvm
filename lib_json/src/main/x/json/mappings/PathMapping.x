/**
 * A mapping for Path values.
 */
const PathMapping
        implements Mapping<Path>
    {
    @Override
    String typeName.get()
        {
        return "Path";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return new Path(in.readString());
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toString());
        }
    }
