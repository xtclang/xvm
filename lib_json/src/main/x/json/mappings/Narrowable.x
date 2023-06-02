/**
 * A mapping mixin for a mapping that may need to replace itself automatically as it reads or writes
 * in order to handle a more specific type (such as a sub-class) than the mapping was instantiated
 * to handle.
 */
mixin Narrowable<Serializable>
        into Mapping<Serializable> {

    @Override
    Serializable read(ElementInput in) {
        Schema schema = in.schema;
        if (schema.enableMetadata,
                Doc typeName ?= in.peekMetadata(schema.typeKey),
                typeName.is(String)) {
            Type type;
            try {
                type = schema.typeForName(typeName);
            } catch (Exception e) {
                throw e.is(IllegalJSON) ? e : new IllegalJSON($"Invalid type name: \"{typeName}\"", e);
            }

            if (!type.is(Type<Serializable>)) {
                throw new IllegalJSON($"Incompatible type: \"{this.typeName}\" required, \"{typeName}\" found");
            }

            Mapping<Serializable> substitute = schema.ensureMapping(type);
            if (&substitute != &this) { // avoid infinite recursion to "this"
                return substitute.read(in);
            }
        }

        return super(in);
    }

    @Override
    void write(ElementOutput out, Serializable value) {
        Type<Serializable> type = &value.actualType;
        if (type != Serializable) {
            Schema schema = out.schema;
            if (schema.enableMetadata) {
                // there is no reason to record the immutability modifier for the type;
                // we only support immutable types and it makes it simpler for the reader
                Type typeSansImmutable = type;
                if (type.explicitlyImmutable) {
                    assert typeSansImmutable := type.modifying();
                }
                out.prepareMetadata(schema.typeKey, schema.nameForType(typeSansImmutable));
            }

            if (Mapping<Serializable> substitute := narrow(schema, type), &substitute != &this) {
                substitute.write(out, value);
                return;
            }
        }

        super(out, value);
    }
}