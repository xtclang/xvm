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
        return byName[in.readString()] ?: assert;
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        out.add(value.name);
        }
    }
