/**
 * An aggregator that sums up numeric values.
 */
const Sum<Element extends Number>
        implements ParallelAggregator<Element, Element, Element>
    {
    @Override
    Aggregator<Element, Partial> elementAggregator.get()
        {
        return this;
        }

    @Override
    Aggregator<Partial, Partial> intermediateAggregator.get()
        {
        return this;
        }

    @Override
    Aggregator<Partial, Result> finalAggregator.get()
        {
        return this;
        }

    @Override
    Accumulator init()
        {
        return new WorkingSum<Element>();
        }

    @Override
    Result reduce(Accumulator accumulator)
        {
        return accumulator.as(WorkingSum<Element>).sum;
        }

    /**
     * A stateful summation of the elements provided. Not my most brilliant piece of work, but it
     * will do.
     */
    private static class WorkingSum<Element extends Number>
            implements Appender<Element>
        {
        Element sum; // REVIEW GG - how is this initialized to zero?

        @Override
        WorkingSum add(Element v)
            {
            sum += v;
            return this;
            }
        }
    }
