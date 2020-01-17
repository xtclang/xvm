/**
 * A reflection-based [Mapping] implementation.
 */
const ReflectionMapping
        implements Mapping<Object>
    {
    @Override
    <ObjectType extends Serializable> ObjectType read<ObjectType>(ElementInput in)
        {
        if (in.schema.enableMetadata)
            {
            // use metadata to determine what to read
            TODO
            }

        if (ObjectType != Object)
            {
            // use ObjectType to determine what to read
            TODO
            }

        throw new MissingMapping(type=ObjectType);
        }

    @Override
    <ObjectType extends Serializable> void write(ElementOutput out, ObjectType value)
        {
        // if no type information is provided, then use the runtime type of the object itself as
        // the source of reflection information for the contents of the JSON serialization
        Type type = ObjectType == Object
                ? &value.actualType
                : ObjectType;

        if (value.is(Nullable))
            {
            // out.
            }

        if (type.is(Type<Number>))
            {
            }
        else if (type.is(Type<Boolean>))
            {
            }
        else
            {
            }

        TODO
        }
    }
