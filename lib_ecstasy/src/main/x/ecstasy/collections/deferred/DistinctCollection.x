/**
 * `DistinctCollection` is the deferred result of a `distinct()` operation on a `Collection`.
 */
class DistinctCollection<Element>
        extends DeferredCollection<Element, Element>
        implements Set<Element> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a DistinctCollection based on an original collection.
     *
     * @param original  the potentially non-distinct collection
     */
    construct(Collection<Element> original) {
        construct DeferredCollection(original);
    }


    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected Collection<Element> instantiateEmptyReified() {
        return new ListSet<Element>();
    }

    @Override
    protected Iterator<Element> unreifiedIterator() {
        if (Collection<Element> original ?= original,
                Orderer? order := original.ordered(),
                order != Null) {
            // it's possible to optimize the iteration without realizing the set, because the
            // original collection is ordered, thus identical elements will be grouped together, and
            // thus a once-through iteration can be accomplished simply by remember the previously
            // returned value -- after the first value has been returned, of course!
            Iterator<Element> orderedIterator = original.iterator();
            return new Iterator<Element>() {
                private Boolean  first = True;
                private Element? prev  = Null;

                @Override Boolean knownDistinct() = True;
                @Override conditional Orderer knownOrder() = (True, order);
                @Override conditional Int knownSize() = this.DistinctCollection.knownSize();

                @Override
                conditional Element next() {
                    if (first) {
                        if (Element next := orderedIterator.next()) {
                            first = False;
                            prev  = next;
                            return True, next;
                        } else {
                            return False;
                        }
                    } else {
                        while (Element next := orderedIterator.next()) {
                            if (next != prev) {
                                prev = next;
                                return True, next;
                            }
                        }
                        return False;
                    }
                }
            };
        }

        assert;
    }

    @Override
    protected void evaluateInto(Appender<Element> accumulator) {
        if (Collection<Element> original ?= original) {
            Orderer? order = original.ordered() ?: Null;
            if (order == Null) {
                if (original.is(Set) || accumulator.is(Set)) {
                    accumulator.addAll(original);
                } else {
                    // only accumulate distinct values; this is expensive but unavoidable
                    Collection<Element> distinctElements = Element.is(Type<Hashable>)
                            ? new HashSet<Element>()
                            : Element.is(Type<Orderable>)
                                    ? new SkiplistSet<Element>()
                                    : new Element[];
                    for (Element element : original) {
                        if (distinctElements.addIfAbsent(element)) {
                            accumulator.add(element);
                        }
                    }
                }
                return;
            }
        }
        return super(accumulator);
    }

    // ----- Collection interface ------------------------------------------------------------------

    @Override
    conditional Orderer? ordered() = (original ?: reified).ordered();

    @Override
    Boolean contains(Element value) = (original ?: reified).contains(value);

    @Override
    Boolean containsAll(Collection<Element> values) = (original ?: reified).containsAll(values);

    @Override
    Set<Element> distinct() {
        return this;
    }
}