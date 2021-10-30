import Transaction.CommitResult;

/**
 * The database interface for scheduling messages that will be processed by later database
 * transactions.
 *
 * TODO explain
 *
 * A `DBProcessor` is always transactional.
 */
interface DBProcessor<Message extends immutable Const>
        extends DBObject
    {
    // ----- message scheduling --------------------------------------------------------------------

    /**
     * Schedule the specified message to be processed after this transaction commits, according to
     * the provided schedule.
     *
     * @param message   the message to process
     * @param when      (optional) the schedule for processing; `Null` indicates "immediately"
     */
    void schedule(Message message, Schedule? when=Null);

    /**
     * Schedule the specified messages to be processed after this transaction completes, according
     * to the provided schedule.
     *
     * @param messages  the messages to process
     * @param when      (optional) the schedule for processing; `Null` indicates "immediately"
     */
    void scheduleAll(Iterable<Message> messages, Schedule? when=Null)
        {
        for (Message el : messages)
            {
            schedule(el, when);
            }
        }

    /**
     * Remove all scheduled instances of the specified message, then re-schedule that message to be
     * processed using the provided [Schedule].
     *
     * @param message   the message to reschedule the processing of
     * @param when      the new schedule for processing the message
     */
    void reschedule(Message message, Schedule when)
        {
        unschedule(message);
        schedule(message, when);
        }

    /**
     * Remove all scheduled instances of the specified message.
     *
     * This does not have any effect on the processing of any messages that is already occurring.
     *
     * This only affects those messages scheduled before this transaction began; messages scheduled
     * while this transaction is in process will not be unscheduled.
     *
     * @return `True` iff the specified message was scheduled at least once
     */
    Boolean unschedule(Message message);

    /**
     * Remove all scheduled messages.
     *
     * This does not have any effect on the processing of any messages that is already occurring.
     *
     * This only affects those messages scheduled before this transaction began; messages scheduled
     * while this transaction is in process will not be unscheduled.
     */
    void unscheduleAll();

    /**
     * Obtain a read-only view of scheduled messages.
     *
     * Due to the transactional nature of the database, the actual state of pending operations is
     * likely to be constantly in flux, with new messages being added and existing messages being
     * processed and retired. Achieving a stable view may be extremely expensive; therefore, it is
     * strongly suggested that the `DBProcessor` first be suspended (by calling [suspend]) before
     * attempting any significant analysis of the resulting list. Note that, even with suspension,
     * new items can continue to be added to the DBProcessor, although they will not appear in this
     * list, due to the "repeatable-read" transactional guarantees provided by the OODB APIs.
     *
     * This is not an instantaneous property; the value of this property reflects the `DBProcessor`
     * state at the beginning of the current transaction, combined with any changes made by the
     * current transaction. In other words, this is a transactional view of the pending messages.
     *
     * Due to security considerations, it is expected that a database employing user-level security
     * will disallow most users from obtaining this information.
     *
     * @return the pending messages that are scheduled for processing
     */
    @RO List<Pending> pending;


    // ----- message processing --------------------------------------------------------------------

    /**
     * Process a message.
     *
     * Generally, this method is invoked by the database to process a message based on the schedule
     * information that was provided with the message when [schedule] was called. However, it is
     * also possible for application code to invoke this method directly within a transaction.
     *
     * @param message  the message to process
     */
    void process(Message message);

    /**
     * Process a group of messages. It may be more efficient to process a group of messages
     * together, and the database developer should implement this method if that is the case.
     *
     * @param messages  the previously scheduled messages to process; note that there are no
     *                  guarantees of uniqueness in the list of messages
     */
    void processAll(List<Message> messages)
        {
        for (Message message : messages)
            {
            process(message);
            }
        }

    /**
     * The specified message has failed to [process]; determine if the processing for the message
     * should be automatically retried at a later point in time, and in a different transaction.
     *
     * A database engine may stop re-trying the processing of the message at any point, and is
     * expected to automatically stop re-trying the processing if the message fails repeatedly,
     * particularly if the failure is exceptional.
     *
     * @param message         the message that failed to process; note that the failure could have
     *                        occurred during the [process] invocation itself, or during the
     *                        subsequent commit of the transaction
     * @param result          the result from the previous failed attempt, which is either a
     *                        commit failure indicated as a [CommitResult], or an `Exception`
     * @param when            the [Schedule] that caused the message to be processed
     * @param startTime       the DateTime that the failed processing of the message began
     * @param timesAttempted  the number of times `(n >= 1)` that the processing of this message has
     *                        been attempted, and has failed
     *
     * @return `True` indicates that the same message should be automatically scheduled to be
     *         processed again
     */
    Boolean autoRetry(Message                  message,
                      CommitResult | Exception result,
                      Schedule?                when,
                      DateTime                 startTime,
                      Int                      timesAttempted)
        {
        // TODO check repeating schedule+policy against start time and current time (abandon if it
        //      is ripe to run again)

        if (result.is(CommitResult))
            {
            switch (result)
                {
                case Committed:
                case DeferredFailed:
                case ConcurrentConflict:
                    // allow concurrency conflicts to retry indefinitely (up to the limit of the
                    // patience of the database engine)
                    return True;

                case PreviouslyClosed:
                case RollbackOnly:
                case ValidatorFailed:
                case RectifierFailed:
                case DistributorFailed:
                    break;

                default:
                case DatabaseError:
                    return False;
                }
            }

        // the default implementation of this method assumes that some number of retries may be
        // necessary due to concurrent transactions colliding or some other factor that could
        // (wishfully) disappear if the process is repeated
        return timesAttempted < maxAttempts;
        }

    /**
     * A suggested limit for the number of auto-retries to successfully [process] a message.
     */
    @RO Int maxAttempts = 3;

    /**
     * This method is invoked by the database engine when the message has failed to be processed,
     * and either [autoRetry] has returned `False`, or the database engine has made the decision to
     * stop automatically retrying the processing of this message.
     *
     * @param message         the message that failed to be processed and committed
     * @param result          the result from the last failed attempt, which is either a
     *                        commit failure indicated as a [CommitResult], or an `Exception`
     * @param when            the [Schedule] that caused the message to be processed
     * @param startTime       the DateTime that the failed processing of the message began
     * @param timesAttempted  the number of times that the processing of this message has been
     *                        attempted, and has failed
     */
    void abandon(Message                  message,
                 CommitResult | Exception result,
                 Schedule?                when,
                 DateTime                 startTime,
                 Int                      timesAttempted)
        {
        String messageString;
        try
            {
            messageString = message.toString();
            }
        catch (Exception e)
            {
            messageString = $"??? (Exception={e.text})";
            }

        dbLogFor<DBLog<String>>(Path:/sys/errors).add(
                $|Failed to process {messageString} due to\
                 | {result.is(CommitResult) ? "commit error" : "exception"} {result};\
                 | abandoning after {timesAttempted} attempts
                );
        }


    // ----- runtime management methods ------------------------------------------------------------

    /**
     * Suspend the processing of messages until a call is made to [resume].
     *
     * This is not an instantaneous method; the suspension only takes effect when the current
     * transaction is successfully committed.
     */
    void suspend();

    /**
     * Indicates whether the processing of messages has been suspended.
     *
     * This is not an instantaneous property; the value of this property reflects the suspension
     * state at the beginning of the current transaction (or the state of the suspension as the
     * result of this transaction).
     */
    @RO Boolean suspended;

    /**
     * Resume the processing of messages at some point after a call was made to [suspend].
     *
     * This is not an instantaneous method; the resumption only takes effect when the current
     * transaction is successfully committed.
     */
    void resume();


    // ----- annotations ---------------------------------------------------------------------------

    /**
     * The `@Dedupe` annotation indicates that the `DBProcessor` should _automatically_ remove
     * duplicate `Pending` entries, such that multiple entries for the same `Message` on the same
     * `DBProcessor` that -- at some point in time -- _could_ be processed as multiple invocations
     * of [process], would instead be processed as a single call to [process] that `Message`. This
     * may involve removing duplicates when they are scheduled with the DBProcessor, and it may also
     * involve de-duping the `Message`s to process when selecting from a backlog of [Pending] items.
     *
     * In the absence of this annotation, for each time that the same `Message` value is scheduled,
     * a separate call to [process] will occur (or the same `Message` will appear multiple times in
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
     * than one message per transaction; in other words, there should be only one call to this
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
     * "For this dbprocess, don't put any other dbprocessors' processing in with me"
     *
     * In the absence of this annotation, a database implementation _may_ choose to combine the
     * processing of multiple `DBProcessor` objects within a single transaction, for efficiency.
     */
    static mixin Isolated
            into DBProcessor
        {
        }


    // ----- pending message representation --------------------------------------------------------

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
    static const Pending<Message extends immutable Const>
            (
            Path      processor,
            Message   message,
            Schedule? schedule         = Null,
            Int       previousFailures = 0,
            )
        {
        /**
         * The path of the `DBProcessor`.
         */
        Path processor;

        /**
         * The `Message` to be processed.
         */
        Message message;

        /**
         * Determine the schedule of the invocation, if one has been specified.
         *
         * @return the [Schedule], or `Null` if the message should be processed as soon as
         *         possible
         */
        Schedule? schedule;

        /**
         * Determine if the pending invocation is auto-rescheduling (i.e. repeating).
         *
         * @return True if the invocation is auto-rescheduling, aka "repeating".
         * @return (conditional) the interval of repeating
         * @return (conditional) the policy for scheduling the message processing, when the previous
         *         processing of the same message (i.e. this same `Pending` object) has not yet
         *         completed
         */
        conditional (Duration repeatInterval, Policy repeatPolicy) isRepeating()
            {
            // TODO GG return schedule?.isRepeating() : False;
            if (Schedule schedule ?= schedule)
                {
                return schedule.isRepeating();
                }
            return False;
            }

        /**
         * Determine the priority of the pending message processing.
         */
        Priority priority.get()
            {
            return schedule?.priority : Normal;
            }

        /**
         * The number of times that this pending message processing has already been attempted, and
         * has failed.
         */
        Int previousFailures;
        }


    // ----- schedule representation ---------------------------------------------------------------

    /**
     * The supported priorities for scheduled messages.
     *
     * * High - An indication that the priority is higher than the default priority.
     * * Normal - The default priority.
     * * Low - An indication that the priority is lower than the default priority.
     * * WhenIdle - An indication that the processing should only occur when there appears to be
     *   a lack of (or a measurable lull in) other database activity. (This definition is
     *   purposefully lacking in explicitness.)
     */
    enum Priority {High, Normal, Low, WhenIdle}

    /**
     * Indicates how the repeating period is calculated.
     *
     * * AllowOverlapping - Calculate the next scheduled time based on the time that each
     *   instance of message processing was _supposed_ to begin; it is desired that the next
     *   scheduled processing of the message begin regardless of whether the previous processing
     *   of the message has completed.
     *
     * * SkipOverlapped - Skip the processing of the message if a previous processing of the
     *   message did not completed by the time that the processing of the message was to begin.
     *   This policy prevents a repeating schedule from inadvertently starting the processing of
     *   a message if that same message is already being concurrently processed as the result of
     *   the same schedule.
     *
     * * SuggestedMinimum - Defer the processing of the message as long as the previous
     *   processing of the message from this same repeating schedule has not completed.
     *   This policy prevents a repeating schedule from inadvertently starting the processing of
     *   a message if that same message is already being concurrently processed as the result of
     *   the same schedule.
     *
     * * MeasureFromCommit - calculate the next scheduled time based on the time that the
     *   previous run completes.
     *   This policy prevents a repeating schedule from inadvertently starting the processing of
     *   a message if that same message is already being concurrently processed as the result of
     *   the same schedule.
     */
    enum Policy
        {
        AllowOverlapping,
        SkipOverlapped,
        SuggestedMinimum,
        MeasureFromCommit,
        }

    /**
     * Represents the schedule for a message to be processed.
     */
    static const Schedule(DateTime? scheduledAt      = Null,
                          Time?     scheduledDaily   = Null,
                          Duration? repeatInterval   = Null,
                          Policy    repeatPolicy     = AllowOverlapping, // REVIEW SuggestedMinimum?
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
         * Determine if a specific time is set for the message to be processed.
         *
         * @return True iff the schedule indicates a specific point-in-time or daily-time that the
         *         pending message should be processed
         */
        Boolean isScheduled()
            {
            return scheduledAt != Null || scheduledDaily != Null;
            }

        /**
         * Determine if the message processing is scheduled to repeat automatically.
         *
         * @return True iff the schedule indicates automatic repeating of the message processing
         * @return (conditional) the interval of the repetition
         * @return (conditional) the policy governing the repetition, for example when the previous
         *         message processing has not completed before the next iteration would begin
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
    static interface DBChange<Message>
        {
        /**
         * The messages scheduled in this transaction to be processed later.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added within the transaction _may_ appear in the list.
         */
        List<Message> added;

        /**
         * The messages processed by this transaction.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently taken within the transaction _may_ appear in the list.
         */
        List<Message> removed;
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
            extends DBChange<Message>
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
