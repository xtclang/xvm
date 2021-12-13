import collections.ArrayDeque;
import collections.SparseIntSet;

import oodb.DBClosed;
import oodb.DBProcessor;
import oodb.DBTransaction.Priority;
import oodb.RootSchema;
import oodb.Transaction.CommitResult;

import storage.JsonNtxCounterStore;

import Clock.Cancellable;
import Clock.Alarm;

import DBProcessor.Pending;
import DBProcessor.Policy;
import DBProcessor.Schedule;

import TxManager.Status;


/**
 * The `Scheduler` service handles pending [DBProcessor] processing, which enables the deferred
 * asynchronous behavior ("asynchronous triggers") and the scheduled processing capabilities of the
 * database.
 *
 * During a transaction, including during the "deferred" portion, and the validate, rectify, and
 * distribution phases, items may be _scheduled_, i.e. added to the queues of various DBProcessor
 * objects in the database. Like any other uncommitted transactional data, this is collected within
 * the various ObjectStore instances related to those various DBProcessor instances, and held in
 * a [Changes](ObjectStore.Changes) record, associated with (i.e. keyed by) the uncommitted
 * transaction ID.
 *
 * The most difficult aspect of the DBProcessor / TxManager / Scheduler interaction to understand is
 * that the behavior is truly asynchronous (and concurrent), and simultaneously the behavior is
 * truly transactional. These two concepts are naturally in conflict. What this means is that, from
 * the point of view of the application, all changes to the state of the DBProcessor occur
 * transactionally. This includes pausing and resuming the DBProcessor, scheduling and rescheduling
 * and unscheduling messages, processing messages, deciding whether to retry messages, and
 * abandoning messages. Yet the processing is also occurring concurrently with the transactions that
 * are making those changes to the state of the schedule. As a result, the API has been carefully
 * defined to avoid treating each piece of schedule data as a uniquely addressable unit of
 * transactional data (i.e. one that could be directly manipulated as a singular item), even though
 * in reality that is how it is internally organized (using a unique identity called a "pid"). This
 * allows one transaction to be "unscheduling" a message, while the Scheduler is busy processing
 * that same message, without causing a conflict (because the behaviors are allowed to _compose_).
 *
 * To some extent, the Scheduler is a "third wheel" on a date: It's tacked on, but not particularly
 * relevant. For example, it does not have any persistent state of its own; instead, it relies on
 * the various ProcessorStore instances to manage their own state, which it then is given as part
 * of starting up the database. When a transaction commits, the Scheduler is not "in the loop"; all
 * of the necessary information (such as "the following pid has been processed") is captured and
 * stored by the various ProcessorStore instances, and only relayed to the Scheduler after-the-fact.
 * And when the database is shutting down, the careful coordination is between the TxMqnager and the
 * ObjectStores, and the Scheduler is pretty much on the sidelines, with any of its in-flight
 * processing at the risk of being aborted and rolled back by the TxManager. In other words, the
 * Scheduler is well-connected (to information) inside the database, and its life cycle is managed
 * by the database, but other than those aspects, it has no more "rights" than an application would.
 *
 * TODO on startup & recovery, all DBProcessors need to be started (so they dump their pending items
 *      onto the Scheduler)
 *
 * TODO need a way to track stats by dbo id
 *      class DboStats
 *          {
 *          Int inFlight;
 *          Int completed;
 *          retries
 *          failure by types (by commit result or exception -- and maybe which exception(s)?)
 *          Int totalFailed;
 *          Int exceptionCount;
 *          }
 *
 * TODO currently, the message processing is sequential, and not done in parallel across multiple
 *      clients, which is a temporary implementation (for simplification)
 */
@Concurrent
service Scheduler<Schema extends RootSchema>(Catalog<Schema> catalog)
        implements Closeable
    {
    construct(Catalog<Schema> catalog)
        {
        this.catalog    = catalog;
        this.clock      = catalog.clock;
        this.processors = catalog.metadata?.dbObjectInfos
                .filter(info -> info.category == DBProcessor)
                .map(info -> info.id, new SparseIntSet())
                .as(Set<Int>);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The clock shared by all of the services in the database.
     */
    Clock clock;

    /**
     * Cached wake-up time.
     */
    DateTime? wakeUp;

    /**
     * The next scheduled wake-up for the scheduler.
     */
    Cancellable? cancelWakeUp = Null;

    /**
     * Set to `True` while the Scheduler is busy processing messages.
     */
    Boolean busy = False;

    /**
     * The status of the Scheduler.
     */
    public/private Status status = Initial;

    /**
     * Evaluates to `True` when the database appears to be relatively idle.
     */
    Boolean databaseIdle;

    /**
     * The set of ids of DBProcessors.
     */
    protected/private Set<Int> processors;

    /**
     * The storage implementation for the pid counter.
     */
    @Lazy
    protected/private JsonNtxCounterStore pidCounter.calc()
        {
        return catalog.storeFor(Catalog.BuiltIn.PidCounter.id).as(JsonNtxCounterStore);
        }

    /**
     * A pool of artificial clients for processing messages.
     */
    protected/private ArrayDeque<Client<Schema>> clientCache = new ArrayDeque();

    /**
     * This is the schedule. It contains all of the scheduled items, organized first by priority,
     * and within a priority, by DateTime.
     */
    protected/private SkiplistMap<DateTime, Process>[] byPriority
            // TODO GG this doesn't work if Priority is a typedef
            = new SkiplistMap[Priority.count](_ -> new SkiplistMap());

    /**
     * This is the lookup of all of the schedule items, by PID.
     */
    protected/private SkiplistMap<Int, Process> byPid = new SkiplistMap();


    // ----- lifecycle management ------------------------------------------------------------------

    /**
     * Allow the Scheduler to process pending work.
     *
     * This method is intended to only be used by the [Catalog].
     *
     * @return True iff the Scheduler was successfully enabled
     */
    @Synchronized
    Boolean enable()
        {
        switch (status)
            {
            case Enabled:
                return True;

            case Disabled:
            case Initial:
                status       = Enabled;
                databaseIdle = False;
                scheduleAlarm();
                return True;

            case Closed:
                return False;
            }
        }

    /**
     * Stop the Scheduler from processing work.
     *
     * This method is intended to only be used by the [Catalog].
     *
     * @return True iff the Scheduler was successfully disabled
     */
    @Synchronized
    Boolean disable()
        {
        switch (status)
            {
            case Initial:
                status = Disabled;
                return True;

            case Enabled:
                cancelAlarm();
                return True;

            case Disabled:
            case Closed:
                return True;
            }
        }

    /**
     * True whenever status is not enabled.
     */
    Boolean disabled.get()
        {
        return status != Enabled;
        }

    @Override
    @Synchronized
    void close(Exception? cause = Null)
        {
        if (status == Enabled)
            {
            disable();
            }

        clearProcesses();
        status = Closed;
        }


    // ----- support for the ProcessorStore implementation -----------------------------------------

    /**
     * Last known status of a given pid.
     */
    enum PidStatus {New, Completed, Failed, Abandoned}

    /**
     * Allows a ProcessorStore to register a pid that was previously persisted in the database. This
     * occurs while the database is loading and/or recovering.
     *
     * @param dboId     the id of the DBProcessor
     * @param pid       the pid to register
     * @param status    the last known status of the pid
     * @param created   the datetime that the pid was created
     * @param previous  the last known elapsed period of time for the processing that resulted in
     *                  the passed status; `Null` if status is `New`
     * @param pending   the information about the message to process and the schedule to use
     * @param tries     the number of tries, iff the status is `Failed`
     */
    void preRegisterPid(Int              dboId,
                        Int              pid,
                        PidStatus        status,
                        DateTime         created,
                        Range<DateTime>? previous,
                        Pending          pending,
                        Int              tries = 0,
                       )
        {
        assert disabled;
        assert status == Failed ^^ tries == 0;
        assert status != Abandoned || pending.isRepeating();

        DateTime scheduled = calcSchedule(status, created, previous, pending);

        Process process = new Process(dboId, pid, created, scheduled, pending);
        if (status == Failed)
            {
            process.previousFailures = tries;
            }

        registerProcess(process);
        }

    /**
     * Allows a ProcessorStore to request a new unique pid. This is purposefully split out from the
     * [registerNewPending] operation, so that the ProcessorStore can claim the pid, update the
     * persistent storage accordingly, and not worry about any race conditions with the Scheduler.
     *
     * Note that the parameters passed in may be used in some way to color the pid value, but this
     * operation is **not** _registering_ the pid.
     *
     * @param dboId     the id of the DBProcessor
     * @param schedule  the optional schedule
     *
     * @return a persistable identity to use for a scheduled process
     */
    Int generatePid(Int dboId, Schedule? schedule)
        {
        return pidCounter.next();
        }

    /**
     * Allows a ProcessorStore to register a new pid with the associated pending message to
     * process.
     *
     * @param dboId    the id of the DBProcessor
     * @param pid      the PID to register
     * @param created  the date/time that the pid was created in the database
     * @param pending  the information about the message to process and the schedule to use
     */
    void registerPid(Int dboId, Int pid, DateTime created, Pending pending)
        {
        if (disabled)
            {
            return;
            }

        DateTime scheduled = calcSchedule(New, created, Null, pending);
        Process  process   = new Process(dboId, pid, created, scheduled, pending);
        registerProcess(process);

        if (scheduled < wakeUp? : True)
            {
            scheduleAlarm();
            }
        }

    /**
     * Allows a ProcessorStore to explicitly remove the registration of a pid.
     *
     * @param dboId  the id of the DBProcessor
     * @param pid    the PID to remove
     */
    void unregisterPid(Int dboId, Int pid)
        {
        if (disabled)
            {
            return;
            }

        assert Process process := byPid.get(pid), process.dboId == dboId;
        unregisterProcess(process);
        }

    /**
     * Allows a ProcessorStore to explicitly remove the registration of a bunch of pids.
     *
     * @param dboId  the id of the DBProcessor
     * @param pid    the PID to remove
     */
    void unregisterPids(Int dboId, Int[] pids)
        {
        if (disabled)
            {
            return;
            }

        for (Int pid : pids)
            {
            unregisterPid(dboId, pid);
            }
        }


    // ----- schedule helpers ----------------------------------------------------------------------

    /**
     * Determine when to schedule the pending item for.
     *
     * @param status    the last known status of the pid
     * @param created   the datetime that the pid was created
     * @param previous  the last known elapsed execution of the pid (if any)
     * @param pending   the information about the message to process and the schedule to use
     *
     * @return the datetime value at which the pending item should be scheduled
     */
    DateTime calcSchedule(PidStatus        status,
                          DateTime         created,
                          Range<DateTime>? previous,
                          Pending          pending,
                         )
        {
        DateTime now = clock.now;
        assert now >= created && now >= previous?.last;

        // failed attempts are automatically retried until they are abandoned
        if (status == Failed)
            {
            return now;
            }

        // check if there is no specified schedule (which means ASAP), or if the specified datetime
        // is in the past (this should cover probably 99% of cases)
        Schedule? schedule = pending.schedule;
        if (schedule == Null || !schedule.isRepeating())
            {
            return schedule?.scheduledAt? : now;
            }

        // if this is the first time to run, then calculate the first time that this should be run
        if (status == New)
            {
            // use the specified date/time, if there is one
            return schedule.scheduledAt?;

            // if there was no scheduled date/time, then it must be "schedule daily" at a specific
            // time (since we already covered the non-repeating and non-scheduled options above);
            // if we scheduled a daily run at 11:59, but we did so at 12:01, then we need to wait
            // until the next day (at 11:59) to run it
            assert Time daily ?= schedule.scheduledDaily;
            return created.with(
                    date = (created.time < daily ? created.date : created.date + Duration:1D),
                    time = daily);
            }

        // at this point, only Completed and Abandoned statuses remain, and they are both treated
        // the same, since we we are scheduling the next repetition of the processing
        assert (Duration repeatInterval, Policy repeatPolicy) := schedule.isRepeating();

        // previous run
        assert previous != Null;
        DateTime started  = previous.first;
        DateTime finished = previous.last;
        assert created <= started <= finished <= now;

        // attempt to determine if the previous run was an "unaligned initial run"
        if (DateTime initial ?= schedule.scheduledAt, Time daily ?= schedule.scheduledDaily)
            {
            assert created <= initial <= started;

            // this is a special case, in which something is scheduled at e.g. 6pm (18:00), but then
            // put on a daily schedule to run at midnight (12am, i.e. 00:00), so if the previous run
            // started between 6pm and midnight on the scheduledAt date (i.e. before the first of
            // the "repeating midnights"), then special handling is used
            DateTime secondRun = initial.with(
                    date = (initial.time < daily ? initial.date : initial.date + Duration:1D),
                    time = daily);

            if (started < secondRun)
                {
                switch (repeatPolicy)
                    {
                    case AllowOverlapping:
                        return secondRun;

                    case SkipOverlapped:
                        return finished > secondRun
                                ? finished.with(
                                    date = (finished.time < daily ? finished.date : finished.date + Duration:1D),
                                    time = daily)
                                : secondRun;

                    case SuggestedMinimum:
                    case MeasureFromCommit:
                        return finished > secondRun ? finished : secondRun;
                    }
                }
            }

        // complexities specific to daily repeating at a particular time
        if (repeatPolicy != MeasureFromCommit, Time daily ?= schedule.scheduledDaily)
            {
            DateTime originalPlan = started.with(
                    date = (started.time < daily ? started.date : started.date + Duration:1D),
                    time = daily);

            DateTime revisedPlan = finished.with(
                    date = (finished.time < daily ? finished.date : finished.date + Duration:1D),
                    time = daily);

            switch (repeatPolicy)
                {
                case AllowOverlapping:
                    return originalPlan;

                case SkipOverlapped:
                    return revisedPlan;

                case SuggestedMinimum:
                    return finished < originalPlan ? originalPlan : finished;
                }
            }

        // otherwise, repeating at a regular interval (but apply
        switch (repeatPolicy)
            {
            case AllowOverlapping:
                return started + repeatInterval;

            case SkipOverlapped:
                DateTime originalPlan = started + repeatInterval;
                if (originalPlan > finished)
                    {
                    return originalPlan;
                    }

                val gapSize   = (finished - started).picoseconds;
                val cycleSize = repeatInterval.picoseconds;
                Int cycles    = (gapSize / cycleSize + 1).toInt64();

                DateTime revisedPlan = started + repeatInterval * cycles;
                assert revisedPlan >= finished;
                return revisedPlan;

            case SuggestedMinimum:
                DateTime originalPlan = started + repeatInterval;
                return originalPlan > finished
                        ? originalPlan
                        : finished;

            case MeasureFromCommit:
                return finished + repeatInterval;
            }
        }


    // ----- Process state -------------------------------------------------------------------------

    /**
     * A Process contains all of the information about a pending message to process, or an in-flight
     * message being processed.
     */
    protected static class Process(Int dboId, Int pid, DateTime created, DateTime scheduled, Pending pending)
        {
        /**
         * A linked list of PidState objects that all have the same priority and schedule DateTime.
         */
        @LinkedList Process? next = Null;

        /**
         * The number of times that this pending message processing has already been attempted, and
         * has failed.
         */
        Int previousFailures;

        /**
         * True iff the process is a repeating process.
         */
        Boolean repeating.get()
            {
            return pending.isRepeating();
            }

        /**
         * True iff the repeating process allows process overlapping.
         */
        Boolean overlapping.get()
            {
            assert (_, Policy repeatPolicy) := pending.isRepeating();
            return repeatPolicy == AllowOverlapping;
            }

        @Override
        String toString()
            {
            return $|{this:class.name}:\{dboId={dboId}, pid={pid}, created={created},
                    | scheduled={scheduled}, pending={pending}}
                    ;
            }
        }

    /**
     * Add a message process to the Scheduler's registry, and add it to the schedule.
     *
     * @param process  specifies the message to process and the information about its schedule
     */
    protected void registerProcess(Process process)
        {
        assert byPid.putIfAbsent(process.pid, process);
        scheduleProcess(process);
        }

    /**
     * Add a message to process into the schedule.  This assumes that the process is already
     * registered with the Scheduler.
     *
     * @param process  specifies the message to process and the information about its schedule
     */
    protected void scheduleProcess(Process process)
        {
        SkiplistMap<DateTime, Process> byDateTime = byPriority[process.pending.priority.ordinal];
        DateTime scheduled = process.scheduled;
        if (Process head := byDateTime.get(scheduled))
            {
            head.&next.add(process);
            }
        else
            {
            byDateTime.put(scheduled, process);
            }
        }

    /**
     * Remove a message to process from the schedule. This does not unregister the process from the
     * Scheduler.
     *
     * @param process  specifies the message to process and the information about its schedule
     */
    protected void unscheduleProcess(Process process)
        {
        SkiplistMap<DateTime, Process> byDateTime = byPriority[process.pending.priority.ordinal];
        DateTime scheduled = process.scheduled;
        assert Process head := byDateTime.get(scheduled);
        if (&head == &process)
            {
            if (Process tail ?= process.next)
                {
                byDateTime.put(scheduled, tail);
                }
            else
                {
                byDateTime.remove(scheduled);
                }
            }
        else
            {
            head.&next.remove(process);
            }
        }

    /**
     * Remove a message to process from the schedule, and unregister it from the Scheduler.
     *
     * @param process  specifies the message to process and the information about its schedule
     */
    protected void unregisterProcess(Process process)
        {
        assert byPid.remove(process.pid, process);
        unscheduleProcess(process);
        }

    /**
     * Discard all of the schedule data.
     */
    protected void clearProcesses()
        {
        for (val map : byPriority)
            {
            map.clear();
            }
        byPid.clear();
        }


    // ----- message-processing --------------------------------------------------------------------

    /**
     * Check for messages that are scheduled to be processed at or before the current point in time,
     * and process some of them if possible.
     */
    protected void checkRipe()
        {
        if (busy)
            {
            return;
            }

        busy = True;
        try
            {
            // this method will schedule its own subsequent wake-up
            cancelAlarm();

            DateTime cutoff = clock.now;
            Processing: for (Priority priority : High..Idle)
                {
                if (priority == Idle && !databaseIdle)
                    {
                    break;
                    }

                SkiplistMap<DateTime, Process> byWhen = byPriority[priority.ordinal];
                if (DateTime first := byWhen.first(), first <= cutoff)
                    {
                    // calculate a limit of messages to process, with each lower priority degrading
                    // exponentially from the number being processed of the priority above it
                    Int limit = 1 << (priority.ordinal * 2);
                    Int count = 0;
                    for (Process firstProcess : byWhen[first..cutoff].values)
                        {
                        Process? nextProcess = firstProcess;
                        while (Process process ?= nextProcess)
                            {
                            nextProcess = process.next;

                            if (++count > limit)
                                {
                                // quota reached
                                continue Processing;
                                }

                            if (disabled)
                                {
                                break Processing;
                                }

                            Boolean repeats = process.repeating;
                            if (repeats)
                                {
                                // the current schedule for this message to process is about to be
                                // fulfilled, so remove it from the schedule, but keep the process
                                // because it's repeating and will be rescheduled
                                unscheduleProcess(process);
                                }
                            else
                                {
                                // it does not repeat, so remove it altogether
                                unregisterProcess(process);
                                }

                            DateTime? nextRun     = Null;
                            Boolean   overlapping = False;
                            if (repeats)
                                {
                                // estimate the next time this would be run, if all goes well (and
                                // assuming that it takes zero time to process the message)
                                DateTime now = clock.now;
                                nextRun = calcSchedule(Completed, process.created, now..now, process.pending);

                                overlapping = process.overlapping;
                                if (overlapping)
                                    {
                                    // schedule the following run right now, before we run the
                                    // current one
                                    process.scheduled = nextRun;
                                    scheduleProcess(process);
                                    }
                                }

                            // processing loop for the pending message (i.e. auto-retry loop)
                            Range<DateTime>          elapsed;
                            CommitResult | Exception result;
                            PidStatus                status;
                            while (True)
                                {
                                // attempt to process the pending message
                                (elapsed, result) = processOne(process);
                                if (result == Committed)
                                    {
                                    status = Completed;
                                    break;
                                    }

                                if (disabled || result.is(DBClosed))
                                    {
                                    return;
                                    }

                                status = Failed;
                                Int attempts = process.previousFailures + 1;
                                process.previousFailures = attempts;

                                // determine if the process should be abandoned
                                Boolean abandoning = attempts > 8;
                                abandoning |= handleFailure(process, elapsed, result, abandoning);
                                if (abandoning)
                                    {
                                    status = Abandoned;
                                    break;
                                    }
                                }

                            // the process loop is complete; if it is a non-overlapping repeating
                            // process, then re-schedule it based on the elapsed time of the last
                            // run
                            if (repeats && !overlapping)
                                {
                                process.scheduled = calcSchedule(status, process.created, elapsed, process.pending);
                                scheduleProcess(process);
                                }
                            }
                        }
                    }
                }
            }
        finally
            {
            busy = False;
            scheduleAlarm();
            }
        }

    /**
     * Calculate when the next scheduled item is ripe.
     *
     * @return the DateTime to set the alarm for, or Null
     */
    DateTime? calculateWakeup()
        {
        DateTime? when = Null;

        for (Priority priority : High..Idle)
            {
            if (DateTime first := byPriority[priority.ordinal].first())
                {
                 if (when == Null || first < when)
                     {
                     when = first;
                     }
                }
            }

        return when;
        }

    /**
     * Set the alarm for the next wake-up.
     */
    void scheduleAlarm()
        {
        // schedule next processing
        if (!disabled, DateTime wakeUp ?= calculateWakeup())
            {
            this.wakeUp       = wakeUp;
            this.cancelWakeUp = clock.schedule(wakeUp, checkRipe);
            }
        }

    /**
     * Turn off the alarm clock.
     */
    void cancelAlarm()
        {
        cancelWakeUp?();
        wakeUp       = Null;
        cancelWakeUp = Null;
        }

    /**
     * Process the passed [Process] object.
     *
     * @param process  specifies the message to process and the DBProcessor to process it
     *
     * @return elapsed  the period of time consumed by the processing of the message
     * @return result   the result of the processing, indicating success or detailed failure
     */
    protected (Range<DateTime> elapsed, CommitResult | Exception result) processOne(Process process)
        {
        Client<Schema> client = allocateClient();
        try
            {
            // create a transaction
            try
                {
                client.createProcessTx(process.previousFailures);
                }
            catch (DBClosed e)
                {
                DateTime now = clock.now;
                return (now..now, e);
                }

            // process the message
            (Range<DateTime> elapsed, Exception? failure) = processOne(client, process);

            // commit the transaction
            CommitResult | Exception result;
            if (failure == Null)
                {
                try
                    {
                    result = client.commitProcessTx();
                    }
                catch (Exception e)
                    {
                    result = e;
                    }
                }
            else
                {
                result = failure;
                }

            // on failure, roll back the transaction
            if (result != Committed)
                {
                client.rollbackProcessTx();
                }

            return elapsed, result;
            }
        finally
            {
            recycleClient(client);
            }
        }

    /**
     * Process the specified message using the provided client.
     *
     * @param client   the client to use to do the processing
     * @param process  specifies the message to process and the DBProcessor to process it
     *
     * @return elapsed  the period of time consumed by the processing of the message
     * @return failure  the exception if one occurred during processing the message, otherwise Null
     */
    protected (Range<DateTime> elapsed, Exception? failure) processOne(Client<Schema> client, Process process)
        {
        Pending pending = process.pending;

        Range<DateTime> elapsed;
        Exception?      failure = Null;
        DateTime        start   = clock.now;
        try
            {
            pending.Message message = pending.message;
            (elapsed, failure) = client.processMessage(process.dboId, process.pid, pending.message);
            }
        catch (Exception e)
            {
            client.rollbackProcessTx();
            elapsed = start..clock.now;
            failure = e;
            }

        return elapsed, failure;
        }

    /**
     * Plan a course of action based on the specified failure that occurred when processing a
     * message.
     *
     * @param process  the message that failed to process and the DBProcessor to process it
     * @param elapsed  the period of time consumed by the processing of the message
     * @param result   the result of the processing, indicating the reason for the failure
     * @param abandoning  True if the Scheduler has already decided to abandon the process
     *
     * @return True if the process should be abandoned
     */
    protected Boolean handleFailure(Process process, Range<DateTime> elapsed, CommitResult | Exception result, Boolean abandoning)
        {
        Client<Schema> client = allocateClient();
        try
            {
            return client.processingFailed(process.dboId, process.pid, process.pending.message,
                    result, process.pending.schedule, elapsed, process.previousFailures, abandoning);
            }
        finally
            {
            recycleClient(client);
            }
        }

    /**
     * Obtain a Client object from an internal pool of Client objects. These Client objects are used
     * to represent specific stages of transaction processing. When the use of the Client is
     * finished, it should be returned to the pool by passing it to [recycleClient].
     *
     * @return an "internal" Client object
     */
    protected Client<Schema> allocateClient()
        {
        return clientCache.takeOrCompute(() -> catalog.createClient(system=True));
        }

    /**
     * Return the passed Client object to the internal pool of Client objects.
     *
     * @param client  an "internal" Client object previously obtained from [allocateClient]
     */
    protected void recycleClient(Client<Schema> client)
        {
        return clientCache.reversed.add(client);
        }

    /**
     * Commit or roll back the transaction on the processing client.
     *
     * @param client   the processing client
     * @param failure  the exception, if any, thus far within the transaction
     *
     * @return Committed, if the transaction committed successfully, otherwise a CommitResult or
     *         Exception indicating the cause of the failure
     */
    protected CommitResult | Exception completeTx(Client<Schema> client, Exception? failure)
        {
        // commit the transaction
        CommitResult | Exception result;
        if (failure == Null)
            {
            try
                {
                result = client.commitProcessTx();
                }
            catch (Exception e)
                {
                result = e;
                }
            }
        else
            {
            result = failure;
            }

        // on failure, roll back the transaction
        if (result != Committed)
            {
            client.rollbackProcessTx();
            }

        return result;
        }
    }