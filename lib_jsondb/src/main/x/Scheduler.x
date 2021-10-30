import collections.ArrayDeque;
import collections.SparseIntSet;

import oodb.DBProcessor;
import oodb.RootSchema;

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
 * Committing those DBProcessor ObjectStore instances
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
     * The status of the Scheduler.
     */
    public/private Status status = Initial;

    /**
     * The set of ids of DBProcessors.
     */
    protected/private Set<Int> processors;

    /**
     * TODO
     */
    protected/private Int pidCounter;

    /**
     * A pool of artificial clients for processing messages.
     */
    protected/private ArrayDeque<Client<Schema>> clientCache = new ArrayDeque();

    /**
     * TODO
     */
    protected/private SkiplistMap<>[] byPriority = new SkiplistMap[4](_ -> new SkiplistMap());

    // TODO ripe
    // TODO tracking in flight
//    by client
//    by dbo id
//    by process

    // TODO stats by dbo id
//    class DboStats
//        {
//        Int inFlight;
//        Int completed;
//        retries
//        failure by types (by commit result or exception -- and maybe which exception(s)?)
//        Int totalFailed;
//        Int exceptionCount;
//        }

    // TODO need some way to "drain" the scheduler, i.e. stop it from submitting new work to process


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
            case Initial:
                // TODO
                return True;

            case Enabled:
                return True;

            case Disabled:
                status = Enabled;
                // TODO
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


    // ----- message-processing client caching -----------------------------------------------------

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
    }