/**
 * A mapping for TODO CP
 */
mixin Narrowable<Serializable>
        into Mapping<Serializable>
    {
    @Override
    Serializable read(ElementInput in)
        {
        Schema schema = in.schema;
        if (schema.enableMetadata,
                Doc typeName ?= in.peekMetadata(schema.typeKey),
                typeName.is(String))
            {
            // TODO GG: (some weird error messages and not clear what I should have been doing ..)
            // TODO GG: Mapping<Serializable> substitute = schema.ensureMapping(schema.typeForName(typeName).as(Serializable));
            // TODO GG: Mapping<Serializable> substitute = schema.ensureMapping(schema.typeForName<Serializable>(typeName));
            // TODO GG: Mapping<Serializable> substitute = schema.ensureMapping(schema.typeForName(typeName)).as(Mapping<Serializable>);
            // TODO GG: Mapping<Serializable> substitute = schema.ensureMapping(schema.typeForName(typeName).as(Serializable));
            Mapping<Serializable> substitute = schema.ensureMapping(schema.typeForName(typeName).as(Type<Serializable>));
            if (&substitute != &this) // avoid infinite recursion to "this"
                {
                return substitute.read(in);
                }
            }

        return super(in);
        }

    @Override
    void write(ElementOutput out, Serializable value)
        {
        Type<Serializable> type = &value.actualType;
        if (type != Serializable)
            {
            Schema schema = out.schema;
            if (schema.enableMetadata)
                {
                out.prepareMetadata(schema.typeKey, schema.nameForType(type));
                }

            if (Mapping<Serializable> substitute := narrow(schema, type), &substitute != &this)
                {
                substitute.write(out, value);
                return;
                }
            }

        super(out, value);
        }

//    @Override
//    <SubType extends Serializable> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type)
//        {
//        if (type != Serializable)
//            {
//            try
//                {
//                return True, TODO CP
//                }
//            catch (MissingMapping e) {}
//            }
//
//        return False;
//        }
    }
