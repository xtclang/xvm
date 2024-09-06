/**
 * FilterableQueue is a Queue that supports the use of element filters in each of the Queue methods.
 * This allows elements to be selected based on characteristics of the elements themselves.
 *
 * REVIEW this interface is for evaluation and discussion, and has not been finalized/approved
 */
interface FilterableQueue<Element>
        extends Queue<Element> {
    typedef function Boolean(Element) as Filter;

    /**
     * If a filter is provided, then this method scans the queue for an element that matches the
     * filter, and if one is found, that element is taken from the queue and returned.
     *
     * If a filter is not provided, then this method works as described by [Queue.next].
     */
    @Override
    conditional Element next(Filter? matches = Null);

    /**
     * If a filter is provided, then this method scans the queue for an element that matches the
     * filter, and if one is found, that element is taken from the queue and returned. If a matching
     * element is not found, then a `@Future` representing the next matching element is returned,
     * such that the queue will deliver that matching element when it is added to the queue.
     *
     * If a filter is not provided, then this method works as described by [Queue.take].
     */
    @Override
    Element take(Filter? matches = Null);

    /**
     * If a filter is provided, then this method scans the queue for an element that matches the
     * filter, and if one is found, that element is taken from the queue and "piped" to the
     * Consumer. If a matching element is not found, then the queue will evaluate elements as they
     * are added to the queue, and (if no previous request takes precedence) "pipe" the first
     * matching element when it is added to the queue.
     *
     * If a filter is not provided, then this method works as described by [Queue.pipeNext].
     */
    @Override
    Cancellable pipeNext(Consumer pipe, Filter? matches = Null);

    /**
     * If a filter is provided, then this method scans the queue for all elements that match the
     * filter, and for each one found, that element is taken from the queue and "piped" to the
     * Consumer. Additionally, until canceled, the queue will continue to evaluate elements as they
     * are added to the queue, and (if no previous request takes precedence) "pipe" each matching
     * element when it is added to the queue.
     *
     * If a filter is not provided, then this method works as described by [Queue.pipeAll], except
     * that it does not prevent subsequent calls to `pipeNext` and `pipeAll` that specify a filter.
     */
    @Override
    Cancellable pipeAll(Consumer pipe, Filter? matches = Null);
}
