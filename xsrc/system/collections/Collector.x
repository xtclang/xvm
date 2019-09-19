/**
 * The Collector interface encapsulates the functionality of all of the various stages of
 * reduction, including support for parallelization.
 */
interface Collector<Element, Cumulative, Result>
    {
    typedef function Cumulative () Supplier;
    typedef function Boolean (Cumulative, Element) Accumulator;
    typedef function Cumulative (Cumulative, Cumulative) Combiner;
    typedef function Result (Cumulative) Finisher;

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
     * appropriate for cases in which the Cumulative is the same as the Result.
     *
     * @param supply      the supplier function for the new collector
     * @param accumulate  the accumulator function for the new collector
     * @param combine     the combiner function for the new collector
     *
     * @return a new {@code Collector} described by the given {@code supply},
     *         {@code accumulate}, and {@code combine} functions assuming that
     *         the Result is the same as the Cumulative
     */
    static <Element, Cumulative>
        Collector<Element, Cumulative, Cumulative> of(
            function Cumulative ()                       supply,     // Supplier
            function Boolean (Cumulative, Element)       accumulate, // Accumulator
            function Cumulative (Cumulative, Cumulative) combine)    // Combiner
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
    static <Element, Cumulative, Result>
        Collector<Element, Cumulative, Result> of(
            function Cumulative ()                       supply,
            function Boolean (Cumulative, Element)       accumulate,
            function Cumulative (Cumulative, Cumulative) combine,
            function Result (Cumulative)                 finish)
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

