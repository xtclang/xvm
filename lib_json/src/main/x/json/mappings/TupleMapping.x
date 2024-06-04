/**
 * A mapping for an immutable Tuple object.
 */
const TupleMapping<Serializable extends Tuple>(Mapping[] valueMappings)
        implements Mapping<Serializable> {
    /**
     * Construct the TupleMapping.
     *
     * @param keyMapping    the mapping to use for the keys of the `Map`
     * @param valueMapping  the mapping to use for the values of the `Map`
     */
    construct(Mapping[] valueMappings) {
        this.valueMappings = valueMappings;

        StringBuffer buf = new StringBuffer();
        valueMappings.appendTo(buf, sep=", ", pre="Tuple<", post=">", render= m -> m.typeName);
        this.typeName = buf.toString();
    }

    @Override
    Serializable read(ElementInput in) {
        Schema schema = in.schema;
        Tuple  tuple  = Tuple:();

        using (FieldInput mapInput = in.openObject()) {
            using (ElementInput entriesInput = mapInput.openArray("e")) {
                for (Int i = 0; entriesInput.canRead; i++) {
                    Mapping mapping = valueMappings[i];
                    mapping.Serializable value =
                        entriesInput.readUsing(mapping.as(Mapping<mapping.Serializable>));
                    tuple = tuple.add(value);
                }
            }
        }
        return tuple.makeImmutable().as(Serializable);
    }

    @Override
    void write(ElementOutput out, Serializable tuple) {
        Schema schema = out.schema;

        using (FieldOutput mapOutput = out.openObject()) {
            using (ElementOutput entriesOutput = mapOutput.openArray("e")) {
                for (Int i : 0 ..< tuple.size) {
                     Mapping mapping = valueMappings[i];
                     mapping.Serializable value = tuple[i].as(mapping.Serializable);
                     entriesOutput.addUsing(mapping.as(Mapping<mapping.Serializable>), value);
                }
            }
        }
    }
}