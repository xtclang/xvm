/**
 * An Aggregator that collects into any specified `Collection` type of elements.
 */
const Collect<Element, Result extends Replicable+Collection<Element>>(Type<Result> result,
                                                                      Boolean freeze=False)
        implements Aggregator<Element, Result> {

    assert() {
        assert:arg result == Result;
        assert:arg freeze == False || Element.is(Type<Shareable>) && result.is(Type<Freezable>);
    }

    /**
     * Obtain an object that will "Collect to" the specified Collection type, and optionally freeze
     * the result.
     *
     * @param result  the specified Collection type
     *
     * @return an CollectArray
     */
    static <Element, Result extends Replicable+Collection<Element>>
            Collect<Element, Result> to(Type<Result> result, Boolean freeze=False) {
        return new Collect<Element, Result>(result, freeze);
    }

    /**
     * Obtain an object that will "Collect to" the specified Freezable Collection type, and Freeze
     * it before yielding the final result.
     *
     * @param result  the specified Freezable Collection type
     *
     * @return a collecting aggregator that will result in the specified immutable Collection type
     */
    static <Element extends Shareable, Result extends Freezable+Replicable+Collection<Element>>
            Collect<Element, Result> toFrozen(Type<Result> result) {
        // ideally this would have an "immutable Result", but since the same type is used both for
        // collecting (via the init() method) and the final result (via reduce()), it is not
        // possible to assume immutable for the instance used during the collecting phase
        return Collect.to(result, freeze=True) /** TODO GG remove */ .as(Collect<Element, Result>);
    }

    @Override
    Result init(Int capacity = 0) {
        return new Result();
    }

    @Override
    Result reduce(Accumulator accumulator) {
        if (freeze) {
            accumulator = accumulator.as(Freezable).freeze(inPlace=True);
        }
        return accumulator.as(Result);
    }
}
