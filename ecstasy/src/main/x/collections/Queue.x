/**
 * A Queue is a representation of elements in a queue, including the potential for elements that
 * will eventually arrive in that queue.
 *
 * Queue supports both demand-based and inversion-of-control (event-based) programming models:
 *
 * * The [next] method allows a caller to test for the presence of an element in the queue, and
 *   to take the first element if the queue contains any elements.
 * * The [take] method allows a caller to take the first element in the queue, or to wait for that
 *   first element to arrive (if there are currently no elements in the queue).
 * * The [pipeNext] method allows a caller to direct the first element in the queue (or the next
 *   element to arrive if the queue is currently empty) to be routed ("piped") to a particular
 *   consumer.
 * * The [pipeAll] method allows a caller to direct all elements in the queue, including those
 *   that will be enqueued in the future, to be routed ("piped") to a particular consumer.
 *
 * In the case of the `take` method, the result can be obtained as an asynchronous `@Future` value,
 * allowing for reactive-style or continuation-based handling. This allows a caller to build its
 * logic in a non-blocking manner. Similarly, the `pipe*` methods are used for an event-driven
 * programming model; however, the `@Future` approach has the benefit of a standard model for
 * dealing with cross-service invocation, continuations, and exception handling (e.g. an exception
 * indicating that the Queue was closed).
 */
@Concurrent
interface Queue<Element>
        extends Appender<Element>
    {
    /**
     * Test for the presence of an element in the queue, and take the first element if the queue
     * contains any elements.
     *
     * @return True iff the Queue was not empty
     * @return the next element (conditional)
     */
    conditional Element next();

    /**
     * Take the first element in the queue, or wait for an element to be added to the Queue (if
     * there are currently no elements in the queue).
     *
     * Given some message handler and queue of messages:
     *
     *   void handleMessage(Message? msg, Exception? e) {...}
     *   Queue<Message> queue = ...
     *
     * Blocking `take()` example:
     *
     *   Message? msg = Null;
     *   try
     *     {
     *     // if the Queue is empty, this method call may block indefinitely
     *     msg = queue.take();
     *     }
     *   catch (Exception e)
     *     {
     *     handleMessage(Null, e);
     *     }
     *   handleMessage(msg?, Null);
     *
     * Non-blocking `take()` example:
     *
     *   // regardless of the Queue status (whether it is empty or not), neither of the following
     *   // two lines of code will block
     *   @Future Message msg = queue.take();
     *   &msg.whenComplete(handleMessage);
     *
     * @return the next element in the queue (or a `@Future` representing the next element if there
     *         is no element in the queue, but the queue will deliver an element when it is added)
     */
    Element take();

    /**
     * Take the first element in the queue, or return the specified default if the queue is empty.
     *
     * @param defaultElement  the default element value to return, iff the queue is empty
     *
     * @return the element taken from the queue; otherwise, the specified default element
     */
    Element takeOrDefault(Element defaultElement)
        {
        if (Element e := next())
            {
            return e;
            }

        return defaultElement;
        }

    /**
     * Take the first element in the queue, or return the result of evaluating the provided function
     * if the queue is empty.
     *
     * @param compute  the function that will be called iff the queue is empty, in order to produce
     *                 an element to return
     *
     * @return the element taken from the queue if the queue is not empty; otherwise, the result
     *         from the provided function
     */
    Element takeOrCompute(function Element () compute)
        {
        if (Element e := next())
            {
            return e;
            }

        return compute();
        }

    typedef function void (Element) Consumer;
    typedef function void () Cancellable;

    /*
     * Redirect the first element in the queue (or the next element to arrive if the queue is
     * currently empty) to be routed ("piped") to the specified consumer.
     *
     * Invoking the returned #Cancellable will _attempt_ to cancel the redirection of an element,
     * but the cancellation is not guaranteed, since the Queue may have already called (or is
     * concurrently calling) the Consumer.
     *
     * @param pipe  the Consumer to pipe the next element in the queue to
     *
     * @return a cancel function that allows the redirection to be canceled later
     */
    Cancellable pipeNext(Consumer pipe);

    /*
     * Redirect all elements in the queue, including those that have not yet arrived (those that
     * will be enqueued in the future) to be routed ("piped") to the specified consumer. The
     * redirection will remain in place until canceled. If previous `take` and `pipeNext` requests
     * are still pending, then this request will take effect only after the previous requests have
     * been satisfied. Once the `pipeAll` request has been made, and until it is cancelled, calls
     * to any of the Queue methods are invalid, and will result in an exception.
     *
     * Invoking the returned #Cancellable will cancel the redirection of elements, but there are no
     * guarantees with respect to concurrent execution.
     *
     * @param pipe  the Consumer to pipe the next element in the queue to
     *
     * @return a cancel function that allows the redirection to be canceled later
     */
    Cancellable pipeAll(Consumer pipe);
    }
