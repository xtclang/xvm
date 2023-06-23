/**
 * `FilteredCollection` is the deferred result of a `filter()` operation on a `Collection`.
 */
class FilteredCollection<Element>
        extends DeferredCollection<Element, Element> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a FilteredCollection based on an original collection and a filter.
     *
     * @param original  the unfiltered collection
     * @param include   the inclusion filter
     */
    construct(Collection<Element> original, function Boolean(Element) include) {
        construct DeferredCollection(original);
        this.include = include;
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * The filtering function, or Null after it has been filtered (which allows memory to be
     * collected).
     */
    protected function Boolean(Element)? include;

    @Override
    protected void postReifyCleanup() {
        include = Null;
        super();
    }

    @Override
    protected Iterator<Element> unreifiedIterator() {
        return original?.iterator().filter(include?) : assert;
    }

    @Override
    protected void evaluateInto(Appender<Element> accumulator) {
        if (DeferredCollection<Element> nextDeferred := original.is(DeferredCollection<Element>),
                function Boolean(Element) include ?= include) {
            class ApplyFilter(Appender<Element> accumulator, function Boolean(Element) include)
                    implements Appender<Element> {
                @Override Appender<Element> add(Element v) {
                    if (include(v)) {
                        accumulator.add(v);
                    }
                    return this;
                }
            }
            nextDeferred.evaluateInto(new ApplyFilter(accumulator, include));
        } else {
            super(accumulator);
        }
    }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    conditional Orderer? ordered() = (original ?: reified).ordered();

    @Override
    Boolean contains(Element value) {
        if (Collection<Element> original ?= original, function Boolean(Element) include ?= include) {
            return include(value) && original.contains(value);
        } else {
            return reified.contains(value);
        }
    }

    @Override
    Boolean containsAll(Collection<Element> values) {
        if (Collection<Element> original ?= original, function Boolean(Element) include ?= include) {
            return values.all(include) && original.containsAll(values);
        } else {
            return reified.containsAll(values);
        }
    }
}