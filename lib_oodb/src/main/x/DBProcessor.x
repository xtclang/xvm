/**
 * The database interface for scheduling work that will occur in one or more later database
 * transactions.
 *
 * A `DBProcessor` is always transactional.
 */
interface DBProcessor<Element extends immutable Const>
        extends DBObject
    {
    // ----- scheduling methods --------------------------------------------------------------------

    /**
     * Schedule the specified element to run after this transaction completes, according to the
     * provided schedule.
     *
     * @param element   the element to process
     * @param schedule  (optional) the schedule for processing; `Null` indicates "immediately"
     */
    void schedule(Element element, Schedule? when=Null);

    /**
     * Schedule the specified elements to run after this transaction completes, according to the
     * provided schedule.
     *
     * @param elements  the elements to process
     * @param schedule  (optional) the schedule for processing; `Null` indicates "immediately"
     */
    void scheduleAll(Iterable<Element> elements, Schedule? when=Null)
        {
        for (Element el : elements)
            {
            schedule(el, when);
            }
        }

    /**
     * Remove all previously scheduled processing of the specified element, then add a new schedule
     * for that same element.
     *
     * @param element   the element to reschedule the processing of
     * @param schedule  the new schedule for processing the element
     */
    void reschedule(Element element, Schedule when)
        {
        unschedule(element);
        schedule(element, when);
        }

    /**
     * Unschedule the specified element that may currently be scheduled for processing.
     *
     * @return `True` iff the specified element was scheduled at least once, and now is not
     *         scheduled
     */
    Boolean unschedule(Element element);

    /**
     * Unschedule all scheduled processing of elements. This does not attempt to undo or stop the
     * processing of any elements that is already occurring.
     */
    void unscheduleAll();


    // ----- processing methods --------------------------------------------------------------------

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
     * @param elements  the previously scheduled elements to process; note that there are no
     *                  guarantees of uniqueness in the list of elements
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


    // ----- runtime management methods ------------------------------------------------------------

    /**
     * Suspend the processing of elements until a call is made to [resume].
     *
     * This is not an instantaneous method; **the suspension only takes effect when the current
     * transaction is successfully committed**.
     */
    void suspend();

    /**
     * Indicates whether the processing of elements has been suspended.
     *
     * This is not an instantaneous property; the value of this property reflects the suspension
     * state at the beginning of the current transaction (or the state of the suspension as the
     * result of this transaction).
     */
    @RO Boolean suspended;

    /**
     * Resume the processing of elements at some point after a call was made to [suspend].
     *
     * This is not an instantaneous method; **the resumption only takes effect when the current
     * transaction is successfully committed**.
     */
    void resume();

    /**
     * Obtain a **read-only** view of all previously added "pending" elements to process.
     *
     * Due to the transactional nature of the database, the actual state of pending operations is
     * likely to be constantly in flux, with new elements being added and existing elements being
     * processed and retired. Achieving a stable view may be extremely expensive; therefore, it is
     * strongly suggested that the `DBProcessor` first be suspended (by calling [suspend]) before
     * attempting any significant analysis of the resulting list. Note that, even with suspension,
     * new items can continue to be added to the DBProcessor, although they will not appear in this
     * list, due to the "repeatable-read" transactional guarantees provided by the OODB APIs.
     *
     * This is not an instantaneous property; the value of this property reflects the `DBProcessor`
     * state at the beginning of the current transaction (or the state of the `DBProcessor` as the
     * result of this transaction).
     *
     * Due to reasonable security considerations, it should not be assumed that this data will
     * even be accessible to most user connections.
     *
     * @return the pending elements that are already scheduled for processing
     */
    @RO List<Pending> pending;


    // ----- annotations ---------------------------------------------------------------------------

    /**
     * The `@Dedupe` annotation indicates that the `DBProcessor` should _automatically_ remove
     * duplicate `Pending` entries, such that multiple entries for the same `Element` on the same
     * `DBProcessor` that -- at some point in time -- _could_ be processed as multiple invocations
     * of [process], would instead be processed as a single call to [process] that `Element`. This
     * may involve removing duplicates when they are scheduled with the DBProcessor, and it may also
     * involve de-duping the `Element`s to process when selecting from a backlog of [Pending] items.
     *
     * In the absence of this annotation, for each time that the same `Element` value is scheduled,
     * a separate call to [process] will occur (or the same `Element` will appear multiple times in
     * the argument to [processAll], or some combination of the two).
     */
    static mixin Dedupe
            into DBProcessor
        {
        }

    /**
     * The `@Sequential` annotation indicates that the `DBProcessor` should not be run in parallel
     * (i.e. concurrently, and with concurrent transactions) when the opportunity to do so arises.
     * Instead, as much as possible, the processing should occur in a manner that appears to be
     * sequential, such that a call to [process] or [processAll] would not occur until the previous
     * call to [process] or [processAll] has completed and had its results committed.
     *
     * In the absence of this annotation, the expected default behavior by a database implementation
     * is that, when facing a backlog, multiple instances of this `DBProcessor` (each from a
     * separate client, and thus each with a separate transaction) would be used opportunistically
     * to reduce the backlog.
     */
    static mixin Sequential
            into DBProcessor
        {
        }

    /**
     * The `@Individual` annotation indicates that the `DBProcessor` should **not** process more
     * than one element per transaction; in other words, there should be only one call to this
     * `DBProcessor`'s [process] method within a transaction, regardless of the number of `Pending`
     * items that are ready to be processed by this DBProcessor.
     *
     * In the absence of this annotation, the expected default behavior by a database implementation
     * is that the `DBProcessor` will _automatically_ gather and [processAll] multiple (and perhaps
     * _all_) `Pending` entries for this `DBProcessor`, inside of the same transaction.
     */
    static mixin Individual
            into DBProcessor
        {
        }

    /**
     * The `@Isolated` annotation indicates that the processing of this `DBProcessor` should not be
     * combined within the same transaction with the processing of _other_ `DBProcessor`s.
     *
     * In the absence of this annotation, a database implementation _may_ choose to combine the
     * processing of multiple `DBProcessor` objects within a single transaction, for efficiency.
     */
    static mixin Isolated
            into DBProcessor
        {
        }


    // ----- pending element representation --------------------------------------------------------

    /**
     * The representation of a pending `DBProcessor` execution in the database.
     *
     * By representing an invocation as data:
     *
     * * The information can be communicated over a network connection;
     *
     * * A record of various invocations can be collected for later examination; and
     *
     * * Desired future invocations can be stored in persistent storage to ensure that the request
     *   for their execution can survive a database shutdown, outage, or other events.
     */
    static const Pending<Element extends immutable Const>
            (
            Path      processor,
            Element   element,
            Schedule? schedule         = Null,
            Int       previousFailures = 0,
            )
        {
        /**
         * The path of the `DBProcessor`.
         */
        Path processor;

        /**
         * The `Element` to be processed.
         */
        Element element;

        /**
         * Determine the schedule of the invocation, if one has been specified.
         *
         * @return the [Schedule], or `Null` if the invocation should be processed as soon as
         *         possible
         */
        Schedule? schedule;

        /**
         * Determine if the pending invocation is auto-rescheduling (i.e. repeating).
         *
         * @return True if the invocation is auto-rescheduling, aka "repeating".
         * @return (conditional) the interval of repeating
         * @return (conditional) the policy of repeating when the previous execution has not already
         *         completed
         */
        conditional (Duration repeatInterval, Schedule.Policy repeatPolicy) isRepeating()
            {
            // TODO GG return schedule?.isRepeating() : False;
            if (Schedule schedule ?= schedule)
                {
                return schedule.isRepeating();
                }
            return False;
            }

        /**
         * Determine the priority of the pending execution.
         */
        Schedule.Priority priority.get()
            {
            return schedule?.priority : Normal;
            }

        /**
         * The number of times that this pending invocation has already been attempted, and has failed.
         */
        Int previousFailures;
        }


    // ----- Scheduling support --------------------------------------------------------------------

    /**
     * Represents the schedule for an element to be processed.
     */
    static const Schedule(DateTime? scheduledAt      = Null,
                          Time?     scheduledDaily   = Null,
                          Duration? repeatInterval   = Null,
                          Policy    repeatPolicy     = AllowOverlapping,
                          Priority  priority         = Normal,
                         )
        {
        assert()
            {
            if (scheduledDaily != Null)
                {
                if (repeatInterval == Null)
                    {
                    repeatInterval = Duration:24h;
                    }
                else
                    {
                    assert repeatInterval == Duration:24h;
                    }
                }

            // can't be scheduled both at a specific date/time and the same time every day
            assert scheduledAt == Null || scheduledDaily == Null;
            }

        /**
         * Create a new Schedule from this Schedule with only the specified properties modified.
         */
        Schedule with(DateTime? scheduledAt      = Null,
                      Time?     scheduledDaily   = Null,
                      Duration? repeatInterval   = Null,
                      Policy?   repeatPolicy     = Null,
                      Priority? priority         = Null,
                     )
            {
            return new Schedule(scheduledAt    ?: this.scheduledAt,
                                scheduledDaily ?: this.scheduledDaily,
                                repeatInterval ?: this.repeatInterval,
                                repeatPolicy   ?: this.repeatPolicy,
                                priority       ?: this.priority,
                               );
            }

        /**
         * Determine if a specific time is set for the element to be processed.
         *
         * @return True iff the schedule indicates a specific point-in-time or daily-time that the
         *         pending element should be processed
         * @return (conditional) the point-in-time (DateTime) or daily-time (Time)
         */
        conditional DateTime | Time isScheduled()
            {
            if (scheduledAt != Null)
                {
                return True, scheduledAt;
                }

            if (scheduledDaily != Null)
                {
                return True, scheduledDaily;
                }

            return False;
            }

        /**
         * Determine if the element processing is scheduled to repeat automatically.
         *
         * @return True iff the schedule indicates automatic repeating of the element processing
         * @return (conditional) the interval of the repetition
         * @return (conditional) the policy governing the repetition, for example when the previous
         *         execution has not completed before the next execution would begin
         */
        conditional (Duration repeatInterval, Policy repeatPolicy) isRepeating()
            {
            if (repeatInterval != Null)
                {
                return True, repeatInterval, repeatPolicy;
                }

            return False;
            }

        /**
         * The supported priorities for scheduled items.
         *
         * * High - An indication that the priority is higher than the default priority.
         * * Medium - The default priority.
         * * Low - An indication that the priority is lower than the default priority.
         * * WhenIdle - An indication that the processing should only occur when there appears to be
         *   a lack of (or a measurable lull in) other database activity.
         */
        enum Priority {High, Normal, Low, WhenIdle}

        /**
         * Indicates how the repeating period is calculated.
         *
         * * AllowOverlapping - calculate the next scheduled time based on the time that each run
         *   was supposed to begin; if a previous execution has not yet completed, start the next
         *   execution.
         *
         * * SkipOverlapped - if the previous run has not finished running at the point that a
         *   subsequent run is scheduled to be run, then the subsequent run is skipped. This policy
         *   prevents overlapping of processing caused by same repeating-schedule Pending element.
         *
         * * SuggestedMinimum - calculate the next scheduled time based on the time that each run
         *   was supposed to begin; if a previous execution has not yet completed, then delay the
         *   start of the next execution until the previous completes. This policy prevents
         *   overlapping of processing caused by same repeating-schedule Pending element.
         *
         * * MeasureFromCommit - calculate the next scheduled time based on the time that the
         *   previous run completes. This policy prevents overlapping of processing caused by same
         *   repeating-schedule Pending element.
         */
        enum Policy
            {
            AllowOverlapping,
            SkipOverlapped,
            SuggestedMinimum,
            MeasureFromCommit,
            }

        /**
         * Create a processing schedule that indicates immediate processing. This is slightly
         * different than a default (no date/time indicated) schedule, in that the immediacy is
         * explicit; for example, later adding a daily schedule will not remove the indication that
         * immediate processing is also required.
         *
         * The database will attempt to process the work at that point in time, but may be forced
         * to delay the processing based on various factors, including the load profile of the
         * database.
         *
         * @return the new schedule
         */
        Schedule now()
            {
            @Inject Clock clock;
            return this.with(scheduledAt=clock.now);
            }

        /**
         * Create a schedule that indicates a specific point in time.
         *
         * The database will attempt to process the work at that point in time, but may be forced
         * to delay the processing based on various factors, including the load profile of the
         * database.
         *
         * @return the new schedule
         */
        Schedule at(DateTime when)
            {
            return this.with(scheduledAt=when);
            }

        /**
         * Create a schedule that indicates some initial delay from the present point in time.
         *
         * The database will attempt to process the work at the end of the indicated delay, but may
         * be forced to delay the processing based on various factors, including the load profile
         * of the database.
         *
         * @return the new schedule
         */
        Schedule after(Duration delay)
            {
            @Inject Clock clock;
            return this.with(scheduledAt=clock.now+delay);
            }

        /**
         * Configure a scheduled item to repeat its processing repeatedly, based on the specified
         * period of time. If the execution takes longer
         * than the specified time
         *
         * @param interval  the period of time to repeat the processing at
         * @param policy    the policy to use for scheduling a periodic process when the previous
         *                  execution is still running
         */
        Schedule every(Duration interval, Policy? policy=Null)
            {
            return this.with(repeatInterval = interval,
                             repeatPolicy   = policy ?: this.repeatPolicy);
            }

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
        Schedule dailyAt(Time timeOfDay, Policy? policy=Null)
            {
            return this.with(scheduledDaily = timeOfDay,
                             repeatPolicy   = policy ?: this.repeatPolicy);
            }

        /**
         * Modify the priority of the scheduled item.
         *
         * @param priority  the new priority for the scheduled item
         */
        Schedule prioritize(Priority priority)
            {
            return this.with(priority = priority);
            }
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
