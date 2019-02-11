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
    static <ElementType, AccumulationType>
        Collector<ElementType, AccumulationType, AccumulationType> of(
            function AccumulationType ()                                   supply,     // Supplier
            function Boolean (AccumulationType, ElementType)               accumulate, // Accumulator
            function AccumulationType (AccumulationType, AccumulationType) combine)    // Combiner
        {
        return of(supply, accumulate, combine, result -> result);
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
            function AccumulationType ()                                   supply,
            function Boolean (AccumulationType, ElementType)               accumulate,
            function AccumulationType (AccumulationType, AccumulationType) combine,
            function ResultType (AccumulationType)                         finish)
        {
        const SimpleCollector<EType, AType, RType>
                (
                function AType ()               supply,
                function Boolean (AType, EType) accumulate,
                function AType (AType, AType)   combine,
                function RType (AType)          finish
                )
            implements Collector<EType, AType, RType>;

        return new SimpleCollector(supply, accumulate, combine, finish);
        }
    }

