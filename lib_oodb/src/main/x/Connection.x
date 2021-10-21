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
        extends Closeable
    {
    /**
     * The [DBUser] that this `Connection` represents.
     */
    @RO DBUser dbUser;

    /**
     * The current [Transaction] for this Connection, or `Null` if no `Transaction` is active.
     */
    @RO (Transaction<Schema> + Schema)? transaction;

    /**
     * Create a new transaction.
     *
     * @param id          (optional) an integer identifier to associate with the transaction; if
     *                    none is specified, then one will be automatically provided using a
     *                    persistent counter (with no guarantees regarding contiguous identities)
     * @param name        (optional) a descriptive name to associate with the transaction
     * @param priority    (optional) the transactional priority
     * @param readOnly    (optional) pass True to indicate that transaction is not going to modify
     *                    any data
     * @param timeout     (optional) the requested time-out, which allows the database to roll back
     *                    and discard the transaction after that period of time has elapsed
     * @param retryCount  (optional) the number of times that this same transaction has already been
     *                    attempted
     *
     * @return the [Transaction] object
     *
     * @throws IllegalState  if a Transaction already exists
     */
    (Transaction<Schema> + Schema) createTransaction(UInt?                  id          = Null,
                                                     String?                name        = Null,
                                                     DBTransaction.Priority priority    = Normal,
                                                     Boolean                readOnly    = False,
                                                     Duration?              timeout     = Null,
                                                     Int                    retryCount  = 0,
                                                    );

    @Override
    void close(Exception? e = Null)
        {
        if (Transaction tx ?= transaction)
            {
            if (tx.pending)
                {
                tx.rollback();
                }
            tx.close(e);
            }
        }
    }
