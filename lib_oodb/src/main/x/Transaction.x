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
     * Commit the transaction.
     *
     * @return `True` iff the commit succeeded
     *
     * @throws IllegalState  if the Transaction has already rolled back
     */
    Boolean commit();

    /**
     * Roll back the transaction.
     *
     * @throws IllegalState  if the Transaction has already committed
     */
    void rollback();

    @Override
    void close()
        {
        commit();
        }
    }
