/**
 * A database connection.
 *
 * This interface is designed as the root injectable representation of an object database. Database
 * engines have dramatically differing designs and implementations, so an attempt to represent that
 * scope of variability in an interface would be challenging to say the least. Instead, to simplify
 * the actual usage of a database, one can imagine all of that work of starting and connecting to a
 * database (which may differ substantially in method and approach from one database engine to
 * another) will have already need to have been completed by the time that an application is able
 * to access that database, which is the point represented by this interface.
 *
 * A connection represents the union of the contents of the database (the "root of the schema") with
 * the potential for a transaction. This allows a database to be used in an "auto-commit" style,
 * which means either without transactionality, or with each mutating operation automatically
 * creating and committing a transaction around the mutation. Alternatively, the connection can be
 * used to create a transaction (up to one transaction at a time, without transaction nesting);
 * similar to the connection, the transaction represents the union of the contents of the database
 * (the "root of the schema") with the ability to commit or roll back the transaction.
 */
interface Connection<Schema extends RootSchema>
        extends RootSchema
        extends Closeable {
    /**
     * The [DBUser] that this `Connection` represents.
     */
    @RO DBUser dbUser;

    @Override
    @RO (Connection<Schema> + Schema) connection.get() = this.as(Connection<Schema> + Schema);

    /**
     * The current [Transaction] for this Connection, or `Null` if no `Transaction` is active.
     */
    @Override
    @RO (Transaction<Schema> + Schema)? transaction;

    /**
     * Create a new [Transaction] if one does not exist, or if a `Transaction` already exists, then
     * this creates and returns a _nested_ `Transaction`. With the `using` statement, it becomes
     * straightforward to manage transactions, without having to check if a `Transaction` already
     * exists:
     *
     *     using (con.createTransaction()) {
     *         // within this block, all db operations are performed within a transaction
     *         // ...
     *     }
     *
     * For each call to `createTransaction()`, the [Transaction.close] method must be called on the
     * returned result; this is performed automatically by the `using` statement, as in the above
     * example.
     *
     * For a nested `Transaction`:
     *
     * * A call to [Transaction.commit] has no effect if the `Transaction` has **not** been
     *   marked as [Transaction.rollbackOnly], and returns a failure code otherwise;
     * * A call to [Transaction.rollback] marks the enclosing `Transaction` as
     *   [Transaction.rollbackOnly];
     * * A call to [Transaction.close] will close the nested `Transaction`, and if that
     *   `Transaction` has not already been committed or rolled back, then an attempt to do so is
     *   performed automatically, committing in the absence of an exception and if
     *   [Transaction.rollbackOnly] is not indicated.
     *
     * If a `Transaction` already exists, the parameters to this method are ignored.
     *
     * @param id          (optional) an integer identifier to associate with the [Transaction]; if
     *                    none is specified, then one will be automatically provided using a
     *                    persistent counter (with no guarantees regarding contiguous identities)
     * @param name        (optional) a descriptive name to associate with the [Transaction]
     * @param priority    (optional) the [Transaction]'s priority
     * @param readOnly    (optional) pass True to indicate that [Transaction] is not going to modify
     *                    any data
     * @param timeout     (optional) the requested time-out, which allows the database to roll back
     *                    and discard the [Transaction] after that period of time has elapsed
     * @param retryCount  (optional) the number of times that this same [Transaction] has already
     *                    been attempted
     *
     * @return a new [Transaction] object
     */
    @Override
    (Transaction<Schema> + Schema) createTransaction(UInt?                  id          = Null,
                                                     String?                name        = Null,
                                                     DBTransaction.Priority priority    = Normal,
                                                     Boolean                readOnly    = False,
                                                     Duration?              timeout     = Null,
                                                     Int                    retryCount  = 0,
                                                    );

    /**
     * Create a new `Connection` instance, which is the same as this `Connection` instance with the
     * same `DBUser`, but **without** copying any in-flight `Transaction`.
     *
     * @return a new `Connection` to the same database as this `Connection`, and with the same user
     */
    Connection clone();

    @Override
    void close(Exception? e = Null) {
        if (Transaction tx ?= transaction) {
            if (tx.pending) {
                tx.rollback();
            }
            tx.close(e);
        }
    }
}
