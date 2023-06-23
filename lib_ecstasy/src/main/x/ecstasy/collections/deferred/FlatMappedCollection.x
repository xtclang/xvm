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
    protected void postReifyCleanup() {
        flatten = Null;
        super();
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

            private enum Mode { None, Single, List, Buffer, Iterator, EOF }
            private Mode mode = None;

            // various storage for the "next" element(s)
            private Element? e;
            private List<Element>? list;
            private Int index;
            private Int size;
            private Element[]? buffer;
            private Iterator<Element>? iter;

            /*
             * @return a buffer for adding pending items to
             */
            Element[] ensureBuffer() {
                Element[] buffer;
                if (!(buffer ?= this.buffer)) {
                    buffer = new Element[];
                    this.buffer = buffer;
                }

                switch (mode) {
                case None:
                    break;

                case Single:
                    assert Element e ?= e;
                    if (buffer.size < 1) {
                        buffer.add(e);
                    } else {
                        buffer[0] = e;
                    }
                    size = 1;
                    break;

                case List:
                    assert List<Element> list ?= list;
                    assert index == 0;
                    // how many elements will fit into the buffer, vs. how much does the buffer need
                    // to expand?
                    Int space = buffer.size;
                    Int count = list.size;
                    if (space >= count) {
                        // simple copy into the buffer
                        for (Int i = 0; i < count; ++i) {
                            buffer[i] = list[i];
                        }
                    } else {
                        buffer.ensureCapacity(count);
                        // use the existing buffer space for the items that will fit
                        for (Int i = 0; i < space; ++i) {
                            buffer[i] = list[i];
                        }
                        // expand the buffer to hold the rest of the items
                        for (Int i = space; i < count; ++i) {
                            buffer.add(list[i]);
                        }
                    }
                    size = count;
                    break;

                case Buffer:
                    return buffer;

                case Iterator:
                    // this is weird (someone added an iterator and now they're adding a
                    // non-iterator) so we can support this inefficiently
                    assert Iterator<Element> iter ?= iter;
                    Int space = buffer.size;
                    Loop: for (Element e : iter) {
                        if (Loop.count < space) {
                            buffer[Loop.count] = e;
                        } else {
                            buffer.add(e);
                        }
                    }
                    this.iter = Null;
                    break;

                case EOF:
                    assert;
                }
                mode  = Buffer;
                list  = buffer;
                index = 0;
                return buffer;
            }

            /*
             * load the storage for the "next" element(s).
             */
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
                    case Buffer:
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
                switch (mode) {
                case None:
                    mode = Single;
                    e    = v;
                    break;
                case Single:
                case List:
                case Buffer:
                case Iterator:
                    ensureBuffer().add(v);
                    break;
                case EOF:
                    assert;
                }
                return this;
            }

            @Override
            Appender<Element> addAll(Iterable<Element> iterable) {
                switch (mode) {
                case None:
                    if (iterable.is(List<Element>), iterable.indexed) {
                        mode  = List;
                        list  = iterable;
                        index = 0;
                        size  = iterable.size;
                    } else {
                        mode = Iterator;
                        iter = iterable.iterator();
                    }
                    break;
                case Single:
                case List:
                case Buffer:
                    ensureBuffer().addAll(iterable);
                    break;
                case Iterator:
                    iter = iter?.concat(iterable.iterator()) : assert;
                    break;
                case EOF:
                    assert;
                }
                return this;
            }

            @Override
            Appender<Element> addAll(Iterator<Element> iterator) {
                switch (mode) {
                case None:
                    mode = Iterator;
                    iter = iterator;
                    break;
                case Single:
                case List:
                case Buffer:
                    ensureBuffer().addAll(iterator);
                    break;
                case Iterator:
                    iter = iter?.concat(iterator) : assert;
                    break;
                case EOF:
                    assert;
                }
                return this;
            }
        }
        return new FlatMapIterator(original?.iterator(), flatten?) : assert;
    }

    @Override
    protected void evaluateInto(Appender<Element> accumulator) {
        if (DeferredCollection<FromElement> nextDeferred := original.is(DeferredCollection<FromElement>),
                function void(FromElement, Appender<Element>) flatten ?= flatten) {
            class ApplyFlatten(Appender<Element> accumulator, function void(FromElement, Appender<Element>) flatten)
                    implements Appender<FromElement> {
                @Override Appender<FromElement> add(FromElement v) {
                    flatten(v, accumulator);
                    return this;
                }
            }
            nextDeferred.evaluateInto(new ApplyFlatten(accumulator, flatten));
        } else {
            super(accumulator);
        }
    }


    // ----- Collection interface ------------------------------------------------------------------

}