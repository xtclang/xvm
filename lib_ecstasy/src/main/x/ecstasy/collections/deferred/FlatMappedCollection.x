/**
 * `FlatMappedCollection` is the deferred result of a `flatmap()` operation on a `Collection`.
 */
class FlatMappedCollection<Element, FromElement>
        extends DeferredCollection<Element, FromElement> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a FlatMappedCollection based on an original collection and a flattener.
     *
     * @param original    the unflattened collection
     * @param flatten   the flattener
     */
    construct(Collection<FromElement> original, function void(FromElement, Appender<Element>) flatten) {
        construct DeferredCollection(original);
        this.flatten = flatten;
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * The mapping function, or Null after it has been applied (which allows memory to be
     * collected).
     */
    public function void(FromElement, Appender<Element>)? flatten;

    @Override
    protected Collection<Element> createReified() {
        assert Collection<FromElement>                       original ?= original;
        assert function void(FromElement, Appender<Element>) flatten  ?= flatten;
        Element[] result = new Element[];
        for (FromElement e : original) {
            flatten(e, result);
        }
        return result;
    }

    @Override
    protected Iterator<Element> unreifiedIterator() {
        return original?.iterator().flatMap(flatten?) : assert;
    }

    @Override
    protected void evaluate(Appender<Element> accumulator) {
        if (DeferredCollection<FromElement> nextDeferred := original.is(DeferredCollection<FromElement>),
                function void(FromElement, Appender<Element>) flatten ?= flatten) {
            class ApplyFlatten(Appender<Element> accumulator, function void(FromElement, Appender<Element>) flatten)
                    implements Appender<FromElement> {
                @Override Appender<FromElement> add(FromElement v) {
                    flatten(v, accumulator);
                    return this;
                }
            }
            nextDeferred.evaluate(new ApplyFlatten(accumulator, flatten));
        } else {
            super(accumulator);
        }
    }


    // ----- Collection interface ------------------------------------------------------------------

}