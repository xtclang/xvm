/**
 * A database connection.
 */
interface Connection<Schema extends RootSchema>
        extends RootSchema
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
     * @param timeout     the requested time-out, which allows the database to roll back and discard
     *                    the transaction after that period of time has elapsed
     * @param name        a descriptive name to associate with the transaction
     * @param id          an integer identifier to associate with the transaction
     * @param priority    the transactional priority
     * @param retryCount  the number of times that this same transaction has already been attempted
     *
     * @return the [Transaction] object
     *
     * @throws IllegalState  if a Transaction already exists
     */
    (Transaction<Schema> + Schema) createTransaction(Duration?              timeout     = Null,
                                                     String?                name        = Null,
                                                     UInt?                  id          = Null,
                                                     DBTransaction.Priority priority    = Normal,
                                                     Int                    retryCount  = 0);
    }
