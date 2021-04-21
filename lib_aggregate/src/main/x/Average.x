/**
 * An aggregator that averages numeric values.
 */
const Average<Element extends Number, Result extends Number>
        implements ParallelAggregator<Element, CountAndSum<Element>, Result>
    {
    assert()
        {
        assert val clz := Element.fromClass(), clz.defaultValue()
                as $"Element type \"{Element}\" does not have a default value";
        }

    @Override
    @Lazy Aggregator<Element, Partial> elementAggregator.calc()
        {
        return new ElementAggregator<Element>();
        }

    @Override
    @Lazy Aggregator<Partial, Partial> intermediateAggregator.calc()
        {
        return new IntermediateAggregator<Element>();
        }

    @Override
    @Lazy Aggregator<Partial, Result> finalAggregator.calc()
        {
        return new FinalAggregator<Element, Result>();
        }

    @Override
    Accumulator init()
        {
        return new ElementSummer<Element>();
        }

    @Override
    Result reduce(Accumulator accumulator)
        {
        return accumulator.as(ElementSummer<Element>).calculateAverage(Result);
        }


    // ----- child aggregators ---------------------------------------------------------------------

    private static const ElementAggregator<Value extends Number>
            implements Aggregator<Value, CountAndSum<Value>>
        {
        @Override
        Accumulator init()
            {
            return new ElementSummer<Value>();
            }

        @Override
        Result reduce(Accumulator accumulator)
            {
            return accumulator.as(ElementSummer<Value>).makeImmutable();
            }
        }

    private static const IntermediateAggregator<Value extends Number>
            implements Aggregator<CountAndSum<Value>, CountAndSum<Value>>
        {
        @Override
        Accumulator init()
            {
            return new ResultSummer<Value>();
            }

        @Override
        Result reduce(Accumulator accumulator)
            {
            return accumulator.as(ResultSummer<Value>).makeImmutable();
            }
        }

    private static const FinalAggregator<Value extends Number, Result extends Number>
            implements Aggregator<CountAndSum<Value>, Result>
        {
        @Override
        Accumulator init()
            {
            return new ResultSummer<Value>();
            }

        @Override
        Result reduce(Accumulator accumulator)
            {
            return accumulator.as(ResultSummer<Value>).calculateAverage(Result);
            }
        }


    // ----- private inner classes -----------------------------------------------------------------

    /**
     * A stateful count and summation of the elements provided.
     */
    private static class CountAndSum<Value extends Number>
        {
        Int   count;
        Value sum;

        <Result extends Number> Result calculateAverage(Type<Result> resultType)
            {
            function Result(Value) fromSum   = Number.converterFor(Value, Result);
            function Result(Int)   fromCount = Int.converterTo(Result);
            return fromSum(sum) / fromCount(count);
            }
        }

    /**
     * A class that sums Elements into a CountAndSum.
     */
    private static class ElementSummer<Value extends Number>
            extends CountAndSum<Value>
            implements Appender<Value>
        {
        @Override
        ElementSummer add(Value v)
            {
            sum += v;
            ++count;
            return this;
            }
        }

    /**
     * A class that sums CountAndSums into a CountAndSum.
     */
    private static class ResultSummer<Value extends Number>
            extends CountAndSum<Value>
            implements Appender<CountAndSum<Value>>
        {
        @Override
        ResultSummer add(CountAndSum<Value> v)
            {
            sum   += v.sum;
            count += v.count;
            return this;
            }
        }
    }
