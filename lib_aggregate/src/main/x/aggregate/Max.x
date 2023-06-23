/**
 * An aggregator that determines the maximum value.
 */
const Max<Element extends Orderable>
        implements ParallelAggregator<Element, WorkingMax<Element>, Element?> {

    @Override
    @Lazy Aggregator<Element, Partial> elementAggregator.calc() {
        return new ElementAggregator<Element>();
    }

    @Override
    @Lazy Aggregator<Partial, Partial> intermediateAggregator.calc() {
        return new PartialAggregator<Element>();
    }

    @Override
    @Lazy Aggregator<Partial, Result> finalAggregator.calc() {
        return new FinalAggregator<Element>();
    }

    @Override
    Accumulator init(Int capacity = 0) {
        return elementAggregator.init();
    }

    @Override
    Result reduce(Accumulator accumulator) {
        return accumulator.as(WorkingMax<Element>).max;
    }


    // ----- child aggregators ---------------------------------------------------------------------

    private static const ElementAggregator<Value extends Orderable>
            implements Aggregator<Value, immutable WorkingMax<Value>> {

        @Override
        Accumulator init(Int capacity = 0) {
            return new ElementAccumulator<Value>();
        }

        @Override
        Result reduce(Accumulator accumulator) {
            return accumulator.as(WorkingMax<Value>).makeImmutable();
        }
    }

    private static const PartialAggregator<Value extends Orderable>
            implements Aggregator<WorkingMax<Value>, WorkingMax<Value>> {

        @Override
        Accumulator init(Int capacity = 0) {
            return new ResultAccumulator<Value>();
        }

        @Override
        Result reduce(Accumulator accumulator) {
            return accumulator.as(WorkingMax<Value>).makeImmutable();
        }
    }

    private static const FinalAggregator<Value extends Orderable>
            implements Aggregator<WorkingMax<Value>, Value?> {

        @Override
        Accumulator init(Int capacity = 0) {
            return new ResultAccumulator<Value>();
        }

        @Override
        Result reduce(Accumulator accumulator) {
            return accumulator.as(WorkingMax<Value>).max;
        }
    }


    // ----- private inner classes -----------------------------------------------------------------

    /**
     * A stateful maximum.
     */
    private static class WorkingMax<Value extends Orderable> {
        Boolean hasResult;

        @Unassigned Value result;

        Value? max.get() {
            return hasResult ? result : Null;
        }
    }

    /**
     * A stateful WorkingMax of the Elements provided.
     */
    private static class ElementAccumulator<Value extends Orderable>
            extends WorkingMax<Value>
            implements Appender<Value> {

        @Override
        ElementAccumulator add(Value v) {
            if (hasResult) {
                if (v > result) {
                    result = v;
                }
            } else {
                result    = v;
                hasResult = True;
            }

            return this;
        }
    }

    /**
     * A stateful WorkingMax of the WorkingMaxes provided.
     */
    private static class ResultAccumulator<Value extends Orderable>
            extends WorkingMax<Value>
            implements Appender<WorkingMax<Value>> {

        @Override
        ResultAccumulator add(WorkingMax<Value> v) {
            if (v.hasResult) {
                if (this.hasResult) {
                    if (v.result > this.result) {
                        this.result = v.result;
                    }
                } else {
                    this.result    = v.result;
                    this.hasResult = True;
                }
            }

            return this;
        }
    }
}
