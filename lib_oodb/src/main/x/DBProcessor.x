/**
 * The database interface for scheduling work that will occur in one or more later database
 * transactions.
 *
 * A `DBProcessor` is always transactional.
 */
interface DBProcessor<Element extends immutable Const>
        extends DBObject
    {
    /**
     * Schedule the specified element to run after this transaction completes, and return the
     * ability to customize the schedule.
     *
     * @param element  the element to process
     *
     * @return an object that can be used to adjust the schedule
     */
    Schedulable schedule(Element element);

    /**
     * Schedule the specified elements to run after this transaction completes, and return the
     * ability to customize the schedule.
     *
     * @param elements  the elements to process
     *
     * @return an object that can be used to adjust the schedule
     */
    Schedulable scheduleAll(Iterable<Element> elements);

    /**
     * Represents the ability to customize the schedule of an element to process.
     *
     *     dbprocessor.schedule(work)
     *                .after(Duration:8h)
     *                .every(Duration:1h)
     *                .priority(High);
     */
    static interface Schedulable
            extends Repeatable
        {
        /**
         * Schedule the work to occur as soon as the current transaction completes. This is the
         * default scheduling.
         *
         * @return an interface that allows the work to be scheduled repeatedly, and prioritized
         */
        Repeatable now()
            {
            return this;
            }

        /**
         * Schedule the work to occur at the specified date/time.
         *
         * The database will attempt to process the work at that point in time, but may be forced
         * to delay the processing based on various factors, including the load profile of the
         * database.
         *
         * @return an interface that allows the work to be scheduled repeatedly, and prioritized
         */
        Repeatable at(DateTime when);

        /**
         * Schedule the work to occur after some delay, after the current transaction completes.
         *
         * The database will attempt to process the work at the end of the indicated delay, but may
         * be forced to delay the processing based on various factors, including the load profile
         * of the database.
         *
         * @return an interface that allows the work to be scheduled repeatedly, and prioritized
         */
        Repeatable after(Duration delay);
        }

    /**
     * Represents the ability to repeat the processing on a schedule.
     *
     *     dbprocessor.schedule(work)
     *                .after(Duration:8h)
     *                .every(Duration:1h)
     *                .priority(High);
     */
    static interface Repeatable
            extends Prioritizable
        {
        /**
         * Configure a scheduled item to repeat its processing repeatedly, based on the specified
         * period of time. If the execution takes longer
         * than the specified time
         *
         * @param interval  the period of time to repeat the processing at
         * @param policy    the policy to use for scheduling a periodic process when the previous
         *                  execution is still running
         */
        Prioritizable every(Duration interval, Policy policy=AllowOverlapping);

        /**
         * Schedule the work to occur on a daily basis, at the specified time.
         *
         * The database will attempt to process the work at that point in time, but may be forced
         * to delay the processing based on various factors, including the load profile of the
         * database.
         *
         * @param timeOfDay  the time to repeat the processing at every day
         * @param policy     the policy to use for scheduling a periodic process when the previous
         *                   execution is still running
         *
         * @return an interface that allows the work to be prioritized
         */
        Prioritizable dailyAt(Time timeOfDay, Policy policy=AllowOverlapping);

        /**
         * Indicates how the repeating period is calculated.
         *
         * * AllowOverlapping - calculate the next scheduled time based on the time that each run
         *   was supposed to begin; if a previous execution has not yet completed, start the next
         *   execution
         *
         * * SuggestedMinimum - calculate the next scheduled time based on the time that each run
         *   was supposed to begin; if a previous execution has not yet completed, then delay the
         *   start of the next execution until the previous completes. This policy prevents
         *   overlapping of processing of the same repetitive-scheduled element.
         *
         * * MeasureFromCommit - calculate the next scheduled time based on the time that the
         *   previous run completes. This policy naturally prevents overlapping of processing of
         *   the same repetitive-scheduled element.
         */
        enum Policy
            {
            AllowOverlapping,
            SuggestedMinimum,
            MeasureFromCommit,
            }
        }

    /**
     * An interface that supports the prioritization of scheduled items.
     *
     *     dbprocessor.schedule(work)
     *                .after(Duration:8h)
     *                .every(Duration:1h)
     *                .priority(High);
     */
    static interface Prioritizable
        {
        /**
         * Modify the priority of the scheduled item.
         *
         * * High - An indication that the priority is higher than the default priority.
         * * Medium - The default priority.
         * * Low - An indication that the priority is lower than the default priority.
         * * WhenIdle - An indication that the processing should only occur when there is nothing
         *              else to process.
         */
        enum Priority {High, Normal, Low, WhenIdle}

        /**
         * Modify the priority of the scheduled item.
         *
         * @param priority  the new priority for the scheduled item
         */
        void priority(Priority priority);
        }

    /**
     * Unschedule the specified element that may currently be scheduled for processing.
     *
     * @return `True` iff the specified element was scheduled at least once, and now is not
     *         scheduled
     */
    Boolean unschedule(Element element);

    /**
     * Unschedule all scheduled processing of elements.
     *
     * @return the elements that were scheduled for processing
     */
    Element[] unscheduleAll();

    /**
     * Suspend the processing of elements until a call is made to [resume].
     */
    void suspend();

    /**
     * Indicates whether the processing of elements has been suspended.
     */
    @RO Boolean suspended;

    /**
     * Resume the processing of elements at some point after a call was made to [suspend].
     */
    void resume();


    // ----- DBObject methods ----------------------------------------------------------------------

    /**
     * Process an element.
     *
     * @param element  the previously scheduled element to process
     */
    void process(Element element);

    /**
     * Process a group of elements. It may be more efficient to process a group of elements
     * together, and a developer should implement this method if that is the case.
     *
     * @param elements  the previously scheduled elements to process
     */
    void processAll(List<Element> elements)
        {
        for (Element element : elements)
            {
            process(element);
            }
        }

    /**
     * Determine if the failure of processing the element should be automatically retried.
     *
     * @param element         the element that failed to be processed and committed
     * @param timesAttempted  the number of times that the processing of this element has been
     *                        attempted, and has failed
     *
     * @return `True` if the DBProcessor should try to process the same element again automatically
     */
    Boolean autoRetry(Element element, Int timesAttempted)
        {
        if (timesAttempted < 5)
            {
            return True;
            }

        String elementString;
        try
            {
            elementString = element.toString();
            }
        catch (Exception e)
            {
            elementString = $"??? (Exception={e.text})";
            }
        dbLogFor<DBLog<String>>(Path:/sys/errors).add($|Failed to process {elementString};\
                                                       | abandoning after {timesAttempted} attempts
                                                     );
        return False;
        }


    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBProcessor;
        }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Represents specific database changes that occurred to a transactional database processor.
     *
     * This interface represents the change without the context of the `DBProcessor`, thus it is
     * `static`, and cannot provide a before and after view on its own; when combined with the
     * `TxChange` interface, it can provide both the change information, and a before/after view of
     * the data.
     */
    static interface DBChange<Element>
        {
        /**
         * The elements scheduled in this transaction to be processed later.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added within the transaction _may_ appear in the list.
         */
        List<Element> added;

        /**
         * The elements processed by this transaction.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently taken within the transaction _may_ appear in the list.
         */
        List<Element> removed;
        }

    /**
     * Represents a transactional change to a database processor.
     *
     * This interface provides both the discrete change information, as well as the contextual
     * before-and-after view of the transaction. Obtaining a 'before' or 'after' transactional
     * view of the processor should be assumed to be a relatively expensive operation.
     */
    @Override
    interface TxChange
            extends DBChange<Element>
        {
        }


    // ----- transaction trigger API ---------------------------------------------------------------

    // these interfaces can be used in lieu of the more generic interfaces of the same names found
    // on [DBObject], but these exists only as a convenience, in that they can save the application
    // database developer a few type-casts that might otherwise be necessary.

    @Override static interface Validator<TxChange extends DBObject.TxChange>
            extends DBObject.Validator<TxChange> {}
    @Override static interface Rectifier<TxChange extends DBObject.TxChange>
            extends DBObject.Rectifier<TxChange> {}
    @Override static interface Distributor<TxChange extends DBObject.TxChange>
            extends DBObject.Distributor<TxChange> {}
    }
