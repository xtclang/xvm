/**
 * A database transaction, as viewed from outside of the database.
 */
interface Transaction<Schema extends RootSchema>
        extends RootSchema
        extends Closeable
    {
    /**
     * The database connection. This property is only available from within an `Active` transaction.
     */
    @RO (Connection<Schema> + Schema) connection;

    /**
     * True iff the transaction is active and can theoretically be committed or rolled back.
     */
    @RO Boolean pending;

    /**
     * Commit the transaction.
     *
     * @return `True` iff the commit succeeded
     *
     * @throws IllegalState  if the Transaction has already rolled back
     */
    Boolean commit();

    /**
     * Roll back the transaction. This allows the database to release any resources held by the
     * transaction.
     *
     * @throws IllegalState  if the Transaction has already committed
     */
    void rollback();

    @Override
    void close(Exception? e = Null)
        {
        if (pending)
            {
            if (e == Null)
                {
                commit();
                }
            else
                {
                rollback();
                }

            assert !pending;
            }
        }
    }
