/**
 * `FilteredCollection` is the deferred result of a `filter()` operation on a `Collection`.
 */
class FilteredCollection<Element>
        extends DeferredCollection<Element> {
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
    public function Boolean(Element)? include;

    @Override
    protected Collection<Element> createReified() {
        assert Collection<Element>       original ?= original;
        assert function Boolean(Element) include  ?= include;
        Element[] contents = new Element[];
        for (Element el : original) {
            if (include(el)) {
                contents.add(el);
            }
        }
        return contents;
    }

    @Override
    protected Iterator<Element> unreifiedIterator() {
        return original?.iterator().filter(include?) : assert;
    }

// TODO GG
//    @Override
//    protected void evaluate(Appender<Element> accumulator) {
//        if (DeferredCollection nextDeferred := original.is(DeferredCollection)) {
//            Appender<Element> applyFilter = new Appender<Element>() {
//                private function Boolean(Element) x = TODO
////                private function Boolean(Element) x = include ?: assert;
//                @Override Appender<Element> add(Element v) {
//                    if (x(v)) {
//                        accumulator.add(v);
//                    }
//                    return this;
//                }
//            };
//            nextDeferred.evaluate(applyFilter);
//        } else {
//            super(accumulator);
//        }
//    }


    // ----- Collection interface ------------------------------------------------------------------

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

    @Override
    <Result extends Collection!> Result filter(function Boolean(Element) match,
                                               Aggregator<Element, Result>? collector = Null) {
        if (collector == Null,
                Collection<Element> original ?= original,
                function Boolean(Element) include ?= include) {
            // TODO GG forgetting "<Element>" on the next line produces an unhelpful error message
            return new FilteredCollection<Element>(original, e -> include(e) && match(e)).as(Result);
        }
        // TODO GG return reified.filter(match, collector);
        return super(match, collector);
    }
}