import collections.ArrayDeque;
import collections.SparseIntSet;

import oodb.DBProcessor;
import oodb.RootSchema;
import oodb.Transaction.CommitResult;

import Clock.Cancellable;
import Clock.Alarm;

import DBProcessor.Pending;
import DBProcessor.Policy;
import DBProcessor.Priority;
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
     * The next scheduled wake-up for the scheduler.
     */
    Cancellable? cancelWakeUp = Null;

    /**
     * TODO
     */
    Boolean busy = False;

    /**
     * The status of the Scheduler.
     */
    public/private Status status = Initial;

    /**
     * True whenever status is not enabled.
     */
    Boolean disabled.get()
        {
        return status != Enabled;
        }

    /**
     * TODO
     */
    Boolean databaseIdle;

    /**
     * The set of ids of DBProcessors.
     */
    protected/private Set<Int> processors;

    /**
     * A counter used to generate unique PID values. This will be initialized only when all of the
     * DBProcessor stores have contributed their scheduled items.
     */
    protected/private Int pidCounter = 0;

    /**
     * A pool of artificial clients for processing messages.
     */
    protected/private ArrayDeque<Client<Schema>> clientCache = new ArrayDeque();

    /**
     * This is the schedule. It contains all of the scheduled items, organized first by priority,
     * and within a priority, by DateTime.
     */
    protected/private SkiplistMap<DateTime, Process>[] byPriority = new SkiplistMap[4](_ -> new SkiplistMap());

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
    Boolean enable()
        {
        switch (status)
            {
            case Enabled:
                return True;

            case Initial:
                // TODO
                continue;
            case Disabled:
                // TODO
                status = Enabled;
                checkRipe();
                return True;

            case Closed:
                return False;
            }
        }

    /**
     * Verify that the transaction manager is open for client requests.
     *
     * @return True iff the transaction manager is enabled
     *
     * @throws IllegalState iff the transaction manager is not enabled
     */
    Boolean checkEnabled()
        {
        assert status == Enabled as $"Scheduler is not enabled (status={status})";
        return True;
        }

    /**
     * Stop the Scheduler from processing work.
     *
     * This method is intended to only be used by the [Catalog].
     *
     * @return True iff the Scheduler was successfully disabled
     */
    Boolean disable()
        {
        switch (status)
            {
            case Initial:
                status = Disabled;
                return True;

            case Enabled:
                // TODO
                return True;

            case Disabled:
            case Closed:
                return True;
            }
        }

    @Override
    void close(Exception? cause = Null)
        {
        if (status == Enabled)
            {
            disable();
            }

        status = Closed;
        }


    // ----- support for the ProcessorStore implementation -----------------------------------------

    /**
     * TODO
     */
    Int generatePid(Int dboId, Schedule? when)
        {
        return ++pidCounter;
        }

    /**
     * TODO
     */
    void registerPending(Int pid, Pending pending)
        {
        TODO
        }

    /**
     * TODO
     */
    void unregisterPending(Int pid)
        {
        TODO
        }

    /**
     * TODO
     */
    void unregisterPendingList(Int[] pids)
        {
        for (Int pid : pids)
            {
            unregisterPending(pid);
            }
        }


    // ----- Process state -------------------------------------------------------------------------

    /**
     * A Process contains all of the information about a pending message to process, or an in-flight
     * message being processed.
     */
    class Process(Int dboId, Int pid, DateTime when, Pending pending)
        {
        // TODO toString etc.

        /**
         * A linked list of PidState objects that all have the same priority and schedule DateTime.
         */
        @LinkedList Process? next = Null;
        }

// TODO need a way to track stats by dbo id
//    class DboStats
//        {
//        Int inFlight;
//        Int completed;
//        retries
//        failure by types (by commit result or exception -- and maybe which exception(s)?)
//        Int totalFailed;
//        Int exceptionCount;
//        }

    /**
     * Add a message to process into the schedule.
     *
     * @param process  specifies the message to process and the information about its schedule
     */
    void registerProcess(Process process)
        {
        assert byPid.putIfAbsent(process.pid, process);

        SkiplistMap<DateTime, Process> byDateTime = byPriority[process.pending.priority.ordinal];
        DateTime when = process.when;
        if (Process head := byDateTime.get(when))
            {
            head.&next.add(process);
            }
        else
            {
            byDateTime.put(when, process);
            }
        }

    /**
     * Remove a message to process from the schedule.
     *
     * @param process  specifies the message to process and the information about its schedule
     */
    void unregisterProcess(Process process)
        {
        assert byPid.remove(process.pid, process);

        SkiplistMap<DateTime, Process> byDateTime = byPriority[process.pending.priority.ordinal];
        DateTime when = process.when;
        assert Process head := byDateTime.get(when);
        if (&head == &process)
            {
            if (Process tail ?= process.next)
                {
                byDateTime.put(when, tail);
                }
            else
                {
                byDateTime.remove(when);
                }
            }
        else
            {
            head.&next.remove(process);
            }
        }


    // ----- message-processing client caching -----------------------------------------------------

    /**
     * Check for messages that are scheduled to be processed at or before the current point in time,
     * and process some of them if possible.
     */
    void checkRipe()
        {
        if (busy || disabled)
            {
            return;
            }

        busy = True;
        try
            {
            // this method will schedule its own subsequent wake-up
            if (Cancellable cancel ?= cancelWakeUp)
                {
                cancel();
                cancelWakeUp = Null;
                }

            DateTime cutoff = clock.now;
            // TODO GG: Processing: for (Priority priority : High..Idle)
            Processing: for (Priority priority : oodb.DBTransaction.Priority.High..oodb.DBTransaction.Priority.Idle)
                {
                if (priority == Idle && !databaseIdle)
                    {
                    break;
                    }

                SkiplistMap<DateTime, Process> byWhen = byPriority[priority.ordinal];
                if (DateTime first := byWhen.first(), first <= cutoff)
                    {
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
                                break;
                                }

                            if (disabled)
                                {
                                break Processing;
                                }

                            (Range<DateTime> elapsed, CommitResult | Exception result) = processOne(process);
                            if (result != Committed)
                                {
                                handleFailure(process, elapsed, result);
                                }
                            unregisterProcess(process);
                            }
                        }
                    }
                }
            }
        finally
            {
            busy = False;

            // schedule next processing
            DateTime? wakeUp = Null;
            if (!disabled)
                {
                // TODO GG for (Priority priority : High..Idle)
                for (Priority priority : oodb.DBTransaction.Priority.High..oodb.DBTransaction.Priority.Idle)
                    {
                    if (DateTime first := byPriority[priority.ordinal].first())
                        {
                        // TODO GG shoot me now
//                        if (wakeUp == Null || first < wakeUp)
//                            {
//                            wakeup = first;
//                            }
                        wakeUp = first < wakeUp? ? first : wakeUp : first;
                        }
                    }

                cancelWakeUp = clock.schedule(wakeUp?, checkRipe);
                }
            }
        }

    /**
     * Process the passed [Process] object.
     *
     * @param process  specifies the message to process and the DBProcessor to process it
     *
     * @return elapsed  the period of time consumed by the processing of the message
     * @return result   the result of the processing, indicating success or detailed failure
     */
    (Range<DateTime> elapsed, CommitResult | Exception result) processOne(Process process)
        {
        Client<Schema> client = allocateClient();
        try
            {
            // create a transaction
            client.createProcessTx(process.pending.previousFailures);

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
    (Range<DateTime> elapsed, Exception? failure) processOne(Client<Schema> client, Process process)
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
     */
    void handleFailure(Process process, Range<DateTime> elapsed, CommitResult | Exception result)
        {
        Client<Schema> client = allocateClient();
        try
            {
            Int attempts = process.pending.previousFailures + 1;
            client.processingFailed(process.dboId, process.pid, process.pending.message, result,
                    process.pending.schedule, elapsed, attempts, attempts > 8);
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
    Client<Schema> allocateClient()
        {
        return clientCache.takeOrCompute(() -> catalog.createClient(system=True));
        }

    /**
     * Return the passed Client object to the internal pool of Client objects.
     *
     * @param client  an "internal" Client object previously obtained from [allocateClient]
     */
    void recycleClient(Client<Schema> client)
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
    CommitResult | Exception completeTx(Client<Schema> client, Exception? failure)
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