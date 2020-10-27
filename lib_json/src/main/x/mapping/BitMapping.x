/**
 * A mapping for Bit values.
 */
const BitMapping
        implements Mapping<Bit>
    {
    @Override
    String typeName.get()
        {
        return "Bit";
        }

    @Override
    Serializable read(ElementInput in)
        {
        return in.readIntLiteral().toBit();
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.toInt());
        }
    }
