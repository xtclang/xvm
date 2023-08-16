/**
 * `DeferredCollection` is a base class for deferred results from various operation on a
 * `Collection`, such as `map()` and `filter()`.
 */
@Abstract class DeferredCollection<Element, FromElement>
        delegates Collection<Element>(reified) {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a DeferredCollection based on an original collection.
     *
     * @param original  the collection from which this collection's contents will be drawn
     */
    construct(Collection<FromElement> original) {
        this.original = original;
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * The Collection from which the contents of this Collection will be drawn, or `Null` after this
     * Collection has been reified (to allow memory to be collected).
     */
    protected Collection<FromElement>? original;

    /**
     * The cached reified copy of this Collection.
     */
    protected @Lazy(createReified) Collection<Element> reified;

    /**
     * @return the reified copy of this Collection to cache
     */
    protected Collection<Element> createReified() {
        assert Collection<FromElement> original ?= original;
        Collection<Element> reified = instantiateEmptyReified();
        evaluateInto(reified);
        postReifyCleanup();
        return reified;
    }

    /**
     * This is the method that creates the data structure that will hold the reified result.
     * Sub-classes can override this to use a specific data structure, or pre-size the data
     * structure accordingly.
     *
     * @return the empty data structure that will be filled to create the reified copy of the
     *         contents of this collection
     */
    protected Collection<Element> instantiateEmptyReified() {
        return new Element[];
    }

    /**
     * Clean up any references that can hold memory that were only necessary up until and including
     * the process of reification.
     */
    protected void postReifyCleanup()
        {
        original = Null;
        }

    /**
     * Indicate whether this Collection has already cached its reified contents.
     */
    protected Boolean alreadyReified.get() = original == Null;

    /**
     * This is the method that allows the contents of this Collection to be iterated, without
     * creating a reified copy of the data in this Collection. Each sub-class must provide an
     * implementation.
     *
     * @return an Iterator that provides the contents of this Collection based on the original
     *         Collection
     */
    protected @Abstract Iterator<Element> unreifiedIterator();

    /**
     * This method is used to efficiently reify a chain of DeferredCollection objects, such as with
     * a sequence of calls like `map(...).filter(...).flatMap(...).reduce(...).toArray()`.
     *
     * @param accumulator  the Appender to append all of the elements to from this
     *                     DeferredCollection
     */
    protected void evaluateInto(Appender<Element> accumulator) {
        if (alreadyReified) {
            Int count = reified.size;
            if (count > 0) {
                accumulator.ensureCapacity(count)
                           .addAll(reified);
            }
        } else {
            for (Element e : this) {
                accumulator.add(e);
            }
        }
    }


    // ----- Collection interface ------------------------------------------------------------------

    @Override
    Iterator<Element> iterator() {
        // assume that some percentage of DeferredCollections are created and then iterated exactly
        // once, in which case it's wasteful to realize the results; conversely, if multiple
        // iterations occur, then realize the results
        private Boolean iteratedAtLeastOnce = False;
        if (!iteratedAtLeastOnce && !alreadyReified) {
            iteratedAtLeastOnce = True;
            return unreifiedIterator();
        }
        return reified.iterator();
    }

    @Override
    @RO Boolean inPlace.get() = True;

    @Override
    conditional Int knownSize() {
        if (Collection<FromElement> original ?= original) {
            // this implementation only knows the size iff the original is known to be empty;
            // sub-classes that have more knowledge of the relationship between the size of the
            // original and the size of the reified result should override this method accordingly
            if (Int origSize := original.knownSize(), origSize == 0) {
                return True, 0;
                }
            return False;
        } else {
            return reified.knownSize();
        }
    }

    @Override
    @RO Boolean empty.get() {
        if (Int origSize := original?.knownSize(), origSize == 0) {
            return True;
        }
        return reified.empty;
    }

    @Override
    void forEach(function void (Element) process) {
        if (alreadyReified) {
            reified.forEach(process);
        } else {
            using (val iter = iterator()) {
                iter.forEach(process);
            }
        }
    }

    @Override
    conditional Element any(function Boolean(Element) match = _ -> True) {
        if (alreadyReified) {
            return reified.any(match);
        } else {
            using (val iter = iterator()) {
                return iter.untilAny(match);
            }
        }
    }

    @Override
    Boolean all(function Boolean(Element) match) {
        if (alreadyReified) {
            return reified.all(match);
        } else {
            using (val iter = iterator()) {
                return iter.whileEach(match);
            }
        }
    }

    @Override
    Collection<Element> filter(function Boolean(Element) match) {
        return alreadyReified
            ? reified.filter(match)
            : new FilteredCollection<Element>(this, match);
    }

    @Override
    <Result extends Collection<Element>> Result filter(function Boolean(Element) match,
                                                       Aggregator<Element, Result> collector) {
        if (alreadyReified) {
            return reified.filter(match, collector);
        } else {
            collector.Accumulator accum = collector.init();
            for (Element e : this) {
                if (match(e)) {
                    accum.add(e);
                }
            }
            return collector.reduce(accum);
        }
    }

    @Override
    (Collection<Element> matches, Collection<Element> misses) partition(function Boolean(Element) match) {
        if (alreadyReified) {
            return reified.partition(match);
        } else {
            PartitionedCollection<Element> matches = new PartitionedCollection(this, match);
            return matches, matches.inverse;
        }
    }

    @Override
    <Result extends Collection<Element>>
    (Result matches, Result misses) partition(function Boolean(Element)   match,
                                              Aggregator<Element, Result> collector) {
        if (alreadyReified) {
            return reified.partition(match, collector);
        } else {
            collector.Accumulator matches = collector.init();
            collector.Accumulator misses  = collector.init();
            for (Element e : this) {
                (match(e) ? matches : misses).add(e);
            }
            return collector.reduce(matches), collector.reduce(misses);
        }
    }

    @Override
    <Value> Collection<Value> map(function Value(Element) transform) {
        return alreadyReified
            ? reified.map(transform)
            : new MappedCollection<Value, Element>(this, transform);
    }

    @Override
    <Value, Result extends Collection<Value>>
            Result map(function Value(Element) transform, Aggregator<Value, Result> collector) {
        if (alreadyReified) {
            return reified.map(transform, collector);
        } else {
            collector.Accumulator accum = collector.init();
            for (Element e : this) {
                accum.add(transform(e));
            }
            return collector.reduce(accum);
        }
    }

    @Override
    <Value> Collection<Value> flatMap(function Iterable<Value>(Element) flatten) {
        // TODO CP need to alter delegates clause to not delegate this method
        return flatMap((e, dest) -> dest.addAll(flatten(e)));
    }

    @Override
    <Value, Result extends Collection<Value>>
            Result flatMap(function Iterable<Value>(Element) flatten,
                           Aggregator<Value, Result>         collector) {
        // TODO CP need to alter delegates clause to not delegate this method
        return flatMap((e, dest) -> dest.addAll(flatten(e)), collector);
    }

    @Override
    <Value> Collection<Value> flatMap(function void(Element, Appender<Value>) flatten) {
        return alreadyReified
            ? reified.flatMap(flatten)
            : new FlatMappedCollection<Value, Element>(this, flatten);
    }

    @Override
    <Value, Result extends Collection<Value>>
            Result flatMap(function void(Element, Appender<Value>) flatten,
                           Aggregator<Value, Result>               collector) {
        if (alreadyReified) {
            return reified.flatMap(flatten, collector);
        } else {
            collector.Accumulator accum = collector.init();
            for (Element e : this) {
                flatten(e, accum);
            }
            return collector.reduce(accum);
        }
    }

    // TODO CP 4x associate etc.

    @Override
    <Result> Result reduce(Result                           initial,
                           function Result(Result, Element) accumulate) {
        if (alreadyReified) {
            return reified.reduce(initial, accumulate);
        }

        // this is the default implementation from Collection; if we super(), we would instead go
        // go via delegation to reified, and we are trying not to reify (at least not the first time
        // through)
        Result result = initial;
        forEach(e -> {
            result = accumulate(result, e);
        });
        return result;
    }

    @Override
    <Result> Result reduce(Aggregator<Element, Result> aggregator) {
        if (alreadyReified) {
            return reified.reduce(aggregator);
        } else {
            aggregator.Accumulator accumulator = aggregator.init();
            if (Int count := knownSize()) {
                if (count == 0) {
                    return aggregator.reduce(accumulator);
                }
                accumulator.ensureCapacity(count);
            }
            for (Element e : this) {
                accumulator.add(e);
            }
            return aggregator.reduce(accumulator);
        }
    }

    @Override
    Set<Element> distinct() {
        return alreadyReified
            ? reified.distinct()
            : new DistinctCollection<Element>(this);
    }

    @Override
    <Result extends Collection<Element>> Result distinct(Aggregator<Element, Result> collector) {
        return reified.distinct(collector);
    }

    @Override
    Collection<Element> reify() = reified;

    @Override @Op("+") Collection<Element> add(Element value)                              = throw new ReadOnly();
    @Override @Op("+") Collection<Element> addAll(Iterable<Element> values)                = throw new ReadOnly();
    @Override Collection<Element> addAll(Iterator<Element> iter)                           = throw new ReadOnly();
    @Override conditional Collection<Element> addIfAbsent(Element value)                   = throw new ReadOnly();
    @Override @Op("-") Collection<Element> remove(Element value)                           = throw new ReadOnly();
    @Override @Op("-") Collection<Element> removeAll(Iterable<Element> values)             = throw new ReadOnly();
    @Override conditional Collection<Element> removeIfPresent(Element value)               = throw new ReadOnly();
    @Override (Collection<Element>, Int) removeAll(function Boolean(Element) shouldRemove) = throw new ReadOnly();
    @Override Collection<Element> retainAll(Iterable<Element> values)                      = throw new ReadOnly();
    @Override Collection<Element> clear()                                                  = throw new ReadOnly();
}