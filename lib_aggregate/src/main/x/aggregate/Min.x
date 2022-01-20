/**
 * An aggregator that determines the minimum value.
 */
const Min<Element extends Orderable>
        implements ParallelAggregator<Element, WorkingMin<Element>, Element?>
    {
    @Override
    @Lazy Aggregator<Element, Partial> elementAggregator.calc()
        {
        return new ElementAggregator<Element>();
        }

    @Override
    @Lazy Aggregator<Partial, Partial> intermediateAggregator.calc()
        {
        return new PartialAggregator<Element>();
        }

    @Override
    @Lazy Aggregator<Partial, Result> finalAggregator.calc()
        {
        return new FinalAggregator<Element>();
        }

    @Override
    Accumulator init()
        {
        return elementAggregator.init();
        }

    @Override
    Result reduce(Accumulator accumulator)
        {
        return accumulator.as(WorkingMin<Element>).min;
        }


    // ----- child aggregators ---------------------------------------------------------------------

    private static const ElementAggregator<Value extends Orderable>
            implements Aggregator<Value, immutable WorkingMin<Value>>
        {
        @Override
        Accumulator init()
            {
            return new ElementAccumulator<Value>();
            }

        @Override
        Result reduce(Accumulator accumulator)
            {
            return accumulator.as(WorkingMin<Value>).makeImmutable();
            }
        }

    private static const PartialAggregator<Value extends Orderable>
            implements Aggregator<WorkingMin<Value>, WorkingMin<Value>>
        {
        @Override
        Accumulator init()
            {
            return new ResultAccumulator<Value>();
            }

        @Override
        Result reduce(Accumulator accumulator)
            {
            return accumulator.as(WorkingMin<Value>).makeImmutable();
            }
        }

    private static const FinalAggregator<Value extends Orderable>
            implements Aggregator<WorkingMin<Value>, Value?>
        {
        @Override
        Accumulator init()
            {
            return new ResultAccumulator<Value>();
            }

        @Override
        Result reduce(Accumulator accumulator)
            {
            return accumulator.as(WorkingMin<Value>).min;
            }
        }


    // ----- private inner classes -----------------------------------------------------------------

    /**
     * A stateful minimum.
     */
    private static class WorkingMin<Value extends Orderable>
        {
        Boolean hasResult;

        @Unassigned Value result;

        Value? min.get()
            {
            return hasResult ? result : Null;
            }
        }

    /**
     * A stateful WorkingMin of the Elements provided.
     */
    private static class ElementAccumulator<Value extends Orderable>
            extends WorkingMin<Value>
            implements Appender<Value>
        {
        @Override
        ElementAccumulator add(Value v)
            {
            if (hasResult)
                {
                if (v < result)
                    {
                    result = v;
                    }
                }
            else
                {
                result    = v;
                hasResult = True;
                }

            return this;
            }
        }

    /**
     * A stateful WorkingMin of the WorkingMins provided.
     */
    private static class ResultAccumulator<Value extends Orderable>
            extends WorkingMin<Value>
            implements Appender<WorkingMin<Value>>
        {
        @Override
        ResultAccumulator add(WorkingMin<Value> v)
            {
            if (v.hasResult)
                {
                if (this.hasResult)
                    {
                    if (v.result < this.result)
                        {
                        this.result = v.result;
                        }
                    }
                else
                    {
                    this.result    = v.result;
                    this.hasResult = True;
                    }
                }

            return this;
            }
        }
    }
