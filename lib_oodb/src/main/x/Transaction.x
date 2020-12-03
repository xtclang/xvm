/**
 * A database transaction, as viewed from outside of the database. In other words, this is the
 * client interface for managing a database transaction.
 *
 * A transaction represents the union of the contents of the database (the "root of the schema")
 * with the ability to commit or roll back the transaction.
 */
interface Transaction<Schema extends RootSchema>
        extends RootSchema
        extends Closeable
    {
    /**
     * The database connection. This property is only guaranteed to be available from within an
     * [pending] transaction.
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
