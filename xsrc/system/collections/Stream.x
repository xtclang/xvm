/**
 * A Stream is a representation of a bounded group of values that can be sorted, filtered, and
 * queried or manipulated in a number of other common ways. The purpose of the fluent style of the
 * Stream interface is to allow a caller to compose a complex sequence of operations against the
 * Stream, all of which are deferrable until a terminal operation is requested.
 */
interface Stream<ElementType>
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
    Stream concat(Stream! that);

    /**
     * Returns a stream consisting of the elements of this stream that match the given predicate.
     *
     * This is an intermediate operation.
     *
     * @param include  a predicate function to check if an element should be included
     *
     * @return a new stream representing the filtered contents of this stream
     */
    Stream filter(function Boolean (ElementType) include);

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
    Stream distinct();

    /**
     * Returns a stream representing the same elements of this stream, but in a sorted order.
     *
     * @param comparator  the {@link Comparator} to use to sort the stream's elements, or null (the
     *                    default) to indicate the natural sort order
     *
     * @return a new stream representing the same elements from this stream in a sorted order
     */
    Stream sort(Comparator<ElementType>? comparator = null);

    /**
     * Returns a stream representing the same elements of this stream, but in reverse order. (A
     * stream that is not predictably ordered, either naturally as in a list or as the result of
     * sorting, may choose to ignore this request altogether.)
     *
     * @return a new stream representing the same elements from this stream, but in reverse order
     */
    Stream reverse();

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
    Stream peek(function void accept(ElementType));

    /**
     * Returns a stream representing only the first {@code count} elements of this stream.
     *
     * This is a short-circuiting stateful intermediate operation.
     *
     * @param count  the number of elements the resulting stream should be limited to
     *
     * @return a new stream that only includes up to the first {@code count} elements of this stream
     */
    Stream limit(Int count);

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
    Stream skip(Int count);

    /**
     * Returns a second stream that is a copy of this stream, allowing a terminal operation on one
     * of the two streams to be used without affecting the other.
     *
     * @return a clone of this stream
     */
    Stream clone();


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
    Iterator<ElementType> iterator();

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
        // the default behavior delegates to the Iterator, but an implementation that is aware of
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
        if (ElementType minValue : iter.next())
            {
            if (comparator == null)
                {
                assert ElementType.is(Type<Orderable>);

                for (ElementType el : iter)
                    {
                    if (el < minValue)
                        {
                        minValue = el;
                        }
                    }
                }
            else
                {
                for (ElementType el : iter)
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
        if (ElementType maxValue : iter.next())
            {
            if (comparator == null)
                {
                assert ElementType.is(Type<Orderable>);

                for (ElementType el : iter)
                    {
                    if (el > maxValue)
                        {
                        maxValue = el;
                        }
                    }
                }
            else
                {
                for (ElementType el : iter)
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
        // the default behavior delegates to the Iterator, but an implementation that is aware of
        // additional metadata (like the "size" property of a Collection) could short-cut the
        // processing
        Int n = 0;
        for (ElementType el : this)
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
        // the default behavior delegates to the Iterator, but an implementation that is aware of
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
        Iterator<ElementType> iter = iterator();
        if (ElementType result : iter.next())
            {
            for (ElementType element : iter)
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
     * Performs a mutable reduction operation on the elements of this stream. A mutable reduction
     * is one in which the reduced value is a mutable result container, such as a List, and elements
     * are incorporated by updating the state of the result rather than by replacing the result.
     *f
     * This method produces a result equivalent to:
     *
     *   ResultType result = supply();
     *   for (ElementType element : this)
     *       {
     *       if (!accumulate(result, element))
     *           {
     *           break;
     *           }
     *       }
     *   return result;
     *
     * This is a terminal operation.
     *
     * @param supply      a function that creates a new result container; if called more than once,
     *                    this function must return a fresh value each time
     * @param accumulate  an associative, non-interfering, stateless function for incorporating an
     *                    additional element into a result
     * @param combine     when the collection process is performed in two or more units, such as
     *                    when performed concurrently, this function merges any two results into
     *                    one result; it may be called more than once such that each of the partial
     *                    results will ultimately be combined into a single result
     *
     * @return the result of the reduction
     */
    <ResultType> ResultType collect(
                    function ResultType supply(),
                    function Boolean accumulate(ResultType, ElementType),
                    function ResultType combine(ResultType, ResultType))
        {
        return collect(Collector.of(supply, accumulate, combine));
        }

    /**
     * Performs a mutable reduction operation on the elements of this stream using a
     * {@code Collector}. The Collector encapsulates the functionality of all of the various stages
     * of reduction.
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
        AccumulationType container = collector.supply();
        iterator().whileEach(element -> collector.accumulate(container, element));
        return collector.finish(container);
        }
    }
