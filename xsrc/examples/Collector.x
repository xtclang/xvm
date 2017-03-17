/**
 * See java.util.stream.Collector
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
    @ro Supplier supply;

    /**
     * A function which folds a value into a mutable result container.
     */
    @ro Accumulator accumulate;

    /**
     * A function which combines two partial results into a combined result.
     */
    @ro Combiner combine;

    /**
     * A function which transforms the intermediate result to the final result.
     */
    @ro Finisher finish;

    /**
     * @return a new {@code Collector} described by the given {@code supply},
     *         {@code accumulate}, {@code combine} and {@code finish} functions
     *
     * @param supply      the supplier function for the new collector
     * @param accumulate  the accumulator function for the new collector
     * @param combine     the combiner function for the new collector
     * @param finish      the finisher function for the new collector
     */
    static Collector<> of(Supplier supply, Accumulator accumulate, Combiner combine, Finisher finish)
        {
        return new SimpleCollector<>(supply, accumulate, combine, finish);
        }

    /**
     * Trivial Collector.
     */
    static const SimpleCollector<ElementType, AccumulationType, ResultType>
        (Supplier supply, Accumulator accumulate, Combiner combine, Finisher finish)
            implements Collector<ElementType, AccumulationType, ResultType>;
    }

/**
 * A {@link Collector} that has identical AccumulationType and the ResultType
 * (Java calls it IDENTITY_FINISH).
 */
interface UniformCollector<ElementType, ResultType>
        extends Collector<ElementType, ResultType, ResultType>
    {
    /**
     * @return a new {@code Collector} described by the given {@code supply},
     *         {@code accumulate}, and {@code combine} functions assuming that
     *         the ResultType is the same as the AccumulationType
     *
     * @param supply      the supplier function for the new collector
     * @param accumulate  the accumulator function for the new collector
     * @param combine     the combiner function for the new collector
     */
    static UniformCollector<> of(Supplier supply, Accumulator accumulate, Combiner combine)
        {
        return of(supply, accumulate, combine, result -> result);
        }
    }
