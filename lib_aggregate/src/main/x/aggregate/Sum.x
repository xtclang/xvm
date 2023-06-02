/**
 * An aggregator that sums up numeric values.
 */
const Sum<Element extends Number>
        implements ParallelAggregator<Element, Element, Element> {

    assert() {
        assert val clz := Element.fromClass(), clz.defaultValue()
                as $"Element type \"{Element}\" does not have a default value";
    }

    @Override
    Aggregator<Element, Partial> elementAggregator.get() {
        return this;
    }

    @Override
    Aggregator<Partial, Partial> intermediateAggregator.get() {
        return this;
    }

    @Override
    Aggregator<Partial, Result> finalAggregator.get() {
        return this;
    }

    @Override
    Accumulator init() {
        return new WorkingSum();
    }

    @Override
    Result reduce(Accumulator accumulator) {
        return accumulator.as(WorkingSum).sum;
    }


    // ----- private inner classes -----------------------------------------------------------------

    /**
     * A stateful summation of the elements provided.
     */
    private class WorkingSum
            implements Appender<Element> {

        Element sum;

        @Override
        WorkingSum add(Element v) {
            sum += v;
            return this;
        }
    }
}
