import Array.Mutability;

/**
 * An Aggregator that collects an array of elements.
 */
const CollectArray<Element>(Mutability? mutability = Null)
        implements Aggregator<Element, Element[]> {

    /**
     * Obtain an CollectArray for the specified Element type.
     *
     * @param elementType  the specified Element type
     *
     * @return an CollectArray
     */
    static <Element> CollectArray<Element> of(Type<Element> elementType, Mutability? mutability = Null) {
        return new CollectArray<elementType.DataType>(mutability);
    }

    @Override
    Element[] init(Int capacity = 0) {
        return new Element[](capacity);
    }

    @Override
    Element[] reduce(Accumulator accumulator) {
        assert accumulator.is(Element[]);

        if (Mutability mutability ?= mutability) {
            return accumulator.toArray(mutability, inPlace=True);
        }
        return accumulator;
    }
}
