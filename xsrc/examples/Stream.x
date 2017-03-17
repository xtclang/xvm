interface Stream<ElementType>
        extends BaseStream<ElementType, Stream<ElementType>>
    {
    typedef function Int (ElementType, ElementType) Comparator;
    // TODO if we decide to have the Comparator interface, then it would be:
    // typedef Comparator<ElementType> Comparator;

    typedef function Boolean (ElementType) Predicate;

    /**
     * Returns a stream consisting of the elements of this stream that match the given predicate.
     *
     * This is an intermediate operation.
     *
     * @param predicate a test function to check if an element should be included
     *
     * @return the new stream
     */
    Stream<ElementType> filter(Predicate predicate);

    /**
     * Returns a stream consisting of the results of applying the given function to the elements of
     * this stream.
     *
     * This is an intermediate operation.
     *
     * @param <MappedType>  the element type of the new stream
     *
     * @param apply  a function to apply to each element
     *
     * @return the new stream
     */
    <MappedType> Stream<MappedType> map(function MappedType apply(ElementType));

    /**
     * Returns a stream consisting of the results of replacing each element of
     * this stream with the contents of a mapped stream produced by applying
     * the provided mapping function to each element.
     *
     * This is an intermediate operation</a>.
     *
     * @param <MappedType>  the element type of the new stream
     *
     * @param apply  a function to apply to each element
     *
     * @return the new stream
     */
    <MappedType> Stream<MappedType> flatMap(function Stream<MappedType> apply(ElementType));

    /**
     * Returns a stream consisting of the distinct elements of this stream.
     *
     * This is a stateful intermediate operation.
     *
     * @return the new stream
     */
    Stream<ElementType> distinct();

    /**
     * Returns a stream consisting of the elements of this stream, additionally
     * performing the provided action on each element as elements are consumed
     * from the resulting stream.
     *
     * This is an intermediate operation.
     *
     * @param accept  a function to perform on the elements as they are consumed
     *                from the resulting stream
     * @return the new stream
     */
    Stream<ElementType> peek(function Void accept(ElementType));

    /**
     * Returns a stream consisting of the elements of this stream, truncated
     * to be no longer than the specified length.
     *
     * This is a short-circuiting stateful intermediate operation.
     *
     * @param maxSize  the number of elements the stream should be limited to
     *
     * @return the new stream
     */
    Stream<ElementType> limit(Int maxSize);

    /**
     * Returns a stream consisting of the remaining elements of this stream
     * after discarding the first {@code skipCount} elements of the stream.
     * If this stream contains fewer than {@code skipCount} elements then an
     * empty stream will be returned.
     *
     * This is a stateful intermediate operation.
     *
     * @param n the number of leading elements to skip
     *
     * @return the new stream
     */
    Stream<ElementType> skip(Int skipCount);

    // ----- terminal operations -----

    /**
     * Performs a specified action for each element of this stream.
     *
     * This is a terminal operation.
     *
     * @param consume  an action to perform on the elements; if that function returns {@code false)
     *                 no further stream elements are processed
     */
    void forEach(function Boolean consume(ElementType))
        {
        for (ElementType element : iterator())
            {
            if (!consume(element))
                {
                return;
                }
            }
        }

    /**
     * Performs a mutable reduction operation on the elements of this stream using a
     * {@code Collector}.
     *
     * This is a terminal operation.
     *
     * @apiNote
     * The following will accumulate strings into an ArrayList:
     * <pre>{@code
     *     List<String> asList = stringStream.collect(Collectors.toList());
     * }</pre>
     *
     * <p>The following will classify {@code Person} objects by city:
     * <pre>{@code
     *     Map<String, List<Person>> peopleByCity
     *         = personStream.collect(Collectors.groupingBy(Person.city));
     * }</pre>
     *
     * <p>The following will classify {@code Person} objects by state and city,
     * cascading two {@code Collector}s together:
     * <pre>{@code
     *     Map<String, Map<String, List<Person>>> peopleByStateAndCity
     *         = personStream.collect(Collectors.groupingBy(Person.state,
     *              Collectors.groupingBy(Person.city)));
     * }</pre>
     *
     * @param collector the {@code Collector} describing the reduction
     *
     * @return the result of the reduction
     */
    <ResultType, AccumulationType> ResultType collect(
            Collector<ElementType, AccumulationType, ResultType> collector)
        {
        AccumulationType      container  = collector.supply();
        Collector.Accumulator accumulate = collector.accumulate;

        forEach(element -> accumulate(container, element));

        return collector.finish(container);
        }

    /**
     * Performs a reduction on the elements of this stream, using the provided
     * identity value and an associative accumulation function, and returns the
     * reduced value.
     *
     * The {@code identity} value must be an identity for the accumulator function. This means that
     * for all {@code el}, {@code accumulate(identity, el)} is equal to {@code el}.
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
        for (ElementType element : iterator())
            {
            result = accumulate(result, element);
            }
        return result;

        }

    /**
     * Performs a reduction on the elements of this stream, using an
     * associative accumulation function, and returns a conditional reduced value,
     * if any.
     *
     * This is a terminal operation.
     *
     * @param accumulate  an associative, non-interfering, stateless
     *                    function for combining two values
     * @return a conditional result of the reduction
     */
    conditional ElementType reduce(function ElementType accumulate(ElementType, ElementType))
        {
        Iterator<ElementType> iterator = iterator();
        if (ElementType result : iterator.next())
            {
            for (ElementType element : iterator)
                {
                result = accumulate(result, element);
                }
            return (true, result)
            }
        else
            {
            return false;
            }
        }

    /**
     * Performs a reduction on the elements of this stream, using the provided identity,
     * accumulation and combining functions.  This is equivalent to:
     * <pre>{@code
     *     U result = identity;
     *     for (ElementType element : this stream)
     *         result = accumulator.apply(result, element)
     *     return result;
     * }</pre>
     * but is not constrained to execute sequentially.
     *
     * This is a terminal operation.
     *
     * @param identity the identity value for the combiner function
     * @param accumulate  an associative, non-interfering, stateless
     *                    function for combining two values
     * @param combine     an associative, non-interfering, stateless
     *                    function for combining two values, which must be
     *                    compatible with the accumulator function
     * @return the result of the reduction
     */
    <ResultType> ResultType reduce(
                    ResultType identity,
                    function ResultType accumulate(ResultType, ElementType),
                    function ResultType combine(ResultType, ResultType))
        {
        return collect(UniformCollector.of(() -> identity, accumulate, combine));
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
        return collect(UniformCollector.of(supply, accumulate, combine));
        }

    /**
     * @return an array containing the elements of this stream
     *
     * This is a terminal operation.
     */
    ElementType[] toArray();  // TODO trivial default impl

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
    conditional ElementType min(Comparator comparator);

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
    conditional ElementType max(Comparator comparator);

    /**
     * @return the count of elements in this stream
     *
     * This is a terminal operation.
     */
    Int count();

    /**
     * Checks whether any elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for determining the result.
     * If the stream is empty then {@code false} is returned and the predicate is not evaluated.
     *
     * This is a short-circuiting terminal operation.
     *
     * @param predicate  a non-interfering, stateless predicate to test the elements of this stream
     * @return {@code true} if any elements of the stream match the provided
     *         predicate, otherwise {@code false}
     */
    Boolean anyMatch(Predicate predicate);

    /**
     * Checks whether all elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for determining the result.
     * If the stream is empty then {@code false} is returned and the predicate is not evaluated.
     *
     * This is a short-circuiting terminal operation.
     *
     * @param predicate  a non-interfering, stateless predicate to test the elements of this stream
     * @return {@code true} if all elements of the stream match the provided
     *         predicate, otherwise {@code false}
     */
    Boolean allMatch(Predicate predicate);

    /**
     * Checks whether no elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for determining the result.
     * If the stream is empty then {@code false} is returned and the predicate is not evaluated.
     *
     * This is a short-circuiting terminal operation.
     *
     * @param predicate  a non-interfering, stateless predicate to test the elements of this stream
     * @return {@code true} if no elements of the stream match the provided
     *         predicate, otherwise {@code false}
     */
    Boolean noneMatch(Predicate predicate);

    /**
     * @return a conditional result describing the first element of this stream
     *
     * This is a short-circuiting terminal operation.
     */
    conditional ElementType findFirst();

    /**
     * @return a conditional result describing any element of this stream
     *
     * This is a short-circuiting terminal operation.
     */
    conditional ElementType findAny();

    // ----- static factories -----

    /**
     * @return a stream builder
     */
    public static <ElementType> Builder<ElementType> builder()
        {
        return new Streams.StreamBuilderImpl<>();
        }

    /**
     * Returns an empty sequential {@code Stream}.
     *
     * @param <ElementType> the type of stream elements
     * @return an empty sequential stream
     */
    public static<ElementType> Stream<ElementType> empty() {
        return StreamSupport.stream(Spliterators.<ElementType>emptySpliterator(), false);
    }

    /**
     * Returns a sequential {@code Stream} containing a single element.
     *
     * @param t the single element
     * @param <ElementType> the type of stream elements
     * @return a singleton sequential stream
     */
    public static<ElementType> Stream<ElementType> of(ElementType t) {
        return StreamSupport.stream(new Streams.StreamBuilderImpl<>(t), false);
    }

    /**
     * Returns a sequential ordered stream whose elements are the specified values.
     *
     * @param <ElementType> the type of stream elements
     * @param values the elements of the new stream
     * @return the new stream
     */
    @SafeVarargs
    @SuppressWarnings("varargs") // Creating a stream from an array is safe
    public static<ElementType> Stream<ElementType> of(ElementType... values) {
        return Arrays.stream(values);
    }

    /**
     * Returns an infinite sequential ordered {@code Stream} produced by iterative
     * application of a function {@code f} to an initial element {@code seed},
     * producing a {@code Stream} consisting of {@code seed}, {@code f(seed)},
     * {@code f(f(seed))}, etc.
     *
     * <p>The first element (position {@code 0}) in the {@code Stream} will be
     * the provided {@code seed}.  For {@code n > 0}, the element at position
     * {@code n}, will be the result of applying the function {@code f} to the
     * element at position {@code n - 1}.
     *
     * @param <ElementType> the type of stream elements
     * @param seed the initial element
     * @param f a function to be applied to to the previous element to produce
     *          a new element
     * @return a new sequential {@code Stream}
     */
    public static<ElementType> Stream<ElementType> iterate(final ElementType seed, final UnaryOperator<ElementType> f) {
        Objects.requireNonNull(f);
        final Iterator<ElementType> iterator = new Iterator<ElementType>() {
            @SuppressWarnings("unchecked")
            ElementType t = (ElementType) Streams.NONE;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public ElementType next() {
                return t = (t == Streams.NONE) ? seed : f.apply(t);
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                iterator,
                Spliterator.ORDERED | Spliterator.IMMUElementTypeABLE), false);
    }

    /**
     * Returns an infinite sequential unordered stream where each element is
     * generated by the provided {@code Supplier}.  ElementTypehis is suitable for
     * generating constant streams, streams of random elements, etc.
     *
     * @param <ElementType> the type of stream elements
     * @param s the {@code Supplier} of generated elements
     * @return a new infinite sequential unordered {@code Stream}
     */
    public static<ElementType> Stream<ElementType> generate(Supplier<ElementType> s) {
        Objects.requireNonNull(s);
        return StreamSupport.stream(
                new StreamSpliterators.InfiniteSupplyingSpliterator.OfRef<>(Long.MAX_VALUE, s), false);
    }

    /**
     * Creates a lazily concatenated stream whose elements are all the
     * elements of the first stream followed by all the elements of the
     * second stream.  The resulting stream is ordered if both
     * of the input streams are ordered, and parallel if either of the input
     * streams is parallel.  When the resulting stream is closed, the close
     * handlers for both input streams are invoked.
     *
     * @implNote
     * Use caution when constructing streams from repeated concatenation.
     * Accessing an element of a deeply concatenated stream can result in deep
     * call chains, or even {@code StackOverflowException}.
     *
     * @param <ElementType> The type of stream elements
     * @param a the first stream
     * @param b the second stream
     * @return the concatenation of the two input streams
     */
    public static <ElementType> Stream<ElementType> concat(Stream<? extends ElementType> a, Stream<? extends ElementType> b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);

        @SuppressWarnings("unchecked")
        Spliterator<ElementType> split = new Streams.ConcatSpliterator.OfRef<>(
                (Spliterator<ElementType>) a.spliterator(), (Spliterator<ElementType>) b.spliterator());
        Stream<ElementType> stream = StreamSupport.stream(split, a.isParallel() || b.isParallel());
        return stream.onClose(Streams.composedClose(a, b));
    }

    /**
     * A mutable builder for a {@code Stream}.  This allows the creation of a
     * {@code Stream} by generating elements individually and adding them to the
     * {@code Builder} (without the copying overhead that comes from using
     * an {@code ArrayList} as a temporary buffer.)
     *
     * <p>A stream builder has a lifecycle, which starts in a building
     * phase, during which elements can be added, and then transitions to a built
     * phase, after which elements may not be added.  The built phase begins
     * when the {@link #build()} method is called, which creates an ordered
     * {@code Stream} whose elements are the elements that were added to the stream
     * builder, in the order they were added.
     *
     * @param <ElementType> the type of stream elements
     */
    public interface Builder<ElementType> extends Consumer<ElementType> {

        /**
         * Adds an element to the stream being built.
         *
         * @throws IllegalStateException if the builder has already transitioned to
         * the built state
         */
        @Override
        void accept(ElementType t);

        /**
         * Adds an element to the stream being built.
         *
         * @implSpec
         * The default implementation behaves as if:
         * <pre>{@code
         *     accept(t)
         *     return this;
         * }</pre>
         *
         * @param t the element to add
         * @return {@code this} builder
         * @throws IllegalStateException if the builder has already transitioned to
         * the built state
         */
        default Builder<ElementType> add(ElementType t) {
            accept(t);
            return this;
        }

        /**
         * Builds the stream, transitioning this builder to the built state.
         * An {@code IllegalStateException} is thrown if there are further attempts
         * to operate on the builder after it has entered the built state.
         *
         * @return the built stream
         * @throws IllegalStateException if the builder has already transitioned to
         * the built state
         */
        Stream<ElementType> build();

    }
}
