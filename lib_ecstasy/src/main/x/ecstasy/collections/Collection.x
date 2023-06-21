/**
 * `Collection` is the root of the tree of types for _container_ data structures. A _container_ is
 * an object that _contains_ other objects. Common examples of data structures that are containers
 * include: arrays, lists, sets, trees, bags, and dictionaries aka maps. (Note: The term "container
 * data type" is not related to Ecstasy's [Container] functionality.)
 *
 * The `Collection` interface represents any "bag" of values, each of which is called an _element_.
 * There are a number of generic capabilities and loose contracts defined by the Collection
 * interface itself, and specialization (with additional contractual constraints and
 * specializations) in several derived interfaces, including:
 *
 * * The distinct-values form of this interface is [Set], with the well-known example implementation
 *   [HashSet].
 *
 * * The consistently-append-ordered form of this interface is [List], with the well-known example
 *   implementations being the [Array] class and the [LinkedList] property annotation.
 *
 * * A related interface that is **not** derived from this Collection interface is the key/value
 *   (dictionary) interface [Map], which may itself be viewed as a `Collection` of
 *   [entries](Map.Entry).
 *
 * The Collection API supports both in-place mutation and _persistent_ data structures; from
 * Wikipedia: "a persistent data structure is a data structure that always preserves the previous
 * version of itself when it is modified. Such data structures are effectively immutable, as their
 * operations do not (visibly) update the structure in-place, but instead always yield a new updated
 * structure." If an implementation uses in-place mutation, then its [inPlace] metadata property
 * will be `True`; if an implementation generates a new Collection to represent the result of a
 * mutation, then its [inPlace] metadata property will be `False`. (Note: The term "persistent" is
 * **not** related to the well-known concept of persistent storage, e.g. "storage on disk"; to avoid
 * confusion, the term "persistent" is avoided in this API.)
 *
 * Collections should also support true immutability by carefully implementing the [Freezable]
 * interface. The goal of the interface is to efficiently support the transformation of a collection
 * to an immutable form, without accidentally making something immutable that needed to remain
 * mutable; in Ecstasy, immutable is _deep_ immutability, so making an object immutable will also
 * make everything that it can reach immutable as well. Implementations that support both in-place
 * and persistent modes should switch to the persistent mode when frozen; otherwise, they should
 * throw [ReadOnly] for mutating operations after the collection becomes immutable.
 *
 * To implement a read-only collection, one must implement at least the [size] property and the
 * [iterator()] method.
 */
interface Collection<Element>
        extends Iterable<Element>
        extends Appender<Element>
        extends Stringable {
    /**
     * An Orderer is a function that compares two elements for order.
     */
    typedef Element.Orderer as Orderer;


    // ----- abstract methods ----------------------------------------------------------------------

    /**
     * Determine the size of the `Collection`, which is the number of elements that are contained in
     * the `Collection`.
     */
    @Override
    @RO Int size;

    /**
     * Obtain an iterator over the elements in the `Collection`.
     *
     * @return an `Iterator` over each of the collection's elements
     */
    @Override
    Iterator<Element> iterator();


    // ----- metadata ------------------------------------------------------------------------------

    /**
     * Metadata: Are mutating operations on the collection processed in place, or do they result in
     * a new copy of the collection that incorporates any mutations? Any data structure that creates
     * a new copy to perform a mutation is called a _persistent_ data structure; that term is
     * generally avoided here because of the multiple meanings in software of the term "persistent".
     *
     * It is expected that all mutating operations that can not return a resulting collection will
     * check that `inPlace` is `True`, and otherwise throw a [ReadOnly] exception; one example is
     * when the `value` property on a [List.Cursor] is set.
     */
    @RO Boolean inPlace.get() = True;

    /**
     * Metadata: Is the collection maintained in a specific order? And if that order is a function
     * of the elements in the collection, what is the [Type.Orderer] that represents that ordering?
     *
     * @return True iff the element order within the collection is significant
     * @return (conditional) the Orderer that determines the order between two elements; `Null`
     *         indicates that an order is maintained, but not by comparison of elements, for example
     *         when a collection stores elements in the order that they are added
     */
    conditional Orderer? ordered() = False;

    /**
     * Metadata: Is the collection of a known size? The size is available from the [size] property,
     * but may require significant effort to compute; this metadata (similar to that available on
     * the [Iterator] interface) provides an indication of whether the size is "free" to obtain.
     *
     * @return True iff the `Collection` size is efficiently known
     * @return (conditional) the `Collection` size, if it is efficiently known
     */
    @Concurrent
    conditional Int knownSize() {
        // implementations of Collection that do not have a cached size of the collection should
        // override this method and return False when the size requires any calculation more
        // expensive than O(1)
        return True, size;
    }


    // ----- read operations -----------------------------------------------------------------------

    /**
     * Determine if the collection is empty.
     *
     * This is equivalent to the following code, but may be implemented more efficiently for
     * implementations (such as a linked list) that have a cost associated with calculating the
     * size:
     *
     *     return size == 0;
     */
    @Concurrent
    @RO Boolean empty.get() {
        // implementations of Collection that do not have a cached size of the collection should
        // override this method if a more efficient means of determining emptiness is available
        return size == 0;
    }

    /**
     * Determine if this collection contains the specified value.
     *
     * @param value  the value that may be present in this `Collection`
     *
     * @return `True` iff the specified value is present in this `Collection`
     */
    @Concurrent
    Boolean contains(Element value) {
        // this should be overridden by any implementation that has a structure that can do better
        // than an O(n) search, such as a sorted structure (binary search) or a hashed structure
        return iterator().untilAny(element -> element == value);
    }

    /**
     * Determine if the collection contains all of the specified values.
     *
     * @param values  another collection containing values to search for in this collection
     *
     * @return `True` iff the specified values all exist in this collection
     */
    Boolean containsAll(Collection! values) {
        // this.contains(values) is always True when there are no values to test, or when the passed
        // values collection is the same collection as this
        if (values.empty || &values == &this) {
            return True;
        }

        // this.contains(values) is always False when there are values to test and this is empty
        if (this.empty) {
            return False;
        }

        // if both collections are sorted by the same thing, then a "zipper" algorithm is O(N)
        if (Orderer? thisOrder := this.ordered(),
            Orderer? thatOrder := values.ordered(),
            thisOrder? == thatOrder?) {
            using (Iterator<Element> iterThat = values.iterator(),
                   Iterator<Element> iterThis = this  .iterator()) {
                assert Element valThat := iterThat.next();
                for (Element valThis : iterThis) {
                    switch (thisOrder(valThis, valThat)) {
                    case Lesser:
                        // the current element in "this" is not one that we are looking for
                        break;

                    case Equal:
                        // found it! advance to the next element in "that" to find
                        if (!(valThat := iterThat.next())) {
                            // we found everything!
                            return True;
                        }
                        break;

                    case Greater:
                        // "this" was missing the element from "that" that we were looking for
                        return False;
                    }
                }
            }

            // we ran out of elements before finding the element from "that"
            return False;
        }

        // assume that sets have O(N) time with fast e.g. O(1) look-ups
        // assume that sorted collections have O(N*logN) time with fast e.g. O(logN) look-ups
        // assume that optimizing for small collections is a negative return
        // use hashing as the optimization (requiring elements to be Hashable)
        if (!this.is(Set) && this.ordered() && size > 17 && Element.is(Type<Hashable>)) {
            // it's expensive to create a new HashSet for this purpose, but it turns an O(N^2)
            // operation into an O(2*N) problem
            return new HashSet<Element>(this).containsAll(values);
        }

        // brute force search for each value from the passed-in values collection
        using (val iter = values.iterator()) {
            return iter.whileEach(contains(_));
        }
    }

    /**
     * Perform the specified action for all elements in this collection.
     *
     * @param process  an action to perform on each element
     */
    void forEach(function void (Element) process) {
        for (Element e : this) {
            process(e);
        }
    }

    /**
     * Determine if any elements of the `Collection` match the provided criteria.
     *
     * @param match  a function that evaluates elements of this collection for inclusion
     *
     * @return True if any element matched the specified criteria
     * @return (conditional) the first element that matched the criteria
     */
    @Concurrent
    conditional Element any(function Boolean(Element) match = _ -> True) {
        using (val iter = iterator()) {
            return iter.untilAny(match);
        }
    }

    /**
     * Determine if all elements of the `Collection` match the provided criteria.
     *
     * @param match  a function that evaluates an element of the `Collection` for inclusion
     *
     * @return True if every single element in the `Collection` matched the specified criteria
     */
    @Concurrent
    Boolean all(function Boolean(Element) match) {
        using (val iter = iterator()) {
            return iter.whileEach(match);
        }
    }

    /**
     * Evaluate the contents of this `Collection` using the provided criteria, and produce a
     * resulting `Collection` that contains only the elements that match.
     *
     * @param match      a function that evaluates an element of the `Collection` for inclusion
     * @param collector  an optional [Aggregator] to use to collect the results
     *
     * @return the resulting `Collection` containing the elements that matched the criteria; the
     *         returned `Collection` may depend on this `Collection`, so [reify] the result if
     *         subsequent changes to this `Collection` must not alter the contents of the returned
     *         `Collection`
     *
     */
    @Concurrent
    <Result extends Collection!> Result filter(function Boolean(Element) match,
                                               Aggregator<Element, Result>? collector = Null) {
        if (collector == Null) {
            if (Int count := knownSize(), count == 0) {
                Element[] empty = [];
                return empty.as(Result);
            }
            return new deferred.FilteredCollection<Element>(this, match).as(Result);
        }

        collector.Accumulator accumulator = collector.init();
        if (&accumulator == &this) {
            accumulator = this.removeAll(e -> !match(e));
        } else {
            for (Element e : this) {
                if (match(e)) {
                    accumulator = accumulator.add(e);
                }
            }
        }
        return collector.reduce(accumulator);
    }

    /**
     * Partition the elements of the collection into those that match the provided criteria, and
     * those that do not.
     *
     * @param match      a function that evaluates an element of the `Collection` for inclusion
     * @param collector  an optional [Aggregator] to use to collect the results
     *
     * @return matches   the list of elements that match the provided criteria; the returned
     *                   `Collection` may depend on this `Collection`, so [reify] the result if
     *                   subsequent changes to this `Collection` must not alter the contents of the
     *                   returned `Collection`
     * @return misses    the list of elements that **do not** match the provided criteria; the
     *                   returned `Collection` may depend on this `Collection`, so [reify] the
     *                   result if subsequent changes to this `Collection` must not alter the
     *                   contents of the returned `Collection`
     */
    @Concurrent
    <Result extends Collection!> (Result matches, Result misses)
            partition(function Boolean(Element) match,
                      Aggregator<Element, Result>? collector = Null) {

        if (collector == Null) {
            Element[] matches = new Element[];
            Element[] misses  = new Element[];
            for (Element e : this) {
                (match(e) ? matches : misses).add(e);
            }
            // TODO CP defer
            return matches.as(Result), misses.as(Result);
        }

        Appender<Element> matches = collector.init();
        Appender<Element> misses  = collector.init();
        if (&matches == &this) {
            for (Element e : this) {
                if (!match(e)) {
                    misses.add(e);
                }
            }
            Result missResult = collector.reduce(misses);
            return collector.reduce(this.removeAll(missResult)), missResult;
        }

        if (&misses == &this) {
            for (Element e : this) {
                if (match(e)) {
                    matches.add(e);
                }
            }
            Result matchResult = collector.reduce(matches);
            return matchResult, collector.reduce(this.removeAll(matchResult));
        }

        for (Element e : this) {
            (match(e) ? matches : misses).add(e);
        }
        return collector.reduce(matches), collector.reduce(misses);
    }

    /**
     * Build a `Collection` that has one value "mapped from" each value in this `Collection`, using
     * the provided function.
     *
     * @param transform  a function that creates a "mapped" element from each element in this
     *                   `Collection`
     * @param collector  an optional [Aggregator] to use to collect the results
     *
     * @return the resulting `Collection` containing the elements that matched the criteria; the
     *         returned `Collection` may depend on this `Collection`, so [reify] the result if
     *         subsequent changes to this `Collection` must not alter the contents of the returned
     *         `Collection`
     */
    <Value, Result extends Collection!<Value>>
            Result map(function Value(Element)    transform,
                       Aggregator<Value, Result>? collector = Null) {
        if (collector == Null) {
            if (Int count := knownSize(), count == 0) {
                Value[] empty = [];
                return empty.as(Result);
            }
            return new deferred.MappedCollection<Value, Element>(this, transform).as(Result);
        }

        Iterator<Element> iter = iterator();

        Appender<Value> dest = collector.init(knownSize() ?: 0);
        if (&dest == &this) {
            // there is no way to optimize the in-place mapping on an amorphous Collection;
            // implementations of this interface that have a more optimal solution should override
            // this default behavior
            Element[] temp = new Element[knownSize()?](_ -> iter.take()) : new Element[].addAll(this);
            clear();
            iter = temp.iterator();
        }

        for (Element e : iter) {
            dest = dest.add(transform(e));
        }
        return collector.reduce(dest);
    }

    /**
     * Build a `Collection` that has zero or more elements "flattened from" each element of this
     * collection.
     *
     * @param transform  a function that provides an iter the "flattened" elements from each element in this
     *                   `Collection`
     * @param collector  an optional [Aggregator] to use to collect the results
     *
     * @return the resulting `Collection` containing the elements that matched the criteria; the
     *         returned `Collection` may depend on this `Collection`, so [reify] the result if
     *         subsequent changes to this `Collection` must not alter the contents of the returned
     *         `Collection`
     */
    @Concurrent
    <Value, Result extends Collection!<Value>>
            Result flatMap(function Iterable<Value>(Element) flatten,
                           Aggregator<Value, Result>? collector = Null) {
        return flatMap((e, dest) -> dest.addAll(flatten(e)), collector);
    }

    /**
     * Build a `Collection` that has zero or more elements "flattened from" each element of this
     * collection.
     *
     * @param transform  a function that creates the "flattened" elements from each element in this
     *                   `Collection`
     * @param collector  an optional [Aggregator] to use to collect the results
     *
     * @return the resulting `Collection` containing the elements that matched the criteria; the
     *         returned `Collection` may depend on this `Collection`, so [reify] the result if
     *         subsequent changes to this `Collection` must not alter the contents of the returned
     *         `Collection`
     */
    @Concurrent
    <Value, Result extends Collection!<Value>>
            Result flatMap(function void(Element, Appender<Value>) flatten,
                           Aggregator<Value, Result>? collector = Null) {

        if (collector == Null) {
            if (Int count := knownSize(), count == 0) {
                Value[] empty = [];
                return empty.as(Result);
            }
            return new deferred.FlatMappedCollection<Value, Element>(this, flatten).as(Result);
        }

        Appender<Value>     result  = collector.init();
        Boolean             inPlace = &this == &result;
        Appender<Value>     dest    = inPlace ? new Value[] : result;
        forEach(e -> flatten(e, dest));
        if (inPlace) {
            // the results were temporarily held in a temporary array; since the collector
            // specified that "this" is the destination, we need to clear "this" and copy the
            // temporary results here
            result = clear().as(Collection<Value>).addAll(dest.as(Value[]));
        }
        return collector.reduce(result);
    }

    /**
     * Build a distinct [Set] of elements found in this collection.
     *
     * @param collector  an optional [Aggregator] to use to collect the results
     *
     * @return the resulting `Collection` containing the distinct set of elements
     */
    @Concurrent
    <Result extends Collection!<Element>>
            Result distinct(Aggregator<Element, Result>? collector = Null) {

        if (collector == Null) {
            if (Int count := knownSize(), count == 0) {
                Element[] empty = [];
                return empty.as(Result);
            }
            return new deferred.DistinctCollection<Element>(this).as(Result);
        }

        Collection<Element> src  = this;
        Appender<Element>   dest = collector.init();
        if (&src == &dest) {
            // the source and destination are the same collection; it is non-trivial to "uniquify"
            // in place, when all we have to work with is the set of methods on Collection, so
            // create a temporary copy and do the work there
            src  = new ListSet<Element>(this);  // note: src was this
            dest = this.clear();                // note: dest is this
        }

        if (src.is(Set<Element>) || dest.is(Set<Element>)) {
            dest = dest.addAll(this);
        } else if (dest.is(Collection<Element>)) {
            for (Element e : this) {
                dest := dest.addIfAbsent(e);
            }
        } else {
            // inefficient but correct
            dest = dest.addAll(new ListSet(this));
        }
        return collector.reduce(dest);
    }

    /**
     * Reduce this collection of elements to a result value using the provided function. This
     * operation is also called a _folding_ operation.
     *
     * @param initial     the initial value to start accumulating from
     * @param accumulate  the function that will be used to accumulate elements into a result
     *
     * @return the result of the reduction
     */
    @Concurrent
    <Result> Result reduce(Result                           initial,
                           function Result(Result, Element) accumulate) {
        Result result = initial;
        forEach(e -> {
            result = accumulate(result, e);
        });
        return result;
    }

    /**
     * Use an [Aggregator] to analyze the contents of the collection to produce a single result,
     * thus _reducing_ the collection to a single value.
     *
     * @param aggregator  the mechanism for the reduction
     */
    @Concurrent
    <Result> Result reduce(Aggregator<Element, Result> aggregator) {
        Appender<Element> accumulator = aggregator.init();
        accumulator.addAll(this);
        return aggregator.reduce(accumulator);
    }

    /**
     * Create a [Map] from the contents of this `Collection` by transforming each element of the
     * collection into a key and value.
     *
     * @param transform  the function that transforms an Element into a Key and a Value
     * @param dest       the optional map to contribute to
     *
     * @return the resulting `Map`
     */
    @Concurrent
    <Key, Value> Map<Key,Value> associate(function (Key, Value) (Element) transform,
                                          Map<Key,Value>?                 dest = Null) {// TODO CP replace with a collector
        Map<Key, Value> map = dest ?: new ListMap();
        forEach(e -> {
            (Key k, Value v) = transform(e);
            map.put(k, v);
        });
        return map;
    }

    /**
     * Create a [Map] from the contents of this `Collection` by using each element as a value in the
     * resulting map, and obtaining a corresponding key for that element using the provided
     * function. In the case that the same key is generated for more than one element, then the last
     * element with the same key will be present in the resulting map, having replaced any
     * previously existing map entry for the same key.
     *
     * @param keyFor  the function that provides a `Key` for each `Element`
     * @param dest    the optional `Map` to contribute to
     *
     * @return the resulting `Map`
     */
    @Concurrent
    <Key> Map<Key, Element> associateBy(function Key(Element) keyFor,
                                        Map<Key, Element>?    dest = Null) {// TODO CP replace with a collector
        return associate(e -> {return keyFor(e), e;}, dest);
    }

    /**
     * Create a [Map] from the contents of this `Collection` by using each element as a key in the
     * resulting map, and obtaining a corresponding value for that element using the provided
     * function. In the case that multiple elements in the collection are identical, then the last
     * encountered identical element and its corresponding value will be present in the resulting
     * map, having replaced any previously existing map entry for the same key.
     *
     * @param valueFor  the function that provides a `Value` for each `Element`
     * @param dest      the optional map to contribute to
     *
     * @return the resulting `Map`
     */
    @Concurrent
    <Value> Map<Element, Value> associateWith(function Value(Element) valueFor,
                                              Map<Element, Value>?    dest = Null) {// TODO CP replace with a collector
        return associate(e -> {return e, valueFor(e);}, dest);
    }

    /**
     * Create a [Map] from the contents of this `Collection` by evaluating each element to obtain a
     * key for that element using the provided function, and then placing that element into a
     * collection that is associated with that key inside the resulting map. In the case that the
     * same key is generated for more than one element, then **all** of those elements will be
     * added to the collection associated with that key. The behavior of this method is conceptually
     * the inverse of the [flatMap] method.
     *
     * @param keyFor  the function that provides a `Key` for each `Element`
     * @param dest    the optional map to contribute to
     *
     * @return the resulting `Map` of collections of elements
     */
    @Concurrent
    <Key> Map<Key, Collection!<Element>> groupBy(function Key(Element)           keyFor,
                                                 Map<Key, Collection!<Element>>? dest   = Null) {// TODO CP replace with a collector
        Map<Key, Collection<Element>> map = dest ?: new ListMap();
        forEach(e -> {
            map.computeIfAbsent(keyFor(e), () -> new ListSet<Element>()).add(e);
        });
        return map;
    }

    /**
     * Create a [Map] from the contents of this `Collection` by using each element as a key, and
     * using one of the two specified functions to either create an initial value for that key, or
     * for modifying the value that is already associated with that key.
     *
     * For example, to count the occurrences of each element in a collection, this produces a map
     * keyed by the element, with the corresponding value being the count of occurrences:
     *
     *      Collection<String> bag    = ...
     *      Map<String, Int>   counts = bag.groupWith((_, c) -> c+1, (_) -> 1);
     *
     * @param update  a function that affects the entry in the map corresponding to each element
     * @param dest    the optional map to contribute to
     *
     * @return the resulting `Map`
     */
    @Concurrent
    <Value> Map<Element, Value> groupWith(function Value(Element, Value) accumulate,
                                          function Value(Element)        initial,
                                          Map<Element, Value>?           dest = Null) { // TODO CP replace with a collector
        Map<Element, Value> map = dest ?: new ListMap();
        forEach(e -> {
            map.process(e, entry -> {
                entry.value = entry.exists
                        ? accumulate(e, entry.value)
                        : initial(e);
                return Null;
            });
        });
        return map;
    }

    /**
     * Create a sorted `List` from this `Collection`.
     *
     * @param orderer  an optional [Type.Orderer] to control the sort order; `Null` means to use the
     *                 element type's natural order
     *
     * @return a sorted list
     *
     * @throws UnsupportedOperation  if no [Type.Orderer] is provided and [Element] is not
     *                               [Orderable]
     */
    @Concurrent
    List<Element> sorted(Orderer? orderer = Null) { // TODO CP add collector
        return toArray(Mutable).sorted(orderer, True);
    }

    /**
     * Obtain a `Collection` that has the same contents as this `Collection`, but which has two
     * additional attributes:
     *
     * * First, if this `Collection` is dependent on another `Collection` for its storage such that
     *   the other `Collection` may contain additional data that is not present in this
     *   `Collection`, then the resulting `Collection` will no longer be dependent on that other
     *   `Collection` for its storage (i.e. it will hold its own copy of its data, and release its
     *   reference to the other `Collection`, which may allow memory to be reclaimed);
     *
     * * Second, if this `Collection` is dependent on another `Collection` such that changes to this
     *   `Collection` may be visible in the other `Collection`, and/or that changes to the other
     *   `Collection` may be visible in this `Collection`, then the resulting `Collection` will no
     *   longer have that attribute, i.e. changes to the resulting `Collection` will not be visible
     *   in the other `Collection`, nor will changes to the other `Collection` be visible in the
     *   resulting `Collection`. This guarantee is essential when a `Collection` may have resulted
     *   from an operation (such as the [map] method) that may defer most of the work of the
     *   operation by holding onto the original `Collection` -- which may itself be subject to later
     *   mutation -- and when the result itself is held long enough that subsequent mutation of the
     *   original `Collection` may occur.
     *
     * This contract is designed to allow `Collection` implementations to take advantage of lazy
     * and deferred behavior in order to achieve time and space optimizations.
     *
     * @return a reified Collection, which may be `this`
     */
    @Concurrent
    Collection reify() {
        // this method must be overridden by any implementing Collection that may return a view of
        // itself as a Collection, such that mutations to one might be visible from the other
        return this;
    }


    // ----- write operations ----------------------------------------------------------------------

    /**
     * Add the specified value to this collection.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param value  the value to store in the collection
     *
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element addition
     */
    @Override
    @Op("+") Collection add(Element value) {
        throw new ReadOnly();
    }

    /**
     * Add all of the passed values to this collection.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param values  an iterable source providing values to add to this collection
     *
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element addition
     */
    @Override
    @Op("+") Collection addAll(Iterable<Element> values);

    /**
     * Add all of the passed values to this collection.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param values  an iterable source providing values to add to this collection
     *
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element addition
     */
    @Override
    @Concurrent
    Collection addAll(Iterator<Element> iter) {
        Collection collection = this;
        iter.forEach(value -> {
            collection = collection.add(value);
        });
        return collection;
    }

    /**
     * Add the specified value to this collection if it is not already present in the collection.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param value  the value to store in the collection
     *
     * @return `True` iff the collection did not contain the specified value, and now does
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element addition
     */
    @Concurrent
    conditional Collection addIfAbsent(Element value) {
        if (contains(value)) {
            return False;
        }

        return True, add(value);
    }

    /**
     * Remove the specified value from this collection.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param value  the value to remove from this collection
     *
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element removal
     */
    @Op("-") Collection remove(Element value) {
        throw new ReadOnly();
    }

    /**
     * Remove all of the specified values from this collection.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param values  an iterable source providing values to remove from this collection
     *
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element removal
     */
    @Concurrent
    @Op("-") Collection removeAll(Iterable<Element> values) {
        if (&values == &this) {
            return clear();
        }

        // this naive implementation is likely to be overridden in cases where optimizations can be
        // made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered; it must obviously be overridden for non-mutable collections
        Collection result = this;
        values.iterator().forEach(value -> {
            result = result.remove(value);
        });
        return result;
    }

    /**
     * Remove the specified value from this collection, reporting back to the caller whether or not
     * the value was found and removed.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param value  the value to remove from this collection
     *
     * @return True iff the specified value existed in the collection, and has been removed
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element removal
     */
    @Concurrent
    conditional Collection removeIfPresent(Element value) {
        if (contains(value)) {
            return True, remove(value);
        }

        return False;
    }

    /**
     * For each value in the collection, evaluate it using the specified function, removing each
     * value for which the specified function evaluates to `True`.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param shouldRemove  a function used to filter this collection, returning `False` for each
     *                      value of this collection that should be removed
     *
     * @return the resulting collection, which is always `this` for an in-place collection
     * @return the number of elements removed
     *
     * @throws ReadOnly  if the collection does not support element removal
     */
    @Concurrent
    (Collection, Int) removeAll(function Boolean (Element) shouldRemove) {
        Element[]? values = Null;
        forEach(value -> {
            if (shouldRemove(value)) {
                values = (values ?: new Element[]) + value;
            }
        });

        if (values == Null) {
            return this, 0;
        }

        return removeAll(values), values.size;
    }

    /**
     * Remove all of the values from this collection that do **not** occur in the specified
     * collection.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @param values  the values to retain in this collection
     *
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element removal
     */
    @Concurrent
    Collection retainAll(Iterable<Element> values) {
        if (&values == &this) {
            return this;
        }

        // this default implementation is likely to be overridden in cases where optimizations can
        // be made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered; it must obviously be overridden for non-mutable collections
        Collection<Element> retain = values.is(Collection<Element>) ? values : new ListSet(values);
        return removeAll(value -> !retain.contains(value));
    }

    /**
     * Remove all values from the collection.
     *
     * If this collection is [inPlace], then the mutation occurs to this collection; otherwise, a
     * new collection with the mutation applied is returned.
     *
     * @return the resulting collection, which is always `this` for an in-place collection
     *
     * @throws ReadOnly  if the collection does not support element removal
     */
    @Concurrent
    Collection clear() {
        // this default implementation is likely to always be overridden for efficiency
        return removeAll(value -> True);
    }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength(
            String                    sep    = ", ",
            String?                   pre    = "[",
            String?                   post   = "]",
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null) {
        Int elementCount = size;
        Int count        = (pre?.size : 0) + (post?.size : 0);

        if (elementCount == 0) {
            return count;
        }

        Int displayCount = limit?.notGreaterThan(elementCount) : elementCount;

        if (render == Null && Element.is(Type<Stringable>)) {
            val iter = this.iterator();
            if (displayCount < elementCount) {
                iter   = iter.limit(displayCount);
                count += trunc.size;
            }
            for (Element e : iter) {
                count += e.estimateStringLength();
            }
        } else {
            // random educated guess for an element size, because we don't want to actually render
            // each element at this point
            count += displayCount * 4;
        }

        return count + (displayCount-1) * sep.size;
    }

    /**
     * Append the contents of the `Collection` to the specified buffer.
     *
     * @param buf     the buffer to append to
     * @param sep     the separator string that will be placed between elements
     * @param pre     the string to precede the elements
     * @param post    the string to follow the elements
     * @param limit   the maximum number of elements to include in the string
     * @param trunc   the string that indicates that the maximum number of elements was exceeded
     * @param render  the optional function to use to render each element to a string
     *
     * @return the buffer
     */
    @Override
    Appender<Char> appendTo(
            Appender<Char>            buf,
            String                    sep    = ", ",
            String?                   pre    = "[",
            String?                   post   = "]",
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null) {
        pre?.appendTo(buf);

        function Appender<Char>(Element) appendElement = switch () {
            case render != Null              : (e -> render(e).appendTo(buf));
            case Element.is(Type<Stringable>): (e -> e.appendTo(buf));
            default: (e -> e.is(Stringable) ? e.appendTo(buf) : buf.addAll(e.toString()));
        };

        if (limit == Null || limit < 0) {
            limit = MaxValue;
        }

        Loop: for (Element e : this) {
            if (!Loop.first) {
                sep.appendTo(buf);
            }

            if (Loop.count >= limit) {
                trunc.appendTo(buf);
                break;
            }

            appendElement(e);
        }

        post?.appendTo(buf);
        return buf;
    }


    // ----- equality ------------------------------------------------------------------------------

    /**
     * Two collections are equal iff they are they contain the same values.
     */
    static <CompileType extends Collection>
            Boolean equals(CompileType collection1, CompileType collection2) {
        // they must be of the same arity
        Int size = collection1.size;
        if (collection2.size != size) {
            return False;
        }

        // empty collections are considered equal
        if (size == 0) {
            return True;
        }

        // check if a once-through zipper algorithm can be used
        Boolean sameOrder = size == 1;
        if (!sameOrder,
                Collection<CompileType.Element>.Orderer? order1 := collection1.ordered(),
                Collection<CompileType.Element>.Orderer? order2 := collection2.ordered(),
                order1? == order2?) {
            sameOrder = True;
        }

        if (sameOrder) {
            // if either is sorted, then both must be in the same order;
            // the collections were of the same arity, so the second iterator shouldn't run out
            // before the first
            Iterator<CompileType.Element> iter1 = collection1.iterator();
            Iterator<CompileType.Element> iter2 = collection2.iterator();
            for (val value1 : iter1) {
                assert val value2 := iter2.next();
                if (value1 != value2) {
                    return False;
                }
            }

            // the collections were of the same arity, so the first iterator shouldn't run out
            // before the second
            return !iter2.next();
        }

        // in theory, sets may execute in O(N) time
        if (collection1.is(Set<CompileType.Element>)) {
            return collection1.containsAll(collection2);
        } else if (collection2.is(Set<CompileType.Element>)) {
            return collection2.containsAll(collection1);
        }

        // if the type is hashable, then build a HashSet, and if its size is the same as before,
        // then the contents are unique; it looks expensive, but this optimizes to O(2*N)
        if (CompileType.Element.is(Type<Hashable>)) {
            Map<CompileType.Element, Int> map = collection1.groupWith(
                    (_, n) -> (n + 1),
                    (_) -> 1,
                    new HashMap());
            if (map.size == size) {
                return collection1.containsAll(collection2);
            } else {
                return collection2.any(e -> map.process(e, entry -> {
                    if (!entry.exists) {
                        // found no matching element in collection1
                        return True;
                    }

                    Int oldValue = entry.value;
                    if (oldValue <= 1) {
                        entry.delete();
                    } else {
                        entry.value = oldValue-1;
                    }
                    return False;
                }));
            }
        }

        // this is text-book inefficiency; we're comparing two bags of non-Hashable values
        enum NonExistent {NotAValue}
        typedef (CompileType.Element | NonExistent) as Remnant;
        Remnant[] remnants = new Array(Mutable, collection1);

        for (CompileType.Element value : collection2) {
            if (Int i := remnants.indexOf(value)) {
                remnants[i] = NotAValue;
            } else {
                return False;
            }
        }
        return True;
    }
}