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
    protected function void(FromElement, Appender<Element>)? flatten;

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
        // this is an ugly class that buffers one FromElement's worth of flattened Elements; it has
        // the benefit of making all of the ways that the flatten function could "append" the data
        // be as cheap as possible to then iterate, by not doing any unnecessary transformations,
        // at the cost of the following complexity:
        class FlatMapIterator(Iterator<FromElement> unflattenedIterator, function void(FromElement, Appender<Element>) flatten)
                implements Iterator<Element>
                implements Appender<Element> {
            // ----- internal ----------------------------------------------------------------------

            private Iterator<FromElement> unflattenedIterator;
            private function void(FromElement, Appender<Element>) flatten;

            private enum Mode { None, Single, List, Iterator, EOF }
            private Mode mode = None;

            // various storage for the "next" element(s)
            private Element? e;
            private List<Element>? list;
            private Int index;
            private Int size;
            private Iterator<Element>? iter;

            // load the storage for the "next" element(s)
            void loadNext() {
                mode = None;
                while (FromElement unflattened := unflattenedIterator.next()) {
                    flatten(unflattened, this);
                    if (mode != None) {
                        return;
                    }
                }
                mode = EOF;
            }

            // ----- Iterator interface ------------------------------------------------------------

            @Override
            conditional Element next() {
                while (True) {
                    switch (mode) {
                    case None:
                        break;
                    case Single:
                        assert Element e ?= e;
                        loadNext();
                        return True, e;
                    case List:
                        if (index < size) {
                            return True, list?[index++] : assert;
                        }
                        break;
                    case Iterator:
                        if (Element e := (iter ?: assert).next()) {
                            return True, e;
                        }
                        break;
                    case EOF:
                        return False;
                    }
                    loadNext();
                }
            }

            @Override
            Boolean knownEmpty() = unflattenedIterator.knownEmpty();

            @Override
            conditional Int knownSize() = knownEmpty() ? (True, 0) : False;

            // ----- Appender interface ------------------------------------------------------------

            @Override
            Appender<Element> add(Element v) {
                mode = Single;
                e    = v;
                return this;
            }

            @Override
            Appender<Element> addAll(Iterable<Element> iterable) {
                if (iterable.is(List<Element>), iterable.indexed) {
                    mode  = List;
                    list  = iterable;
                    index = 0;
                    size  = iterable.size;
                } else {
                    mode = Iterator;
                    iter = iterable.iterator();
                }
                return this;
            }

            @Override
            Appender<Element> addAll(Iterator<Element> iterator) {
                mode = Iterator;
                iter = iterator;
                return this;
            }
        }
        return new FlatMapIterator(original?.iterator(), flatten?) : assert;
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