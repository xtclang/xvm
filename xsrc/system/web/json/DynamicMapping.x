/**
 * A [Mapping] implementation that delegates back through a schema for dynamically resolving types.
 *
 * TODO verify other type traits come through e.g. immutable
 */
const DynamicMapping<Serializable>(Mapping<Serializable>? defaultMapping = Null)
        implements Mapping<Serializable>
    {
    // ----- Mapping interface ---------------------------------------------------------------------

    @Override
    Serializable read(ElementInput in)
        {
        if (in.isNull())
            {
            return Null.as(Serializable);
            }

        Schema schema = in.schema;
        if (schema.enableReflection)
            {
            // TODO get metadata ("$type" has the name) and ask schema for a Mapping
            // schema.reflectionMapper.ensureMapping(&value.actualType).write(out, value);
            }

        return defaultMapping?.read(in);
        throw new MissingMapping(type=Serializable);
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        Schema schema = out.schema;
        if (value.is(Nullable))
            {
            out.add(Null);
            }
        else if (schema.enableReflection) // TODO short circuit if it matches the default mapping
            {
            schema.reflectionMapper.ensureMapping(&value.actualType.as(Type<Serializable>)).write(out, value);
            }
        else
            {
            defaultMapping?.write(out, value) : throw new MissingMapping(type=Serializable);
            }
        }
    }
