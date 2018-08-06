/**
 * A Stream is a representation of a bounded group of values that can be sorted, filtered, and
 * queried or manipulated in a number of other common ways. The purpose of the fluent style of the
 * Stream interface is to allow a caller to compose a complex sequence of operations against the
 * Stream, all of which are deferrable until a terminal operation is requested.
 */
interface Stream<ElementType>
        extends Iterable<ElementType>
    {
    // ----- intermediate operations ---------------------------------------------------------------

    /**
     * Concatenate another stream to this stream, producing a new stream.
     *
     * This is an intermediate operation.
     *
     * @param that  the stream to concatenate to this stream
     *
     * @return a new stream representing the concatenation of the two input streams
     */
    Stream<ElementType> concat(Stream!<ElementType> that);

    /**
     * Returns a stream consisting of the elements of this stream that match the given predicate.
     *
     * This is an intermediate operation.
     *
     * @param include  a predicate function to check if an element should be included
     *
     * @return a new stream representing the filtered contents of this stream
     */
    Stream<ElementType> filter(function Boolean (ElementType) include);

    /**
     * Returns a stream consisting of the results of applying the given function to the elements of
     * this stream.
     *
     * This is an intermediate operation.
     *
     * @param <MappedType>  the element type of the new stream
     *
     * @param apply  a function to apply to each element of this stream
     *
     * @return a new stream representing the results of applying the specified function to each
     *         element in this stream
     */
    <MappedType> Stream<MappedType> map(function MappedType (ElementType) apply);

    /**
     * Returns a stream consisting of the the concatenation of all of the streams resulting from
     * applying the provided mapping function to each element of this stream.
     *
     * This is an intermediate operation.
     *
     * @param <MappedType>  the element type of the new stream
     * @param flatten       a function to apply to each element, resulting in a
     *                      {@code Stream<MappedType>}
     *
     * @return a new stream representing the concatenated results of applying the specified function
     *         to each element in this stream
     */
    <MappedType> Stream<MappedType> flatMap(function Stream!<MappedType> (ElementType) flatten);

    /**
     * Returns a stream representing the _distinct_ elements of this stream.
     *
     * This is a stateful intermediate operation.
     *
     * @return a new stream representing only the distinct set of elements from this stream
     */
    Stream<ElementType> distinct();

    /**
     * Returns a stream representing the same elements of this stream, but in a sorted order.
     *
     * @param comparator  the {@link Comparator} to use to sort the stream's elements, or null (the
     *                    default) to indicate the natural sort order
     *
     * @return a new stream representing the same elements from this stream in a sorted order
     */
    Stream<ElementType> sort(Comparator<ElementType>? comparator = null);

    /**
     * Returns a stream representing the same elements of this stream, but in reverse order. (A
     * stream that is not predictably ordered, either naturally as in a list or as the result of
     * sorting, may choose to ignore this request altogether.)
     *
     * @return a new stream representing the same elements from this stream, but in reverse order
     */
    Stream<ElementType> reverse();

    /**
     * Returns a stream representing the same elements as exist in this stream, but additionally
     * performing the provided action on each element of the resulting stream as elements are
     * consumed from it. This capability is considered particularly useful for debugging purposes.
     *
     * This is an intermediate operation.
     *
     * @param accept  a function to perform on the elements as they are consumed from the resulting
     *                stream
     *
     * @return a new stream with the specified functionality attached to it
     */
    Stream<ElementType> peek(function void accept(ElementType));

    /**
     * Returns a stream representing only the first {@code count} elements of this stream.
     *
     * This is a short-circuiting stateful intermediate operation.
     *
     * @param count  the number of elements the resulting stream should be limited to
     *
     * @return a new stream that only includes up to the first {@code count} elements of this stream
     */
    Stream<ElementType> limit(Int count);

    /**
     * Returns a stream representing only the remaining elements of this stream after discarding the
     * first {@code count} elements of this stream.
     *
     * This is a stateful intermediate operation.
     *
     * @param count  the number of leading elements to skip in this stream
     *
     * @return a new stream that does not include the first {@code count} elements of this stream
     */
    Stream<ElementType> skip(Int count);

    // ----- terminal operations -------------------------------------------------------------------

    /**
     * Obtain an Iterator over the contents of the stream. This method is useful if the caller wants
     * to iterate through the contents, but the Iterator also has a number of other useful
     * capabilities, including:
     * * {@link Iterator.forEach forEach} - a sequential form of the Stream's {@link forEach}
     * * {@link Iterator.whileEach whileEach} - a sequential form of the Stream's {@link allMatch}
     * * {@link Iterator.untilAny untilAny} - a sequential form of the Stream's {@link anyMatch}
     *
     * This is a terminal operation.
     *
     * @return an Iterator over the contents of the Stream
     */
    @Override
    Iterator<ElementType> iterator();

    /**
     * Obtain an iterator over a portion of the contents of the Stream.
     *
     * This is a terminal operation.
     *
     * (This method is overridden on Stream in order to utilize the Stream's filtering capability.)
     *
     * @param match  a function that determines which elements match the desired contents of the
     *               resulting Iterator
     *
     * @return an Iterator over the filtered contents of the Stream
     */
    @Override
    Iterator<ElementType> iterator(function Boolean (ElementType) match)
        {
        return filter(match).iterator();
        }

    /**
     * Perform the specified action on all of the elements of this stream. There is no guarantee of
     * order of when specific elements are processed, unless the stream is sorted; to achieve a
     * sequential order of execution, use the {@link Iterator.forEach forEach} method on the
     * Iterator returned from {@link iterator} instead.
     *
     * @param process  an action to perform on each element
     */
    void forEach(function void (ElementType) process)
        {
        // the default behavior delegates to the Iterator, but an implementation that parallelizes
        // the stream will likely override this behavior
        iterator().forEach(process);
        }

    /**
     * Determines whether any elements of this stream match the provided predicate. This method may
     * not evaluate the predicate on all of the elements if it can determine the result without
     * doing so. If the stream is empty then {@code false} is returned and the predicate is not
     * evaluated.
     *
     * This is a short-circuiting terminal operation.
     *
     * @param match  a predicate function that determines which elements match some criteria
     *
     * @return {@code true} if all elements of the stream match the provided predicate, otherwise
     *         {@code false}
     */
    Boolean anyMatch(function Boolean (ElementType) match)
        {
        // the default behavior delegates to the Iterator, but an implementation that parallelizes
        // the stream will likely override this behavior
        return iterator().untilAny(match);
        }

    /**
     * Determines whether all elements of this stream match the provided predicate. This method may
     * not evaluate the predicate on all of the elements if it can determine the result without
     * doing so. If the stream is empty then {@code true} is returned and the predicate is not
     * evaluated.
     *
     * This is a short-circuiting terminal operation.
     *
     * @param match  a predicate function that determines which elements match some criteria
     *
     * @return {@code true} if all elements of the stream match the provided predicate, otherwise
     *         {@code false}
     */
    Boolean allMatch(function Boolean (ElementType) match)
        {
        // the default behavior delegates to the Iterator, but an implementation that parallelizes
        // the stream will likely override this behavior
        return iterator().whileEach(match);
        }

    /**
     * Determines whether no elements of this stream match the provided predicate. This method may
     * not evaluate the predicate on all of the elements if it can determine the result without
     * doing so. If the stream is empty then {@code true} is returned and the predicate is not
     * evaluated.
     *
     * This is a short-circuiting terminal operation.
     *
     * @param match  a predicate function that determines which elements match some criteria
     *
     * @return {@code true} if no elements of the stream match the provided predicate, otherwise
     *         {@code false}
     */
    Boolean noneMatch(function Boolean (ElementType) match)
        {
        return !anyMatch(match);
        }

    /**
     * Obtain the first element of the stream.  There is no guarantee of order of elements in the
     * stream, unless the stream is sorted; in other words, if the stream is not sorted, the "first"
     * element is arbitrary.
     *
     * This is a short-circuiting terminal operation.
     *
     * @return a conditional result providing the first element of this stream, iff any
     */
    conditional ElementType first()
        {
        // the default behavior delegates to the Iterator, but an implementation that parallelizes
        // the stream will likely override this behavior, and an implementation that is aware of
        // the underlying data structure could short-cut the processing
        return iterator().next();
        }

    /**
     * @return the minimum element of this stream according to the provided
     *         {@code Comparator}.
     *
     * This is a terminal operation that is a special case of a reduction.
     *
     * @param comparator a non-interfering, stateless function
     *                   to compare elements of this stream
     * @return a conditional result describing the minimum element of this stream
     */
    conditional ElementType min(Comparator<ElementType>? comparator = null)
        {
        Iterator<ElementType> iter = iterator();
        if (ElementType minValue : iter)
            {
            if (comparator != null)
                {
                while (ElementType el : iter)
                    {
                    if (el < minValue)
                        {
                        minValue = el;
                        }
                    }
                }
            else
                {
                while (ElementType el : iter)
                    {
                    if (comparator.compareForOrder(el, minValue) == Lesser)
                        {
                        minValue = el;
                        }
                    }
                }
            return true, minValue;
            }
        return false;
        }

    /**
     * Returns the maximum element of this stream according to the provided
     * {@code Comparator}.  This is a special case of a reduction.
     *
     * This is a terminal operation.
     *
     * @param comparator a non-interfering, stateless function
     *                   to compare elements of this stream
     * @return a conditional result describing the maximum element of this stream
     */
    conditional ElementType max(Comparator<ElementType>? comparator = null)
        {
        Iterator<ElementType> iter = iterator();
        if (ElementType maxValue : iter)
            {
            if (comparator == null)
                {
                while (ElementType el : iter)
                    {
                    if (el > maxValue)
                        {
                        maxValue = el;
                        }
                    }
                }
            else
                {
                while (ElementType el : iter)
                    {
                    if (comparator.compareForOrder(el, maxValue) == Greater)
                        {
                        maxValue = el;
                        }
                    }
                }
            return true, maxValue;
            }
        return false;
        }

    /**
     * Determine the actual number of elements in this stream, as if the stream has to count them.
     *
     * This is a terminal operation.
     *
     * @return the count of elements in this stream
     */
    Int count()
        {
        // the default behavior delegates to the Iterator, but an implementation that parallelizes
        // the stream will likely override this behavior, and an implementation that is aware of
        // additional metadata (like the "size" property of a Collection) could short-cut the
        // processing
        Int n = 0;
        while (ElementType el : this)
            {
            ++n;
            }
        return n;
        }

    /**
     * Obtain an array that contains all of the elements in this stream. The contents will be
     * ordered if the stream is ordered. There is no guarantee as to whether the returned array is
     * _mutable_, _fixed-size_, _persistent_, or {@code const}.
     *
     * This is a terminal operation.
     *
     * @return an array containing the elements of this stream
     */
    ElementType[] toArray()
        {
        // the default behavior delegates to the Iterator, but an implementation that parallelizes
        // the stream will likely override this behavior, and an implementation that is aware of
        // additional metadata (like the "size" property and the contents of a Collection) could
        // short-cut the processing
        ElementType[] elements = new ElementType[];
        for (ElementType el : this)
            {
            elements += el;
            }
        return elements;
        }

    // ----- advanced terminal operations ----------------------------------------------------------

    /**
     * Performs a reduction on the elements of this stream, using the provided identity value and an
     * associative accumulation function, and returns the reduced value.
     *
     * The {@code identity} value must be an identity for the accumulator function. This means that
     * for all {@code el}, {@code accumulate(identity, el)} is equal to {@code el}.
     *
     * For example, to sum a stream of Int values:
     *
     *   Stream<Int> ints = ...
     *   Int sum = ints.reduce(0, (n1, n2) -> n1 + n2);
     *
     * This is a terminal operation.
     *
     * @param identity    the identity value for the accumulating function
     * @param accumulate  an associative, non-interfering, stateless function for
     *                    combining two values
     * @return the result of the reduction
     */
    ElementType reduce(ElementType identity, function ElementType accumulate(ElementType, ElementType))
        {
        ElementType result = identity;
        for (ElementType element : this)
            {
            result = accumulate(result, element);
            }
        return result;
        }

    /**
     * Performs a reduction on the elements of this stream, using an associative accumulation
     * function, and returns a conditional reduced value, if any.
     *
     * For example, to concatenate a stream of String values:
     *
     *   Stream<String> strings = ...
     *   String concat = strings.reduce((s1, s2) -> s1 + s2);
     *
     * This is a terminal operation.
     *
     * @param accumulate  an associative function for combining two values
     *
     * @return a conditional result of the reduction
     */
    conditional ElementType reduce(function ElementType accumulate(ElementType, ElementType))
        {
        Iterator<ElementType> iterator = iterator();
        if (ElementType result : iterator.next())
            {
            while (ElementType element : iterator.next())
                {
                result = accumulate(result, element);
                }
            return true, result;
            }
        else
            {
            return false;
            }
        }

    /**
     * Performs a mutable reduction operation on the elements of this stream.
     * A mutable reduction is one in which the reduced value is a mutable result container,
     * such as a List, and elements are incorporated by updating the state of
     * the result rather than by replacing the result.  This
     * produces a result equivalent to:
     * <pre>{@code
     *     R result = supplier.get();
     *     for (ElementType element : this stream)
     *         accumulator.accept(result, element);
     *     return result;
     * }</pre>
     *
     * This is a terminal operation.
     *
     * @param supply      a function that creates a new result container. For a
     *                    parallel execution, this function may be called
     *                    multiple times and must return a fresh value each time
     * @param accumulate  an associative, non-interfering, stateless
     *                    function for incorporating an additional element into a result
     * @param combine     an associative, non-interfering, stateless
     *                    function for combining two values, which must be
     *                    compatible with the accumulator function
     * @return the result of the reduction
     */
    <ResultType> ResultType collect(
                    function ResultType supply(),
                    function ResultType accumulate(ResultType, ElementType),
                    function ResultType combine(ResultType, ResultType))
        {
        return collect(Collector.of(supply, accumulate, combine));
        }

    /**
     * Performs a mutable reduction operation on the elements of this stream using a
     * {@code Collector}. The Collector encapsulates the functionality of all of the various stages
     * of reduction, including support for parallelization.
     *
     * This is a terminal operation.
     *
     * @param collector the {@code Collector} that defines the functions to utilize for the various
     *        stages of the reduction
     *
     * @return the result of the reduction
     */
    <ResultType, AccumulationType>
    ResultType collect(Collector<ElementType, AccumulationType, ResultType> collector)
        {
        // the default behavior delegates to the Iterator, but an implementation that parallelizes
        // the stream will likely override this behavior
        AccumulationType      container  = collector.supply();
        Collector.Accumulator accumulate = collector.accumulate;
        iterator().forEach(element -> accumulate(container, element));
        return collector.finish(container);
        }

    /**
     * The Collector interface encapsulates the functionality of all of the various stages of
     * reduction, including support for parallelization.
     */
    interface Collector<ElementType, AccumulationType, ResultType>
        {
        typedef function AccumulationType () Supplier;
        typedef function Boolean (AccumulationType, ElementType) Accumulator;
        typedef function AccumulationType (AccumulationType, AccumulationType) Combiner;
        typedef function ResultType (AccumulationType) Finisher;

        /**
         * A function which returns a new, mutable result container.
         */
        @RO Supplier supply;

        /**
         * A function which folds a value into a mutable result container.
         */
        @RO Accumulator accumulate;

        /**
         * A function which combines two partial results into a combined result.
         */
        @RO Combiner combine;

        /**
         * A function which transforms the intermediate result to the final result.
         */
        @RO Finisher finish;

        /**
         * Create a Collector from a Supplier function, an Accumulator function, and a Combiner
         * function; use an identity function as the Finisher function. This method is only
         * appropriate for cases in which the AccumulationType is the same as the ResultType.
         *
         * @param supply      the supplier function for the new collector
         * @param accumulate  the accumulator function for the new collector
         * @param combine     the combiner function for the new collector
         *
         * @return a new {@code Collector} described by the given {@code supply},
         *         {@code accumulate}, and {@code combine} functions assuming that
         *         the ResultType is the same as the AccumulationType
         */
//        static <CollectorType extends Collector>
//                Collector<CollectorType.ElementType, CollectorType.ResultType, CollectorType.ResultType> of(
//                        CollectorType.Supplier    supply,
//                        CollectorType.Accumulator accumulate,
//                        CollectorType.Combiner    combine)
        static <ElementType, AccumulationType, ResultType> Collector<ElementType, ResultType, ResultType> of(
                        Collector<ElementType, ResultType, ResultType>.Supplier    supply,
                        Collector<ElementType, ResultType, ResultType>.Accumulator accumulate,
                        Collector<ElementType, ResultType, ResultType>.Combiner    combine)
            {
            return new SimpleCollector<ElementType, AccumulationType, ResultType>(
                    supply, accumulate, combine, result -> result);
            }

        /**
         * Create a Collector from a Supplier function, an Accumulator function, a Combiner
         * function, and a Finisher function.
         *
         * @param supply      the supplier function for the new collector
         * @param accumulate  the accumulator function for the new collector
         * @param combine     the combiner function for the new collector
         * @param finish      the finisher function for the new collector
         *
         * @return a new {@code Collector} described by the given {@code supply},
         *         {@code accumulate}, {@code combine} and {@code finish} functions
         */
        static <ElementType, AccumulationType, ResultType>
                Collector<ElementType, AccumulationType, ResultType> of(
                        Supplier supply, Accumulator accumulate, Combiner combine, Finisher finish)
            {
            return new SimpleCollector<ElementType, AccumulationType, ResultType>(
                    supply, accumulate, combine, finish);
            }

        /**
         * Trivial Collector.
         */
        static const SimpleCollector<ElementType, AccumulationType, ResultType>
            (Supplier supply, Accumulator accumulate, Combiner combine, Finisher finish)
                implements Collector<ElementType, AccumulationType, ResultType>
            {
            }
        }
    }
