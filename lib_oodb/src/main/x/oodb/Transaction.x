/**
 * A database `Transaction`, as viewed from outside of the database. In other words, this is the
 * client interface for managing a database `Transaction`.
 *
 * A `Transaction` represents the union of the contents of the database (the "root of the schema")
 * with the ability to commit or roll back the `Transaction`.
 */
interface Transaction<Schema extends RootSchema>
        extends RootSchema
        extends Closeable {
    /**
     * The database [Connection]. This property is only guaranteed to be available from within a
     * [pending] `Transaction`.
     */
    @Override
    @RO (Connection<Schema> + Schema) connection;

    @Override
    @RO (Transaction<Schema> + Schema)? transaction.get() = connection.transaction;

    @Override
    (Transaction!<Schema> + Schema) createTransaction();

    /**
     * Represents the parameters used to create a `Transaction`.
     *
     * @param id          an integer identifier to associate with the `Transaction`
     * @param name        (optional) a descriptive name to associate with the `Transaction`
     * @param priority    (optional) the `Transaction` priority
     * @param readOnly    (optional) pass True to indicate that `Transaction` is not going to modify
     *                    any data
     * @param timeout     (optional) the requested time-out, which allows the database to roll back
     *                    and discard the `Transaction` after that period of time has elapsed;
     *                    `Null` indicates that the database's default time-out should be used, and
     *                    even if a time-out is specified, the database may use a shorter or longer
     *                    value
     * @param retryCount  (optional) the number of times that this same `Transaction` has already
     *                    been attempted
     */
    static const TxInfo(UInt                   id,
                        String?                name        = Null,
                        DBTransaction.Priority priority    = Normal,
                        Boolean                readOnly    = False,
                        Duration?              timeout     = Null,
                        Int                    retryCount  = 0,
                       ) {
        @Override
        String toString() {
            StringBuffer buf = new StringBuffer().append("TxInfo(");

            buf.append("id=")
               .append(id);

            if (name != Null) {
                buf.append(", name=")
                   .append(name);
            }

            if (priority != Normal) {
                buf.append(", priority=")
                   .append(priority);
            }

            if (readOnly) {
                buf.append(", readOnly");
            }

            if (timeout != Null) {
                buf.append(", timeout=")
                   .append(timeout);
            }

            if (retryCount != 0) {
                buf.append(", retryCount=")
                   .append(retryCount);
            }

            return buf.append(')').toString();
        }
    }

    /**
     * The parameters used to create this `Transaction` object.
     */
    @RO TxInfo txInfo;

    /**
     * `True` iff the `Transaction` is active and can theoretically be committed or rolled back.
     */
    @RO Boolean pending;

    /**
     * `True` iff the `Transaction` is nested.
     */
    @RO Boolean nested;

    /**
     * `True` indicates that the `Transaction` is not allowed to commit. Once set to `True`, this
     * cannot be reset to `False`.
     */
    Boolean rollbackOnly;

    /**
     * Indicates the result of a transaction commit attempt:
     *
     * * Committed - the transaction committed successfully
     * * PendingCommit - the transaction was nested, so the commit will not occur until each nested
     *   transaction completes and closes successfully, and then the outermost transaction commits
     * * PreviouslyClosed - the commit processing did not occur because the transaction was
     *   previously committed, rolled back, or otherwise closed/abandoned
     * * RollbackOnly - the transaction cannot commit because it has been marked as rollback-only
     * * DeferredFailed - a [deferred update](DBObject.defer) caused the commit to fail
     * * ConcurrentConflict - a concurrent transaction has committed data modifications that
     *   invalidate this transaction's assumptions, preventing this transaction from committing;
     *   this also includes a failure caused by a [required condition](DBObject.require) not holding
     * * ValidatorFailed - a pre-commit [DBObject.Validator] failed, or rejected the transaction
     * * RectifierFailed - a pre-commit [DBObject.Rectifier] failed, or rejected the transaction
     * * DistributorFailed - a pre-commit [DBObject.Distributor] failed, or rejected the transaction
     * * DatabaseError - the commit failed because of a failure in the database engine, which could
     *   include an exception while processing, storage constraints, I/O failure, or any other
     *   unexpected failure
     *
     * It is expected that the type of commit failure will indicate whether or not the transaction
     * can be retried (by replaying the same steps in a new transaction); for example, the
     * `ConcurrentConflict` result clearly indicates that a transaction can be retried.
     */
    enum CommitResult {
        Committed,
        PendingCommit,
        PreviouslyClosed,
        RollbackOnly,
        DeferredFailed,
        ConcurrentConflict,
        ValidatorFailed,
        RectifierFailed,
        DistributorFailed,
        DatabaseError,
    }

    /**
     * Attempt to commit the transaction. Committing the transaction involves a sequence of steps,
     * some of which may be performed concurrently with other transactions, depending on the
     * implementation of the database engine:
     *
     * * Verifying that the transaction has neither committed nor rolled back previously, and that
     *   it has not been marked as rollback-only;
     * * Verifying that the transaction has not been nested (since a nested commit is a no-op);
     * * Application of [deferred processing](DBObject.defer) (which can fail, invalidating this
     *   transaction);
     * * Evaluation of the impact of concurrent transactions that have committed (any of which could
     *   invalidate this transaction);
     * * Evaluation of the [registered requirements](DBObject.require) for this transaction, against
     *   the immediately preceding transaction, once this transaction's place in the sequence of
     *   committed transactions is determined;
     * * Evaluation of the [Validators](DBObject.Validator);
     * * Execution of the [Rectifiers](DBObject.Rectifier);
     * * Execution of the [Distributors](DBObject.Distributor);
     * * The remainder of the database engine's internal process of committing the data, including
     *   any I/O that it performs to achieve persistent storage of the information.
     *
     * Failure to commit a non-nested `Transaction` will cause the `Transaction` to be rolled back
     * automatically; a caller should expect that a non-nested `Transaction` will either be
     * committed or rolled back once this method completes.
     *
     * Committing a nested `Transaction` does nothing; however, the call can still fail if the
     * `Transaction` has already been marked as [rollbackOnly].
     *
     * @return `Committed` iff the commit succeeded; `PendingCommit` iff the transaction is nested
     *         and no commit failure is already known; otherwise an indication of the commit failure
     */
    CommitResult commit();

    /**
     * Roll back the `Transaction`. This allows the database to release any resources held by the
     * transaction.
     *
     * If the `Transaction` is nested, then this method will mark the transaction as [rollbackOnly],
     * but will not roll back the transaction.
     *
     * @return False iff the Transaction has already committed, or otherwise cannot be rolled back
     */
    Boolean rollback();

    @Override
    void close(Exception? e = Null) {
        if (pending) {
            Exception? failure = Null;

            if (e == Null && !rollbackOnly) {
                try {
                    commit();
                } catch (Exception commitFailed) {
                    failure = commitFailed;
                }
            }

            if (pending) {
                try {
                    rollback();
                } catch (Exception rollbackFailed) {
                    failure ?:= rollbackFailed;
                }
            }

            throw failure?;
            assert !pending;
        }
    }
}
