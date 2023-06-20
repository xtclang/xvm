/**
 * `MappedCollection` is the deferred result of a `map()` operation on a `Collection`.
 */
class MappedCollection<Element, FromElement>
        extends DeferredCollection<Element, FromElement> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MappedCollection based on an original collection and a transformer.
     *
     * @param original    the untransformed collection
     * @param transform   the transformer
     */
    construct(Collection<FromElement> original, function Element(FromElement) transform) {
        construct DeferredCollection(original);
        this.transform = transform;
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * The mapping function, or Null after it has been applied (which allows memory to be
     * collected).
     */
    public function Element(FromElement)? transform;

    @Override
    protected Collection<Element> createReified() {
        assert Collection<FromElement>       original  ?= original;
        assert function Element(FromElement) transform ?= transform;
        Element[] contents = new Element[](original.knownSize() ?: 0);
        for (FromElement e : original) {
            contents.add(transform(e));
        }
        return contents;
    }

    @Override
    protected Iterator<Element> unreifiedIterator() {
        return original?.iterator().map(transform?) : assert;
    }

    @Override
    protected void evaluate(Appender<Element> accumulator) {
        if (DeferredCollection<FromElement> nextDeferred := original.is(DeferredCollection<FromElement>),
                function Element(FromElement) transform ?= transform) {
            class ApplyTransform(Appender<Element> accumulator, function Element(FromElement) transform)
                    implements Appender<FromElement> {
                @Override Appender<FromElement> add(FromElement v) {
                    accumulator.add(transform(v));
                    return this;
                }
            }
            nextDeferred.evaluate(new ApplyTransform(accumulator, transform));
        } else {
            super(accumulator);
        }
    }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    @RO Int size.get() {
        if (Int origSize := original?.knownSize()) {
            return origSize;
        }
        return reified.size;
    }

    @Override
    conditional Orderer? ordered() {
        if (Collection<FromElement> original ?= original) {
            return original.ordered()
                    ? (True, Null)      // order is stable, but we cannot describe it post-transform
                    : False;
        }
        return reified.ordered();
    }

    @Override
    conditional Int knownSize() {
        // while the `?:` operator would be ideal here, the two types differ (and who wants to deal
        // with an unnecessary cast?)
        return original?.knownSize() : reified.knownSize();
    }
}