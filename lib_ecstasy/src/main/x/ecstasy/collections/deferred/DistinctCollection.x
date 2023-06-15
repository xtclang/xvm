/**
 * `DistinctCollection` is the deferred result of a `distinct()` operation on a `Collection`.
 */
class DistinctCollection<Element>(Collection<Element> original)
        implements Set<Element>
        delegates Collection<Element>(reified) {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The potentially non-distinct Collection.
     */
    private Collection<Element> original;

    /**
     * The explicitly distinct Collection.
     */
    private ListSet<Element>? realized.get() = &reified.peek() ?: Null;

    /**
     * The cached reified Collection.
     */
    private @Lazy ListSet<Element> reified.calc() = new ListSet<Element>().addAll(original);


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    conditional Int knownSize() {
        return realized?.knownSize();

        if (original.empty) {
            return True, 0;
        }
        return False;
    }

    @Override
    @RO Boolean empty.get() = realized?.empty : original.empty;

    @Override
    Boolean contains(Element value) = realized?.contains(value) : original.contains(value);

    @Override
    Boolean containsAll(Collection<Element> values) = realized?.containsAll(values) : original.containsAll(values);

    @Override
    <Result extends Collection<Element>>
            Result distinct(Aggregator<Element, Result>? collector = Null) {
        return collector == Null ? this.as(Result) : super();
    }

    @Override
    Set<Element> reify() = reified;
}