/**
 * A mapping for `enum` values.
 */
const EnumMapping<EnumType extends Enum>
        implements Mapping<EnumType>
    {
    construct()
        {
        assert Class clz := EnumType.fromClass(), clz.is(Enumeration<EnumType>);
        this.typeName = clz.displayName;
        this.byName   = clz.byName;
        }

    /**
     * Enum values by name.
     */
    protected Map<String, EnumType> byName;

    @Override
    Serializable read(ElementInput in)
        {
        // TODO GG return byName[in.readString()] ?: assert;
        assert EnumType value := byName.get(in.readString());
        return value;
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        // TODO GG out.add(value.name);
        out.add(value.as(Enum).name);
        }
    }
