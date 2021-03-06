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
 *
 * TODO is it possible to "extends conditional NumericAggregator<Element extends Number>"
 */
interface Collection<Element>
        extends Iterable<Element>
        extends Appender<Element>
    {
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
    @RO Boolean inPlace.get()
        {
        return True;
        }

    /**
     * An Orderer is a function that compares two elements for order.
     */
    typedef function Ordered (Element, Element) Orderer;

    /**
     * An [Orderer] for elements of this Collection that will order the elements in their "natural"
     * order, which is the order defined by the [Orderable] implementation on the Element type
     * itself. If the Element type is not `Orderable`, then the `naturalOrderer` is `Null`.
     */
    @RO Orderer? naturalOrderer.get()
        {
        return Element.is(Type<Orderable>) ? ((Element e1, Element e2) -> e1 <=> e2) : Null;
        }

    /**
     * Metadata: Is the collection maintained in a specific order? And if that order is a function
     * of the elements in the collection, what is the [Orderer] that represents that ordering?
     *
     * @return True iff the element order within the collection is significant
     * @return (conditional) the [Orderer] that determines the order between two elements; `Null`
     *         indicates that the order is maintained, but not by comparison of elements, such as
     *         when elements are stored in the order in which they are added to the collection
     */
    conditional Orderer? orderedBy()
        {
        return False;
        }

    /**
     * Metadata: Is the collection of a known size? The size is available from the [size] property,
     * but may require significant effort to compute; this metadata (similar to that available on
     * the [Iterator] interface) provides an indication of whether the size is "free" to obtain.
     *
     * @return True iff the `List` size is efficiently known
     * @return (conditional) the `List` size, if it is efficiently known
     */
    conditional Int knownSize()
        {
        // implementations of Collection that do not have a cached size of the collection should
        // override this method
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
    @RO Boolean empty.get()
        {
        return size == 0;
        }

    /**
     * Determine if this collection contains the specified value.
     *
     * @param value  the value that may be present in this `Collection`
     *
     * @return `True` iff the specified value is present in this `Collection`
     */
    Boolean contains(Element value)
        {
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
    Boolean containsAll(Collection! values)
        {
        // this.contains(values) is always True when there are no values to test
        if (values.empty)
            {
            return True;
            }

        // this.contains(values) is always False when there are values to test and this is empty
        if (this.empty)
            {
            return False;
            }

        // if both collections are sorted by the same thing, then a "zipper" algorithm is O(N)
        if (Orderer? thisOrder := this.orderedBy(), Orderer? thatOrder := values.orderedBy(),
                thisOrder? == thatOrder?)
            {
            Iterator<Element> iterThat = values.iterator();
            Iterator<Element> iterThis = this  .iterator();
            assert Element    valThis := iterThis.next();
            for (val valThat : iterThat)
                {
                switch (thisOrder(valThis, valThat))
                    {
                    case Lesser:
                        if (valThis := iterThis.next())
                            {
                            break;
                            }
                        else
                            {
                            return False;
                            }

                    case Equal:
                        if (valThat := iterThat.next())
                            {
                            break;
                            }
                        else
                            {
                            return True;
                            }

                    case Greater:
                        return False;
                    }
                }
            return True;
            }

        // assume that sets have O(N) time with fast e.g. O(1) look-ups
        // assume that sorted collections have O(N*logN) time with fast e.g. O(logN) look-ups
        // assume that optimizing for small collections is a negative return
        // use hashing as the optimization (requiring elements to be Hashable)
        if (!this.is(Set) && this.orderedBy() && size > 17 && Element.is(Type<Hashable>))
            {
            // it's expensive to create a new HashSet for this purpose, but it turns an O(N^2)
            // operation into an O(2*N) problem
            return new HashSet<Element>(this).containsAll(values);
            }

        // brute force search for each value from the passed-in values collection
        return values.iterator().whileEach(contains(_));
        }

    /**
     * Determine if any elements of the `Collection` match the provided criteria.
     *
     * @param match  a function that evaluates elements of this collection for inclusion
     *
     * @return True if any element matched the specified criteria
     * @return (conditional) the first element that matched the criteria
     */
    conditional Element any(function Boolean(Element) match = _ -> True)
        {
        return iterator().untilAny(match);
        }

    /**
     * Determine if all elements of the `Collection` match the provided criteria.
     *
     * @param match  a function that evaluates an element of the `Collection` for inclusion
     *
     * @return True if every single element in the `Collection` matched the specified criteria
     */
    Boolean all(function Boolean(Element) match)
        {
        return iterator().whileEach(match);
        }

    /**
     * Evaluate the contents of this `Collection` using the provided criteria, and produce a
     * resulting `Collection` that contains only the elements that match.
     *
     * @param match  a function that evaluates an element of the `Collection` for inclusion
     * @param dest   an optional `Collection` to collect the results in; pass `this` collection to
     *               filter out the values "in place"
     *
     * @return the resulting `Collection` containing the elements that matched the criteria
     */
    Collection! filter(function Boolean(Element) match,
                       Collection!?              dest  = Null)
        {
        if (dest == Null)
            {
            dest = new Element[];       // TODO replace with deferred-filter collection
            }
        else if (&dest == &this)
            {
            return this.removeAll(e -> !match(e));
            }

        for (Element e : this)
            {
            if (match(e))
                {
                dest.add(e);
                }
            }
        return dest;
        }

    /**
     * Partition the elements of the collection into those that match the provided criteria, and
     * those that do not.
     *
     * @param match      a function that evaluates an element of the `Collection` for inclusion
     * @param trueList   an optional `List` to collect the matching results in
     * @param falseList  an optional `List` to collect the non-matching results in
     *
     * @return trueList   the list of elements that match the provided criteria
     * @return falseList  the list of elements that **do not** match the provided criteria
     */
    (List<Element> trueList, List<Element> falseList) partition(function Boolean(Element) match,
                                                                List<Element>? trueList  = Null,
                                                                List<Element>? falseList = Null)
        {
        trueList  ?:= new Element[];
        falseList ?:= new Element[];
        for (Element e : this)
            {
            (match(e) ? trueList : falseList).add(e);
            }
        return trueList, falseList;
        }

    /**
     * Build a `Collection` that has one value "mapped from" each value in this `Collection`, using
     * the provided function.
     *
     * @param transform  a function that creates a "mapped" element from each element in this
     *                   `Collection`
     * @param dest       an optional `Collection` to collect the results in; pass `this` collection
     *                   to map the values "in place"
     *
     * @return the resulting `Collection` containing the elements that matched the criteria
     */
    <Result> Collection!<Result> map(function Result(Element) transform,
                                     Collection!<Result>?     dest      = Null)
        {
        Iterator<Element> iter = iterator();

        // there is no way to optimize the in-place mapping on an amorphous Collection;
        // implementations of this interface should replace this default behavior
        if (&dest == &this)
            {
            Result[] results = new Result[size](_ -> transform(iter.take()));
            clear();
            addAll(results.as(List<Element>));
            assert dest != Null;
            return dest;
            }

        if (dest == Null)
            {
            // TODO replace with deferred-map collection?
            return new Result[size](_ -> transform(iter.take()));
            }

        for (Element e : iter)
            {
            dest.add(transform(e));
            }
        return dest;
        }

    /**
     * Build a `Collection` that has some number of elements "flattened from" each element of this
     * collection.
     *
     * @param transform  a function that creates the "flattened" elements from each element in this
     *                   `Collection`
     * @param dest       an optional `Collection` to collect the results in; "in place" flat mapping
     *                   is not supported
     *
     * @return the resulting `Collection` containing the elements that matched the criteria
     */
    <Result> Collection!<Result> flatMap(function Iterable<Result>(Element) flatten,
                                         Collection!<Result>?               dest    = Null)
        {
        assert:arg &dest != &this;

        dest ?:= new Result[];
        for (Element e : this)
            {
            dest.addAll(flatten(e));
            }
        return dest;
        }

    /**
     * Build a distinct [Set] of elements found in this collection.
     *
     * @param dest  an optional `Set` to collect the results in
     *
     * @return the resulting `Set` containing the distinct set of elements
     */
    Set<Element> distinct(Set<Element>? dest = Null)
        {
        if (dest == Null)
            {
            return this.is(Set<Element>)
                    ? this
                    : new ListSet(this); // TODO replace with deferred-distinct set ???
            }

        return &dest == &this ? dest : dest.addAll(this);
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
    <Result> Result reduce(Result                           initial,
                           function Result(Result, Element) accumulate)
        {
        Result result = initial;
        for (Element e : this)
            {
            result = accumulate(result, e);
            }
        return result;
        }

    /**
     * A customizable, parallelizable reduction state machine. The design of the API allows the
     * `Reducer` to be stateless, which allows a reducer to be immutable (for example, implemented
     * as a `const`) and passed efficiently to multiple services, if parallel reduction is desired.
     *
     * Implementations designed for parallel reduction should either be a `service` or immutable
     * (such as a `const`), and should produce Accumulator instances that are immutable or that
     * implement Freezable.
     */
    static interface Reducer<Element, Result>
        {
        /**
         * Metadata: Is this Reducer capable of being used in parallel? A Reducer that can be used
         * used in parallel across multiple services should override this to return `True`.
         */
        @RO Boolean parallel.get()
            {
            return False;
            }

        /**
         * The Reducer produces one or more Accumulator objects to collect partial results.
         */
        typedef Appender<Element> Accumulator;

        /**
         * Generate an intermediate, mutable data structure that will accumulate intermediate
         * information as part of the `reduce` implementation. Called at least once; called more
         * than once if the reduction is performed in parallel.
         *
         * @return an `Accumulator` that will be used to accumulate values of the `Collection` that
         *         is being reduced
         */
        Accumulator init();

        /**
         * Combine multiple `Accumulator`s into one `Accumulator`.
         *
         * @param accumulators  two or more `Accumulator` objects produced by this `Reducer`
         *
         * @return a single `Accumulator` that represents all of the passed-in information; this
         *         result may reuse one of the passed-in objects
         */
        Accumulator combine(Iterable<Accumulator> accumulators);

        /**
         * Extract the result from the provided `Accumulator`. This is the last step of the
         * reduction process.
         *
         * @param accumulator  the `Accumulator` to extract the result from
         *
         * @return the result of the reduction process
         */
        Result finalize(Accumulator accumulator);
        }

    /**
     * Analyze the contents of the collection to produce a single result, thus _reducing_ the
     * collection to a single value.
     *
     * @param reducer  the mechanism for the reduction
     */
    <Result> Result reduce(Reducer<Element, Result> reducer)
        {
        Appender<Element> accumulator = reducer.init();
        accumulator.addAll(this);
        return reducer.finalize(accumulator);
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
    <Key, Value> Map<Key,Value> associate(function (Key, Value) (Element) transform,
                                          Map<Key,Value>?                 dest = Null)
        {
        Map<Key, Value> map = dest ?: new ListMap();
        for (Element e : this)
            {
            (Key k, Value v) = transform(e);
            map.put(k, v);
            }
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
    <Key> Map<Key, Element> associateBy(function Key(Element) keyFor,
                                        Map<Key, Element>?    dest = Null)
        {
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
    <Value> Map<Element, Value> associateWith(function Value(Element) valueFor,
                                              Map<Element, Value>?    dest = Null)
        {
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
    <Key> Map<Key, Collection!<Element>> groupBy(function Key(Element)           keyFor,
                                                 Map<Key, Collection!<Element>>? dest   = Null)
        {
        Map<Key, Collection<Element>> map = dest ?: new ListMap();
        for (Element e : this)
            {
            map.computeIfAbsent(keyFor(e), () -> new ListSet<Element>()).add(e);
            }
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
    <Value> Map<Element, Value> groupWith(function Value(Element, Value) accumulate,
                                          function Value(Element)        initial,
                                          Map<Element, Value>?           dest = Null)
        {
        Map<Element, Value> map = dest ?: new ListMap();
        for (Element e : this)
            {
            map.process(e, entry ->
                {
                entry.value = entry.exists
                        ? accumulate(e, entry.value)
                        : initial(e);
                return Null;
                });
            }
        return map;
        }

    /**
     * Create a sorted `List` from this `Collection`.
     *
     * @param orderer  an optional [Orderer] to control the sort order; `Null` means to use the
     *                 element type's natural order
     *
     * @return a sorted list
     *
     * @throws UnsupportedOperation  if no [Orderer] is provided and [Element] is not [Orderable]
     */
    List<Element> sorted(Orderer? orderer = Null)
        {
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
    Collection reify()
        {
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
    @Op("+") Collection add(Element value)
        {
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
    Collection addAll(Iterator<Element> iter);

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
    conditional Collection addIfAbsent(Element value)
        {
        if (contains(value))
            {
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
    @Op("-")
    Collection remove(Element value)
        {
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
    @Op("-")
    Collection removeAll(Iterable<Element> values)
        {
        // this naive implementation is likely to be overridden in cases where optimizations can be
        // made with knowledge of either this collection and/or the passed in values, for example
        // if both are ordered; it must obviously be overridden for non-mutable collections
        Collection result = this;
        for (Element value : values)
            {
            result = result.remove(value);
            }
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
    conditional Collection removeIfPresent(Element value)
        {
        if (contains(value))
            {
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
     (Collection, Int) removeAll(function Boolean (Element) shouldRemove)
        {
        Element[]? values = Null;
        for (Element value : this)
            {
            if (shouldRemove(value))
                {
                values = (values ?: new Element[]) + value;
                }
            }

        if (values == Null)
            {
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
    Collection retainAll(Iterable<Element> values)
        {
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
    Collection clear()
        {
        // this default implementation is likely to always be overridden for efficiency
        return removeAll(value -> True);
        }


    // ----- String formatting ---------------------------------------------------------------------

    /**
     * Join the contents of the `Collection` together using an `Appender`.
     *
     * @param buf     the optional `Appender` to append to
     * @param sep     the separator string that will be placed between elements
     * @param pre     the string to precede the elements
     * @param post    the string to follow the elements
     * @param limit   the maximum number of elements to include in the string
     * @param trunc   the string that indicates that the maximum number of elements was exceeded
     * @param render  the optional function to use to render each element to a string
     *
     * @return the resulting `Appender<Char>`
     */
    Appender<Char> join(
            Appender<Char>?           bufN   = Null,
            String                    sep    = ", ",
            String                    pre    = "",
            String                    post   = "",
            Int?                      limit  = Null,
            String                    trunc  = "...",
            function String(Element)? render = Null)
        {
        Appender<Char> buf = bufN ?: new StringBuffer();
        pre.appendTo(buf);

        function Appender<Char>(Element) appendElement = switch()
            {
            case render != Null              : (e -> render(e).appendTo(buf));
            case Element.is(Type<Stringable>): (e -> e.appendTo(buf));
            default: (e -> e.is(Stringable) ? e.appendTo(buf) : buf.addAll(e.toString()));
            };

        if (limit == Null || limit < 0)
            {
            limit = Int.maxvalue;
            }

        Loop: for (Element e : this)
            {
            if (!Loop.first)
                {
                sep.appendTo(buf);
                }

            if (Loop.count >= limit)
                {
                trunc.appendTo(buf);
                break;
                }

            appendElement(e);
            }

        post.appendTo(buf);
        return buf;
        }

    @Override
    String toString()
        {
        if (this.is(Stringable))
            {
            StringBuffer buf = new StringBuffer(estimateStringLength());
            appendTo(buf);
            return buf.toString();
            }

        return join(pre=$"{&this.actualClass}[", post="]").toString();
        }


    // ----- equality ------------------------------------------------------------------------------

    /**
     * Two collections are equal iff they are they contain the same values.
     */
    static <CompileType extends Collection>
            Boolean equals(CompileType collection1, CompileType collection2)
        {
        // they must be of the same arity
        Int size = collection1.size;
        if (collection2.size != size)
            {
            return False;
            }

        // empty collections are considered equal
        if (size == 0)
            {
            return True;
            }

        // check if a once-through zipper algorithm can be used
        Boolean sameOrder = size == 1;
        if (!sameOrder,
                Collection<CompileType.Element>.Orderer? order1 := collection1.orderedBy(),
                Collection<CompileType.Element>.Orderer? order2 := collection2.orderedBy(),
                order1? == order2?)
            {
            sameOrder = True;
            }

        if (sameOrder)
            {
            // if either is sorted, then both must be in the same order;
            // the collections were of the same arity, so the second iterator shouldn't run out
            // before the first
            Iterator<CompileType.Element> iter1 = collection1.iterator();
            Iterator<CompileType.Element> iter2 = collection2.iterator();
            for (val value1 : iter1)
                {
                assert val value2 := iter2.next();
                if (value1 != value2)
                    {
                    return False;
                    }
                }

            // the collections were of the same arity, so the first iterator shouldn't run out
            // before the second
            return !iter2.next();
            }

        // in theory, sets may execute in O(N) time
        if (collection1.is(Set))
            {
            return collection1.containsAll(collection2);
            }
        else if (collection2.is(Set))
            {
            return collection2.containsAll(collection1);
            }

        // if the type is hashable, then build a HashSet, and if its size is the same as before,
        // then the contents are unique; it looks expensive, but this optimizes to O(2*N)
        if (CompileType.Element.is(Type<Hashable>))
            {
            Map<CompileType.Element, Int> map = collection1.groupWith(
                    (_, n) -> (n + 1),
                    (_) -> 1,
                    new HashMap());
            if (map.size == size)
                {
                return collection1.containsAll(collection2);
                }
            else
                {
                return collection2.any(e -> map.process(e, entry ->
                    {
                    if (!entry.exists)
                        {
                        // found no matching element in collection1
                        return True;
                        }

                    Int oldValue = entry.value;
                    if (oldValue <= 1)
                        {
                        entry.delete();
                        }
                    else
                        {
                        entry.value = oldValue-1;
                        }
                    return False;
                    }));
                }
            }

        // this is text-book inefficiency; we're comparing two bags of non-Hashable values
        enum NonExistent {NotAValue}
        typedef (CompileType.Element | NonExistent) Remnant;
        Iterator<CompileType.Element> iter = collection1.iterator();
        Remnant[] remnants = new Array<Remnant>(collection1.size, _ ->
            {
            assert val e := iter.next(); return e;
            });
        assert !iter.next();
        for (CompileType.Element value : collection2)
            {
            if (Int i := remnants.indexOf(value))
                {
                remnants[i] = NotAValue;
                }
            else
                {
                return False;
                }
            }
        return True;
        }
    }
