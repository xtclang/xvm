/**
 * An aggregator that determines the minimum and maximum value, as a Range.
 */
const MinMax<Element extends Orderable>
        implements ParallelAggregator<Element, WorkingMinMax<Element>, Range<Element>?> {

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
    Accumulator init() {
        return elementAggregator.init();
    }

    @Override
    Result reduce(Accumulator accumulator) {
        return accumulator.as(WorkingMinMax<Element>).result;
    }


    // ----- child aggregators ---------------------------------------------------------------------

    private static const ElementAggregator<Value extends Orderable>
            implements Aggregator<Value, immutable WorkingMinMax<Value>> {

        @Override
        Accumulator init() {
            return new ElementAccumulator<Value>();
        }

        @Override
        Result reduce(Accumulator accumulator) {
            return accumulator.as(WorkingMinMax<Value>).makeImmutable();
        }
    }

    private static const PartialAggregator<Value extends Orderable>
            implements Aggregator<WorkingMinMax<Value>, WorkingMinMax<Value>> {

        @Override
        Accumulator init() {
            return new ResultAccumulator<Value>();
        }

        @Override
        Result reduce(Accumulator accumulator) {
            return accumulator.as(WorkingMinMax<Value>).makeImmutable();
        }
    }

    private static const FinalAggregator<Value extends Orderable>
            implements Aggregator<WorkingMinMax<Value>, Range<Value>?> {

        @Override
        Accumulator init() {
            return new ResultAccumulator<Value>();
        }

        @Override
        Result reduce(Accumulator accumulator) {
            return accumulator.as(WorkingMinMax<Value>).result;
        }
    }


    // ----- private inner classes -----------------------------------------------------------------

    /**
     * A stateful minimum and maximum.
     */
    private static class WorkingMinMax<Value extends Orderable> {
        Boolean hasResult;

        @Unassigned Value min;
        @Unassigned Value max;

        Range<Value>? result.get() {
            return hasResult ? min..max : Null;
        }
    }

    /**
     * A stateful WorkingMinMax of the Elements provided.
     */
    private static class ElementAccumulator<Value extends Orderable>
            extends WorkingMinMax<Value>
            implements Appender<Value> {

        @Override
        ElementAccumulator add(Value v) {
            if (hasResult) {
                if (v < min) {
                    min = v;
                } else if (v > max) {
                    max = v;
                }
            } else {
                min       = v;
                max       = v;
                hasResult = True;
            }

            return this;
        }
    }

    /**
     * A stateful WorkingMinMax of the WorkingMinMaxes provided.
     */
    private static class ResultAccumulator<Value extends Orderable>
            extends WorkingMinMax<Value>
            implements Appender<WorkingMinMax<Value>> {

        @Override
        ResultAccumulator add(WorkingMinMax<Value> v) {
            if (v.hasResult) {
                if (this.hasResult) {
                    if (v.min < this.min) {
                        this.min = v.min;
                    }
                    if (v.max > this.max) {
                        this.max = v.max;
                    }
                } else {
                    this.min       = v.min;
                    this.max       = v.max;
                    this.hasResult = True;
                }
            }

            return this;
        }
    }
}
