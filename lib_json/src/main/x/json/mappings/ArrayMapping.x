/**
 * A mapping for Array values.
 */
const ArrayMapping<Element>(Mapping<Element> elementMapping)
        implements Mapping<Array<Element>> {
    /**
     * Construct the ArrayMapping.
     *
     * @param underlying  the mapping to use for the elements of the `Array`
     */
    construct(Mapping<Element> elementMapping) {
        this.elementMapping = elementMapping;
        this.typeName       = $"Array<{elementMapping.typeName}>";
    }

    @Override
    Serializable read(ElementInput in) {
        return in.readArrayUsing(elementMapping);
    }

    @Override
    void write(ElementOutput out, Serializable value) {
        out.addObjectArray(value);
    }

    @Override
    <SubType extends Array<Element>> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type) {
        if (SubType.Element != Element,
                val narrowedElement := schema.findMapping(SubType.Element),
                &narrowedElement != &elementMapping) {
            return True, new ArrayMapping<SubType.Element>(narrowedElement).as(Mapping<SubType>);
        }
        return False;
    }
}
