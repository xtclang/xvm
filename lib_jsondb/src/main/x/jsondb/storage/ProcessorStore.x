import oodb.DBProcessor;
import oodb.RootSchema;
import oodb.Transaction;

import DBProcessor.Pending;
import DBProcessor.Schedule;

import Transaction.CommitResult;


/**
 * A "processor" storage API, to support DBProcessor objects.
 */
interface ProcessorStore<Message extends immutable Const> {
    /**
     * Schedule the specified message for processing.
     *
     * @param txId     the "write" transaction identifier
     * @param message  the message to process
     * @param when     the optional schedule for processing
     */
    void schedule(Int txId, Message message, Schedule? when);

    /**
     * Remove all instances of the specified message from the processing schedule.
     *
     * @param txId     the "write" transaction identifier
     * @param message  the message to process
     */
    void unschedule(Int txId, Message message);

    /**
     * Remove all instances of all messages from the processing schedule.
     *
     * @param txId     the "write" transaction identifier
     */
    void unscheduleAll(Int txId);

    /**
     * Obtain a list of pending PIDs.
     *
     * @param txId  the transaction identifier
     *
     * @return an array of PIDs
     */
    Int[] pidListAt(Int txId);

    /**
     * Obtain the information about a pending scheduled message.
     *
     * @param txId  the transaction identifier
     * @param pid   the process identifier
     *
     * @return the Pending object
     */
    Pending pending(Int txId, Int pid);

    /**
     * Determine if the DBProcessor is enabled.
     *
     * @param txId  the transaction identifier
     *
     * @return True iff the DBProcessor is enabled
     */
    Boolean isEnabled(Int txId);

    /**
     * Specify whether or not the DBProcessor is enabled.
     *
     * @param txId    the "write" transaction identifier
     * @param enable  False to suspend, or True to resume
     */
    void setEnabled(Int txId, Boolean enable);

    /**
     * Notify the store that an attempt to process a scheduled message has successfully completed.
     *
     * The Scheduler will call this method after the successful completion of a PID.
     *
     * @param txId     the "write" transaction identifier
     * @param message  the message
     * @param pid      the process id
     * @param elapsed  the interval of time that the processing consumed
     */
    void processCompleted(Int txId, Message message, Int pid, Range<Time> elapsed);

    /**
     * Notification of a decision to retry. Whether or not the transaction commits, the information
     * should be persisted by the ProcessorStore.
     *
     * @param txId     the "write" transaction identifier
     * @param message  the message
     * @param pid      the process id
     * @param elapsed  the interval of time that the processing consumed
     * @param result   the indication of the failure
     */
    void retryPending(Int txId, Message message, Int pid, Range<Time> elapsed, CommitResult | Exception result);

    /**
     * Notification of a decision to abandon. Whether or not the transaction commits, the
     * information should be persisted by the ProcessorStore.
     *
     * @param txId     the "write" transaction identifier
     * @param message  the message
     * @param pid      the process id
     * @param elapsed  the interval of time that the processing consumed
     * @param result   the indication of the failure
     */
    void abandonPending(Int txId, Message message, Int pid, Range<Time> elapsed, CommitResult | Exception result);
}