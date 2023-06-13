/**
 * An Aggregator that collects an array of elements, and freezes it when complete.
 */
const CollectImmutableArray<Element extends Shareable>
        implements Aggregator<Element, immutable Element[]> {

    /**
     * Obtain an CollectImmutableArray for the specified Element type.
     *
     * @param elementType  the specified Element type
     *
     * @return an CollectImmutableArray
     */
    static <Element> CollectImmutableArray<Element> of(Type<Element> elementType) {
        return new CollectImmutableArray<elementType.DataType>();
    }

    @Override
    Element[] init(Int capacity = 0) {
        return new Element[](capacity);
    }

    @Override
    immutable Element[] reduce(Accumulator accumulator) {
        return accumulator.as(Element[]).freeze(inPlace=True);
    }
}
